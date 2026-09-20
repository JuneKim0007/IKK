plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Plain Kotlin/JVM on purpose: the contract has no Android dependency, so its
// tests run on the desktop JVM and it is impossible to reach for a Context
// here. Android and a JVM backend can both consume this artifact directly.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
