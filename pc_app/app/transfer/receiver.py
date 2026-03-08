from __future__ import annotations

import base64
import hashlib
from pathlib import Path

from app.crypto.encryptor import ChunkEncryptor
from app.network.transport import Session
from app.transfer.storage import SafeStorage


class _ReceiveState:
    def __init__(self, temp_path: Path):
        self.temp_path = temp_path
        self.handle = temp_path.open("wb")

    def close(self) -> None:
        self.handle.close()


class TransferReceiver:
    def __init__(self, session: Session, encryptor: ChunkEncryptor, storage: SafeStorage):
        self.session = session
        self.encryptor = encryptor
        self.storage = storage
        self._active: dict[str, _ReceiveState] = {}

    async def handle_chunk(self, payload: dict, expected_checksum: str | None = None) -> bool:
        rel = payload["relative_path"]
        eof = bool(payload.get("eof", False))

        if rel not in self._active:
            self._active[rel] = _ReceiveState(self.storage.temp_path(rel))
        state = self._active[rel]

        if eof:
            state.close()
            if expected_checksum:
                actual = self._sha256_file(state.temp_path)
                if actual != expected_checksum:
                    raise ValueError(f"Checksum mismatch for {rel}: {actual} != {expected_checksum}")
            final = self.storage.resolve_final_path(rel, overwrite=False)
            self.storage.commit_temp_file(state.temp_path, final)
            del self._active[rel]
            return True

        idx = int(payload["chunk_index"])
        ciphertext = base64.b64decode(payload["ciphertext_b64"].encode("ascii"))
        aad = f"{payload['transfer_id']}:{rel}:{idx}".encode("utf-8")
        plain = self.encryptor.decrypt_chunk(idx, ciphertext, aad)
        state.handle.write(plain)

        await self.session.send(
            "chunk_ack",
            {
                "transfer_id": payload["transfer_id"],
                "relative_path": rel,
                "chunk_index": idx,
            },
        )
        return False

    @staticmethod
    def _sha256_file(path: Path) -> str:
        h = hashlib.sha256()
        with path.open("rb") as f:
            for block in iter(lambda: f.read(1024 * 1024), b""):
                h.update(block)
        return h.hexdigest()
