import logging
from logging.handlers import RotatingFileHandler

from app.core.constants import APP_DIR, DEFAULT_LOG_FILE


def configure_logging() -> None:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    formatter = logging.Formatter("%(asctime)s | %(levelname)s | %(name)s | %(message)s")

    root = logging.getLogger()
    root.setLevel(logging.INFO)
    if root.handlers:
        return

    console = logging.StreamHandler()
    console.setFormatter(formatter)

    file_handler = RotatingFileHandler(
        DEFAULT_LOG_FILE,
        maxBytes=5 * 1024 * 1024,
        backupCount=3,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)

    root.addHandler(console)
    root.addHandler(file_handler)
