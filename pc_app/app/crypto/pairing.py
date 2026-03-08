from __future__ import annotations

import secrets

from app.utils.hashing import sha256_text


def generate_pairing_code(length: int = 6) -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(length))


def pairing_code_hash(pairing_code: str) -> str:
    return sha256_text(pairing_code)
