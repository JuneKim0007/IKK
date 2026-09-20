pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Resolves the JDK declared by jvmToolchain(17) on any machine, downloading it
// if absent. This is what replaces the absolute org.gradle.java.home that used
// to be hardcoded in gradle.properties.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IKK"

// Layered modules. Dependencies point downward only:
//   app -> data -> core
include(":app")
include(":data")
include(":core")
