plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain Kotlin/JVM on purpose: core has no Android dependency, so its tests
// run on the desktop JVM and it is impossible to reach for a Context here.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
