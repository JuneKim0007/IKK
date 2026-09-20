# IKK

Android application (Kotlin + Jetpack Compose). Currently a Hello World
scaffold on a layered multi-module build.

## Requirements

| | |
|---|---|
| JDK | 21 — AGP 9.x does not support JDK 25+ |
| Android SDK | API 37 installed |
| Gradle | 9.6.1 via the wrapper — do not use a system `gradle` |

The JDK path is pinned in `gradle.properties` (`org.gradle.java.home`). The SDK
path is read from `local.properties` (`sdk.dir`), which is git-ignored and must
exist locally.

## Modules

Dependencies point downward only. Nothing below reaches up.

```text
app  ──>  data  ──>  core
```

| Module | Plugin | Purpose |
|---|---|---|
| `app` | `com.android.application` | Activity, Compose UI, dependency wiring |
| `data` | `com.android.library` | Repositories and data sources |
| `core` | `org.jetbrains.kotlin.jvm` | Pure logic. No Android dependency |

`core` applies no Android plugin, so `android.*` is not on its compile
classpath — a layering violation is a build failure, not a review comment. Its
tests run on the desktop JVM in well under a second.

`data` exposes `core` with `api(project(":core"))`, so `app` sees both through
one dependency.

Feature modules get added alongside `app` when there are features. None exist
yet, so none are declared.

## Components

### Build

| File | Role |
|---|---|
| `settings.gradle.kts` | Declares the three modules and the repositories |
| `build.gradle.kts` | Declares plugins for subprojects, applies none itself |
| `gradle/libs.versions.toml` | Single source of versions for plugins and libraries |
| `gradle.properties` | JDK pin, JVM args, AndroidX flag |
| `app/build.gradle.kts` | `compileSdk 37`, `minSdk 26`, `targetSdk 36`, Compose on |
| `data/build.gradle.kts` | Android library, `compileSdk 37`, `minSdk 26` |
| `core/build.gradle.kts` | Kotlin/JVM targeting Java 17 bytecode |

Kotlin sources live in `src/<set>/kotlin`, not the Android default
`src/<set>/java`. Each Android module's `sourceSets` block sets that.

### `core`

| File | Role |
|---|---|
| `com/ikk/core/Greeting.kt` | `greeting(name): String` — pure, the one piece of real logic |
| `com/ikk/core/GreetingTest.kt` | Unit test for it |

### `data`

| File | Role |
|---|---|
| `com/ikk/data/GreetingRepository.kt` | `GreetingRepository` interface and `DefaultGreetingRepository`, which delegates to `core` |
| `com/ikk/data/GreetingRepositoryTest.kt` | Unit test for the delegation |

The repository is a seam, not yet an abstraction that earns its keep. If no
real data source ever lands here, fold it into `app`.

### `app`

| File | Role |
|---|---|
| `com/ikk/MainActivity.kt` | `ComponentActivity`, constructs the repository, sets the Compose content |
| `com/ikk/ui/GreetingScreen.kt` | Stateless composable taking the message as a parameter, plus its `@Preview` |
| `AndroidManifest.xml` | Declares `MainActivity` as the launcher activity |
| `res/values/strings.xml` | `app_name` |
| `res/values/themes.xml` | `Theme.IKK`, a platform theme — no appcompat dependency needed |

`MainActivity` instantiates `DefaultGreetingRepository` directly. That is a
`TODO`: swap for dependency injection once there is more than one dependency.

### Other

| Path | Role |
|---|---|
| `docs/` | Project documentation. Empty of substance so far |
| `prototype/` | Standalone HTML UI prototypes. Not part of the Gradle build |

## Build

```sh
./gradlew assembleDebug
```

## Test

```sh
./gradlew :core:test                 # pure JVM, fastest
./gradlew :data:testDebugUnitTest
./gradlew test                       # every module
./gradlew connectedDebugAndroidTest  # instrumented, needs a device/emulator
```

`app` currently has no unit tests — its logic lives in the modules below it.

Note that `connectedDebugAndroidTest` uninstalls the app when it finishes, so
run `installDebug` again before launching by hand.

## Run

Open the project root in IntelliJ IDEA / Android Studio and let it import the
Gradle build, then run the `app` configuration. From the command line:

```sh
./gradlew installDebug
adb shell am start -n com.ikk/.MainActivity
adb logcat --pid=$(adb shell pidof -s com.ikk)
```

## Known issues

- `gradle.properties` pins an absolute JDK path from one machine. The build
  will fail on any other checkout. Replace with a Gradle toolchain.
- `compileSdk`, `minSdk` and the Java 17 settings are duplicated across `app`
  and `data`. Move to a convention plugin under `build-logic/` if a third
  Android module appears.
