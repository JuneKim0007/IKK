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

The client owns the dirty-queue gate before `Generate`; the backend cannot see
unsent browser edits. The backend still makes generation transactional, so a
failed emitter cannot leave a checkpoint without its artifacts.

## Out of scope

`GroupNode` and nesting · data binding and behaviour · the AI implementation
stage · real-time co-editing · design-system tokens as a user feature ·
animation · iOS.
