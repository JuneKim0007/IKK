# Roadmap — backend

Owner: Kotlin/JVM backend and code-generation orchestration. Frontend work lives
in [roadmap-frontend.md](roadmap-frontend.md).

Completed work is removed from this roadmap, not kept as checked boxes. The
current baseline is Kotlin/Spring Boot, the shared
`:packages:design-contract` module, JDBC/Flyway persistence for H2 and
PostgreSQL, project/contract/node/checkpoint/asset routes, optional bearer auth,
request IDs, and atomic CSS/HTML/Compose generation through
`packages/codegen`. The previous Python reference implementation has been
deleted.

## Remaining backend work

- [ ] Run the integration suite against PostgreSQL in CI in addition to the
      fast H2 compatibility-mode tests.
- [ ] Replace the single shared bearer token with per-user authorization before
      any hosted or multi-tenant deployment.
- [ ] Define retention for immutable checkpoints and generated artifacts once
      projects can contain more than one screen.

The backend is **Kotlin**. `apps/backend` currently holds a Python/FastAPI
implementation that exists only as the reference the Kotlin port translates.

### This removes an implementation, it does not add one

Kotlin on the backend can depend on `:packages:design-contract` **directly** —
the same module Android already uses. So the contract model drops from three
implementations to two:

| | Before (Python backend) | Now (Kotlin backend) |
|---|---|---|
| Kotlin model | Android | Android **and** backend |
| Python model | backend | — deleted |
| JS model | web editor, codegen | web editor, codegen |

Two implementations that must agree instead of three, and the backend's half is
already written and tested. `docs/fixtures/` stays: it is what keeps the Kotlin
and JS halves honest, and it has caught eight divergences so far.

- [ ] **B0.1** Kotlin service skeleton (Ktor or Spring Boot — pick one and say
      why in the module README) serving the routes in `docs/api.md`
- [ ] **B0.2** Depend on `:packages:design-contract`. Do **not** redeclare the
      model; if it is wrong, fix it there and in `docs/json_contract.md`
- [ ] **B0.3** Reuse `ContractValidator` for V1–V15 rather than porting it
- [ ] **B0.4** Run `docs/fixtures/` in the service's own test suite
- [ ] **B0.5** `POST /v1/projects/{id}/generate` shells out to
      `packages/codegen`. The emitter stays one implementation
- [ ] **B0.6** **Delete `apps/backend` (Python) once B0.4 passes.** Not
      archived, not renamed to `legacy/` — deleted

---

## B1 · Contract service

No database while the scope is one machine. Persistence is B1.2, and nothing
above it depends on its absence.

- [ ] **B1.1** `GET /healthz`, `POST /v1/contracts/validate`
- [ ] **B1.2** Persistence — projects, screens, nodes
- [ ] **B1.3** `GET /v1/projects/{id}/contract`
- [ ] **B1.4** `PUT /v1/projects/{id}/nodes` — batch upsert, per-node LWW,
      **409 on a version not greater than stored**
- [ ] **B1.5** Server-computed checksums; clients never compute them
- [ ] **B1.6** `features/checkpoints` — immutable snapshots, `cp_00N`
- [ ] **B1.7** Assets: upload and serve for `ImageNode`
- [ ] **B1.8** AuthN/AuthZ, structured logging with request ids
- [ ] **B1.9** Integration tests against a real database, not mocks

---

## B2 · Generate

Codegen lives in `packages/codegen` and nowhere else. The backend shells out to
it. Two emitters would have to stay byte-identical forever; one does not.

- [ ] **B2.1** HTML emitter — structure only, no inline geometry
- [ ] **B2.2** Golden test: every fixture emits and re-emits identically (`--check`)
- [ ] **B2.3** `POST /v1/projects/{id}/generate` — cut a checkpoint, run codegen,
      return the manifest
- [ ] **B2.4** Refuse to generate while the sync queue is dirty. Generating from
      a stale contract produces output that does not match the screen, which
      users report as a broken generator
- [ ] **B2.5** Generated/authored boundary enforced: codegen writes only
      `*.generated.*`, never an authored file

---
The client owns the dirty-queue gate before `Generate`; the backend cannot see
unsent browser edits. The backend still makes generation transactional, so a
failed emitter cannot leave a checkpoint without its artifacts.

## Out of scope

`GroupNode` and nesting · data binding and behaviour · the AI implementation
stage · real-time co-editing · design-system tokens as a user feature ·
animation · iOS.
