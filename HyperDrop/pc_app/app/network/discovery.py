from __future__ import annotations

import asyncio
import json
import socket
from dataclasses import dataclass
from time import time
from typing import Callable

from app.core.constants import DISCOVERY_MAGIC, DISCOVERY_PORT


@dataclass(slots=True)
class DiscoveredDevice:
    device_id: str
    name: str
    ip: str
    port: int
    platform: str
    last_seen: float


class DiscoveryService:
    def __init__(
        self,
        device_id: str,
        device_name: str,
        tcp_port: int,
        on_devices: Callable[[dict[str, DiscoveredDevice]], None],
        platform: str = "pc",
    ) -> None:
        self.device_id = device_id
        self.device_name = device_name
        self.tcp_port = tcp_port
        self.platform = platform
        self._on_devices = on_devices
        self._devices: dict[str, DiscoveredDevice] = {}
        self._transport: asyncio.DatagramTransport | None = None
        self._probe_task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        if self._transport is not None:
            return
        loop = asyncio.get_running_loop()
        self._transport, _ = await loop.create_datagram_endpoint(
            lambda: _DiscoveryProtocol(self),
            local_addr=("0.0.0.0", DISCOVERY_PORT),
            family=socket.AF_INET,
            allow_broadcast=True,
        )
        sock = self._transport.get_extra_info("socket")
        if sock is not None:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        self._probe_task = asyncio.create_task(self._probe_loop())

    async def stop(self) -> None:
        if self._probe_task is not None:
            self._probe_task.cancel()
            try:
                await self._probe_task
            except asyncio.CancelledError:
                pass
            self._probe_task = None
        if self._transport is not None:
            self._transport.close()
            self._transport = None

    async def _probe_loop(self) -> None:
        while True:
            self.send_probe()
            self._prune_stale()
            await asyncio.sleep(3.0)

    def send_probe(self) -> None:
        if self._transport is None:
            return
        packet = self._build_packet("discover_request")
        self._transport.sendto(packet, ("255.255.255.255", DISCOVERY_PORT))

    def handle_datagram(self, data: bytes, addr: tuple[str, int]) -> None:
        try:
            payload = json.loads(data.decode("utf-8"))
        except Exception:
            return
        if payload.get("magic") != DISCOVERY_MAGIC:
            return

        msg_type = payload.get("type", "")
        sender_id = str(payload.get("device_id", "")).strip()
        if not sender_id or sender_id == self.device_id:
            return

        if msg_type == "discover_request":
            self._send_response(addr)
            return
        if msg_type != "discover_response":
            return

        device = DiscoveredDevice(
            device_id=sender_id,
            name=str(payload.get("device_name", "Unknown")),
            ip=addr[0],
            port=int(payload.get("port", 0) or 0),
            platform=str(payload.get("platform", "unknown")),
            last_seen=time(),
        )
        if device.port <= 0:
            return
        self._devices[device.device_id] = device
        self._on_devices(dict(self._devices))

    def _send_response(self, addr: tuple[str, int]) -> None:
        if self._transport is None:
            return
        self._transport.sendto(self._build_packet("discover_response"), addr)

    def _build_packet(self, msg_type: str) -> bytes:
        return json.dumps(
            {
                "magic": DISCOVERY_MAGIC,
                "type": msg_type,
                "device_id": self.device_id,
                "device_name": self.device_name,
                "port": self.tcp_port,
                "platform": self.platform,
            },
            separators=(",", ":"),
        ).encode("utf-8")

    def _prune_stale(self) -> None:
        now = time()
        stale = [device_id for device_id, dev in self._devices.items() if now - dev.last_seen > 10.0]
        if not stale:
            return
        for device_id in stale:
            self._devices.pop(device_id, None)
        self._on_devices(dict(self._devices))


class _DiscoveryProtocol(asyncio.DatagramProtocol):
    def __init__(self, service: DiscoveryService) -> None:
        self.service = service

    def datagram_received(self, data: bytes, addr: tuple[str, int]) -> None:
        self.service.handle_datagram(data, addr)
