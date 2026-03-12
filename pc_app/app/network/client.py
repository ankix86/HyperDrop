from __future__ import annotations

import asyncio
import base64
import logging
import secrets
from typing import Callable

from app.core.models import AppConfig, TransferTask
from app.crypto.key_exchange import KeyPair
from app.crypto.session_keys import derive_session_key
from app.network.transport import FramedTransport, Session
from app.transfer.manifest import build_manifest
from app.transfer.sender import TransferSender

logger = logging.getLogger(__name__)


class TransferDeclinedError(RuntimeError):
    """Raised when the remote receiver explicitly declines the transfer."""


def _filter_transfer_payload(manifest, source_map: dict, accepted_paths: set[str]) -> tuple:
    if not accepted_paths:
        return manifest, source_map

    filtered_entries = [entry for entry in manifest.entries if entry.relative_path in accepted_paths]
    filtered_source_map = {
        path: source
        for path, source in source_map.items()
        if path in accepted_paths
    }
    manifest.entries = filtered_entries
    return manifest, filtered_source_map


class LanClient:
    def __init__(
        self,
        config: AppConfig,
        device_id: str,
        device_name: str,
        status_callback: Callable[[str], None] | None = None,
    ) -> None:
        self.config = config
        self.device_id = device_id
        self.device_name = device_name
        self._status_callback = status_callback
        self._cancelled = False
        self._active_session: Session | None = None

    def cancel_transfer(self) -> None:
        self._emit_status("Client Cancellation requested")
        self._cancelled = True
        if self._active_session:
            try:
                self._active_session.transport.writer.close()
            except Exception:
                pass

    async def send_task(self, task: TransferTask) -> None:
        self._cancelled = False
        reader, writer = await asyncio.open_connection(task.target_ip, task.target_port)
        session = Session(FramedTransport(reader, writer))
        self._active_session = session
        try:
            session.peer_device_id = task.receiver_device_id
            await session.send("hello", {"device_id": self.device_id, "device_name": self.device_name})
            await self._receive_server_identity(session)
            await self._key_exchange(session)

            auth = await session.recv()
            if auth.msg_type != "auth" or not auth.payload.get("ok"):
                raise RuntimeError(f"Auth failed: {auth.payload}")

            manifest, source_map = build_manifest(task.source_paths, self.device_id, task.receiver_device_id)
            transfer_salt = secrets.token_bytes(8)

            await session.send(
                "transfer_offer",
                {
                    "transfer_id": manifest.transfer_id,
                    "entries": [
                        {
                            "relative_path": e.relative_path,
                            "file_name": e.file_name,
                            "size": e.size,
                            "is_directory": e.is_directory,
                        }
                        for e in manifest.entries
                    ],
                },
            )
            self._emit_status("SENDER_REQUEST:waiting")
            accepted = await session.recv()
            if accepted.msg_type == "transfer_decline":
                self._emit_status("SENDER_REQUEST:rejected")
                reason = accepted.payload.get("reason") if isinstance(accepted.payload, dict) else None
                raise TransferDeclinedError(str(reason or "Transfer declined by receiver"))
            if accepted.msg_type != "transfer_accept":
                self._emit_status("SENDER_REQUEST:rejected")
                raise RuntimeError("Transfer not accepted")
            self._emit_status("SENDER_REQUEST:accepted")
            accepted_paths = {
                str(path)
                for path in accepted.payload.get("accepted_paths", [])
                if str(path).strip()
            }
            manifest, source_map = _filter_transfer_payload(manifest, source_map, accepted_paths)

            await session.send(
                "manifest",
                {
                    "transfer_id": manifest.transfer_id,
                    "sender_device_id": manifest.sender_device_id,
                    "receiver_device_id": manifest.receiver_device_id,
                    "transfer_salt_b64": base64.b64encode(transfer_salt).decode("ascii"),
                    "entries": [
                        {
                            "relative_path": e.relative_path,
                            "file_name": e.file_name,
                            "mime_type": e.mime_type,
                            "size": e.size,
                            "modified_time": e.modified_time,
                            "checksum": e.checksum,
                            "is_directory": e.is_directory,
                        }
                        for e in manifest.entries
                    ],
                },
            )

            from app.crypto.encryptor import ChunkEncryptor

            def _progress(rel: str, sent: int, total: int) -> None:
                self._emit_status(f"PROGRESS:{rel}:{sent}:{total}")

            def _check_cancel() -> bool:
                return self._cancelled

            sender = TransferSender(
                session,
                ChunkEncryptor(session.session_key, transfer_salt),
                on_progress=_progress,
                check_cancel=_check_cancel,
            )
            for entry in manifest.entries:
                if self._cancelled:
                    raise asyncio.CancelledError("Transfer cancelled by user")
                if entry.is_directory:
                    continue
                self._emit_status(f"Sending: {entry.relative_path}")
                await sender.send_file(manifest.transfer_id, entry.relative_path, source_map[entry.relative_path])

            if self._cancelled:
                raise asyncio.CancelledError("Transfer cancelled by user")

            await session.send("transfer_complete", {"transfer_id": manifest.transfer_id})
            self._emit_status(f"Transfer complete: {manifest.transfer_id}")
            
        except Exception as e:
            if self._cancelled:
                raise asyncio.CancelledError() from e
            raise
        finally:
            self._active_session = None
            try:
                await session.transport.close()
            except Exception:
                pass

    async def _receive_server_identity(self, session: Session) -> None:
        first = await session.recv()
        if first.msg_type == "pair_confirm":
            if "device_id" in first.payload:
                session.peer_device_id = first.payload["device_id"]
            self._emit_status("Connected with remote device")
            return
        raise RuntimeError(f"Unexpected handshake message: {first.msg_type}")

    async def _key_exchange(self, session: Session) -> None:
        server = await session.recv()
        if server.msg_type != "key_exchange":
            raise RuntimeError("Expected key_exchange from server")
        peer_public = base64.b64decode(server.payload["public_key_b64"])

        keypair = KeyPair()
        shared = keypair.shared_secret(peer_public)
        peer_id = session.peer_device_id or "unknown"
        transcript = "|".join(sorted([self.device_id, peer_id])).encode("utf-8")
        session.session_key = derive_session_key(shared, transcript)

        await session.send("key_exchange", {"public_key_b64": base64.b64encode(keypair.public_bytes()).decode("ascii")})

    def _emit_status(self, text: str) -> None:
        logger.info(text)
        if self._status_callback:
            self._status_callback(text)
