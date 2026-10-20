plugins {
    id("oauth.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:backend-contract"))
        }
    }
}
