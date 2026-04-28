from __future__ import annotations

import asyncio
import base64
from pathlib import Path
from typing import Callable

from app.core.constants import CHUNK_SIZE, PIPELINE_WINDOW
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

        # Semaphore limits the number of unacknowledged chunks in flight so the
        # sender does not overwhelm the receiver or exhaust memory while still
        # keeping the full network pipeline busy.
        window_sem = asyncio.Semaphore(PIPELINE_WINDOW)

        # Maps chunk_index → plain-text byte count for progress accounting.
        chunk_sizes: dict[int, int] = {}
        progress_bytes = 0
        eof_event = asyncio.Event()
        total_chunks = 0

        async def _send_chunks() -> None:
            nonlocal total_chunks
            idx = 0
            write_offset = 0
            with source_file.open("rb") as f:
                while True:
                    if self._check_cancel and self._check_cancel():
                        await self.session.send("cancel", {"transfer_id": transfer_id})
                        raise asyncio.CancelledError("Transfer cancelled by user")

                    await window_sem.acquire()

                    chunk = f.read(CHUNK_SIZE)
                    if not chunk:
                        # EOF – give back the slot we just acquired but won't use.
                        window_sem.release()
                        break

                    aad = f"{transfer_id}:{relative_path}:{idx}".encode("utf-8")
                    encrypted = self.encryptor.encrypt_chunk(idx, chunk, aad)
                    chunk_sizes[idx] = len(chunk)

                    # Queue the frame without draining; drain() is handled by
                    # the asyncio transport in the background so the ACK
                    # receiver coroutine can run concurrently.
                    self.session.queue(
                        "file_chunk",
                        {
                            "transfer_id": transfer_id,
                            "relative_path": relative_path,
                            "chunk_index": idx,
                            "offset": write_offset,
                            "ciphertext_b64": base64.b64encode(encrypted).decode("ascii"),
                            "eof": False,
                        },
                    )
                    # Explicit flush every PIPELINE_WINDOW chunks so we push a
                    # full window's worth of data to the OS in one drain() call
                    # rather than draining after every individual chunk.
                    if (idx + 1) % PIPELINE_WINDOW == 0:
                        await self.session.flush()

                    write_offset += len(chunk)
                    idx += 1
                    total_chunks += 1

            # Flush any remaining buffered frames before signalling EOF.
            await self.session.flush()
            eof_event.set()

        async def _recv_acks() -> None:
            nonlocal progress_bytes
            while True:
                ack = await self.session.recv()
                if ack.msg_type != "chunk_ack":
                    raise RuntimeError(f"Expected chunk_ack, got {ack.msg_type}")
                acked_idx = int(ack.payload["chunk_index"])
                progress_bytes += chunk_sizes.pop(acked_idx, 0)
                window_sem.release()
                if self._on_progress:
                    self._on_progress(relative_path, progress_bytes, file_size)
                # Stop once the sender has finished and all ACKs are collected.
                if eof_event.is_set() and not chunk_sizes:
                    break

        send_task = asyncio.create_task(_send_chunks())
        ack_task = asyncio.create_task(_recv_acks())
        try:
            while True:
                done, pending = await asyncio.wait(
                    {send_task, ack_task},
                    return_when=asyncio.FIRST_COMPLETED,
                )
                for t in done:
                    if not t.cancelled():
                        exc = t.exception()
                        if exc is not None:
                            raise exc
                if not pending:
                    break
                # Sender finished – if nothing is in-flight the ack_task may be
                # blocked on recv() waiting for ACKs that will never arrive
                # (e.g., very small / empty files, or a fast-ACK network where
                # all ACKs arrived before eof_event was set).  Cancel it.
                if send_task.done() and not chunk_sizes:
                    ack_task.cancel()
                    await asyncio.gather(ack_task, return_exceptions=True)
                    break
        except BaseException:
            send_task.cancel()
            ack_task.cancel()
            await asyncio.gather(send_task, ack_task, return_exceptions=True)
            raise

        # Send the EOF sentinel (no per-chunk ACK expected for this marker).
        await self.session.send(
            "file_chunk",
            {
                "transfer_id": transfer_id,
                "relative_path": relative_path,
                "chunk_index": total_chunks,
                "offset": file_size,
                "ciphertext_b64": "",
                "eof": True,
            },
        )
        # Ensure 100% reported on completion
        if self._on_progress and file_size > 0:
            self._on_progress(relative_path, file_size, file_size)
