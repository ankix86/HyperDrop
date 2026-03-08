from __future__ import annotations

import mimetypes
import uuid
from pathlib import Path

from app.core.models import ManifestEntry, TransferManifest
from app.utils.hashing import sha256_file


def _entry_for_file(base_dir: Path, file_path: Path) -> ManifestEntry:
    rel = file_path.relative_to(base_dir).as_posix()
    mime, _ = mimetypes.guess_type(file_path.name)
    st = file_path.stat()
    return ManifestEntry(
        relative_path=rel,
        file_name=file_path.name,
        mime_type=mime or "application/octet-stream",
        size=st.st_size,
        modified_time=st.st_mtime,
        checksum=sha256_file(file_path),
        is_directory=False,
    )


def build_manifest(source_paths: list[Path], sender_device_id: str, receiver_device_id: str) -> tuple[TransferManifest, dict[str, Path]]:
    entries: list[ManifestEntry] = []
    source_map: dict[str, Path] = {}

    for src in source_paths:
        src = src.resolve()
        if src.is_file():
            st = src.stat()
            entry = ManifestEntry(
                relative_path=src.name,
                file_name=src.name,
                mime_type=mimetypes.guess_type(src.name)[0] or "application/octet-stream",
                size=st.st_size,
                modified_time=st.st_mtime,
                checksum=sha256_file(src),
                is_directory=False,
            )
            entries.append(entry)
            source_map[entry.relative_path] = src
            continue

        if src.is_dir():
            base = src.parent
            entries.append(
                ManifestEntry(
                    relative_path=src.name,
                    file_name=src.name,
                    mime_type="inode/directory",
                    size=0,
                    modified_time=src.stat().st_mtime,
                    checksum="",
                    is_directory=True,
                )
            )
            for child in src.rglob("*"):
                rel = child.relative_to(base).as_posix()
                if child.is_dir():
                    entries.append(
                        ManifestEntry(
                            relative_path=rel,
                            file_name=child.name,
                            mime_type="inode/directory",
                            size=0,
                            modified_time=child.stat().st_mtime,
                            checksum="",
                            is_directory=True,
                        )
                    )
                else:
                    entry = _entry_for_file(base, child)
                    entries.append(entry)
                    source_map[entry.relative_path] = child
            continue

        raise FileNotFoundError(f"Path not found: {src}")

    manifest = TransferManifest(
        transfer_id=str(uuid.uuid4()),
        sender_device_id=sender_device_id,
        receiver_device_id=receiver_device_id,
        entries=entries,
    )
    return manifest, source_map
