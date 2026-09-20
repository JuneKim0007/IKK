# fixtures

One corpus, three implementations. Kotlin (`:core`), TypeScript
(`frontend/web`) and Python (`backend`) all run these files and must agree.

| Directory | Assertion |
|---|---|
| `valid/` | parses, validates clean, and round-trips byte-identically |
| `invalid/` | **rejected**, with the named rule in the violation list |

Each `invalid/` file's name is the rule it violates. A parser that accepts one
of them has a bug, whatever its own tests say.

Adding a fixture is a contract change: update `docs/json_contract.md` in the
same commit.
