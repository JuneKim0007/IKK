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
    ├── codegen/       Jinja2 templates. Codegen lives here and nowhere else
    └── assets/
```

`contract/` is a shared kernel rather than a feature because sync, checkpoints
and codegen all depend on it. Making it a feature would invert the dependency.

## Why codegen is here

The backend cannot call Kotlin, so the emitters are Jinja2 templates rather
than methods on `DesignNode`. *Rendering* a contract at runtime (both clients)
and *generating* source from it (backend only) are different jobs — the Android
app never needs to emit Kotlin source.

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
