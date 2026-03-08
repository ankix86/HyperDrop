from __future__ import annotations

import json
from pathlib import Path

from app.core.constants import (
    APP_DIR,
    CONFIG_FILE_NAME,
    DEFAULT_BIND_HOST,
    DEFAULT_MAX_BATCH_SIZE,
    DEFAULT_MAX_FILE_SIZE,
    DEFAULT_PORT,
    DEFAULT_RECEIVE_DIR,
)
from app.core.models import AppConfig


def _default_config() -> AppConfig:
    return AppConfig(
        bind_host=DEFAULT_BIND_HOST,
        port=DEFAULT_PORT,
        receive_dir=str(DEFAULT_RECEIVE_DIR),
        auto_start_server=True,
        max_file_size=DEFAULT_MAX_FILE_SIZE,
        max_batch_size=DEFAULT_MAX_BATCH_SIZE,
    )


def config_path() -> Path:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    return APP_DIR / CONFIG_FILE_NAME


def load_config() -> AppConfig:
    path = config_path()
    if not path.exists():
        cfg = _default_config()
        save_config(cfg)
        Path(cfg.receive_dir).mkdir(parents=True, exist_ok=True)
        return cfg
    data = json.loads(path.read_text(encoding="utf-8"))
    cfg = AppConfig.from_json(data)
    Path(cfg.receive_dir).mkdir(parents=True, exist_ok=True)
    return cfg


def save_config(config: AppConfig) -> None:
    path = config_path()
    APP_DIR.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(config.to_json(), indent=2), encoding="utf-8")
