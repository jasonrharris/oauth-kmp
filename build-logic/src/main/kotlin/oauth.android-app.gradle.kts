/** Convention for the Android application module. */
plugins {
    id("com.android.application")
}

android {
    compileSdk = catalogVersion("androidCompileSdk").toInt()

    defaultConfig {
        minSdk = catalogVersion("androidMinSdk").toInt()
        targetSdk = catalogVersion("androidTargetSdk").toInt()
    }
}
