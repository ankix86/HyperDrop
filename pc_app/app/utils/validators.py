from __future__ import annotations

from pathlib import PurePosixPath

INVALID_SEGMENTS = {"", ".", ".."}


def is_safe_relative_path(value: str) -> bool:
    p = PurePosixPath(value)
    if p.is_absolute():
        return False
    return all(part not in INVALID_SEGMENTS for part in p.parts)


def validate_port(value: int) -> bool:
    return 1024 <= value <= 65535
