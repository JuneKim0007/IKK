# Frontend architecture

## Decision

IKK uses two platform-native UI implementations:

- Jetpack Compose under `apps/android`;
- a TypeScript Web application under `apps/web`.

They share a versioned JSON wire format, not UI code. The Kotlin reference
implementation lives in `packages/design-contract`; Web will maintain a
byte-compatible TypeScript implementation and cross-language fixtures.

This corresponds to separate UIs with a shared data contract. Compose
Multiplatform and a WebView shell are not part of v1.

## Why

- Android keeps native rendering, accessibility, and touch behaviour.
- Web remains real DOM/CSS rather than a canvas or embedded mobile page.
- The contract makes geometry and visual properties deterministic across both
  renderers.
- Platform-specific behaviour can evolve without introducing shared-UI build
  complexity.

The cost is two renderers. Golden-image parity tests and deterministic codegen
are therefore product requirements, not optional polish.

## Data is a separate concern

Sharing a contract does not share runtime state. Android app storage and
browser storage are different processes and different sandboxes. For v1:

1. seeded local state is enough for editor development;
2. static JSON export/import is the first interoperability path;
3. a backend becomes necessary only for live multi-client synchronization.

## Generated/authored boundary

The contract owns generated layout files. Application state, callbacks,
navigation, effects, and data access remain authored platform code. See
[repository-layout.md](repository-layout.md) for the file boundary.
