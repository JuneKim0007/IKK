> ## SUPERSEDED — Python, being ported to Java
>
> The backend is Java. This FastAPI implementation exists for exactly one
> reason: it is the working reference the Java port is translating from.
>
> **It is deleted the moment the Java service passes `docs/fixtures/`** — see
> `docs/roadmap-backend.md` B0.5. Not archived, not renamed to `legacy/`.
> Deleted. Do not add features here.

# backend

FastAPI. Contract persistence, sync, and code generation.

## Layout

Feature-first. Each slice under `app/features/` owns its router, schemas,
service, models and tests, and does not reach into another slice.

```
app/
├── main.py            app factory, router mounting, error handlers
├── settings.py
├── shared/            cross-cutting only — db, errors, deps, ids
├── contract/          THE domain model. Shared kernel, not a feature
│   ├── models.py      pydantic mirror of docs/json_contract.md
│   ├── validation.py  rules V1–V12
│   └── canonical.py   canonical serialisation + checksum
└── features/
    ├── projects/
    ├── sync/
    ├── checkpoints/
    └── assets/
```

`contract/` is a shared kernel rather than a feature because sync, checkpoints
and codegen all depend on it. Making it a feature would invert the dependency.

## Codegen is NOT here

Emission lives in the standalone `packages/codegen/` package (Node, no dependencies).
This service persists contracts, resolves sync, and cuts checkpoints; when
`POST /v1/projects/{id}/generate` is wired it will shell out to that package
rather than re-implement the emitters.

Two emitters would have to stay byte-identical forever. One does not.

## Run

```sh
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app --reload
```

Docs at `http://127.0.0.1:8000/docs`.

## Test

```sh
pytest
```

`tests/test_fixtures.py` runs `docs/fixtures/` — the same corpus Kotlin and
TypeScript run. If it fails, the three implementations have drifted.

## Contract changes

`docs/json_contract.md` is normative. Change it in the same commit as the code,
and add a fixture.
