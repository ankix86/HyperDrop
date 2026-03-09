from __future__ import annotations

import asyncio
import math
import os
import queue
import secrets
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any

from app.core.config import load_config, save_config
from app.core.models import TransferTask
from app.network.client import LanClient
from app.network.discovery import DiscoveredDevice, DiscoveryService
from app.network.server import LanServer
from app.transfer.queue import TransferQueue
from app.ui.devices_view import DevicesView
from app.ui.settings_window import SettingsWindow
from app.ui.transfers_view import TransfersView


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


class MainWindow(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("HyperDrop (PC)")
        self.geometry("1140x760")
        self.minsize(980, 680)

        self._colors = {
            "bg": "#070A1F",
            "card": "#121539",
            "hero1": "#0E1446",
            "hero2": "#8A27FF",
            "hero3": "#2EEBFF",
            "text_dark": "#EAF0FF",
            "text_muted": "#A6B1DE",
            "status_bg": "#151A46",
            "status_fg": "#91F4FF",
        }
        self._hero_phase = 0.0
        self._status_phase = 0.0
        self._hero_canvas: tk.Canvas | None = None
        self._hero_ring_outer: int | None = None
        self._hero_ring_inner: int | None = None
        self._hero_glow: int | None = None
        self._status_wrap: tk.Frame | None = None
        self._status_label: tk.Label | None = None
        self.configure(bg=self._colors["bg"])
        self._window_icon = None
        self._set_window_icon()
        self._configure_styles()

        self.runtime = AsyncRuntime()
        self.config_obj = load_config()
        self.device_id = self._load_or_create_device_id()
        self.device_name = os.environ.get("COMPUTERNAME", "PC")

        self.server: LanServer | None = None
        self.discovery: DiscoveryService | None = None
        self.discovered_devices: dict[str, DiscoveredDevice] = {}
        self.client = LanClient(
            config=self.config_obj,
            device_id=self.device_id,
            device_name=self.device_name,
            pairing_code_provider=self._get_pairing_code,
            status_callback=self._push_event,
        )
        self.transfer_queue = TransferQueue(self.client.send_task, status_callback=self._push_event)
        self.runtime.submit(self.transfer_queue.start())

        self._pairing_code = "123456"
        self._event_queue: queue.Queue[tuple[str, Any]] = queue.Queue()
        self._build_ui()
        self._animate_vfx()
        self._drain_events()
        self._start_discovery()

        if self.config_obj.auto_start_server:
            self.start_server()

    def _build_ui(self) -> None:
        outer = ttk.Frame(self, style="Page.TFrame", padding=14)
        outer.pack(fill="both", expand=True)

        self._hero_canvas = tk.Canvas(outer, height=126, bg=self._colors["hero1"], highlightthickness=0)
        self._hero_canvas.pack(fill="x")
        self._hero_canvas.bind("<Configure>", lambda _e: self._paint_hero_gradient())
        self._paint_hero_gradient()

        self.status_var = tk.StringVar(value="Idle")
        self.port_var = tk.StringVar(value=str(self.config_obj.port))
        self.pairing_var = tk.StringVar(value=self._pairing_code)

        controls = ttk.Frame(outer, style="Page.TFrame")
        controls.pack(fill="x", pady=(12, 8))
        controls.columnconfigure(0, weight=4)
        controls.columnconfigure(1, weight=3)

        connection_card = ttk.Frame(controls, style="Card.TFrame", padding=12)
        connection_card.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        ttk.Label(connection_card, text="Connection", style="SectionTitle.TLabel").grid(row=0, column=0, columnspan=7, sticky="w", pady=(0, 8))

        ttk.Label(connection_card, text="Port", style="Muted.TLabel").grid(row=1, column=0, sticky="w", padx=(0, 4))
        ttk.Entry(connection_card, textvariable=self.port_var, width=8).grid(row=1, column=1, sticky="ew", padx=(0, 10))
        ttk.Label(connection_card, text="Pairing", style="Muted.TLabel").grid(row=1, column=2, sticky="w", padx=(0, 4))
        ttk.Entry(connection_card, textvariable=self.pairing_var, width=10).grid(row=1, column=3, sticky="ew", padx=(0, 10))
        self._make_button(connection_card, "Refresh Devices", self.refresh_discovery, accent=True).grid(row=1, column=4, padx=(0, 6))
        self._make_button(connection_card, "New Code", self._new_pairing_code).grid(row=1, column=5, padx=(0, 6))
        self._make_button(connection_card, "Settings", self.open_settings).grid(row=1, column=6)

        service_card = ttk.Frame(controls, style="Card.TFrame", padding=12)
        service_card.grid(row=0, column=1, sticky="nsew", padx=(8, 0))
        ttk.Label(service_card, text="Service", style="SectionTitle.TLabel").pack(anchor="w", pady=(0, 8))

        service_actions = ttk.Frame(service_card, style="Card.TFrame")
        service_actions.pack(fill="x")
        self._make_button(service_actions, "Start Server", self.start_server, accent=True).pack(side="left", padx=(0, 6))
        self._make_button(service_actions, "Stop Server", self.stop_server).pack(side="left", padx=(0, 6))
        self._make_button(service_actions, "Open Receive", self.open_receive_folder).pack(side="left")

        send_card = ttk.Frame(outer, style="Card.TFrame", padding=12)
        send_card.pack(fill="x", pady=(0, 8))
        ttk.Label(send_card, text="Quick Send", style="SectionTitle.TLabel").pack(anchor="w", pady=(0, 8))

        send_actions = ttk.Frame(send_card, style="Card.TFrame")
        send_actions.pack(fill="x")
        self._make_button(send_actions, "Send File(s)", self.send_files, accent=True).pack(side="left", padx=(0, 8))
        self._make_button(send_actions, "Send Folder", self.send_folder, accent=True).pack(side="left")

        self._status_wrap = tk.Frame(outer, bg=self._colors["status_bg"], bd=0, highlightthickness=0)
        self._status_wrap.pack(fill="x", pady=(0, 10))
        self._status_label = tk.Label(
            self._status_wrap,
            textvariable=self.status_var,
            bg=self._colors["status_bg"],
            fg=self._colors["status_fg"],
            font=("Bahnschrift", 10, "bold"),
            padx=10,
            pady=7,
            anchor="w",
        )
        self._status_label.pack(fill="x")

        body = ttk.PanedWindow(outer, orient="horizontal")
        body.pack(fill="both", expand=True)

        self.devices_view = DevicesView(body)
        self.transfers_view = TransfersView(body)
        body.add(self.devices_view, weight=2)
        body.add(self.transfers_view, weight=3)

        self.protocol("WM_DELETE_WINDOW", self._on_close)

    def _configure_styles(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass

        style.configure("Page.TFrame", background=self._colors["bg"])
        style.configure("Card.TFrame", background=self._colors["card"])
        style.configure("SectionTitle.TLabel", background=self._colors["card"], foreground=self._colors["text_dark"], font=("Bahnschrift", 11, "bold"))
        style.configure("Muted.TLabel", background=self._colors["card"], foreground=self._colors["text_muted"], font=("Segoe UI", 9))

        style.configure("TLabel", background=self._colors["card"], foreground=self._colors["text_dark"], font=("Segoe UI", 9))
        style.configure("TEntry", fieldbackground="#1A1F52", foreground="#EAF0FF", bordercolor="#3E4DA1")
        style.configure("Data.Treeview", rowheight=24, font=("Segoe UI", 9), background="#161B45", foreground="#EAF0FF", fieldbackground="#161B45")
        style.configure("Data.Treeview.Heading", font=("Bahnschrift", 9, "bold"), background="#1E2461", foreground="#EAF0FF")

    def _make_button(self, parent: tk.Misc, text: str, command, accent: bool = False) -> tk.Button:
        bg = "#8A27FF" if accent else "#E8ECFF"
        fg = "#FFFFFF" if accent else "#0E1446"
        active_bg = "#A33FFF" if accent else "#C9D4FF"
        active_fg = "#FFFFFF" if accent else "#091032"

        button = tk.Button(
            parent,
            text=text,
            command=command,
            bg=bg,
            fg=fg,
            activebackground=active_bg,
            activeforeground=active_fg,
            relief="flat",
            bd=0,
            highlightthickness=1,
            highlightbackground="#F5F7FF" if accent else "#98A6F2",
            highlightcolor="#53EEFF",
            padx=14,
            pady=8,
            font=("Bahnschrift", 10, "bold"),
            cursor="hand2",
        )

        def on_enter(_event):
            button.configure(bg=active_bg, fg=active_fg)

        def on_leave(_event):
            button.configure(bg=bg, fg=fg)

        button.bind("<Enter>", on_enter)
        button.bind("<Leave>", on_leave)
        return button

    def _paint_hero_gradient(self) -> None:
        if self._hero_canvas is None:
            return
        canvas = self._hero_canvas
        canvas.delete("all")
        width = max(canvas.winfo_width(), 1000)
        height = max(canvas.winfo_height(), 126)
        c1 = self._hex_to_rgb(self._colors["hero1"])
        c2 = self._hex_to_rgb(self._colors["hero2"])
        c3 = self._hex_to_rgb(self._colors["hero3"])

        for x in range(0, width, 3):
            ratio = x / max(width - 1, 1)
            if ratio < 0.55:
                local = ratio / 0.55
                color = self._blend(c1, c2, local)
            else:
                local = (ratio - 0.55) / 0.45
                color = self._blend(c2, c3, local)
            canvas.create_line(x, 0, x, height, fill=self._rgb_to_hex(color), width=3)

        self._hero_glow = canvas.create_oval(
            width - 220,
            -30,
            width - 90,
            100,
            fill="#4EEBFF",
            outline="",
            stipple="gray50",
        )
        self._hero_ring_outer = canvas.create_oval(
            width - 360,
            -180,
            width + 120,
            220,
            outline="#6FE6FF",
            width=3,
        )
        self._hero_ring_inner = canvas.create_oval(
            width - 420,
            -220,
            width + 140,
            260,
            outline="#C45BFF",
            width=2,
        )
        canvas.create_text(
            22,
            36,
            anchor="w",
            text="HyperDrop Control Deck",
            fill="#F4F7FF",
            font=("Bahnschrift", 21, "bold"),
        )
        canvas.create_text(
            22,
            72,
            anchor="w",
            text="Neon-speed encrypted transfer across PC and Android",
            fill="#D7E5FF",
            font=("Segoe UI", 11),
        )

    def _animate_vfx(self) -> None:
        self._hero_phase = (self._hero_phase + 0.0065) % 1.0
        self._status_phase = (self._status_phase + 0.08) % 6.28318

        if self._hero_canvas is not None:
            width = max(self._hero_canvas.winfo_width(), 1000)
            wave = math.sin(self._hero_phase * math.tau)
            drift_outer = int(wave * 14)
            drift_inner = int(math.cos(self._hero_phase * math.tau * 1.3) * 18)
            drift_glow = int(wave * 26)
            if self._hero_ring_outer is not None:
                self._hero_canvas.coords(
                    self._hero_ring_outer,
                    width - 360 + drift_outer,
                    -180,
                    width + 120 + drift_outer,
                    220,
                )
            if self._hero_ring_inner is not None:
                self._hero_canvas.coords(
                    self._hero_ring_inner,
                    width - 420 + drift_inner,
                    -220,
                    width + 140 + drift_inner,
                    260,
                )
            if self._hero_glow is not None:
                self._hero_canvas.coords(
                    self._hero_glow,
                    width - 220 + drift_glow,
                    -30,
                    width - 90 + drift_glow,
                    100,
                )

        if self._status_wrap is not None and self._status_label is not None:
            wave = (1.0 + math.sin(self._status_phase)) * 0.5
            bg = self._shift_color(self._colors["status_bg"], 0.24 * wave)
            self._status_wrap.configure(bg=bg)
            self._status_label.configure(bg=bg)

        self.after(90, self._animate_vfx)

    def _shift_color(self, hex_color: str, amount: float) -> str:
        r, g, b = self._hex_to_rgb(hex_color)
        return self._rgb_to_hex(
            (
                min(255, int(r + (255 - r) * amount)),
                min(255, int(g + (255 - g) * amount)),
                min(255, int(b + (255 - b) * amount)),
            )
        )

    @staticmethod
    def _hex_to_rgb(value: str) -> tuple[int, int, int]:
        value = value.lstrip("#")
        return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)

    @staticmethod
    def _rgb_to_hex(rgb: tuple[int, int, int]) -> str:
        return f"#{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}"

    @staticmethod
    def _blend(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
        t = max(0.0, min(1.0, t))
        return (
            int(a[0] + (b[0] - a[0]) * t),
            int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t),
        )

    def _load_or_create_device_id(self) -> str:
        from app.core.constants import APP_DIR

        APP_DIR.mkdir(parents=True, exist_ok=True)
        f = APP_DIR / "device_id.txt"
        if f.exists():
            return f.read_text(encoding="utf-8").strip()
        value = secrets.token_hex(12)
        f.write_text(value, encoding="utf-8")
        return value

    def _set_window_icon(self) -> None:
        icon_path = Path(__file__).resolve().parents[2] / "assets" / "icons" / "hyperdrop-icon-256.png"
        if not icon_path.exists():
            return
        try:
            self._window_icon = tk.PhotoImage(file=str(icon_path))
            self.iconphoto(True, self._window_icon)
        except Exception:
            pass

    def _new_pairing_code(self) -> None:
        self._pairing_code = f"{secrets.randbelow(900000) + 100000}"
        self.pairing_var.set(self._pairing_code)

    def _get_pairing_code(self) -> str:
        return self.pairing_var.get().strip()

    def _push_event(self, text: str) -> None:
        self._event_queue.put(("info", text))

    def _drain_events(self) -> None:
        try:
            while True:
                level, payload = self._event_queue.get_nowait()
                if level == "devices":
                    self.discovered_devices = payload
                    continue
                text = str(payload)
                self.status_var.set(text)
                self.transfers_view.add_event(level, text)
        except queue.Empty:
            pass

        self.devices_view.update_devices(self.config_obj.trusted_devices, self.discovered_devices)
        self.after(250, self._drain_events)

    def _on_discovery_update(self, devices: dict[str, DiscoveredDevice]) -> None:
        self._event_queue.put(("devices", devices))

    def _start_discovery(self) -> None:
        self.discovery = DiscoveryService(
            device_id=self.device_id,
            device_name=self.device_name,
            tcp_port=self.config_obj.port,
            on_devices=self._on_discovery_update,
            platform="pc",
        )
        self.runtime.submit(self.discovery.start())

    def refresh_discovery(self) -> None:
        if self.discovery is not None:
            self.runtime.loop.call_soon_threadsafe(self.discovery.send_probe)
            self._push_event("Scanning local network for devices...")

    def start_server(self) -> None:
        try:
            self.config_obj.bind_host = "0.0.0.0"
            self.config_obj.port = int(self.port_var.get().strip())
            save_config(self.config_obj)
        except Exception as exc:
            messagebox.showerror("Server", f"Invalid bind settings: {exc}")
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
        self._push_event("Server start requested")

    def stop_server(self) -> None:
        if self.server is None:
            return
        self.runtime.submit(self.server.stop())
        self.server = None
        self._push_event("Server stop requested")

    def open_settings(self) -> None:
        SettingsWindow(self, self.config_obj, on_save=lambda: self._push_event("Settings saved"))

    def send_files(self) -> None:
        paths = filedialog.askopenfilenames(title="Select files")
        if not paths:
            return
        target = self.devices_view.get_selected_target()
        if not target:
            messagebox.showinfo("Target Required", "Select a discovered device from the list first.")
            return
        ip, port, device_id = target
        task = TransferTask(ip, port, [Path(p) for p in paths], device_id)
        self.runtime.submit(self.transfer_queue.enqueue(task))
        self._push_event(f"Queued {len(paths)} file(s) for {ip}:{port}")

    def send_folder(self) -> None:
        folder = filedialog.askdirectory(title="Select folder")
        if not folder:
            return
        target = self.devices_view.get_selected_target()
        if not target:
            messagebox.showinfo("Target Required", "Select a discovered device from the list first.")
            return
        ip, port, device_id = target
        task = TransferTask(ip, port, [Path(folder)], device_id)
        self.runtime.submit(self.transfer_queue.enqueue(task))
        self._push_event(f"Queued folder {folder} for {ip}:{port}")

    def open_receive_folder(self) -> None:
        os.startfile(self.config_obj.receive_dir)

    def _on_close(self) -> None:
        try:
            self.stop_server()
            if self.discovery is not None:
                self.runtime.submit(self.discovery.stop())
            # Cancel any ongoing transfer immediately
            self.runtime.submit(self.transfer_queue.stop())
        except Exception:
            pass
        self.runtime.stop()
        self.destroy()
