from __future__ import annotations

import asyncio
import struct
from typing import Optional

from app.network.protocol import ProtocolMessage


class FramedTransport:
    def __init__(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        self.reader = reader
        self.writer = writer
        self._closed = False

    async def send_message(self, message: ProtocolMessage) -> None:
        payload = message.to_bytes()
        self.writer.write(struct.pack("!I", len(payload)) + payload)
        await self.writer.drain()

    def queue_message(self, message: ProtocolMessage) -> None:
        """Write a message to the send buffer without flushing.

        Call :meth:`flush` afterwards to push all queued messages to the OS.
        This allows multiple messages to be batched into a single drain() call,
        keeping the TCP pipeline saturated during chunked transfers.
        """
        payload = message.to_bytes()
        self.writer.write(struct.pack("!I", len(payload)) + payload)

    async def flush(self) -> None:
        """Flush the write buffer, blocking until the OS has accepted the data."""
        await self.writer.drain()

    async def recv_message(self) -> ProtocolMessage:
        header = await self.reader.readexactly(4)
        (length,) = struct.unpack("!I", header)
        if length <= 0 or length > 32 * 1024 * 1024:
            raise ValueError("Invalid frame length")
        body = await self.reader.readexactly(length)
        return ProtocolMessage.from_bytes(body)

    async def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        self.writer.close()
        try:
            await self.writer.wait_closed()
        except Exception:
            pass


class Session:
    def __init__(self, transport: FramedTransport):
        self.transport = transport
        self.device_id: Optional[str] = None
        self.peer_device_id: Optional[str] = None
        self.session_key: Optional[bytes] = None

    async def send(self, msg_type: str, payload: dict) -> None:
        await self.transport.send_message(ProtocolMessage(msg_type, payload))

    def queue(self, msg_type: str, payload: dict) -> None:
        """Buffer a message without flushing (call flush() to drain the buffer)."""
        self.transport.queue_message(ProtocolMessage(msg_type, payload))

    async def flush(self) -> None:
        """Flush all previously queued messages to the network."""
        await self.transport.flush()

    async def recv(self) -> ProtocolMessage:
        return await self.transport.recv_message()
