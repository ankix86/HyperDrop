from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class ResumeState:
    transfer_id: str
    relative_path: str
    next_offset: int


def build_resume_request(transfer_id: str) -> dict:
    return {"transfer_id": transfer_id}


def parse_resume_response(payload: dict) -> list[ResumeState]:
    states: list[ResumeState] = []
    for item in payload.get("files", []):
        states.append(
            ResumeState(
                transfer_id=item["transfer_id"],
                relative_path=item["relative_path"],
                next_offset=int(item.get("next_offset", 0)),
            )
        )
    return states
