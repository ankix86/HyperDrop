from __future__ import annotations

import asyncio
import json
import os
import secrets
import socket
import threading
import time
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

from PySide6.QtCore import QObject, Property, Signal, Slot, QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import QFileDialog

from app.core.config import load_config, save_config
from app.core.models import TransferTask
from app.network.client import LanClient
from app.network.discovery import DiscoveredDevice, DiscoveryService
from app.network.server import IncomingTransferDecision, LanServer
from app.transfer.queue import TransferQueue
from app.ui_qt.sfx import SfxPlayer
from app.utils.validators import validate_port


@dataclass(slots=True)
class IncomingTransferItem:
    relative_path: str
    file_name: str
    proposed_name: str
    size: int
    is_directory: bool
    parent_path: str
    selected: bool


@dataclass(slots=True)
class SendSelectionItem:
    path: Path
    file_name: str
    is_directory: bool
    size_bytes: int


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
    portTextChanged = Signal()
    portStatusChanged = Signal()
    receiveDirChanged = Signal()
    activeTargetChanged = Signal()
    serverRunningChanged = Signal()
    transferringChanged = Signal()
    transferFileNameChanged = Signal()
    transferProgressChanged = Signal()
    quickSaveModeChanged = Signal()
    favoritesChanged = Signal()
    historyChanged = Signal()
    aliasChanged = Signal()
    incomingTransferChanged = Signal()
    outgoingTransferRequestChanged = Signal()
    receiveSessionChanged = Signal()
    sendSelectionChanged = Signal()
    themePreferenceChanged = Signal()
    effectiveThemeChanged = Signal()

    _eventSignal = Signal(str)
    _devicesSignal = Signal(list)
    _incomingTransferSignal = Signal(dict)

    def __init__(self) -> None:
        super().__init__()
        self.runtime = AsyncRuntime()
        self.config_obj = load_config()
        self.device_id = self._load_or_create_device_id()
        self.device_name = os.environ.get("COMPUTERNAME", "PC")
        self._alias = self._get_or_create_alias()

        self.server: LanServer | None = None
        self._active_server_port: int | None = None
        self.discovery: DiscoveryService | None = None
        self.client = LanClient(
            config=self.config_obj,
            device_id=self.device_id,
            device_name=self._alias or self.device_name,
            status_callback=self._push_event,
        )
        self.transfer_queue = TransferQueue(self.client.send_task, status_callback=self._push_event)
        self.runtime.submit(self.transfer_queue.start())

        self._status_text = "Idle"
        self._port_text = str(self.config_obj.port)
        self._port_status_text = ""
        self._port_status_tone = "info"
        self._receive_dir = self.config_obj.receive_dir
        self._events: list[dict[str, str]] = []
        self._sfx = SfxPlayer()
        self._devices: list[dict[str, Any]] = []
        self._device_index: dict[str, DiscoveredDevice] = {}
        self._active_target_name = "No target selected"
        self._active_target_address = "Scan LAN and choose a device"
        self._selected_device_id: str | None = None
        self._server_running = False
        self._quick_save_mode = self.config_obj.quick_save_mode
        self._favorites = self.config_obj.favorites
        self._history = []
        self._incoming_transfer_visible = False
        self._incoming_transfer_sender_closed = False
        self._incoming_transfer_sender_name = ""
        self._incoming_transfer_target_dir = self._receive_dir
        self._incoming_transfer_items: list[IncomingTransferItem] = []
        self._pending_incoming_future: asyncio.Future[IncomingTransferDecision] | None = None
        self._pending_incoming_transfer_id: str | None = None
        self._outgoing_request_visible = False
        self._outgoing_request_rejected = False
        self._outgoing_request_sender_name = ""
        self._outgoing_request_sender_id = ""
        self._outgoing_request_receiver_name = ""
        self._outgoing_request_receiver_id = ""
        self._pending_send_target_name = ""
        self._pending_send_target_id = ""
        self._receive_session_visible = False
        self._receive_session_active = False
        self._receive_session_finished = False
        self._receive_session_transfer_id = ""
        self._receive_session_target_dir = ""
        self._receive_session_files: list[dict[str, Any]] = []
        self._receive_session_speed_bps: float = 0.0
        self._receive_session_started_at: float = 0.0
        self._receive_session_last_ts: float = 0.0
        self._receive_session_last_bytes: int = 0
        self._send_selection_items: list[SendSelectionItem] = []
        self._pending_send_selection_restore: list[SendSelectionItem] = []
        self._theme_preference = self._normalize_theme_preference(self.config_obj.theme_preference)
        if self.config_obj.theme_preference != self._theme_preference:
            self.config_obj.theme_preference = self._theme_preference
            save_config(self.config_obj)

        # Progress bar state
        self._transferring = False
        self._transfer_file_name = ""
        self._transfer_progress: float = 0.0
        self._progress_clear_timer: Any = None

        self._eventSignal.connect(self._append_event)
        self._devicesSignal.connect(self._replace_devices)
        self._incomingTransferSignal.connect(self._show_incoming_transfer)
        self._connect_system_theme_notifications()

        self._start_discovery()
        if self.config_obj.auto_start_server:
            self.startServer()
        else:
            self._sync_port_status()

    def _load_or_create_device_id(self) -> str:
        from app.core.constants import APP_DIR

        APP_DIR.mkdir(parents=True, exist_ok=True)
        path = APP_DIR / "device_id.txt"
        if path.exists():
            return path.read_text(encoding="utf-8").strip()
        value = secrets.token_hex(12)
        path.write_text(value, encoding="utf-8")
        return value

    def _set_port_status(self, text: str, tone: str = "info") -> None:
        changed = False
        if text != self._port_status_text:
            self._port_status_text = text
            changed = True
        if tone != self._port_status_tone:
            self._port_status_tone = tone
            changed = True
        if changed:
            self.portStatusChanged.emit()

    def _parsed_port(self) -> tuple[int | None, str, str]:
        cleaned = self._port_text.strip()
        if not cleaned:
            return None, "Enter a port between 1024 and 65535.", "error"
        try:
            port = int(cleaned)
        except ValueError:
            return None, "Port must be numeric.", "error"
        if not validate_port(port):
            return None, "Port must be between 1024 and 65535.", "error"
        active_port = self._active_server_port if self._server_running else None
        saved_port = self.config_obj.port
        if active_port is not None and port != active_port:
            if port == saved_port:
                return port, "Restart the server to apply the saved port.", "warn"
            return port, "Save the port, then restart the server to apply it.", "warn"
        if port == saved_port:
            state = f"Server is online on port {port}." if active_port is not None else "Saved communication port."
            tone = "success" if active_port is not None else "info"
            return port, state, tone
        if self._server_running:
            return port, "Save the port, then restart the server to apply it.", "warn"
        return port, "Save to use this port next time the server starts.", "info"

    def _sync_port_status(self) -> None:
        _, text, tone = self._parsed_port()
        self._set_port_status(text, tone)

    def _port_unavailable_reason(self, port: int) -> str | None:
        if self._active_server_port is not None and port == self._active_server_port:
            return None
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.bind((self.config_obj.bind_host or "0.0.0.0", port))
        except OSError:
            return f"Port {port} is already in use on this PC."
        return None

    def _build_server(self) -> LanServer:
        return LanServer(
            config=self.config_obj,
            device_id=self.device_id,
            device_name=self._alias or self.device_name,
            status_callback=self._push_event,
            incoming_transfer_handler=self.prompt_incoming_transfer,
        )

    def _start_server_instance(self) -> None:
        if self.discovery is not None:
            self.discovery.tcp_port = self.config_obj.port
        server = self._build_server()
        self.runtime.submit(server.start()).result(timeout=5)
        self.server = server
        self._active_server_port = self.config_obj.port
        self._server_running = True
        self.serverRunningChanged.emit()
        if self.discovery is not None:
            self.discovery.accepting_connections = True
            self.runtime.loop.call_soon_threadsafe(self.discovery.send_announce)
            self.runtime.loop.call_soon_threadsafe(self.discovery.send_probe)

    def _stop_server_instance(self, announce_offline: bool) -> None:
        if self.server is None:
            return
        if self.discovery is not None:
            self.discovery.accepting_connections = False
            if announce_offline:
                try:
                    self.runtime.submit(self.discovery.send_bye()).result(timeout=2)
                except Exception:
                    pass
        try:
            self.runtime.submit(self.server.stop()).result(timeout=5)
        finally:
            self.server = None
            self._active_server_port = None
            self._server_running = False
            self.serverRunningChanged.emit()

    def _save_port_setting(self, port: int) -> bool:
        unavailable_reason = self._port_unavailable_reason(port)
        if unavailable_reason:
            self._set_port_status(unavailable_reason, "error")
            self._push_event(unavailable_reason)
            return False

        port_changed = port != self.config_obj.port
        self.config_obj.bind_host = "0.0.0.0"
        self.config_obj.receive_dir = self._receive_dir
        self.config_obj.port = port
        save_config(self.config_obj)
        if self.discovery is not None and (not self._server_running or port == self._active_server_port):
            self.discovery.tcp_port = port
        if self._server_running and port != self._active_server_port:
            self._set_port_status("Restart the server to apply the saved port.", "warn")
            self._push_event(f"Port saved as {port}. Restart the server to apply it.")
        elif port_changed:
            message = (
                f"Communication port saved as {port}."
            )
            self._set_port_status(message, "success")
            self._push_event(message)
        else:
            self._set_port_status(f"Communication port saved as {port}.", "success")
            self._push_event("Connection settings saved")
        return True

    @staticmethod
    def _format_bytes(size: int) -> str:
        if size >= 1024 * 1024 * 1024:
            return f"{size / (1024 * 1024 * 1024):.1f} GB"
        if size >= 1024 * 1024:
            return f"{size / (1024 * 1024):.1f} MB"
        if size >= 1024:
            return f"{size / 1024:.1f} KB"
        return f"{size} B"

    @staticmethod
    def _measure_path_size(path: Path) -> int:
        try:
            if path.is_file():
                return int(path.stat().st_size)
            if path.is_dir():
                total = 0
                for child in path.rglob("*"):
                    if child.is_file():
                        total += int(child.stat().st_size)
                return total
        except Exception:
            return 0
        return 0

    @staticmethod
    def _normalize_theme_preference(theme: str) -> str:
        value = str(theme).strip().lower()
        if value in {"light", "dark", "system"}:
            return value
        return "system"

    def _connect_system_theme_notifications(self) -> None:
        try:
            from PySide6.QtGui import QGuiApplication
        except Exception:
            return
        app = QGuiApplication.instance()
        if app is None:
            return
        try:
            app.styleHints().colorSchemeChanged.connect(self._on_system_color_scheme_changed)
        except Exception:
            pass

    def _on_system_color_scheme_changed(self, *_args: Any) -> None:
        if self._theme_preference == "system":
            self.effectiveThemeChanged.emit()

    @Property(str, notify=aliasChanged)
    def alias(self) -> str:
        return self._alias

    @Property(int, notify=quickSaveModeChanged)
    def quickSaveMode(self) -> int:
        return self._quick_save_mode

    @Property(list, notify=historyChanged)
    def history(self) -> list:
        return self._history

    @Property(list, notify=favoritesChanged)
    def favorites(self) -> list:
        return self._favorites

    @Property(str, notify=themePreferenceChanged)
    def themePreference(self) -> str:
        return self._theme_preference

    @Property(str, notify=effectiveThemeChanged)
    def effectiveTheme(self) -> str:
        if self._theme_preference == "system":
            from PySide6.QtGui import QGuiApplication
            from PySide6.QtCore import Qt
            scheme = QGuiApplication.styleHints().colorScheme()
            return "dark" if scheme == Qt.ColorScheme.Dark else "light"
        return self._theme_preference

    @Slot(str)
    def setThemePreference(self, theme: str) -> None:
        normalized = self._normalize_theme_preference(theme)
        if normalized == self._theme_preference:
            return
        self._theme_preference = normalized
        self.config_obj.theme_preference = normalized
        save_config(self.config_obj)
        self.themePreferenceChanged.emit()
        self.effectiveThemeChanged.emit()

    def _serialize_send_selection_item(self, item: SendSelectionItem) -> dict[str, Any]:
        return {
            "path": str(item.path),
            "fileName": item.file_name,
            "kind": "Folder" if item.is_directory else "File",
            "sizeBytes": item.size_bytes,
            "sizeText": self._format_bytes(item.size_bytes),
        }

    def _add_send_selection_paths(self, paths: list[str]) -> int:
        known = {item.path for item in self._send_selection_items}
        added = 0
        for raw in paths:
            text = str(raw).strip()
            if not text:
                continue
            path = Path(text).expanduser()
            try:
                resolved = path.resolve()
            except Exception:
                continue
            if not resolved.exists() or resolved in known:
                continue
            item = SendSelectionItem(
                path=resolved,
                file_name=resolved.name or str(resolved),
                is_directory=resolved.is_dir(),
                size_bytes=self._measure_path_size(resolved),
            )
            self._send_selection_items.append(item)
            known.add(resolved)
            added += 1
        if added:
            self.sendSelectionChanged.emit()
        return added

    @staticmethod
    def _sanitize_file_name(value: str) -> str:
        cleaned = value.strip()
        for char in '<>:"/\\|?*':
            cleaned = cleaned.replace(char, "_")
        cleaned = cleaned.strip().rstrip(".")
        if not cleaned or cleaned in {".", ".."}:
            return "renamed-file"
        return cleaned

    def _incoming_summary(self) -> str:
        if not self._incoming_transfer_items:
            return "0 items"
        total_items = sum(1 for item in self._incoming_transfer_items if not item.is_directory and item.selected)
        total_size = sum(item.size for item in self._incoming_transfer_items if not item.is_directory and item.selected)
        if total_items == 0:
            available = sum(1 for item in self._incoming_transfer_items if not item.is_directory)
            return f"0 selected • {available} available"
        noun = "file" if total_items == 1 else "files"
        return f"{total_items} {noun} • {self._format_bytes(total_size)}"

    def _serialize_incoming_item(self, item: IncomingTransferItem) -> dict[str, Any]:
        return {
            "relativePath": item.relative_path,
            "fileName": item.file_name,
            "proposedName": item.proposed_name,
            "sizeText": self._format_bytes(item.size),
            "sizeBytes": item.size,
            "isDirectory": item.is_directory,
            "parentPath": item.parent_path,
            "editable": not item.is_directory,
            "selected": item.selected,
            "selectable": not item.is_directory,
        }

    def _show_incoming_transfer(self, payload: dict[str, Any]) -> None:
        items: list[IncomingTransferItem] = []
        for raw in payload.get("items", []):
            relative_path = str(raw.get("relative_path", "")).strip()
            file_name = str(raw.get("file_name", "")).strip() or PurePosixPath(relative_path).name
            parent_path = PurePosixPath(relative_path).parent.as_posix()
            if parent_path == ".":
                parent_path = ""
            items.append(
                IncomingTransferItem(
                    relative_path=relative_path,
                    file_name=file_name,
                    proposed_name=file_name,
                    size=int(raw.get("size", 0) or 0),
                    is_directory=bool(raw.get("is_directory", False)),
                    parent_path=parent_path,
                    selected=not bool(raw.get("is_directory", False)),
                )
            )
        self._incoming_transfer_visible = True
        self._incoming_transfer_sender_closed = False
        self._incoming_transfer_sender_name = str(payload.get("sender_name", "Unknown device"))
        self._incoming_transfer_target_dir = str(payload.get("receive_dir", self._receive_dir))
        self._incoming_transfer_items = items
        self.incomingTransferChanged.emit()

    async def prompt_incoming_transfer(
        self,
        transfer_id: str,
        sender_name: str,
        preview_entries: list[dict],
    ) -> IncomingTransferDecision:
        if self._pending_incoming_future and not self._pending_incoming_future.done():
            return IncomingTransferDecision(
                accepted=False,
                receive_dir=self._receive_dir,
                rename_map={},
                accepted_paths=set(),
                reason="Another incoming transfer is awaiting approval",
            )
        future: asyncio.Future[IncomingTransferDecision] = self.runtime.loop.create_future()
        self._pending_incoming_future = future
        self._pending_incoming_transfer_id = transfer_id
        self._incomingTransferSignal.emit(
            {
                "transfer_id": transfer_id,
                "sender_name": sender_name,
                "receive_dir": self._receive_dir,
                "items": preview_entries,
            }
        )
        self._push_event(f"Incoming transfer request from {sender_name}")
        try:
            return await future
        except asyncio.CancelledError:
            if self._pending_incoming_future is future:
                self._pending_incoming_future = None
                self._pending_incoming_transfer_id = None
            self._incoming_transfer_sender_closed = True
            self._incoming_transfer_visible = True
            self.incomingTransferChanged.emit()
            raise

    def _clear_incoming_transfer(self) -> None:
        self._incoming_transfer_visible = False
        self._incoming_transfer_sender_closed = False
        self._incoming_transfer_sender_name = ""
        self._incoming_transfer_target_dir = self._receive_dir
        self._incoming_transfer_items = []
        self.incomingTransferChanged.emit()

    def _selected_incoming_paths(self) -> set[str]:
        selected_files = {
            item.relative_path
            for item in self._incoming_transfer_items
            if not item.is_directory and item.selected
        }
        if not selected_files:
            return set()

        directory_paths = {
            item.relative_path
            for item in self._incoming_transfer_items
            if item.is_directory
        }
        resolved = set(selected_files)
        for file_path in selected_files:
            current = PurePosixPath(file_path).parent
            while current.as_posix() not in {"", "."}:
                current_text = current.as_posix()
                if current_text in directory_paths:
                    resolved.add(current_text)
                current = current.parent
        return resolved

    def _resolve_pending_incoming(self, decision: IncomingTransferDecision) -> None:
        future = self._pending_incoming_future
        self._pending_incoming_future = None
        self._pending_incoming_transfer_id = None
        if future is None:
            self._clear_incoming_transfer()
            return

        def _set_result() -> None:
            if not future.done():
                future.set_result(decision)

        self.runtime.loop.call_soon_threadsafe(_set_result)
        self._clear_incoming_transfer()

    @Property(bool, notify=incomingTransferChanged)
    def incomingTransferVisible(self) -> bool:
        return self._incoming_transfer_visible

    @Property(str, notify=incomingTransferChanged)
    def incomingTransferSenderName(self) -> str:
        return self._incoming_transfer_sender_name

    @Property(str, notify=incomingTransferChanged)
    def incomingTransferTargetDir(self) -> str:
        return self._incoming_transfer_target_dir

    @Property(str, notify=incomingTransferChanged)
    def incomingTransferSummary(self) -> str:
        return self._incoming_summary()

    @Property(bool, notify=incomingTransferChanged)
    def incomingTransferSenderClosed(self) -> bool:
        return self._incoming_transfer_sender_closed

    @Property(bool, notify=incomingTransferChanged)
    def incomingTransferCanAccept(self) -> bool:
        return (
            not self._incoming_transfer_sender_closed
            and any(item.selected for item in self._incoming_transfer_items if not item.is_directory)
        )

    @Property("QVariantList", notify=incomingTransferChanged)
    def incomingTransferItems(self) -> list[dict[str, Any]]:
        return [self._serialize_incoming_item(item) for item in self._incoming_transfer_items]

    @Property(bool, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestVisible(self) -> bool:
        return self._outgoing_request_visible

    @Property(bool, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestRejected(self) -> bool:
        return self._outgoing_request_rejected

    @Property(str, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestMessage(self) -> str:
        if self._outgoing_request_rejected:
            return "Receiver rejected the request"
        return "Waiting for response..."

    @Property(str, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestActionLabel(self) -> str:
        return "Close" if self._outgoing_request_rejected else "Cancel"

    @Property(str, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestSenderName(self) -> str:
        return self._outgoing_request_sender_name

    @Property(str, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestSenderId(self) -> str:
        return self._outgoing_request_sender_id

    @Property(str, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestReceiverName(self) -> str:
        return self._outgoing_request_receiver_name

    @Property(str, notify=outgoingTransferRequestChanged)
    def outgoingTransferRequestReceiverId(self) -> str:
        return self._outgoing_request_receiver_id

    @Property(bool, notify=receiveSessionChanged)
    def receiveSessionVisible(self) -> bool:
        return self._receive_session_visible

    @Property(bool, notify=receiveSessionChanged)
    def receiveSessionActive(self) -> bool:
        return self._receive_session_active

    @Property(bool, notify=receiveSessionChanged)
    def receiveSessionCanExit(self) -> bool:
        return self._receive_session_visible and not self._receive_session_active

    @Property(str, notify=receiveSessionChanged)
    def receiveSessionTitle(self) -> str:
        if self._receive_session_active:
            return "Receiving files"
        if self._receive_session_finished:
            return "Finished"
        return "Receive session"

    @Property(str, notify=receiveSessionChanged)
    def receiveSessionTargetDir(self) -> str:
        return self._receive_session_target_dir

    @Property(str, notify=receiveSessionChanged)
    def receiveSessionSummary(self) -> str:
        total, completed, _, _ = self._receive_session_totals()
        return f"Files: {completed}/{total}"

    @Property(str, notify=receiveSessionChanged)
    def receiveSessionSizeLine(self) -> str:
        _, _, total_bytes, received_bytes = self._receive_session_totals()
        return f"Size: {self._format_bytes(received_bytes)} / {self._format_bytes(total_bytes)}"

    @Property(str, notify=receiveSessionChanged)
    def receiveSessionSpeedLine(self) -> str:
        return f"Speed: {self._format_bytes(int(self._receive_session_speed_bps))}/s"

    @Property(float, notify=receiveSessionChanged)
    def receiveSessionProgress(self) -> float:
        _, _, total_bytes, received_bytes = self._receive_session_totals()
        if total_bytes <= 0:
            return 0.0
        return max(0.0, min(1.0, received_bytes / total_bytes))

    @Property("QVariantList", notify=receiveSessionChanged)
    def receiveSessionFiles(self) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        for item in self._receive_session_files:
            rows.append(
                {
                    "relativePath": item.get("relativePath", ""),
                    "fileName": item.get("fileName", ""),
                    "sizeText": self._format_bytes(int(item.get("sizeBytes", 0) or 0)),
                    "statusText": "Done"
                    if bool(item.get("done"))
                    else f"{self._format_bytes(int(item.get('receivedBytes', 0) or 0))} / {self._format_bytes(int(item.get('sizeBytes', 0) or 0))}",
                    "done": bool(item.get("done")),
                    "openPath": item.get("openPath", ""),
                    "canOpen": bool(item.get("openPath")),
                }
            )
        return rows

    def _receive_session_totals(self) -> tuple[int, int, int, int]:
        total_files = len(self._receive_session_files)
        completed = sum(1 for item in self._receive_session_files if bool(item.get("done")))
        total_bytes = sum(int(item.get("sizeBytes", 0) or 0) for item in self._receive_session_files)
        received_bytes = 0
        for item in self._receive_session_files:
            size = int(item.get("sizeBytes", 0) or 0)
            received = int(item.get("receivedBytes", 0) or 0)
            cap = size if size > 0 else received
            received_bytes += min(received, cap)
        return total_files, completed, total_bytes, received_bytes

    def _update_receive_session_progress(self, relative_path: str, sent: int, total: int) -> None:
        if not self._receive_session_visible:
            return
        now = time.monotonic()
        changed = False
        for item in self._receive_session_files:
            if item.get("relativePath") != relative_path:
                continue
            if total > 0:
                item["sizeBytes"] = total
            item["receivedBytes"] = max(0, sent)
            if int(item.get("sizeBytes", 0) or 0) > 0 and int(item.get("receivedBytes", 0) or 0) >= int(item.get("sizeBytes", 0) or 0):
                item["done"] = True
            changed = True
            break
        if changed:
            _, _, _, received_bytes = self._receive_session_totals()
            if self._receive_session_last_ts > 0.0:
                dt = max(0.001, now - self._receive_session_last_ts)
                delta = max(0, received_bytes - self._receive_session_last_bytes)
                inst = delta / dt
                self._receive_session_speed_bps = inst if self._receive_session_speed_bps <= 0 else (
                    self._receive_session_speed_bps * 0.6 + inst * 0.4
                )
            self._receive_session_last_ts = now
            self._receive_session_last_bytes = received_bytes
        if changed:
            self.receiveSessionChanged.emit()

    def _handle_receive_session_signal(self, text: str) -> bool:
        if not text.startswith("RECEIVE_SESSION:"):
            return False
        payload_raw = text.removeprefix("RECEIVE_SESSION:").strip()
        try:
            payload = json.loads(payload_raw)
        except Exception:
            return True

        event_type = str(payload.get("type", "")).strip()
        transfer_id = str(payload.get("transfer_id", "")).strip()

        if event_type == "start":
            files: list[dict[str, Any]] = []
            for row in payload.get("files", []):
                rel = str(row.get("relative_path", "")).strip()
                if not rel:
                    continue
                files.append(
                    {
                        "relativePath": rel,
                        "fileName": str(row.get("file_name") or PurePosixPath(rel).name),
                        "sizeBytes": int(row.get("size", 0) or 0),
                        "receivedBytes": 0,
                        "done": False,
                        "openPath": "",
                    }
                )
            self._receive_session_visible = True
            self._receive_session_active = True
            self._receive_session_finished = False
            self._receive_session_transfer_id = transfer_id
            self._receive_session_target_dir = str(payload.get("target_dir", self._receive_dir))
            self._receive_session_files = files
            self._receive_session_started_at = time.monotonic()
            self._receive_session_last_ts = self._receive_session_started_at
            self._receive_session_last_bytes = 0
            self._receive_session_speed_bps = 0.0
            self.receiveSessionChanged.emit()
            return True

        if self._receive_session_transfer_id and transfer_id and transfer_id != self._receive_session_transfer_id:
            return True

        if event_type == "file_done":
            rel = str(payload.get("relative_path", "")).strip()
            open_path = str(payload.get("open_path", "")).strip()
            size = int(payload.get("size", 0) or 0)
            for item in self._receive_session_files:
                if item.get("relativePath") != rel:
                    continue
                if size > 0:
                    item["sizeBytes"] = size
                    item["receivedBytes"] = size
                item["done"] = True
                if open_path:
                    item["openPath"] = open_path
                break
            self.receiveSessionChanged.emit()
            return True

        if event_type == "complete":
            self._receive_session_active = False
            self._receive_session_finished = True
            self.receiveSessionChanged.emit()
            return True

        if event_type in {"cancelled", "error"}:
            self._receive_session_active = False
            self._receive_session_finished = False
            self._receive_session_speed_bps = 0.0
            self.receiveSessionChanged.emit()
            return True

        return True

    def _clear_outgoing_transfer_request(self) -> None:
        if (
            not self._outgoing_request_visible
            and not self._outgoing_request_rejected
            and not self._pending_send_target_name
            and not self._pending_send_target_id
        ):
            return
        self._outgoing_request_visible = False
        self._outgoing_request_rejected = False
        self._outgoing_request_sender_name = ""
        self._outgoing_request_sender_id = ""
        self._outgoing_request_receiver_name = ""
        self._outgoing_request_receiver_id = ""
        self._pending_send_target_name = ""
        self._pending_send_target_id = ""
        self.outgoingTransferRequestChanged.emit()

    def _show_outgoing_transfer_request(self, rejected: bool) -> None:
        self._outgoing_request_visible = True
        self._outgoing_request_rejected = rejected
        self._outgoing_request_sender_name = self._alias or self.device_name
        self._outgoing_request_sender_id = self.device_id[:8]
        receiver_name = self._pending_send_target_name.strip()
        receiver_id = self._pending_send_target_id.strip()
        if not receiver_name:
            receiver_name = self._active_target_name if self._selected_device_id else "Receiver"
        if not receiver_id:
            receiver_id = (self._selected_device_id or "").strip()
        self._outgoing_request_receiver_name = receiver_name
        self._outgoing_request_receiver_id = receiver_id[:8] if receiver_id else ""
        self.outgoingTransferRequestChanged.emit()

    def _get_or_create_alias(self) -> str:
        if hasattr(self.config_obj, "alias") and self.config_obj.alias:
            return self.config_obj.alias
        
        adjectives = ["Solid", "Neon", "Swift", "Silent", "Hyper", "Vibrant", "Quantum", "Solar", "Lunar", "Arctic"]
        nouns = ["Mango", "Dolphin", "Phoenix", "Nova", "Pulse", "Drift", "Aura", "Sky", "Wave", "Storm"]
        alias = f"{secrets.choice(adjectives)} {secrets.choice(nouns)}"
        
        # Save it back to config
        self.config_obj.alias = alias
        save_config(self.config_obj)
        return alias

    @Slot(str)
    def setAlias(self, value: str) -> None:
        cleaned = value.strip()[:42]
        if not cleaned:
            cleaned = self.device_name
        if cleaned == self._alias:
            return
        self._alias = cleaned
        self.config_obj.alias = cleaned
        save_config(self.config_obj)
        self.client.device_name = cleaned
        if self.discovery is not None:
            self.discovery.device_name = cleaned
            self.discovery.send_announce()
        if self.server is not None:
            self.server.device_name = cleaned
        self.aliasChanged.emit()

    @Slot(int)
    def setQuickSaveMode(self, mode: int) -> None:
        if mode == self._quick_save_mode:
            return
        self._quick_save_mode = mode
        self.config_obj.quick_save_mode = mode
        save_config(self.config_obj)
        self.quickSaveModeChanged.emit()

    @Slot()
    def clearHistory(self) -> None:
        self._history = []
        self.historyChanged.emit()

    @Slot(str)
    def toggleFavorite(self, device_id: str) -> None:
        if device_id in self._favorites:
            self._favorites.remove(device_id)
        else:
            self._favorites.append(device_id)
        self.config_obj.favorites = self._favorites
        save_config(self.config_obj)
        self.favoritesChanged.emit()

    def _push_event(self, text: str) -> None:
        if not text.startswith("SENDER_REQUEST:") and not text.startswith("RECEIVE_SESSION:"):
            self._history.insert(0, {"text": text, "time": ""})
            if len(self._history) > 100:
                self._history.pop()
            self.historyChanged.emit()
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
            device_name=self._alias or self.device_name,
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
        # If the currently selected device has gone undiscoverable, auto-deselect it
        if self._selected_device_id and self._selected_device_id not in devices:
            self._set_active_target(None)

    def _handle_outgoing_request_signal(self, text: str) -> bool:
        if not text.startswith("SENDER_REQUEST:"):
            return False
        signal = text.removeprefix("SENDER_REQUEST:").strip()
        if signal == "waiting":
            self._show_outgoing_transfer_request(rejected=False)
        elif signal == "accepted":
            self._pending_send_selection_restore = []
            self._clear_outgoing_transfer_request()
        elif signal == "rejected":
            self._restore_pending_send_selection()
            self._show_outgoing_transfer_request(rejected=True)
        return True

    def _append_event(self, text: str) -> None:
        if self._handle_receive_session_signal(text):
            return
        if self._handle_outgoing_request_signal(text):
            return

        # Route PROGRESS: messages to the progress bar, not the event log
        if text.startswith("PROGRESS:"):
            try:
                _, rel, sent_s, total_s = text.split(":", 3)
                if rel in ("error", "cancelled"):
                    if self._receive_session_active:
                        self._receive_session_active = False
                        self._receive_session_finished = False
                        self.receiveSessionChanged.emit()
                    if self._pending_send_selection_restore:
                        self._restore_pending_send_selection()
                    self._transferring = False
                    self._transfer_progress = 0.0
                    self._transfer_file_name = ""
                    self.transferringChanged.emit()
                    self.transferProgressChanged.emit()
                    self.transferFileNameChanged.emit()
                    if self._outgoing_request_visible and not self._outgoing_request_rejected:
                        self._clear_outgoing_transfer_request()
                    return

                sent = int(sent_s)
                total = int(total_s)
                ratio = (sent / total) if total > 0 else 0.0
                self._update_receive_session_progress(rel, sent, total)
                self._pending_send_selection_restore = []
                if self._outgoing_request_visible:
                    self._clear_outgoing_transfer_request()
                self._transfer_file_name = rel.split("/")[-1].split("\\")[-1]
                self._transfer_progress = ratio
                self._transferring = True
                self.transferFileNameChanged.emit()
                self.transferProgressChanged.emit()
                self.transferringChanged.emit()
                # Auto-clear when 100%
                if ratio >= 1.0:
                    import threading
                    if self._progress_clear_timer:
                        self._progress_clear_timer.cancel()
                    def _clear():
                        self._transferring = False
                        self._transfer_progress = 0.0
                        self._transfer_file_name = ""
                        self.transferringChanged.emit()
                        self.transferProgressChanged.emit()
                        self.transferFileNameChanged.emit()
                    self._progress_clear_timer = threading.Timer(1.5, _clear)
                    self._progress_clear_timer.daemon = True
                    self._progress_clear_timer.start()
            except Exception:
                pass
            return

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

    @Property(str, notify=portTextChanged)
    def portText(self) -> str:
        return self._port_text

    @Property(str, notify=portStatusChanged)
    def portStatusText(self) -> str:
        return self._port_status_text

    @Property(str, notify=portStatusChanged)
    def portStatusTone(self) -> str:
        return self._port_status_tone

    @Property(str, notify=receiveDirChanged)
    def receiveDir(self) -> str:
        return self._receive_dir

    @Property(str, constant=True)
    def deviceName(self) -> str:
        return self._alias or self.device_name

    @Property(str, constant=True)
    def deviceId(self) -> str:
        return self.device_id

    @Property(str, notify=activeTargetChanged)
    def activeTargetName(self) -> str:
        return self._active_target_name

    @Property(str, notify=activeTargetChanged)
    def activeTargetAddress(self) -> str:
        return self._active_target_address
        
    @Property(str, notify=activeTargetChanged)
    def activeTargetId(self) -> str:
        return self._selected_device_id or ""

    @Property(bool, notify=serverRunningChanged)
    def serverRunning(self) -> bool:
        return self._server_running

    @Property(bool, notify=transferringChanged)
    def transferring(self) -> bool:
        return self._transferring

    @Property(str, notify=transferFileNameChanged)
    def transferFileName(self) -> str:
        return self._transfer_file_name

    @Property(float, notify=transferProgressChanged)
    def transferProgress(self) -> float:
        return self._transfer_progress

    @Property("QVariantList", notify=devicesChanged)
    def devices(self) -> list[dict[str, Any]]:
        return self._devices

    @Property("QVariantList", notify=sendSelectionChanged)
    def sendSelection(self) -> list[dict[str, Any]]:
        return [self._serialize_send_selection_item(item) for item in self._send_selection_items]

    @Property(int, notify=sendSelectionChanged)
    def sendSelectionCount(self) -> int:
        return len(self._send_selection_items)

    @Property(str, notify=sendSelectionChanged)
    def sendSelectionSizeText(self) -> str:
        total = sum(item.size_bytes for item in self._send_selection_items)
        return self._format_bytes(total)

    @Property("QVariantList", notify=eventsChanged)
    def events(self) -> list[dict[str, str]]:
        return self._events

    @Slot(str)
    def setPortText(self, value: str) -> None:
        if value == self._port_text:
            return
        self._port_text = value
        self.portTextChanged.emit()
        self._sync_port_status()

    @Slot(str)
    def selectDevice(self, device_id: str) -> None:
        self._set_active_target(self._device_index.get(device_id))

    @Slot()
    def refreshDiscovery(self) -> None:
        if self.discovery is not None:
            self.runtime.loop.call_soon_threadsafe(self.discovery.send_probe)
        self._push_event("Scanning local network for devices...")

    @Slot()
    def refreshDiscoveryQuietly(self) -> None:
        if self.discovery is not None:
            self.runtime.loop.call_soon_threadsafe(self.discovery.send_probe)

    @Slot()
    def saveConnectionSettings(self) -> None:
        port, message, tone = self._parsed_port()
        if port is None:
            self._set_port_status(message, tone)
            self._push_event(message)
            return
        self._save_port_setting(port)

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
    def chooseIncomingTransferFolder(self) -> None:
        if self._incoming_transfer_sender_closed:
            return
        start_dir = self._incoming_transfer_target_dir or self._receive_dir
        selected = QFileDialog.getExistingDirectory(None, "Choose Receive Folder", start_dir)
        if not selected:
            return
        self._incoming_transfer_target_dir = selected
        self.incomingTransferChanged.emit()

    @Slot(str, bool)
    def setIncomingTransferItemSelected(self, relative_path: str, selected: bool) -> None:
        if self._incoming_transfer_sender_closed:
            return
        changed = False
        for item in self._incoming_transfer_items:
            if item.relative_path == relative_path and not item.is_directory:
                if item.selected != selected:
                    item.selected = selected
                    changed = True
                break
        if changed:
            self.incomingTransferChanged.emit()

    @Slot(str, str)
    def renameIncomingTransferItem(self, relative_path: str, proposed_name: str) -> None:
        if self._incoming_transfer_sender_closed:
            return
        cleaned = proposed_name.strip()
        if not cleaned:
            return
        sanitized = self._sanitize_file_name(cleaned)
        for item in self._incoming_transfer_items:
            if item.relative_path == relative_path and not item.is_directory:
                item.proposed_name = sanitized
                return

    @Slot()
    def acceptIncomingTransfer(self) -> None:
        if self._incoming_transfer_sender_closed:
            self._clear_incoming_transfer()
            return
        accepted_paths = self._selected_incoming_paths()
        if not accepted_paths:
            self._push_event("Select at least one file to receive")
            return
        rename_map: dict[str, str] = {}
        for item in self._incoming_transfer_items:
            if item.is_directory or not item.selected:
                continue
            target_name = self._sanitize_file_name(item.proposed_name)
            if target_name == item.file_name:
                continue
            target_rel = f"{item.parent_path}/{target_name}" if item.parent_path else target_name
            rename_map[item.relative_path] = target_rel
        self._push_event(f"Accepted incoming transfer from {self._incoming_transfer_sender_name}")
        self._resolve_pending_incoming(
            IncomingTransferDecision(
                accepted=True,
                receive_dir=self._incoming_transfer_target_dir or self._receive_dir,
                rename_map=rename_map,
                accepted_paths=accepted_paths,
            )
        )

    @Slot()
    def declineIncomingTransfer(self) -> None:
        if self._incoming_transfer_sender_closed:
            self._clear_incoming_transfer()
            return
        self._push_event(f"Declined incoming transfer from {self._incoming_transfer_sender_name}")
        self._resolve_pending_incoming(
            IncomingTransferDecision(
                accepted=False,
                receive_dir=self._receive_dir,
                rename_map={},
                accepted_paths=set(),
                reason="Declined on receiver",
            )
        )

    @Slot()
    def acknowledgeIncomingTransferClosed(self) -> None:
        self._clear_incoming_transfer()

    @Slot()
    def openReceiveFolder(self) -> None:
        QDesktopServices.openUrl(QUrl.fromLocalFile(self._receive_dir))

    @Slot(str)
    def openReceivedSessionFile(self, file_path: str) -> None:
        path = file_path.strip()
        if not path:
            return
        QDesktopServices.openUrl(QUrl.fromLocalFile(path))

    @Slot()
    def completeReceiveSession(self) -> None:
        if self._receive_session_active:
            return
        self._receive_session_visible = False
        self._receive_session_finished = False
        self._receive_session_transfer_id = ""
        self._receive_session_target_dir = ""
        self._receive_session_files = []
        self._receive_session_speed_bps = 0.0
        self._receive_session_started_at = 0.0
        self._receive_session_last_ts = 0.0
        self._receive_session_last_bytes = 0
        self.receiveSessionChanged.emit()

    @Slot()
    def startServer(self) -> None:
        port, message, tone = self._parsed_port()
        if port is None:
            self._set_port_status(message, tone)
            self._push_event(message)
            return

        if self.server is not None:
            self._push_event("Server already running")
            return

        unavailable_reason = self._port_unavailable_reason(port)
        if unavailable_reason:
            self._set_port_status(unavailable_reason, "error")
            self._push_event(unavailable_reason)
            return

        self.config_obj.bind_host = "0.0.0.0"
        self.config_obj.receive_dir = self._receive_dir
        self.config_obj.port = port
        try:
            self._start_server_instance()
        except Exception as exc:
            self._set_port_status(f"Could not start receiver on port {port}: {exc}", "error")
            self._push_event(f"Could not start receiver on port {port}: {exc}")
            return
        save_config(self.config_obj)
        self._set_port_status(f"Receiver is online on port {port}.", "success")
        self._push_event("Server start requested")

    @Slot()
    def restartServer(self) -> None:
        port, message, tone = self._parsed_port()
        if port is None:
            self._set_port_status(message, tone)
            self._push_event(message)
            return
        if self.server is None:
            self.startServer()
            return

        unavailable_reason = self._port_unavailable_reason(port)
        if unavailable_reason:
            self._set_port_status(unavailable_reason, "error")
            self._push_event(unavailable_reason)
            return

        previous_port = self._active_server_port or self.config_obj.port
        self.config_obj.bind_host = "0.0.0.0"
        self.config_obj.receive_dir = self._receive_dir
        self.config_obj.port = port

        self._push_event(f"Restarting server on port {port}...")
        self._stop_server_instance(announce_offline=True)
        try:
            self._start_server_instance()
        except Exception as exc:
            self.config_obj.port = previous_port
            try:
                self._start_server_instance()
            except Exception:
                pass
            message = f"Could not restart the server on port {port}: {exc}"
            self._set_port_status(message, "error")
            self._push_event(message)
            return

        save_config(self.config_obj)
        self._set_port_status(f"Server restarted on port {port}.", "success")
        self._push_event(f"Server restarted on port {port}")

    @Slot()
    def stopServer(self) -> None:
        if self._pending_incoming_future is not None:
            self._resolve_pending_incoming(
                IncomingTransferDecision(
                    accepted=False,
                    receive_dir=self._receive_dir,
                    rename_map={},
                    accepted_paths=set(),
                    reason="Receiver stopped",
                )
            )
        if self.server is None:
            return
        self._stop_server_instance(announce_offline=True)
        self._sync_port_status()
        self._push_event("Server stop requested")

    @Slot()
    def sendFiles(self) -> None:
        self.addSendFiles()

    @Slot()
    def addSendFiles(self) -> None:
        paths, _ = QFileDialog.getOpenFileNames(None, "Select files")
        if not paths:
            return
        added = self._add_send_selection_paths(paths)
        if added <= 0:
            self._push_event("Selected files are already in the collection")
            return
        self._push_event(f"Added {added} file(s) to selection")

    @Slot()
    def sendFolder(self) -> None:
        self.addSendFolder()

    @Slot()
    def addSendFolder(self) -> None:
        folder = QFileDialog.getExistingDirectory(None, "Select folder")
        if not folder:
            return
        added = self._add_send_selection_paths([folder])
        if added <= 0:
            self._push_event("Selected folder is already in the collection")
            return
        self._push_event("Added folder to selection")

    @Slot("QVariantList")
    def addSendDroppedPaths(self, urls: list[Any]) -> None:
        paths: list[str] = []
        for raw in urls:
            url = raw if isinstance(raw, QUrl) else QUrl(str(raw))
            local_path = url.toLocalFile() if url.isValid() else ""
            if local_path:
                paths.append(local_path)
                continue
            text = str(raw).strip()
            if text.startswith("file:///"):
                maybe_local = QUrl(text).toLocalFile()
                if maybe_local:
                    paths.append(maybe_local)
        if not paths:
            return
        added = self._add_send_selection_paths(paths)
        if added <= 0:
            self._push_event("Dropped items are already in the collection")
            return
        self._push_event(f"Added {added} item(s) to selection")

    @Slot(str)
    def removeSendSelectionItem(self, path: str) -> None:
        target = path.strip()
        if not target:
            return
        kept = [item for item in self._send_selection_items if str(item.path) != target]
        if len(kept) == len(self._send_selection_items):
            return
        self._send_selection_items = kept
        self.sendSelectionChanged.emit()

    @Slot()
    def clearSendSelection(self) -> None:
        if not self._send_selection_items:
            return
        self._send_selection_items = []
        self.sendSelectionChanged.emit()

    def _restore_pending_send_selection(self) -> None:
        if not self._pending_send_selection_restore:
            return
        merged: dict[Path, SendSelectionItem] = {
            item.path: item for item in self._pending_send_selection_restore
        }
        for item in self._send_selection_items:
            merged[item.path] = item
        self._send_selection_items = list(merged.values())
        self._pending_send_selection_restore = []
        self.sendSelectionChanged.emit()

    @Slot(str)
    def sendSelectionToDevice(self, device_id: str) -> None:
        key = device_id.strip()
        if not key:
            self._push_event("Select a discovered device first")
            return
        device = self._device_index.get(key)
        if device is None:
            self._push_event("Selected device is no longer available")
            return
        if not self._send_selection_items:
            self._push_event("Add files to selection before sending")
            return

        source_paths: list[Path] = []
        removed_missing = 0
        for item in self._send_selection_items:
            if item.path.exists():
                source_paths.append(item.path)
            else:
                removed_missing += 1
        if removed_missing:
            self._send_selection_items = [item for item in self._send_selection_items if item.path.exists()]
            self.sendSelectionChanged.emit()
        if not source_paths:
            self._push_event("Selected files are no longer available")
            return

        self._set_active_target(device)
        self._pending_send_target_name = device.name
        self._pending_send_target_id = device.device_id
        self._pending_send_selection_restore = [
            SendSelectionItem(
                path=item.path,
                file_name=item.file_name,
                is_directory=item.is_directory,
                size_bytes=item.size_bytes,
            )
            for item in self._send_selection_items
        ]
        task = TransferTask(device.ip, device.port, source_paths, device.device_id)
        self.runtime.submit(self.transfer_queue.enqueue(task))
        self._send_selection_items = []
        self.sendSelectionChanged.emit()
        self._push_event(f"Queued {len(source_paths)} item(s) for {device.name} ({device.ip}:{device.port})")

    @Slot()
    def cancelOrCloseOutgoingTransferRequest(self) -> None:
        if not self._outgoing_request_visible:
            return
        if self._outgoing_request_rejected:
            self._clear_outgoing_transfer_request()
            return
        self.cancelTransfer()

    @Slot()
    def cancelTransfer(self) -> None:
        restore_selection = self._outgoing_request_visible and not self._outgoing_request_rejected
        self.client.cancel_transfer()
        if self.server:
            self.server.cancel_transfer()
        self.runtime.submit(self.transfer_queue.clear())
        if restore_selection:
            self._restore_pending_send_selection()
        self._transferring = False
        self._transfer_progress = 0.0
        self._transfer_file_name = ""
        self._clear_outgoing_transfer_request()
        self.transferringChanged.emit()
        self.transferProgressChanged.emit()
        self.transferFileNameChanged.emit()
        self._push_event("Cancellation requested for current transfers")

    @Slot()
    def shutdown(self) -> None:
        self._clear_outgoing_transfer_request()
        if self._pending_incoming_future is not None:
            self._resolve_pending_incoming(
                IncomingTransferDecision(
                    accepted=False,
                    receive_dir=self._receive_dir,
                    rename_map={},
                    accepted_paths=set(),
                    reason="Application shutting down",
                )
            )
        futures = []
        if self.server is not None:
            futures.append(self.runtime.submit(self.server.stop()))
            self.server = None
        if self.discovery is not None:
            # Broadcast bye before stopping so nearby phones disconnect immediately
            try:
                self.runtime.submit(self.discovery.send_bye()).result(timeout=1)
            except Exception:
                pass
            futures.append(self.runtime.submit(self.discovery.stop()))
            self.discovery = None
        # Cancel any ongoing transfer immediately
        futures.append(self.runtime.submit(self.transfer_queue.stop()))
        for future in futures:
            try:
                future.result(timeout=2)
            except Exception:
                pass
        self.runtime.stop()
