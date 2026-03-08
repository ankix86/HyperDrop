from __future__ import annotations

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF


def derive_session_key(shared_secret: bytes, transcript: bytes) -> bytes:
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=b"lan-transfer-session-key" + transcript,
    )
    return hkdf.derive(shared_secret)
