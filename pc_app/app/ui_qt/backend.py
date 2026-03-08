from __future__ import annotations

import asyncio
import os
import queue
import secrets
import threading
from pathlib import Path
from typing import Any

from PySide6.QtCore import QObject, Property, Signal, Slot, QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import QFileDialog

from app.core.config import load_config, save_config
from app.core.models import TransferTask
from app.network.client import LanClient
from app.network.discovery import DiscoveredDevice, DiscoveryService
from app.network.server import LanServer
from app.transfer.queue import TransferQueue
from app.ui_qt.sfx import SfxPlayer


class AsyncRuntime:
    def __init__(self) -> None:
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self) -> None:
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def submit(self, coro):
        return asyncio.run_coroutine_threadsafe(coro, self.loop)

    def stop(self) -> None:
        self.loop.call_soon_threadsafe(self.loop.stop)


class HyperDropBackend(QObject):
    statusTextChanged = Signal()
    devicesChanged = Signal()
    eventsChanged = Signal()
    pairingCodeChanged = Signal()
    portTextChanged = Signal()
    receiveDirChanged = Signal()
    activeTargetChanged = Signal()
    serverRunningChanged = Signal()

    _eventSignal = Signal(str)
    _devicesSignal = Signal(list)

    def __init__(self) -> None:
        super().__init__()
        self.runtime = AsyncRuntime()
        self.config_obj = load_config()
        self.device_id = self._load_or_create_device_id()
        self.device_name = os.environ.get("COMPUTERNAME", "PC")

        self.server: LanServer | None = None
        self.discovery: DiscoveryService | None = None
        self.client = LanClient(
            config=self.config_obj,
            device_id=self.device_id,
            device_name=self.device_name,
            pairing_code_provider=self._get_pairing_code,
            status_callback=self._push_event,
        )
        self.transfer_queue = TransferQueue(self.client.send_task, status_callback=self._push_event)
        self.runtime.submit(self.transfer_queue.start())

        self._status_text = "Idle"
        self._pairing_code = "123456"
        self._port_text = str(self.config_obj.port)
        self._receive_dir = self.config_obj.receive_dir
        self._events: list[dict[str, str]] = []
        self._sfx = SfxPlayer()
        self._devices: list[dict[str, Any]] = []
        self._device_index: dict[str, DiscoveredDevice] = {}
        self._active_target_name = "No target selected"
        self._active_target_address = "Scan LAN and choose a device"
        self._selected_device_id: str | None = None
        self._server_running = False

        self._eventSignal.connect(self._append_event)
        self._devicesSignal.connect(self._replace_devices)

        self._start_discovery()
        if self.config_obj.auto_start_server:
            self.startServer()

    def _load_or_create_device_id(self) -> str:
        from app.core.constants import APP_DIR

        APP_DIR.mkdir(parents=True, exist_ok=True)
        path = APP_DIR / "device_id.txt"
        if path.exists():
            return path.read_text(encoding="utf-8").strip()
        value = secrets.token_hex(12)
        path.write_text(value, encoding="utf-8")
        return value

    def _get_pairing_code(self) -> str:
        return self._pairing_code.strip()

    def _push_event(self, text: str) -> None:
        self._eventSignal.emit(text)

    def _serialize_device(self, device: DiscoveredDevice) -> dict[str, Any]:
        return {
            "deviceId": device.device_id,
            "name": device.name,
            "platform": device.platform,
            "ip": device.ip,
            "port": device.port,
            "address": f"{device.ip}:{device.port}",
        }

    def _start_discovery(self) -> None:
        self.discovery = DiscoveryService(
            device_id=self.device_id,
            device_name=self.device_name,
            tcp_port=self.config_obj.port,
            on_devices=self._on_discovery_update,
            platform="pc",
        )
        self.runtime.submit(self.discovery.start())

    def _on_discovery_update(self, devices: dict[str, DiscoveredDevice]) -> None:
        payload = [
            self._serialize_device(device)
            for device in sorted(devices.values(), key=lambda item: (item.name.lower(), item.ip, item.port))
        ]
        self._devicesSignal.emit(payload)
        self._device_index = dict(devices)

    def _append_event(self, text: str) -> None:
        self._status_text = text
        self.statusTextChanged.emit()
        self._sfx.play_for_event(text)

        status = "info"
        lower = text.lower()
        if "failed" in lower or "error" in lower:
            status = "error"
        elif "retry" in lower or "queued" in lower:
            status = "warn"
        elif "complete" in lower or "finished" in lower:
            status = "success"

        self._events.append({"status": status, "details": text})
        self._events = self._events[-200:]
        self.eventsChanged.emit()

    def _replace_devices(self, devices: list[dict[str, Any]]) -> None:
        self._devices = devices
        self.devicesChanged.emit()

    def _set_active_target(self, device: DiscoveredDevice | None) -> None:
        if device is None:
            self._selected_device_id = None
            self._active_target_name = "No target selected"
            self._active_target_address = "Scan LAN and choose a device"
        else:
            self._selected_device_id = device.device_id
            self._active_target_name = device.name
            self._active_target_address = f"{device.ip}:{device.port}"
        self.activeTargetChanged.emit()

    def _ensure_selected_target(self) -> DiscoveredDevice | None:
        if self._selected_device_id is None:
            self._push_event("Select a discovered device first")
            return None
        device = self._device_index.get(self._selected_device_id)
        if device is None:
            self._push_event("Selected device is no longer available")
            self._set_active_target(None)
            return None
        return device

    @Property(str, notify=statusTextChanged)
    def statusText(self) -> str:
        return self._status_text

    @Property(str, notify=pairingCodeChanged)
    def pairingCode(self) -> str:
        return self._pairing_code

    @Property(str, notify=portTextChanged)
    def portText(self) -> str:
        return self._port_text

    @Property(str, notify=receiveDirChanged)
    def receiveDir(self) -> str:
        return self._receive_dir

    @Property(str, constant=True)
    def deviceName(self) -> str:
        return self.device_name

    @Property(str, constant=True)
    def deviceId(self) -> str:
        return self.device_id

    @Property(str, notify=activeTargetChanged)
    def activeTargetName(self) -> str:
        return self._active_target_name

    @Property(str, notify=activeTargetChanged)
    def activeTargetAddress(self) -> str:
        return self._active_target_address

    @Property(bool, notify=serverRunningChanged)
    def serverRunning(self) -> bool:
        return self._server_running

    @Property("QVariantList", notify=devicesChanged)
    def devices(self) -> list[dict[str, Any]]:
        return self._devices

    @Property("QVariantList", notify=eventsChanged)
    def events(self) -> list[dict[str, str]]:
        return self._events

    @Slot(str)
    def setPairingCode(self, value: str) -> None:
        cleaned = value.strip()
        if cleaned == self._pairing_code:
            return
        self._pairing_code = cleaned
        self.pairingCodeChanged.emit()

    @Slot(str)
    def setPortText(self, value: str) -> None:
        if value == self._port_text:
            return
        self._port_text = value
        self.portTextChanged.emit()

    @Slot(str)
    def selectDevice(self, device_id: str) -> None:
        self._set_active_target(self._device_index.get(device_id))

    @Slot()
    def refreshDiscovery(self) -> None:
        if self.discovery is not None:
            self.runtime.loop.call_soon_threadsafe(self.discovery.send_probe)
        self._push_event("Scanning local network for devices...")

    @Slot()
    def saveConnectionSettings(self) -> None:
        try:
            port = int(self._port_text.strip())
        except ValueError:
            self._push_event("Port must be numeric")
            return
        if port < 1024 or port > 65535:
            self._push_event("Port must be in range 1024-65535")
            return

        self.config_obj.bind_host = "0.0.0.0"
        self.config_obj.port = port
        self.config_obj.receive_dir = self._receive_dir
        save_config(self.config_obj)
        if self.discovery is not None:
            self.discovery.tcp_port = port
        self._push_event("Connection settings saved")

    @Slot()
    def generatePairingCode(self) -> None:
        self._pairing_code = f"{secrets.randbelow(900000) + 100000}"
        self.pairingCodeChanged.emit()
        self._push_event("New pairing code generated")

    @Slot()
    def chooseReceiveFolder(self) -> None:
        selected = QFileDialog.getExistingDirectory(None, "Select Receive Folder", self._receive_dir)
        if not selected:
            return
        self._receive_dir = selected
        self.config_obj.receive_dir = selected
        save_config(self.config_obj)
        self.receiveDirChanged.emit()
        self._push_event("Receive folder updated")

    @Slot()
    def openReceiveFolder(self) -> None:
        QDesktopServices.openUrl(QUrl.fromLocalFile(self._receive_dir))

    @Slot()
    def startServer(self) -> None:
        try:
            self.config_obj.bind_host = "0.0.0.0"
            self.config_obj.port = int(self._port_text.strip())
            save_config(self.config_obj)
        except Exception as exc:
            self._push_event(f"Invalid bind settings: {exc}")
            return

        if self.server is not None:
            self._push_event("Server already running")
            return

        if self.discovery is not None:
            self.discovery.tcp_port = self.config_obj.port

        self.server = LanServer(
            config=self.config_obj,
            device_id=self.device_id,
            device_name=self.device_name,
            get_pairing_code=self._get_pairing_code,
            status_callback=self._push_event,
        )
        self.runtime.submit(self.server.start())
        self._server_running = True
        self.serverRunningChanged.emit()
        self._push_event("Server start requested")

    @Slot()
    def stopServer(self) -> None:
        if self.server is None:
            return
        self.runtime.submit(self.server.stop())
        self.server = None
        self._server_running = False
        self.serverRunningChanged.emit()
        self._push_event("Server stop requested")

    @Slot()
    def sendFiles(self) -> None:
        device = self._ensure_selected_target()
        if device is None:
            return
        paths, _ = QFileDialog.getOpenFileNames(None, "Select files")
        if not paths:
            return
        task = TransferTask(device.ip, device.port, [Path(p) for p in paths], device.device_id)
        self.runtime.submit(self.transfer_queue.enqueue(task))
        self._push_event(f"Queued {len(paths)} file(s) for {device.ip}:{device.port}")

    @Slot()
    def sendFolder(self) -> None:
        device = self._ensure_selected_target()
        if device is None:
            return
        folder = QFileDialog.getExistingDirectory(None, "Select folder")
        if not folder:
            return
        task = TransferTask(device.ip, device.port, [Path(folder)], device.device_id)
        self.runtime.submit(self.transfer_queue.enqueue(task))
        self._push_event(f"Queued folder {folder} for {device.ip}:{device.port}")

    @Slot()
    def shutdown(self) -> None:
        futures = []
        if self.server is not None:
            futures.append(self.runtime.submit(self.server.stop()))
            self.server = None
        if self.discovery is not None:
            futures.append(self.runtime.submit(self.discovery.stop()))
            self.discovery = None
        futures.append(self.runtime.submit(self.transfer_queue.stop()))
        for future in futures:
            try:
                future.result(timeout=2)
            except Exception:
                pass
        self.runtime.stop()