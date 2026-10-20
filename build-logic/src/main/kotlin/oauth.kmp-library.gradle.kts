import com.android.build.api.dsl.androidLibrary

/**
 * Convention for every shared module.
 *
 * Targets: jvm (fast tests), android, iosArm64, iosSimulatorArm64.
 * Web is intentionally not included yet; add it here once the web UI decision is made,
 * and every module picks it up.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    explicitApi()

    compilerOptions {
        allWarningsAsErrors.set(warningsAsErrors())
    }

    jvm()

    androidLibrary {
        namespace = androidNamespace()
        compileSdk = catalogVersion("androidCompileSdk").toInt()
        minSdk = catalogVersion("androidMinSdk").toInt()
    }

    // Apple targets are skipped automatically on non-macOS hosts.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
