from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import Optional

from app.core.models import TransferTask
from app.network.client import TransferDeclinedError

logger = logging.getLogger(__name__)


class TransferQueue:
    def __init__(
        self,
        worker: Callable[[TransferTask], Awaitable[None]],
        status_callback: Optional[Callable[[str], None]] = None,
    ) -> None:
        self._worker = worker
        self._status_callback = status_callback
        self._queue: asyncio.Queue[tuple[TransferTask, int]] = asyncio.Queue()
        self._known: set[str] = set()
        self._task: asyncio.Task | None = None
        self._running = False
        self._cancelled = False

    def _fingerprint(self, task: TransferTask) -> str:
        return f"{task.target_ip}:{task.target_port}:{'|'.join(sorted(str(p.resolve()) for p in task.source_paths))}"

    async def start(self) -> None:
        if self._running:
            return
        self._running = True
        self._task = asyncio.create_task(self._run())

    async def stop(self) -> None:
        self._running = False
        self._cancelled = True
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except Exception:
                pass

    async def clear(self) -> None:
        self._cancelled = True
        while not self._queue.empty():
            try:
                self._queue.get_nowait()
                self._queue.task_done()
            except asyncio.QueueEmpty:
                break
        self._known.clear()
        self._emit_status("Transfer queue cleared")

    async def enqueue(self, task: TransferTask) -> bool:
        self._cancelled = False
        fp = self._fingerprint(task)
        if fp in self._known:
            return False
        self._known.add(fp)
        await self._queue.put((task, 0))
        return True

    async def _run(self) -> None:
        while self._running:
            task, attempt = await self._queue.get()
            try:
                self._emit_status(
                    f"Starting transfer to {task.target_ip}:{task.target_port} (attempt {attempt + 1}/6)"
                )
                await self._worker(task)
                self._emit_status(f"Transfer finished for {task.target_ip}:{task.target_port}")
                self._known.discard(self._fingerprint(task))
            except asyncio.CancelledError:
                self._emit_status(f"Transfer cancelled by user for {task.target_ip}:{task.target_port}")
                self._known.discard(self._fingerprint(task))
                self._emit_status("PROGRESS:cancelled:0:0")
                # Clear queue on explicit cancel
                await self.clear()
            except Exception as exc:
                if self._cancelled:
                    self._emit_status("Transfer queue aborted due to cancellation")
                    self._emit_status("PROGRESS:cancelled:0:0")
                    await self.clear()
                    break
                    
                if isinstance(exc, ConnectionRefusedError):
                    logger.warning("Transfer failed: Receiver not accepting files")
                    self._emit_status("The receiver is not accepting files at the moment. Please try again later.")
                    self._known.discard(self._fingerprint(task))
                    self._emit_status("PROGRESS:error:0:0")
                    await self.clear()
                    break

                if isinstance(exc, TransferDeclinedError):
                    logger.info("Transfer declined by receiver: %s", exc)
                    self._emit_status(str(exc))
                    self._known.discard(self._fingerprint(task))
                    self._emit_status("PROGRESS:error:0:0")
                    await self.clear()
                    break

                if attempt < 5:
                    wait = 2 ** attempt
                    logger.warning("Transfer failed, retry in %ss (%s)", wait, exc)
                    self._emit_status(
                        f"Transfer failed (attempt {attempt + 1}/6): {exc}. Retrying in {wait}s"
                    )
                    await asyncio.sleep(wait)
                    await self._queue.put((task, attempt + 1))
                else:
                    logger.exception("Transfer failed permanently: %s", exc)
                    self._emit_status(f"Transfer failed permanently: {exc}. Clearing queue.")
                    self._known.discard(self._fingerprint(task))
                    self._emit_status("PROGRESS:error:0:0")
                    # Clear entire queue on hard failure
                    await self.clear()
            finally:
                self._queue.task_done()

    def _emit_status(self, text: str) -> None:
        if self._status_callback is None:
            return
        self._status_callback(text)
