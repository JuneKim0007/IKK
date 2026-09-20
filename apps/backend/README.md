# backend

Kotlin/JVM with Spring Boot 4.1.1. Contract persistence, sync, and code
generation orchestration.

## Layout

Feature-first. Each slice under `com.ikk.backend.features` owns its controller,
request/response DTOs, service, persistence types, and tests, and does not reach
into another slice.

```text
apps/backend/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/com/ikk/backend/
    │   │   ├── IkkBackendApplication.kt
    │   │   ├── contract/     Jackson ↔ shared contract adapter, canonical JSON
    │   │   ├── shared/       cross-cutting only — errors, ids, security, JDBC types
    │   │   └── features/
    │   │       ├── projects/
    │   │       ├── sync/
    │   │       ├── checkpoints/
    │   │       ├── assets/
    │   │       └── codegen/
    │   └── resources/
    │       └── application.yml
    └── test/kotlin/com/ikk/backend/
```

The backend depends directly on the Kotlin/JVM `:packages:design-contract`
module. All request parsing and contract validation go through the shared
`ContractJson` and `ContractValidator`; the backend must not introduce a second
Java or Kotlin contract model.
Backend boundary tests run the shared `docs/fixtures/` corpus so an integration
that bypasses the strict core path fails CI.

## Codegen is not here

Emission lives in the standalone `packages/codegen/` package (Node, no
dependencies).
This service persists contracts, resolves sync, cuts checkpoints, and invokes
that package from `POST /v1/projects/{id}/generate` rather than re-implementing
the emitters.

Two emitters would have to stay byte-identical forever. One does not.

## Run

JDK 21 and Node.js 18+ are required. From the repository root:

```sh
./gradlew :apps:backend:bootRun
```

The service listens on `http://127.0.0.1:8000` by default.
`IKK_BIND_ADDRESS` changes the bind address (the default is `127.0.0.1`) and
`PORT` changes the port. `IKK_ALLOWED_ORIGINS` is a comma-separated CORS allow
list; it defaults to
`http://127.0.0.1:5173,http://localhost:5173`. The HTTP contract is documented
in `docs/api.md`.

By default, data is stored in `apps/backend/data/ikk.mv.db` through H2 in
PostgreSQL compatibility mode. Flyway applies
`apps/backend/src/main/resources/db/migration/` at startup.
For PostgreSQL, set `IKK_DATABASE_URL`, `IKK_DATABASE_USER`, and
`IKK_DATABASE_PASSWORD`. Set `IKK_API_TOKEN` to require
`Authorization: Bearer <token>` on every route except `/healthz`; leaving it
empty keeps local development unauthenticated. `IKK_CODEGEN_SCRIPT` and
`IKK_NODE_EXECUTABLE` can override the emitter and Node executable paths.

## Test

```sh
./gradlew :apps:backend:test
```

Backend conformance tests run the fixtures in `docs/fixtures/` through the same
Kotlin core used by Android. The standalone Node.js codegen also runs every valid
fixture through its independent normalization and emission path. A failure means
an HTTP, persistence, or emitter boundary has drifted from the normative contract.

## Contract changes

`docs/json_contract.md` is normative. Change it in the same commit as the code,
and add a fixture.
