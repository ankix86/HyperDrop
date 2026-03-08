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


class LanClient:
    def __init__(
        self,
        config: AppConfig,
        device_id: str,
        device_name: str,
        pairing_code_provider: Callable[[], str],
        status_callback: Callable[[str], None] | None = None,
    ) -> None:
        self.config = config
        self.device_id = device_id
        self.device_name = device_name
        self._pairing_code_provider = pairing_code_provider
        self._status_callback = status_callback

    async def send_task(self, task: TransferTask) -> None:
        reader, writer = await asyncio.open_connection(task.target_ip, task.target_port)
        session = Session(FramedTransport(reader, writer))
        try:
            session.peer_device_id = task.receiver_device_id
            await session.send("hello", {"device_id": self.device_id, "device_name": self.device_name})
            await self._maybe_pair(session)
            await self._key_exchange(session)

            auth = await session.recv()
            if auth.msg_type != "auth" or not auth.payload.get("ok"):
                raise RuntimeError(f"Auth failed: {auth.payload}")

            manifest, source_map = build_manifest(task.source_paths, self.device_id, task.receiver_device_id)
            transfer_salt = secrets.token_bytes(8)

            await session.send("transfer_offer", {"transfer_id": manifest.transfer_id})
            accepted = await session.recv()
            if accepted.msg_type != "transfer_accept":
                raise RuntimeError("Transfer not accepted")

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

            sender = TransferSender(session, ChunkEncryptor(session.session_key, transfer_salt))
            for entry in manifest.entries:
                if entry.is_directory:
                    continue
                await sender.send_file(manifest.transfer_id, entry.relative_path, source_map[entry.relative_path])

            await session.send("transfer_complete", {"transfer_id": manifest.transfer_id})
            self._emit_status(f"Transfer complete: {manifest.transfer_id}")
        finally:
            await session.transport.close()

    async def _maybe_pair(self, session: Session) -> None:
        first = await session.recv()
        if first.msg_type == "pair_request":
            await session.send("pair_confirm", {"pairing_code": self._pairing_code_provider() or ""})
            result = await session.recv()
            if result.msg_type == "auth":
                reason = result.payload.get("reason") or "Pairing rejected"
                raise RuntimeError(str(reason))
            if result.msg_type != "pair_confirm" or not result.payload.get("trusted"):
                reason = result.payload.get("reason") if isinstance(result.payload, dict) else None
                raise RuntimeError(str(reason or "Pairing failed"))
            self._emit_status("Pairing confirmed with remote device")
            return
        if first.msg_type == "pair_confirm":
            self._emit_status("Pairing confirmed with remote device")
            return
        raise RuntimeError(f"Unexpected pairing message: {first.msg_type}")

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
