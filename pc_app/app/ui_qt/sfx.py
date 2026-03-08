from __future__ import annotations

import sys
from pathlib import Path


class SfxPlayer:
    """Small event-to-WAV player for desktop notifications."""

    def __init__(self) -> None:
        self._winsound = None
        try:
            import winsound as _winsound  # type: ignore

            self._winsound = _winsound
        except Exception:
            self._winsound = None

        root = self._runtime_root()
        sfx_root = root / "assets" / "sfx"
        self._connect_or_pair = sfx_root / "connect_or_paired_success.wav"
        self._file_send_success = sfx_root / "file_send_success.wav"
        self._file_received_success = sfx_root / "file_recevied_success.wav"

    @staticmethod
    def _runtime_root() -> Path:
        if getattr(sys, "frozen", False):
            return Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parents[2]))
        return Path(__file__).resolve().parents[2]

    def _play(self, path: Path) -> None:
        if self._winsound is None or not path.exists():
            return
        try:
            self._winsound.PlaySound(
                str(path),
                self._winsound.SND_FILENAME
                | self._winsound.SND_ASYNC
                | self._winsound.SND_NODEFAULT,
            )
        except Exception:
            # Sound effects should never interrupt transfer flow.
            return

    def play_for_event(self, event_text: str) -> None:
        lower = event_text.lower()

        if "incoming transfer complete" in lower:
            self._play(self._file_received_success)
            return
        if "transfer complete" in lower or "transfer finished" in lower:
            self._play(self._file_send_success)
            return
        if "pair" in lower and (
            "confirm" in lower or "trusted" in lower or "success" in lower
        ):
            self._play(self._connect_or_pair)
            return
        if "connected target" in lower:
            self._play(self._connect_or_pair)
