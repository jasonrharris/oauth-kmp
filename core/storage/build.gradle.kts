plugins {
    id("oauth.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:oauth"))
            implementation(project(":core:backend-contract"))
        }
    }
}
