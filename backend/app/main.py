"""IKK backend.

Scope note: no database. Contracts live in memory for the life of the process.
That is deliberate for now — see docs/roadmap.md Phase 5. Persistence is item
77 and nothing here depends on its absence.

Codegen is NOT implemented here. It shells out to the standalone `codegen/`
package, which is the single emitter. Two emitters would have to stay
byte-identical forever.
"""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from app.contract.models import Contract, UnsupportedSchemaVersion, parse_contract
from app.contract.validation import validate_contract
from app.settings import settings
from app.shared.errors import ApiError, ContractInvalid, NotFound, api_error_handler
from app.shared.ids import request_id

REPO_ROOT = Path(__file__).resolve().parents[2]
CODEGEN = REPO_ROOT / "codegen" / "generate.mjs"

app = FastAPI(title="IKK", version="0.1.0", docs_url="/docs")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # single-machine scope; tighten before anything ships
    allow_methods=["*"],
    allow_headers=["*"],
)

app.add_exception_handler(ApiError, api_error_handler)

# In-memory store. Swap for the database in roadmap item 77.
_contracts: dict[str, Contract] = {}


@app.middleware("http")
async def tag_request(request: Request, call_next):
    request.state.request_id = request_id()
    response = await call_next(request)
    response.headers["x-request-id"] = request.state.request_id
    return response


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok", "codegen": "present" if CODEGEN.exists() else "missing"}


@app.post(f"{settings.api_prefix}/contracts/validate")
def validate(payload: dict) -> dict:
    """Run V1–V15 without storing anything."""
    try:
        contract = parse_contract(payload)
    except UnsupportedSchemaVersion as exc:
        raise ContractInvalid(str(exc)) from exc
    except Exception as exc:  # pydantic rejection is a validation failure
        raise ContractInvalid(str(exc)) from exc

    violations = validate_contract(contract)
    if violations:
        return {"valid": False, "violations": violations}
    return {"valid": True, "screen": contract.screen, "components": len(contract.components)}


@app.put(f"{settings.api_prefix}/projects/{{project_id}}/contract")
def put_contract(project_id: str, payload: dict) -> dict:
    try:
        contract = parse_contract(payload)
    except Exception as exc:
        raise ContractInvalid(str(exc)) from exc

    violations = validate_contract(contract)
    if violations:
        raise ContractInvalid("contract failed validation", violations)

    _contracts[project_id] = contract
    return {"projectId": project_id, "checkpoint": contract.checkpoint,
            "components": len(contract.components)}


@app.get(f"{settings.api_prefix}/projects/{{project_id}}/contract")
def get_contract(project_id: str) -> dict:
    contract = _contracts.get(project_id)
    if contract is None:
        raise NotFound(f"no contract stored for project {project_id!r}")
    return json.loads(contract.model_dump_json())


@app.post(f"{settings.api_prefix}/projects/{{project_id}}/generate")
def generate(project_id: str) -> dict:
    """Cut artifacts by shelling out to codegen/. See docs/api.md."""
    contract = _contracts.get(project_id)
    if contract is None:
        raise NotFound(f"no contract stored for project {project_id!r}")
    if not CODEGEN.exists():
        raise ApiError("codegen/generate.mjs not found", status=500, code="codegen_missing")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        contract_file = tmp_path / "contract.json"
        contract_file.write_text(contract.model_dump_json(indent=2))
        css = tmp_path / "home.generated.css"
        kt = tmp_path / "HomeLayout.generated.kt"

        result = subprocess.run(
            ["node", str(CODEGEN), "--input", str(contract_file),
             "--css", str(css), "--kotlin", str(kt)],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode != 0:
            raise ApiError(result.stderr.strip() or "codegen failed",
                           status=422, code="codegen_failed")

        return {
            "checkpoint": contract.checkpoint,
            "artifacts": [
                {"name": kt.name, "target": "kotlin", "bytes": kt.stat().st_size,
                 "content": kt.read_text()},
                {"name": css.name, "target": "css", "bytes": css.stat().st_size,
                 "content": css.read_text()},
            ],
        }
