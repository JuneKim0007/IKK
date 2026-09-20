# Web application

The browser-based editor and renderer belong here. At present this directory
contains only a standalone Android-sized device-frame harness in `index.html`;
there is no production TypeScript build yet.

- Editor interaction spec:
  [`../../prototypes/editor-web/index.html`](../../prototypes/editor-web/index.html)
- Component model:
  [`../../docs/component-model.md`](../../docs/component-model.md)
- Normative wire format:
  [`../../docs/json_contract.md`](../../docs/json_contract.md)

The Web implementation will mirror the contract in TypeScript and must remain
byte-compatible with the Kotlin implementation in `packages/design-contract`.
Cross-language canonical-serialization tests are required before codegen or
sync relies on checksums.
