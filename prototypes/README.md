# Prototypes

These are standalone HTML interaction specifications. They are not production
Web code and are not included in the Gradle build.

| Path | Purpose |
|---|---|
| `editor-web/` | Desktop editor layout and interaction model |
| `editor-android/` | Touch-first Android editor states |
| `shared/` | Prototype-only model and rendering helpers used by both editors |
| `pipeline/` | Contract → codegen → agent → validation boundary |
| `agent-loop/` | Per-step agent gates and bounded retries |
| `legacy/contract-designer/` | Earlier semantic component experiment |
| `legacy/deck-composer/` | Earlier deck-composer experiment |

When a prototype disagrees with `docs/json_contract.md`, the contract wins.
