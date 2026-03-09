from __future__ import annotations

import base64
import hashlib
from pathlib import Path
from typing import Callable

from app.crypto.encryptor import ChunkEncryptor
from app.network.transport import Session
from app.transfer.storage import SafeStorage


class _ReceiveState:
    def __init__(self, temp_path: Path, target_relative_path: str):
        self.temp_path = temp_path
        self.target_relative_path = target_relative_path
        self.handle = temp_path.open("wb")
        self.received_bytes: int = 0

    def close(self) -> None:
        self.handle.close()


class TransferReceiver:
    def __init__(
        self,
        session: Session,
        encryptor: ChunkEncryptor,
        storage: SafeStorage,
        on_progress: Callable[[str, int, int], None] | None = None,
        on_file_complete: Callable[[str, str, Path, int], None] | None = None,
        rename_map: dict[str, str] | None = None,
        skip_paths: set[str] | None = None,
    ):
        self.session = session
        self.encryptor = encryptor
        self.storage = storage
        self._active: dict[str, _ReceiveState] = {}
        self._on_progress = on_progress
        self._on_file_complete = on_file_complete
        self._sizes: dict[str, int] = {}  # relative_path -> expected total bytes
        self._rename_map = rename_map or {}
        self._skip_paths = skip_paths or set()

    def set_sizes(self, sizes: dict[str, int]) -> None:
        """Provide expected file sizes from the manifest for progress reporting."""
        self._sizes = sizes

    def _target_relative_path(self, relative_path: str) -> str:
        return self._rename_map.get(relative_path, relative_path)

    async def handle_chunk(self, payload: dict, expected_checksum: str | None = None) -> bool:
        rel = payload["relative_path"]
        eof = bool(payload.get("eof", False))
        if rel in self._skip_paths:
            if not eof:
                await self.session.send(
                    "chunk_ack",
                    {
                        "transfer_id": payload["transfer_id"],
                        "relative_path": rel,
                        "chunk_index": int(payload["chunk_index"]),
                    },
                )
            return eof

        if rel not in self._active:
            target_rel = self._target_relative_path(rel)
            self._active[rel] = _ReceiveState(self.storage.temp_path(target_rel), target_rel)
        state = self._active[rel]

        if eof:
            state.close()
            if expected_checksum:
                actual = self._sha256_file(state.temp_path)
                if actual != expected_checksum:
                    raise ValueError(f"Checksum mismatch for {rel}: {actual} != {expected_checksum}")
            final = self.storage.resolve_final_path(state.target_relative_path, overwrite=False)
            self.storage.commit_temp_file(state.temp_path, final)
            total = self._sizes.get(rel, state.received_bytes)
            if self._on_progress and total > 0:
                self._on_progress(rel, total, total)  # 100% on completion
            if self._on_file_complete:
                self._on_file_complete(rel, state.target_relative_path, final, total)
            del self._active[rel]
            return True

        idx = int(payload["chunk_index"])
        ciphertext = base64.b64decode(payload["ciphertext_b64"].encode("ascii"))
        aad = f"{payload['transfer_id']}:{rel}:{idx}".encode("utf-8")
        plain = self.encryptor.decrypt_chunk(idx, ciphertext, aad)
        state.handle.write(plain)
        state.received_bytes += len(plain)

        await self.session.send(
            "chunk_ack",
            {
                "transfer_id": payload["transfer_id"],
                "relative_path": rel,
                "chunk_index": idx,
            },
        )

        if self._on_progress:
            total = self._sizes.get(rel, 0)
            self._on_progress(rel, state.received_bytes, total)

        return False

    @staticmethod
    def _sha256_file(path: Path) -> str:
        h = hashlib.sha256()
        with path.open("rb") as f:
            for block in iter(lambda: f.read(1024 * 1024), b""):
                h.update(block)
        return h.hexdigest()
