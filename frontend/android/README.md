# frontend/android

The Android editor surface. Worked on the `frontend/android` branch.

Implements roadmap Phase 3 (items 46–65). Touch-first: contextual app bar,
bottom-sheet inspector with peek/half detents, nudge pad, 48dp touch targets.

- Layouts and interaction spec: `prototype/editor-android/index.html`
- Class tree: `docs/component-model.md`
- Wire format: `docs/json_contract.md` — **normative, read it first**

Consumes `:core` for the contract types. Do not redefine them here; if the
model is wrong, change `:core` and `docs/json_contract.md` together.

Code lands in a `:feature-editor` Gradle module, not in this directory —
this directory holds the surface's docs and assets. The split exists because
Gradle module paths are fixed by `settings.gradle.kts`.
