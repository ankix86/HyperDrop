import tkinter as tk
from tkinter import ttk

from app.core.models import DeviceRecord
from app.network.discovery import DiscoveredDevice


class DevicesView(ttk.Frame):
    def __init__(self, master: tk.Misc):
        super().__init__(master, style="Card.TFrame", padding=10)

        ttk.Label(self, text="Discovered Devices", style="SectionTitle.TLabel").pack(anchor="w", pady=(0, 8))

        table = ttk.Frame(self, style="Card.TFrame")
        table.pack(fill="both", expand=True)

        self.tree = ttk.Treeview(
            table,
            columns=("name", "platform", "address", "trusted"),
            show="headings",
            height=12,
            style="Data.Treeview",
        )
        self.tree.heading("name", text="Name")
        self.tree.heading("platform", text="Platform")
        self.tree.heading("address", text="Address")
        self.tree.heading("trusted", text="Paired")

        self.tree.column("name", width=220, stretch=True)
        self.tree.column("platform", width=90, anchor="center", stretch=False)
        self.tree.column("address", width=170, stretch=False)
        self.tree.column("trusted", width=80, anchor="center", stretch=False)

        self._devices: dict[str, DiscoveredDevice] = {}
        self._last_signature: tuple[tuple[str, str, str, str], ...] = tuple()

        scrollbar = ttk.Scrollbar(table, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)

        self.tree.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

    def update_devices(self, paired: dict[str, DeviceRecord], discovered: dict[str, DiscoveredDevice]) -> None:
        current_selection = self.tree.selection()
        selected_id = current_selection[0] if current_selection else None

        rows = []
        for dev in sorted(discovered.values(), key=lambda item: (item.name.lower(), item.ip, item.port)):
            is_paired = paired.get(dev.device_id)
            rows.append(
                (
                    dev.device_id,
                    dev.name,
                    dev.platform,
                    f"{dev.ip}:{dev.port}",
                    "yes" if (is_paired and is_paired.trusted) else "no",
                )
            )

        signature = tuple((r[0], r[1], r[2], r[3], r[4]) for r in rows)
        if signature == self._last_signature and self._devices.keys() == discovered.keys():
            return

        for row in self.tree.get_children():
            self.tree.delete(row)
        self._devices = dict(discovered)
        self._last_signature = signature

        for dev_id, name, platform, address, trusted in rows:
            self.tree.insert(
                "",
                tk.END,
                iid=dev_id,
                values=(name, platform, address, trusted),
            )

        if selected_id and selected_id in self._devices:
            self.tree.selection_set(selected_id)

    def get_selected_target(self) -> tuple[str, int, str] | None:
        selected = self.tree.selection()
        if not selected:
            return None
        dev = self._devices.get(selected[0])
        if dev is None:
            return None
        return dev.ip, dev.port, dev.device_id
