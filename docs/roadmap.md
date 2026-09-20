# Roadmap

Split by owner so two people are not editing one file.

| File | Owner | Covers |
|---|---|---|
| [roadmap-frontend.md](roadmap-frontend.md) | web + Android | editors, client sync, the parity gate |
| [roadmap-backend.md](roadmap-backend.md) | Kotlin/JVM | contract service and generation |

## Two rules for both files

**Completed items are deleted, not ticked.** A roadmap lists work left to do.
Work already done is what `git log` is for, and a file of ticked boxes buries
the twelve things that still matter under sixty that no longer do.

**Superseded code is removed, not parked.** No `legacy/`, no `_old` suffix, no
commented-out block "for reference". Two answers to the same question is how a
repo stops being trustworthy, and git already keeps the history.

## Status

| | |
|---|---|
| Contract model | Kotlin/JVM, reused directly by Android and backend |
| Codegen | `packages/codegen`, emits `.kt`, `.css`, and `.html` |
| Fixture corpus | 3 valid, 19 invalid; Kotlin/backend boundaries enforce it |
| Backend | Kotlin/Spring Boot; Python reference removed |
| Web editor | in progress — `apps/web` |
| Android editor | not started |
