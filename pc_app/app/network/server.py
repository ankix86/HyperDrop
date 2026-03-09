from __future__ import annotations

import asyncio
import base64
import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Awaitable, Callable

from app.core.config import save_config
from app.core.models import AppConfig, DeviceRecord
from app.crypto.key_exchange import KeyPair
from app.crypto.pairing import pairing_code_hash
from app.crypto.session_keys import derive_session_key
from app.network.protocol import ProtocolMessage
from app.network.transport import FramedTransport, Session
from app.transfer.receiver import TransferReceiver
from app.transfer.storage import SafeStorage
from app.utils.validators import is_safe_relative_path

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class ReceivedTransferContext:
    transfer_id: str
    checksums: dict[str, str]
    receiver: TransferReceiver


@dataclass(slots=True)
class IncomingTransferDecision:
    accepted: bool
    receive_dir: str
    rename_map: dict[str, str]
    accepted_paths: set[str]
    reason: str = ""
    remote_cancelled: bool = False


@dataclass(slots=True)
class ApprovedTransferContext:
    transfer_id: str
    receive_dir: str
    rename_map: dict[str, str]
    allowed_paths: set[str]
    sender_filters_selection: bool
    awaiting_manifest_decision: bool


class LanServer:
    def __init__(
        self,
        config: AppConfig,
        device_id: str,
        device_name: str,
        get_pairing_code: Callable[[], str],
        status_callback: Callable[[str], None] | None = None,
        incoming_transfer_handler: Callable[[str, str, list[dict]], Awaitable[IncomingTransferDecision]] | None = None,
    ) -> None:
        self.config = config
        self.device_id = device_id
        self.device_name = device_name
        self._get_pairing_code = get_pairing_code
        self._status_callback = status_callback
        self._incoming_transfer_handler = incoming_transfer_handler
        self._server: asyncio.AbstractServer | None = None
        self._active_sessions: set[Session] = set()

    def cancel_transfer(self) -> None:
        self._emit_status("Server Cancellation requested")
        for session in list(self._active_sessions):
            try:
                # A hard, immediate transport close is required here because 
                # asyncio cannot `send()` a cancel payload while the read buffer 
                # is permanently blocking on `recv()` during huge file chunks.
                # Closing the transport abruptly throws an exception in the 
                # reader thread, forcing the entire queue to abort instantly.
                session.transport.writer.close()
            except Exception:
                pass

    async def start(self) -> None:
        self._server = await asyncio.start_server(self._handle_client, self.config.bind_host, self.config.port)
        self._emit_status(f"Server listening on {self.config.bind_host}:{self.config.port}")

    async def stop(self) -> None:
        if self._server:
            self._server.close()
            await self._server.wait_closed()
            self._emit_status("Server stopped")

    def _emit_status(self, text: str) -> None:
        logger.info(text)
        if self._status_callback:
            self._status_callback(text)

    async def _handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        session = Session(FramedTransport(reader, writer))
        self._active_sessions.add(session)
        contexts: dict[str, ReceivedTransferContext] = {}
        approved_transfers: dict[str, ApprovedTransferContext] = {}
        received_any_transfer_data = False
        stage = "connected"

        try:
            hello = await session.recv()
            stage = "hello"
            if hello.msg_type != "hello":
                raise ValueError("Expected hello")
            peer_id = hello.payload["device_id"]
            peer_name = hello.payload.get("device_name", "Unknown")
            session.peer_device_id = peer_id

            if not await self._authenticate(peer_id, peer_name, session):
                return
            stage = "paired"
            await self._key_exchange(session)
            stage = "key_exchanged"
            await session.send("auth", {"ok": True, "device_id": self.device_id, "device_name": self.device_name})
            stage = "authed"

            while True:
                msg = await session.recv()
                stage = f"msg:{msg.msg_type}"
                if msg.msg_type == "ping":
                    await session.send("pong", {"ts": datetime.now(timezone.utc).isoformat()})
                    continue
                if msg.msg_type == "transfer_offer":
                    transfer_id = str(msg.payload["transfer_id"])
                    preview_entries = list(msg.payload.get("entries", []))
                    if preview_entries:
                        decision = await self._decide_incoming_transfer(
                            session=session,
                            transfer_id=transfer_id,
                            peer_name=peer_name,
                            preview_entries=preview_entries,
                        )
                        if decision.remote_cancelled:
                            return
                        if not decision.accepted:
                            await session.send(
                                "transfer_decline",
                                {"transfer_id": transfer_id, "reason": decision.reason or "Transfer declined"},
                            )
                            self._emit_status(f"Incoming transfer declined from {peer_name}")
                            return
                        approved_transfers[transfer_id] = ApprovedTransferContext(
                            transfer_id=transfer_id,
                            receive_dir=decision.receive_dir,
                            rename_map=dict(decision.rename_map),
                            allowed_paths=set(decision.accepted_paths),
                            sender_filters_selection=True,
                            awaiting_manifest_decision=False,
                        )
                        await session.send(
                            "transfer_accept",
                            {
                                "transfer_id": transfer_id,
                                "accepted_paths": sorted(decision.accepted_paths),
                            },
                        )
                    else:
                        # Older senders do not include preview entries in transfer_offer.
                        # Accept the handshake so they can send the manifest, then prompt
                        # the receiver before any file chunks are written.
                        approved_transfers[transfer_id] = ApprovedTransferContext(
                            transfer_id=transfer_id,
                            receive_dir=self.config.receive_dir,
                            rename_map={},
                            allowed_paths=set(),
                            sender_filters_selection=False,
                            awaiting_manifest_decision=self._incoming_transfer_handler is not None,
                        )
                        await session.send("transfer_accept", {"transfer_id": transfer_id})
                    continue
                if msg.msg_type == "manifest":
                    received_any_transfer_data = True
                    transfer_id = msg.payload["transfer_id"]
                    transfer_salt = base64.b64decode(msg.payload["transfer_salt_b64"])
                    approval = approved_transfers.get(transfer_id)
                    manifest_entries = [
                        entry
                        for entry in msg.payload.get("entries", [])
                        if isinstance(entry, dict) and entry.get("relative_path")
                    ]
                    if approval and approval.awaiting_manifest_decision:
                        preview_entries = [
                            {
                                "relative_path": str(entry["relative_path"]),
                                "file_name": str(entry.get("file_name") or Path(str(entry["relative_path"])).name),
                                "size": int(entry.get("size", 0)),
                                "is_directory": bool(entry.get("is_directory", False)),
                            }
                            for entry in manifest_entries
                        ]
                        decision = await self._decide_incoming_transfer(
                            session=session,
                            transfer_id=transfer_id,
                            peer_name=peer_name,
                            preview_entries=preview_entries,
                        )
                        if decision.remote_cancelled:
                            return
                        if not decision.accepted:
                            await session.send(
                                "transfer_error",
                                {"transfer_id": transfer_id, "reason": decision.reason or "Transfer declined"},
                            )
                            self._emit_status(f"Incoming transfer declined from {peer_name}")
                            return
                        approval.receive_dir = decision.receive_dir
                        approval.rename_map = dict(decision.rename_map)
                        approval.allowed_paths = set(decision.accepted_paths)
                        approval.awaiting_manifest_decision = False
                    receive_root = approval.receive_dir if approval else self.config.receive_dir
                    storage = SafeStorage(Path(receive_root))

                    manifest_paths = {
                        str(entry["relative_path"])
                        for entry in manifest_entries
                    }
                    if approval and approval.sender_filters_selection and approval.allowed_paths and manifest_paths != approval.allowed_paths:
                        raise ValueError("Incoming manifest does not match the approved file list")
                    if approval and not approval.sender_filters_selection and approval.allowed_paths:
                        unexpected_paths = approval.allowed_paths - manifest_paths
                        if unexpected_paths:
                            raise ValueError("Approved file selection does not match the incoming manifest")
                    skip_paths = set()
                    if approval and not approval.sender_filters_selection and approval.allowed_paths:
                        skip_paths = manifest_paths - approval.allowed_paths

                    def _make_progress(emit) -> Callable[[str, int, int], None]:
                        def _cb(rel: str, recv: int, total: int) -> None:
                            emit(f"PROGRESS:{rel}:{recv}:{total}")
                        return _cb

                    def _make_file_complete(emit) -> Callable[[str, str, Path, int], None]:
                        def _cb(rel: str, _target_rel: str, final_path: Path, total: int) -> None:
                            emit(
                                "RECEIVE_SESSION:"
                                + json.dumps(
                                    {
                                        "type": "file_done",
                                        "transfer_id": transfer_id,
                                        "relative_path": rel,
                                        "open_path": str(final_path),
                                        "size": int(total),
                                    }
                                )
                            )

                        return _cb

                    receiver = TransferReceiver(
                        session,
                        self._encryptor(session, transfer_salt),
                        storage,
                        on_progress=_make_progress(self._emit_status),
                        on_file_complete=_make_file_complete(self._emit_status),
                        rename_map=approval.rename_map if approval else None,
                        skip_paths=skip_paths,
                    )
                    checksums: dict[str, str] = {}
                    sizes: dict[str, int] = {}
                    for e in manifest_entries:
                        if not is_safe_relative_path(e["relative_path"]):
                            raise ValueError("Unsafe path in manifest")
                        if e["relative_path"] in skip_paths:
                            continue
                        if e["is_directory"]:
                            storage.ensure_directory(e["relative_path"])
                        else:
                            checksums[e["relative_path"]] = e.get("checksum", "")
                            sizes[e["relative_path"]] = int(e.get("size", 0))
                    receiver.set_sizes(sizes)
                    self._emit_status(
                        "RECEIVE_SESSION:"
                        + json.dumps(
                            {
                                "type": "start",
                                "transfer_id": transfer_id,
                                "target_dir": str(receive_root),
                                "files": [
                                    {
                                        "relative_path": str(entry["relative_path"]),
                                        "file_name": str(
                                            entry.get("file_name") or Path(str(entry["relative_path"])).name
                                        ),
                                        "size": int(entry.get("size", 0)),
                                    }
                                    for entry in manifest_entries
                                    if not bool(entry.get("is_directory", False))
                                    and str(entry["relative_path"]) not in skip_paths
                                ],
                            }
                        )
                    )
                    contexts[transfer_id] = ReceivedTransferContext(transfer_id, checksums, receiver)
                    continue
                if msg.msg_type == "file_chunk":
                    received_any_transfer_data = True
                    transfer_id = msg.payload["transfer_id"]
                    rel = msg.payload["relative_path"]
                    ctx = contexts[transfer_id]
                    await ctx.receiver.handle_chunk(msg.payload, expected_checksum=ctx.checksums.get(rel))
                    continue
                if msg.msg_type == "transfer_complete":
                    self._emit_status(
                        "RECEIVE_SESSION:"
                        + json.dumps(
                            {
                                "type": "complete",
                                "transfer_id": str(msg.payload["transfer_id"]),
                            }
                        )
                    )
                    self._emit_status(f"Transfer complete: {msg.payload['transfer_id']}")
                    contexts.pop(msg.payload["transfer_id"], None)
                    approved_transfers.pop(msg.payload["transfer_id"], None)
                    continue
                if msg.msg_type == "resume_request":
                    await session.send("resume_response", {"files": []})
                    continue
                if msg.msg_type == "cancel":
                    cancelled_id = str(msg.payload.get("transfer_id", ""))
                    self._emit_status(
                        "RECEIVE_SESSION:"
                        + json.dumps(
                            {
                                "type": "cancelled",
                                "transfer_id": cancelled_id,
                            }
                        )
                    )
                    contexts.pop(msg.payload.get("transfer_id", ""), None)
                    approved_transfers.pop(msg.payload.get("transfer_id", ""), None)
                    self._emit_status("PROGRESS:cancelled:0:0")
                    continue
                await session.send("transfer_error", {"reason": f"Unhandled message {msg.msg_type}"})
        except asyncio.IncompleteReadError:
            # A clean remote close after transfer completion is expected and should not look like an error.
            if not received_any_transfer_data:
                self._emit_status(f"Peer disconnected before transfer started (stage={stage})")
            else:
                self._emit_status("PROGRESS:error:0:0")
        except Exception as exc:
            logger.exception("Client handler failed")
            transfer_id = next(iter(contexts.keys()), "")
            if transfer_id:
                self._emit_status(
                    "RECEIVE_SESSION:"
                    + json.dumps(
                        {
                            "type": "error",
                            "transfer_id": transfer_id,
                        }
                    )
                )
            self._emit_status("PROGRESS:error:0:0")
            self._emit_status(f"Session error (stage={stage}): {exc}")
            try:
                await session.send("transfer_error", {"reason": str(exc)})
            except Exception:
                pass
        finally:
            self._active_sessions.discard(session)
            await session.transport.close()

    async def _authenticate(self, peer_id: str, peer_name: str, session: Session) -> bool:
        # No pairing required — auto-accept all discovered peers
        await session.send("pair_confirm", {"trusted": True, "device_id": self.device_id, "device_name": self.device_name})
        self._emit_status(f"Connected with device: {peer_name}")
        return True

    async def _decide_incoming_transfer(
        self,
        session: Session,
        transfer_id: str,
        peer_name: str,
        preview_entries: list[dict],
    ) -> IncomingTransferDecision:
        if self._incoming_transfer_handler is None or not preview_entries:
            allowed_paths = {
                str(entry.get("relative_path", ""))
                for entry in preview_entries
                if isinstance(entry, dict) and entry.get("relative_path")
            }
            return IncomingTransferDecision(
                accepted=True,
                receive_dir=self.config.receive_dir,
                rename_map={},
                accepted_paths=allowed_paths,
            )
        decision_task = asyncio.create_task(self._incoming_transfer_handler(transfer_id, peer_name, preview_entries))
        recv_task = asyncio.create_task(session.recv())
        try:
            while True:
                done, _ = await asyncio.wait({decision_task, recv_task}, return_when=asyncio.FIRST_COMPLETED)
                if decision_task in done:
                    recv_task.cancel()
                    await asyncio.gather(recv_task, return_exceptions=True)
                    return decision_task.result()

                try:
                    msg = recv_task.result()
                except (asyncio.IncompleteReadError, ConnectionResetError, BrokenPipeError, OSError):
                    decision_task.cancel()
                    await asyncio.gather(decision_task, return_exceptions=True)
                    return IncomingTransferDecision(
                        accepted=False,
                        receive_dir=self.config.receive_dir,
                        rename_map={},
                        accepted_paths=set(),
                        reason="Sender closed the session",
                        remote_cancelled=True,
                    )

                if msg.msg_type == "cancel":
                    cancelled_id = str(msg.payload.get("transfer_id", "")).strip()
                    if not cancelled_id or cancelled_id == transfer_id:
                        decision_task.cancel()
                        await asyncio.gather(decision_task, return_exceptions=True)
                        return IncomingTransferDecision(
                            accepted=False,
                            receive_dir=self.config.receive_dir,
                            rename_map={},
                            accepted_paths=set(),
                            reason="Sender closed the session",
                            remote_cancelled=True,
                        )
                raise RuntimeError(f"Unexpected message {msg.msg_type} while awaiting incoming transfer approval")
        finally:
            if not decision_task.done():
                decision_task.cancel()
            if not recv_task.done():
                recv_task.cancel()

    async def _key_exchange(self, session: Session) -> None:
        keypair = KeyPair()
        await session.transport.send_message(
            ProtocolMessage("key_exchange", {"public_key_b64": base64.b64encode(keypair.public_bytes()).decode("ascii")})
        )
        peer = await session.recv()
        if peer.msg_type != "key_exchange":
            raise ValueError("Expected key_exchange")
        shared = keypair.shared_secret(base64.b64decode(peer.payload["public_key_b64"]))
        peer_id = session.peer_device_id or "unknown"
        transcript = "|".join(sorted([self.device_id, peer_id])).encode("utf-8")
        session.session_key = derive_session_key(shared, transcript)

    @staticmethod
    def _encryptor(session: Session, transfer_salt: bytes):
        from app.crypto.encryptor import ChunkEncryptor

        if not session.session_key:
            raise RuntimeError("Session key missing")
        return ChunkEncryptor(session.session_key, transfer_salt)
