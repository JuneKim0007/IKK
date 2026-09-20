# Android application

The native Android surface is split into two existing Gradle modules:

- `app/` — launcher activity, Compose UI, and dependency wiring;
- `data/` — Android repositories and data sources.

The production editor will be added as a sibling `feature-editor/` module. Its
interaction specification is in
[`../../prototypes/editor-android/index.html`](../../prototypes/editor-android/index.html),
and the normative wire format is
[`../../docs/json_contract.md`](../../docs/json_contract.md).

Contract types belong to `packages/design-contract`; do not redefine them in
an Android module. If the model changes, update the normative spec, Kotlin
model, and contract tests together.
