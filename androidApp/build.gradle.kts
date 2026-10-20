plugins {
    id("oauth.android-app")
}

android {
    namespace = "com.baloomba.oauth.app"

    defaultConfig {
        applicationId = "com.baloomba.oauth.app"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":composeApp"))
}
