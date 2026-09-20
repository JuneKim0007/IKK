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
    api(project(":core"))

    testImplementation(libs.junit)
}
