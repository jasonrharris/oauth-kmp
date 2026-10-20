# oauth

Provider-agnostic OAuth 2.0 (authorization code + PKCE) client for Kotlin Multiplatform.
Token exchange happens on a backend; this library never sees a client secret.

## Prerequisites
- JDK 17+
- Android SDK (compileSdk 36)
- macOS + Xcode only when building iOS targets (phase 6)

## First-time setup
Generate the Gradle wrapper (jar and scripts are not committed by the scaffold):

    gradle wrapper --gradle-version 9.1.0

## Everyday commands

    ./gradlew check                    # tests + module boundary check
    ./gradlew verifyModuleBoundaries   # dependency rules only
    ./gradlew :androidApp:assembleDebug

## Module rules
Ports (interfaces) live in `core:oauth`. Every other module is an adapter or consumer.
The allowed dependencies are declared in the root `build.gradle.kts` and enforced by `check`.

## Roadmap
1. Scaffold (this)  2. Core logic  3. Contract + network  4. Storage
5. Android end to end  6. iOS end to end  7. Web  8. Hardening
