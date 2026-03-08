from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from app.core.constants import PROTOCOL_VERSION


MESSAGE_TYPES = {
    "hello",
    "pair_request",
    "pair_confirm",
    "auth",
    "key_exchange",
    "transfer_offer",
    "transfer_accept",
    "transfer_decline",
    "manifest",
    "file_chunk",
    "chunk_ack",
    "transfer_complete",
    "transfer_error",
    "cancel",
    "ping",
    "pong",
    "resume_request",
    "resume_response",
}


@dataclass(slots=True)
class ProtocolMessage:
    msg_type: str
    payload: dict[str, Any]

    def to_bytes(self) -> bytes:
        if self.msg_type not in MESSAGE_TYPES:
            raise ValueError(f"Unsupported message type: {self.msg_type}")
        body = {"version": PROTOCOL_VERSION, "type": self.msg_type, "payload": self.payload}
        return json.dumps(body, separators=(",", ":")).encode("utf-8")

    @classmethod
    def from_bytes(cls, raw: bytes) -> "ProtocolMessage":
        data = json.loads(raw.decode("utf-8"))
        msg_type = data["type"]
        if msg_type not in MESSAGE_TYPES:
            raise ValueError(f"Unsupported message type: {msg_type}")
        return cls(msg_type=msg_type, payload=data.get("payload", {}))
