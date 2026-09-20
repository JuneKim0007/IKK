# IKK

On-device benchmark harness for measuring the energy and performance cost of
Android SDK cryptographic primitives.

Capstone project: *Greenspecting Android Cryptographic Primitives — A
Multi-Objective Energy and Performance Analysis*.

The app executes cryptographic operations under controlled, repeated workloads
and records per-iteration runtime and allocation. Energy is **not** sampled by
the app: power traces are captured off-device in parallel with a run and joined
against exported results during analysis.

Scope is the on-device half only. The analysis pipeline lives elsewhere.

## Structure

```text
app/src/main/kotlin/com/ikk/
├── crypto/        operations under test, grouped by category
├── provider/      Conscrypt / Keystore / Strongbox backends
├── benchmark/     matrix configuration, runner, raw results
├── measurement/   on-device runtime and allocation sampling
└── export/        writing results out for off-device analysis
```

Everything in those packages is currently an interface or data type. No
measurement logic is implemented yet.

## Requirements

- JDK 21 (AGP 9.x does not support JDK 25+). The path is pinned in
  `gradle.properties` via `org.gradle.java.home`.
- Android SDK with API 37 installed. Path is read from `local.properties`
  (`sdk.dir`), which is git-ignored and must exist locally.

## Build

```sh
./gradlew assembleDebug
```

## Test

```sh
./gradlew testDebugUnitTest          # JVM unit tests, no emulator
./gradlew connectedDebugAndroidTest  # instrumented tests, needs a device/emulator
```

## Run

Open the project root in IntelliJ IDEA / Android Studio and let it import the
Gradle build, then run the `app` configuration. From the command line:

```sh
./gradlew installDebug
adb shell am start -n com.ikk/.MainActivity
```
