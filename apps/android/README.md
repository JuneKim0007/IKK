# Android application

Still a Hello World scaffold at the UI layer — `MainActivity` +
`GreetingScreen`, nothing contract-aware yet. Split into two Gradle modules:

- `app/` — launcher activity, Compose UI, and dependency wiring;
- `data/` — Android repositories and data sources, **and now the contract
  model**, via `api(project(":packages:design-contract"))`.

The production editor will be added as a sibling `feature-editor/` module. Its
rules are [`../../docs/architecture/frontend-android.md`](../../docs/architecture/frontend-android.md)
(canvas frame, touch-to-contract normalization, file boundary) and
[`../../docs/component-model.md`](../../docs/component-model.md) §4 (the
`EditorControl` tree — `MoveTool`, `ShapeTool`, etc). The normative wire
format is [`../../docs/json_contract.md`](../../docs/json_contract.md). The
`prototypes/` directory these used to point to was dropped; those three files
are the current reference.

Contract types belong to `packages/design-contract`; do not redefine them in
an Android module. If the model changes, update the normative spec, Kotlin
model, and contract tests together.

`ContractWiringTest` in `data/src/test` proves the dependency is live, not
just declared: it decodes and validates every fixture in
`docs/fixtures/valid/` — the same corpus Kotlin's own `CorpusTest`,
`packages/codegen`'s test suite, and the backend's `test_fixtures.py` all
run. If it fails, this module has drifted from the shared contract.
