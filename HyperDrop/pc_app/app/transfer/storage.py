from __future__ import annotations

import os
from pathlib import Path

from app.utils.paths import safe_join


class SafeStorage:
    def __init__(self, base_dir: Path) -> None:
        self.base_dir = base_dir.resolve()
        self.base_dir.mkdir(parents=True, exist_ok=True)

    def ensure_directory(self, relative_path: str) -> Path:
        target = safe_join(self.base_dir, relative_path)
        target.mkdir(parents=True, exist_ok=True)
        return target

    def temp_path(self, relative_file_path: str) -> Path:
        final_path = safe_join(self.base_dir, relative_file_path)
        final_path.parent.mkdir(parents=True, exist_ok=True)
        return final_path.with_suffix(final_path.suffix + ".part")

    def resolve_final_path(self, relative_file_path: str, overwrite: bool = False) -> Path:
        target = safe_join(self.base_dir, relative_file_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        if overwrite or not target.exists():
            return target

        stem = target.stem
        suffix = target.suffix
        for i in range(1, 10000):
            candidate = target.with_name(f"{stem} ({i}){suffix}")
            if not candidate.exists():
                return candidate
        raise RuntimeError("Could not resolve conflict name")

    def commit_temp_file(self, temp: Path, final: Path) -> None:
        os.replace(temp, final)
