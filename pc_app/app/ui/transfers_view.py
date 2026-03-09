import tkinter as tk
from tkinter import ttk


class TransfersView(ttk.Frame):
    def __init__(self, master: tk.Misc):
        super().__init__(master, style="Card.TFrame", padding=10)

        header = ttk.Frame(self, style="Card.TFrame")
        header.pack(fill="x", pady=(0, 8))
        ttk.Label(header, text="Transfer Log", style="SectionTitle.TLabel").pack(side="left")
        self.count_var = tk.StringVar(value="0 events")
        ttk.Label(header, textvariable=self.count_var, style="Muted.TLabel").pack(side="right")

        table = ttk.Frame(self, style="Card.TFrame")
        table.pack(fill="both", expand=True)

        self.tree = ttk.Treeview(
            table,
            columns=("status", "details"),
            show="headings",
            height=14,
            style="Data.Treeview",
        )
        self.tree.heading("status", text="Status")
        self.tree.heading("details", text="Details")
        self.tree.column("status", width=90, anchor="center", stretch=False)
        self.tree.column("details", width=560, stretch=True)

        scrollbar = ttk.Scrollbar(table, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)

        self.tree.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

    def add_event(self, status: str, details: str) -> None:
        self.tree.insert("", tk.END, values=(status, details))
        children = self.tree.get_children()
        self.count_var.set(f"{len(children)} events")
        if children:
            self.tree.see(children[-1])

    def add_cancel_button(self, cancel_callback):
        btn = ttk.Button(self, text="Cancel Transfer", command=cancel_callback, style="Danger.TButton")
        btn.pack(fill="x", pady=8)
