# docs

| Document | What it is | Read when |
|---|---|---|
| [json_contract.md](json_contract.md) | **Normative** wire format | Before writing any code that touches the contract |
| [component-model.md](component-model.md) | Class tree and shared behaviours | Before implementing a surface |
| [roadmap.md](roadmap.md) | 100-item build plan, both surfaces | Planning, or picking up the next task |

Interactive specs live in `prototype/`:

| Prototype | Shows |
|---|---|
| `prototype/editor-web/` | The web editor layout and interactions |
| `prototype/editor-android/` | The Android editor, five layout states |
| `prototype/pipeline/` | How the contract, codegen and agent stages fit together |
| `prototype/agent-loop/` | The per-step gate runner (phase 2 of the product) |

`prototype/shared/editor-core.js` is imported by both editor prototypes. That
is deliberate: if the two prototypes ever disagree about geometry, it is a
renderer bug, not a difference of opinion.

## Order of authority

When two documents disagree:

1. `json_contract.md`
2. `component-model.md`
3. `roadmap.md`
4. the prototypes

Prototypes are sketches. They are allowed to be out of date; the contract
is not.
