# IKK

IKK is a contract-driven UI editor. A design is stored as one versioned JSON
contract and rendered by separate Android and Web surfaces. Deterministic
codegen will emit platform artifacts from that contract; authored behaviour
stays outside generated files.

## Repository map

```text
apps/
├── android/
│   ├── app/                  Android launcher and Compose UI
│   └── data/                 Android repositories and data sources
└── web/                      Web editor surface

packages/
└── design-contract/          Kotlin contract model, validation, serialization

prototypes/
├── editor-android/           Android interaction specification
├── editor-web/               Web interaction specification
├── shared/                   Shared prototype-only editor logic
├── pipeline/                 Codegen and agent pipeline demonstration
├── agent-loop/               Agent gate-runner demonstration
└── legacy/                   Superseded experiments kept for reference

docs/
├── architecture/             Repository and frontend architecture decisions
├── json_contract.md          Normative wire format
├── component-model.md        Component responsibilities
└── roadmap.md                Delivery plan
```

Root Gradle files, `gradle/`, and `.github/` are build and automation
infrastructure rather than product modules.

See [docs/architecture/repository-layout.md](docs/architecture/repository-layout.md)
for ownership rules and dependency boundaries.

## Gradle modules

| Gradle path | Directory | Responsibility |
|---|---|---|
| `:apps:android:app` | `apps/android/app` | Activity, Compose UI, dependency wiring |
| `:apps:android:data` | `apps/android/data` | Android repositories and data sources |
| `:packages:design-contract` | `packages/design-contract` | Pure Kotlin contract model and validation |

`design-contract` applies no Android plugin. Android or Web implementation
details must not be added to it.

## Current status

| Area | State |
|---|---|
| JSON contract and Kotlin model | Implemented |
| Android application | Hello World scaffold |
| Web application | Static device-frame harness |
| Android and Web editor UX | Interactive prototypes only |
| Production codegen and agent integration | Not merged into `master` |

The authority order is documented in [docs/README.md](docs/README.md). In
short: the JSON contract wins over the component model, roadmap, and
prototypes.

## Requirements

- JDK 17; Gradle toolchains can provision it automatically
- Android SDK with API 37 installed
- Gradle 9.6.1 through the checked-in wrapper

The Android SDK path belongs in the git-ignored `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

## Build and test

```sh
./gradlew test
./gradlew assembleDebug
```

Focused commands:

```sh
./gradlew :packages:design-contract:test
./gradlew :apps:android:data:testDebugUnitTest
./gradlew :apps:android:app:assembleDebug
```

Run the Android app from Android Studio or with:

```sh
./gradlew :apps:android:app:installDebug
adb shell am start -n com.ikk/.MainActivity
```
