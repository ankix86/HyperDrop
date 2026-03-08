from __future__ import annotations

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class ChunkEncryptor:
    def __init__(self, session_key: bytes, transfer_salt: bytes) -> None:
        if len(transfer_salt) != 8:
            raise ValueError("transfer_salt must be exactly 8 bytes")
        self._aesgcm = AESGCM(session_key)
        self._salt = transfer_salt

    def nonce_for_chunk(self, chunk_index: int) -> bytes:
        if chunk_index < 0:
            raise ValueError("chunk_index must be non-negative")
        return self._salt + chunk_index.to_bytes(4, "big")

    def encrypt_chunk(self, chunk_index: int, plaintext: bytes, aad: bytes) -> bytes:
        return self._aesgcm.encrypt(self.nonce_for_chunk(chunk_index), plaintext, aad)

    def decrypt_chunk(self, chunk_index: int, ciphertext: bytes, aad: bytes) -> bytes:
        return self._aesgcm.decrypt(self.nonce_for_chunk(chunk_index), ciphertext, aad)
