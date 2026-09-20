# fixtures

One corpus for every contract boundary. Kotlin `:packages:design-contract` is
reused directly by Android and the Kotlin/Spring Boot 4.1.1 backend. The backend
validates through that shared model before invoking the standalone Node.js
emitter. The emitter assumes a prevalidated contract and runs every valid
fixture deterministically when emitting CSS, HTML, and Compose Kotlin.

| Directory | Assertion |
|---|---|
| `valid/` | Kotlin parses, validates, and round-trips; Node codegen emits every target deterministically |
| `invalid/` | Kotlin design-contract and the backend boundary **reject**, with the named rule in the violation list |

Each `invalid/` file's name is the rule it violates. A parser that accepts one
of them has a bug, whatever its own tests say.

Adding a fixture is a contract change: update `docs/json_contract.md` in the
same commit.
