"""The shared corpus — the same files Kotlin and the Node codegen run."""

import json
from pathlib import Path

import pytest

from app.contract.models import parse_contract
from app.contract.validation import validate_contract

ROOT = Path(__file__).resolve().parents[3]
VALID = sorted((ROOT / "docs" / "fixtures" / "valid").glob("*.json"))
INVALID = sorted((ROOT / "docs" / "fixtures" / "invalid").glob("*.json"))


@pytest.mark.parametrize("path", VALID, ids=lambda p: p.name)
def test_valid_fixture_parses_and_validates(path: Path) -> None:
    contract = parse_contract(json.loads(path.read_text()))
    assert validate_contract(contract) == []


@pytest.mark.parametrize("path", INVALID, ids=lambda p: p.name)
def test_invalid_fixture_is_rejected(path: Path) -> None:
    raw = json.loads(path.read_text())
    try:
        contract = parse_contract(raw)
    except Exception:
        return  # rejected at parse time
    assert validate_contract(contract), f"{path.name} should have been rejected"


def test_codegen_example_is_a_valid_contract() -> None:
    path = ROOT / "packages" / "codegen" / "examples" / "home.json"
    contract = parse_contract(json.loads(path.read_text()))
    assert validate_contract(contract) == []
