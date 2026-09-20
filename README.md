# IKK

Android application (Kotlin + Jetpack Compose). Currently a Hello World
scaffold.

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

## Layout

```text
IKK/
├── settings.gradle.kts         declares the modules in the build
├── build.gradle.kts            plugin versions, applied per module
├── gradle/libs.versions.toml   dependency versions
├── docs/
├── app/     Android application - Activity, Compose UI, wiring
├── data/    Android library - repositories and data sources
└── core/    plain Kotlin/JVM - pure logic, no Android dependency
```

The Gradle files at the root are the build's entry point and have to live
there; Gradle finds the build by locating `settings.gradle.kts`.

Dependencies point downward only:

```text
app  ->  data  ->  core
```

`core` deliberately does not apply an Android plugin. Referencing
`android.*` from it fails compilation rather than passing review, and its
tests run on the desktop JVM with no emulator. Add feature modules alongside
`app` as features arrive; there is no reason to create empty ones now.

Per-module tests:

```sh
./gradlew :core:test                 # fastest, pure JVM
./gradlew :data:testDebugUnitTest
./gradlew test                       # everything
```
