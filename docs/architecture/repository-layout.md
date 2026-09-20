# Repository layout

The repository is organized by deployability and ownership, not by language.

```text
apps/          deployable Android, Web, and backend surfaces
packages/      reusable, platform-independent modules
docs/          normative specs and architecture decisions
.github/       repository automation
gradle/        shared Gradle infrastructure
```

## Dependency rules

1. Apps may depend on packages.
2. Packages never depend on apps.
3. Android-specific data code stays under `apps/android/data`.
4. The design contract stays platform-independent in
   `packages/design-contract`.
5. `docs/json_contract.md` is the wire-format authority. A contract change and
   its model/tests must land together.

## Generated and authored code

Codegen lives under `packages/codegen/`. Its outputs belong inside the
consuming app and must include `.generated.` in the filename.

```text
packages/codegen/                                  generator and tests
packages/codegen/generated/web/*.generated.css     generated Web layout
packages/codegen/generated/web/*.generated.html    generated Web structure
packages/codegen/generated/android/*.generated.kt  generated Compose layout
apps/web/src/                                      authored Web editor
apps/android/app/src/main/kotlin/                  authored Android app
```

Codegen may replace generated files wholesale. It must never write authored
files. An AI agent may propose authored changes through a reviewed branch, but
must not become a second writer for generated artifacts.

## Doc filenames

Hyphenated, lowercase — `frontend-architecture.md`, `repository-layout.md`,
`roadmap-frontend.md`. Not `snake_case`, not `camelCase`. One convention,
repo-wide, so a filename can be guessed instead of grepped for.

## Build ownership

Root Gradle files configure the entire repository. Module build files stay next
to their modules. GitHub workflows are split by concern:

- `.github/workflows/android.yml` owns Android tests/builds and the shared
  design-contract tests on JDK 17.
- `.github/workflows/backend.yml` owns backend tests/builds, shared
  design-contract tests, and Node codegen tests on JDK 21 and Node 22.

A Web workflow should be added when `apps/web` gains a real toolchain.
