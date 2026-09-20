plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ikk.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") { kotlin.srcDir("src/main/kotlin") }
        getByName("test") { kotlin.srcDir("src/test/kotlin") }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Contract types are read from here, never redeclared. Pure Kotlin/JVM —
    // no Android dependency on either side of this edge. See
    // docs/architecture/repository-layout.md and apps/android/README.md.
    api(project(":packages:design-contract"))

    testImplementation(libs.junit)
}
