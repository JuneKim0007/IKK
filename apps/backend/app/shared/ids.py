import secrets


def project_id() -> str:
    return f"p_{secrets.token_hex(6)}"


def asset_ref() -> str:
    return f"asset_{secrets.token_hex(8)}"


def request_id() -> str:
    return f"req_{secrets.token_hex(4)}"


def checkpoint_label(n: int) -> str:
    """docs/json_contract.md §3 — cp_ plus at least three zero-padded digits."""
    return f"cp_{n:03d}"
