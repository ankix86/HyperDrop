from __future__ import annotations

from pathlib import Path, PurePosixPath

from app.utils.validators import is_safe_relative_path


def safe_join(base_dir: Path, relative_path: str) -> Path:
    if not is_safe_relative_path(relative_path):
        raise ValueError(f"Unsafe relative path: {relative_path}")
    normalized = PurePosixPath(relative_path)
    target = base_dir.joinpath(*normalized.parts)
    resolved = target.resolve()
    base_resolved = base_dir.resolve()
    if base_resolved not in resolved.parents and resolved != base_resolved:
        raise ValueError("Path traversal attempt detected")
    return resolved
