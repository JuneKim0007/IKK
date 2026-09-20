# Roadmap — backend

Owner: the JVM backend and code generation. Frontend plan lives in
[roadmap-frontend.md](roadmap-frontend.md).

**Completed items are deleted from this file, not ticked.**
**Superseded code is removed, not parked in a `legacy/` folder.**

---

## B0 · Language

The backend is **Java**. `apps/backend` currently holds a Python/FastAPI
implementation that exists only as the reference the Java port is translating.

- [ ] **B0.1** Java service skeleton with the routes in `docs/api.md`
- [ ] **B0.2** Contract model mirroring `docs/json_contract.md`, unknown fields
      rejected (V11)
- [ ] **B0.3** Validation rules V1–V15
- [ ] **B0.4** Runs `docs/fixtures/` — the same corpus Kotlin and the Node
      generator run. This is the drift detector; it has already caught eight
      divergences
- [ ] **B0.5** **Delete `apps/backend` (Python) once B0.4 passes.** Not archived,
      not renamed to `legacy/` — deleted

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

## Out of scope

Named so they can be declined in one line rather than re-argued:
`GroupNode` and nesting · data binding and behaviour · the AI implementation
stage · real-time co-editing · design-system tokens as a user feature ·
animation · iOS.
