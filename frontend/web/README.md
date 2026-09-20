# frontend/web

The web editor surface. Worked on the `frontend/web` branch.

Implements roadmap Phase 2 (items 26–45). Desktop-first: toolbar with a
grouped shape tool, left layers panel, right inspector, canvas in the middle.

- Layouts and interaction spec: `prototype/editor-web/index.html`
- Device-frame harness: `frontend/web/index.html`
- Class tree: `docs/component-model.md`
- Wire format: `docs/json_contract.md` — **normative, read it first**

The contract types are defined in Kotlin in `:core`. This surface mirrors them
in TypeScript. The mirror must stay byte-compatible: §13 of the contract spec
requires a Kotlin and a TypeScript serialiser to produce identical output for
the same object, and that is a cross-language test, not a within-language one.
