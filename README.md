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

## License
Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Roadmap
Details and status live in [context-flow/plans/PLAN.md](context-flow/plans/PLAN.md) (section 12).

1. Scaffold: done
2. Core logic: done (real crypto adapters for `core:crypto` still open)
3. Contract + network: done
4. Storage: in progress. In-memory stores done; Android (Tink + Keystore) and iOS (Keychain)
   adapters written; iOS adapter type-checked with kotlinc-native but not linked, run or tested on a
   simulator/device; Android adapter uncompiled (no SDK)
5. Android end to end
6. iOS end to end
7. Web
8. Hardening
