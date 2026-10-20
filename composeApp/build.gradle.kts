plugins {
    id("oauth.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:backend-contract"))
            implementation(project(":core:oauth"))
            implementation(project(":core:crypto"))
            implementation(project(":core:storage"))
            implementation(project(":core:network"))
            implementation(project(":platform:browser"))
            implementation(project(":feature:auth"))
        }
    }
}
