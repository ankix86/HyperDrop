from __future__ import annotations

import asyncio
import base64
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

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


class LanServer:
    def __init__(
        self,
        config: AppConfig,
        device_id: str,
        device_name: str,
        get_pairing_code: Callable[[], str],
        status_callback: Callable[[str], None] | None = None,
    ) -> None:
        self.config = config
        self.device_id = device_id
        self.device_name = device_name
        self._get_pairing_code = get_pairing_code
        self._status_callback = status_callback
        self._server: asyncio.AbstractServer | None = None

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
        contexts: dict[str, ReceivedTransferContext] = {}
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
                    await session.send("transfer_accept", {"transfer_id": msg.payload["transfer_id"]})
                    continue
                if msg.msg_type == "manifest":
                    received_any_transfer_data = True
                    transfer_id = msg.payload["transfer_id"]
                    transfer_salt = base64.b64decode(msg.payload["transfer_salt_b64"])
                    storage = SafeStorage(Path(self.config.receive_dir))
                    receiver = TransferReceiver(session, self._encryptor(session, transfer_salt), storage)
                    checksums: dict[str, str] = {}
                    for e in msg.payload["entries"]:
                        if not is_safe_relative_path(e["relative_path"]):
                            raise ValueError("Unsafe path in manifest")
                        if e["is_directory"]:
                            storage.ensure_directory(e["relative_path"])
                        else:
                            checksums[e["relative_path"]] = e.get("checksum", "")
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
                    self._emit_status(f"Transfer complete: {msg.payload['transfer_id']}")
                    contexts.pop(msg.payload["transfer_id"], None)
                    continue
                if msg.msg_type == "resume_request":
                    await session.send("resume_response", {"files": []})
                    continue
                if msg.msg_type == "cancel":
                    contexts.pop(msg.payload.get("transfer_id", ""), None)
                    continue
                await session.send("transfer_error", {"reason": f"Unhandled message {msg.msg_type}"})
        except asyncio.IncompleteReadError:
            # A clean remote close after transfer completion is expected and should not look like an error.
            if not received_any_transfer_data:
                self._emit_status(f"Peer disconnected before transfer started (stage={stage})")
        except Exception as exc:
            logger.exception("Client handler failed")
            self._emit_status(f"Session error (stage={stage}): {exc}")
            try:
                await session.send("transfer_error", {"reason": str(exc)})
            except Exception:
                pass
        finally:
            await session.transport.close()

    async def _authenticate(self, peer_id: str, peer_name: str, session: Session) -> bool:
        rec = self.config.trusted_devices.get(peer_id)
        if rec and rec.trusted:
            rec.last_seen = datetime.now(timezone.utc).isoformat()
            save_config(self.config)
            await session.send("pair_confirm", {"trusted": True, "device_id": self.device_id, "device_name": self.device_name})
            self._emit_status(f"Pairing confirmed with trusted device: {peer_name}")
            return True

        await session.send("pair_request", {"device_id": self.device_id, "device_name": self.device_name, "reason": "pairing required"})
        reply = await session.recv()
        if reply.msg_type != "pair_confirm":
            await session.send("auth", {"ok": False, "reason": "Pairing not confirmed"})
            return False
        code = reply.payload.get("pairing_code", "")
        expected = self._get_pairing_code()
        if not expected or code != expected:
            await session.send("auth", {"ok": False, "reason": "Invalid pairing code"})
            return False

        self.config.trusted_devices[peer_id] = DeviceRecord(
            device_id=peer_id,
            name=peer_name,
            trusted=True,
            last_seen=datetime.now(timezone.utc).isoformat(),
            pairing_token_hash=pairing_code_hash(code),
        )
        save_config(self.config)
        await session.send("pair_confirm", {"trusted": True})
        self._emit_status(f"Pairing confirmed with new device: {peer_name}")
        return True

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
