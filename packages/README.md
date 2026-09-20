# Shared packages

`packages/` contains reusable code that is independent of a deployable app.

## `design-contract`

The Kotlin implementation of the normative design contract:

- serializable contract and node types;
- geometry, colour, stroke, radius, text, and asset primitives;
- schema-version checks and validation rules;
- round-trip and mutation tests.

It must not import Android UI, browser, persistence, networking, or generated
application code. The normative format lives in `docs/json_contract.md`.
