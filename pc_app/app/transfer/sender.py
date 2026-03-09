from __future__ import annotations

import base64
from pathlib import Path
from typing import Callable

from app.core.constants import CHUNK_SIZE
from app.crypto.encryptor import ChunkEncryptor
from app.network.transport import Session


class TransferSender:
    def __init__(
        self,
        session: Session,
        encryptor: ChunkEncryptor,
        on_progress: Callable[[str, int, int], None] | None = None,
        check_cancel: Callable[[], bool] | None = None,
    ):
        self.session = session
        self.encryptor = encryptor
        self._on_progress = on_progress
        self._check_cancel = check_cancel

    async def send_file(self, transfer_id: str, relative_path: str, source_file: Path) -> None:
        file_size = source_file.stat().st_size
        sent = 0
        index = 0

        with source_file.open("rb") as f:
            while True:
                if self._check_cancel and self._check_cancel():
                    await self.session.send("cancel", {"transfer_id": transfer_id})
                    import asyncio
                    raise asyncio.CancelledError("Transfer cancelled by user")

                chunk = f.read(CHUNK_SIZE)
                if not chunk:
                    break
                aad = f"{transfer_id}:{relative_path}:{index}".encode("utf-8")
                encrypted = self.encryptor.encrypt_chunk(index, chunk, aad)
                await self.session.send(
                    "file_chunk",
                    {
                        "transfer_id": transfer_id,
                        "relative_path": relative_path,
                        "chunk_index": index,
                        "offset": sent,
                        "ciphertext_b64": base64.b64encode(encrypted).decode("ascii"),
                        "eof": False,
                    },
                )
                ack = await self.session.recv()
                if ack.msg_type != "chunk_ack":
                    raise RuntimeError(f"Expected chunk_ack, got {ack.msg_type}")
                sent += len(chunk)
                index += 1
                if self._on_progress:
                    self._on_progress(relative_path, sent, file_size)

        await self.session.send(
            "file_chunk",
            {
                "transfer_id": transfer_id,
                "relative_path": relative_path,
                "chunk_index": index,
                "offset": file_size,
                "ciphertext_b64": "",
                "eof": True,
            },
        )
        # Ensure 100% reported on completion
        if self._on_progress and file_size > 0:
            self._on_progress(relative_path, file_size, file_size)
