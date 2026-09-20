# Repository layout

The repository is organized by deployability and ownership, not by language.

```text
apps/          deployable Android and Web surfaces
packages/      reusable, platform-independent modules
prototypes/    disposable interaction specifications
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
5. Prototypes may illustrate production behaviour but are never imported by
   production modules.
6. `docs/json_contract.md` is the wire-format authority. A contract change and
   its model/tests must land together.

## Generated and authored code

When codegen is merged, it belongs under `tools/codegen/`. Its outputs belong
inside the consuming app and must include `.generated.` in the filename.

```text
tools/codegen/                                      generator and tests
apps/web/src/generated/Home.generated.css          generated Web layout
apps/android/app/src/main/kotlin/.../Home.generated.kt
apps/web/src/.../Home.ts                            authored behaviour
apps/android/app/src/main/kotlin/.../Home.kt        authored behaviour
```

Codegen may replace generated files wholesale. It must never write authored
files. An AI agent may propose authored changes through a reviewed branch, but
must not become a second writer for generated artifacts.

## Build ownership

Root Gradle files configure the entire repository. Module build files stay next
to their modules. GitHub workflows are split by concern: the current
`.github/workflows/android.yml` owns Gradle tests and the Android build; a Web
workflow should be added when `apps/web` gains a real toolchain.
