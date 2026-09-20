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
├── settings.gradle.kts     defines which modules are in the build
├── build.gradle.kts        shared config for all modules
├── gradle/libs.versions.toml   dependency versions
└── app/                    the Android application module
    ├── build.gradle.kts
    └── src/{main,test,androidTest}/kotlin/com/ikk/
```

The Gradle files at the root are the build's entry point and have to live
there. Code organisation happens inside modules, and by adding modules.
