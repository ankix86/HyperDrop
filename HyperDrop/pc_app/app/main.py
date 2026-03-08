from __future__ import annotations

import os
import sys
from pathlib import Path


if __package__ is None or __package__ == "":
    project_root = Path(__file__).resolve().parents[1]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

from PySide6.QtCore import QUrl
from PySide6.QtGui import QIcon
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtWidgets import QApplication

from app.core.logging_setup import configure_logging
from app.ui_qt.backend import HyperDropBackend


def _runtime_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parents[1]))
    return Path(__file__).resolve().parents[1]


def main() -> None:
    configure_logging()

    # Use a style that allows custom Control visuals in QML (e.g., themed scrollbars).
    os.environ.setdefault("QT_QUICK_CONTROLS_STYLE", "Basic")

    app = QApplication(sys.argv)

    root = _runtime_root()
    icon_path = root / "assets" / "icons" / "hyperdrop-icon-256.png"
    if icon_path.exists():
        app.setWindowIcon(QIcon(str(icon_path)))

    backend = HyperDropBackend()
    app.aboutToQuit.connect(backend.shutdown)

    engine = QQmlApplicationEngine()
    engine.rootContext().setContextProperty("backend", backend)

    qml_path = root / "app" / "ui_qt" / "qml" / "Main.qml"
    engine.load(QUrl.fromLocalFile(str(qml_path)))
    if not engine.rootObjects():
        raise RuntimeError(f"Failed to load QML UI: {qml_path}")

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
