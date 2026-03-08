import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from typing import Callable

from app.core.config import save_config
from app.core.models import AppConfig
from app.utils.validators import validate_port


class SettingsWindow(tk.Toplevel):
    def __init__(self, master: tk.Misc, config: AppConfig, on_save: Callable[[], None]):
        super().__init__(master)
        self.title("Settings")
        self.resizable(False, False)
        self.config_obj = config
        self.on_save = on_save

        self.port_var = tk.StringVar(value=str(config.port))
        self.receive_var = tk.StringVar(value=config.receive_dir)

        frame = ttk.Frame(self, padding=10)
        frame.pack(fill="both", expand=True)

        ttk.Label(frame, text="Port").grid(row=0, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.port_var, width=28).grid(row=0, column=1, sticky="ew")
        ttk.Label(frame, text="Receive Folder").grid(row=1, column=0, sticky="w")
        ttk.Entry(frame, textvariable=self.receive_var, width=28).grid(row=1, column=1, sticky="ew")
        ttk.Button(frame, text="Browse", command=self._browse).grid(row=1, column=2, padx=4)
        ttk.Button(frame, text="Save", command=self._save).grid(row=2, column=2, pady=10)

    def _browse(self) -> None:
        chosen = filedialog.askdirectory(initialdir=self.receive_var.get())
        if chosen:
            self.receive_var.set(chosen)

    def _save(self) -> None:
        try:
            port = int(self.port_var.get())
        except ValueError:
            messagebox.showerror("Settings", "Port must be numeric")
            return
        if not validate_port(port):
            messagebox.showerror("Settings", "Port must be in range 1024-65535")
            return

        self.config_obj.bind_host = "0.0.0.0"
        self.config_obj.port = port
        self.config_obj.receive_dir = self.receive_var.get().strip()
        save_config(self.config_obj)
        self.on_save()
        self.destroy()
