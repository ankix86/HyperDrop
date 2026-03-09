from __future__ import annotations

from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any


@dataclass(slots=True)
class DeviceRecord:
    device_id: str
    name: str
    trusted: bool = False
    last_seen: str = ""
    pairing_token_hash: str = ""


@dataclass(slots=True)
class AppConfig:
    bind_host: str
    port: int
    receive_dir: str
    auto_start_server: bool = True
    max_file_size: int = 0
    max_batch_size: int = 0
    alias: str = ""
    quick_save_mode: int = 0
    favorites: list[str] = field(default_factory=list)
    trusted_devices: dict[str, DeviceRecord] = field(default_factory=dict)

    def to_json(self) -> dict[str, Any]:
        data = asdict(self)
        data["trusted_devices"] = {k: asdict(v) for k, v in self.trusted_devices.items()}
        return data

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "AppConfig":
        trusted = {
            key: DeviceRecord(**value)
            for key, value in data.get("trusted_devices", {}).items()
        }
        return cls(
            bind_host=data["bind_host"],
            port=int(data["port"]),
            receive_dir=data["receive_dir"],
            auto_start_server=bool(data.get("auto_start_server", True)),
            max_file_size=int(data.get("max_file_size", 0)),
            max_batch_size=int(data.get("max_batch_size", 0)),
            alias=data.get("alias", ""),
            quick_save_mode=int(data.get("quick_save_mode", 0)),
            favorites=list(data.get("favorites", [])),
            trusted_devices=trusted,
        )


@dataclass(slots=True)
class ManifestEntry:
    relative_path: str
    file_name: str
    mime_type: str
    size: int
    modified_time: float
    checksum: str
    is_directory: bool


@dataclass(slots=True)
class TransferManifest:
    transfer_id: str
    sender_device_id: str
    receiver_device_id: str
    entries: list[ManifestEntry]


@dataclass(slots=True)
class TransferTask:
    target_ip: str
    target_port: int
    source_paths: list[Path]
    receiver_device_id: str
    overwrite: bool = False
