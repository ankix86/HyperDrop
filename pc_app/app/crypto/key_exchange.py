from __future__ import annotations

from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat


class KeyPair:
    def __init__(self) -> None:
        self.private_key = X25519PrivateKey.generate()
        self.public_key = self.private_key.public_key()

    def public_bytes(self) -> bytes:
        return self.public_key.public_bytes(Encoding.Raw, PublicFormat.Raw)

    def shared_secret(self, peer_public_bytes: bytes) -> bytes:
        peer = X25519PublicKey.from_public_bytes(peer_public_bytes)
        return self.private_key.exchange(peer)
