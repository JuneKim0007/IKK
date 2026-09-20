"""docs/json_contract.md §12. Reject, do not repair."""

from __future__ import annotations

from app.contract.models import Contract, is_valid_key


def validate_contract(contract: Contract) -> list[dict]:
    out: list[dict] = []

    def bad(rule: str, where: str, message: str) -> None:
        out.append({"rule": rule, "where": where, "message": message})

    seen_ids: set[str] = set()
    seen_z: set[int] = set()
    seen_names: set[str] = set()

    for key, node in contract.components.items():
        if not is_valid_key(key):
            bad("V2", key, "key does not match {type}_{slug(name)}")
        if key != node.key:
            bad("V2", key, f"key disagrees with the node's own key {node.key!r}")
        if node.id in seen_ids:
            bad("V3", key, f"duplicate id {node.id!r}")
        seen_ids.add(node.id)
        if not 0.0 <= node.opacity <= 1.0:
            bad("V4", key, f"opacity {node.opacity} out of 0.0..1.0")
        if node.rect.w <= 0 or node.rect.h <= 0:
            bad("V5", key, f"rect must have positive size, got {node.rect.w}x{node.rect.h}")
        if isinstance(node.radius, (int, float)) and node.radius < 0:
            bad("V6", key, f"radius {node.radius} is negative")
        if node.type == "text" and node.text is None:
            bad("V7", key, "a text node must carry text")
        if node.type == "ellipse" and node.radius != "50%":
            bad("V10", key, 'an ellipse must have radius "50%"')
        if node.z in seen_z:
            bad("V12", key, f"duplicate z value {node.z}")
        seen_z.add(node.z)
        if not node.name.strip():
            bad("V13", key, "name is blank")
        folded = node.name.strip().lower()
        if folded in seen_names:
            bad("V14", key, f"duplicate name {node.name!r} in this scope")
        seen_names.add(folded)

    return out
