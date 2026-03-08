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

    async def recv_message(self) -> ProtocolMessage:
        header = await self.reader.readexactly(4)
        (length,) = struct.unpack("!I", header)
        if length <= 0 or length > 16 * 1024 * 1024:
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

    async def recv(self) -> ProtocolMessage:
        return await self.transport.recv_message()
