# KMP Plan: Provider-Agnostic OAuth Client (Backend Token Exchange)

Status: Phases 1 (scaffold), 2 (core logic) and 3 (contract and network) done. Phase 4 (storage) partially done: in-memory and Android secure storage done, iOS Keychain pending.

## 1. Goals and constraints

- Convert the Baloomba frontend from React Native/Expo to Kotlin Multiplatform, mobile first (Android, iOS), with a web client still required.
- Not Spotify specific: a modular, generic OAuth 2.0 authorization code + PKCE client.
- Token exchange happens on the backend (the existing Go service). The client never holds a client secret.
- Backend stays in Go for now.
- Priorities: easy maintenance, testing and deployment. Development cost is treated as equal across options.

## 2. Design principles

- **Provider is data, not code.** A provider is a config object (endpoints, client ID, scopes). The flow is identical for all providers.
- **Pure logic in `core:oauth`, platform code behind small interfaces (ports).** Only three things are inherently platform specific: secure random and hashing, secure storage, and launching the browser.
- **Two-phase flow.** `signIn` builds the URL and persists a pending session. `resume` validates the callback and exchanges the code. This supports web (page navigates away and the app restarts on return), Android (deep link) and iOS (`ASWebAuthenticationSession`).
- **Hexagonal dependency rule.** Ports (interfaces) live in `core:oauth`. Every other module is an adapter that implements a port, or a consumer. This avoids dependency cycles.
- **The backend contract is a single module** mirroring the Go `ExchangeRequest`, `ExchangeResponse` and `ErrorResponse`.

## 3. Module layout (as scaffolded)

```
oauth/
├─ build-logic/                 convention plugins: oauth.kmp-library, oauth.android-app
├─ gradle/libs.versions.toml    single version catalog
├─ core/
│  ├─ backend-contract/         DTOs + error codes shared with the Go API
│  ├─ oauth/                    config, PKCE, URL building, callback parsing, OAuthClient, ALL ports
│  ├─ crypto/                   adapters: SecureRandom, Sha256
│  ├─ storage/                  adapters: PendingAuthStore, TokenStore
│  └─ network/                  adapter: TokenExchangeApi (Ktor)
├─ platform/
│  └─ browser/                  adapters: AuthRedirectLauncher
├─ feature/
│  └─ auth/                     AuthViewModel, UI state, screens
├─ composeApp/                  shared app shell, wiring, navigation
├─ androidApp/                  Android host
├─ iosApp/                      (phase 6) thin Xcode host
└─ webApp/                      (phase 7) web entry point
```

### Allowed dependencies (enforced by `./gradlew check`)

| Module | May depend on |
|---|---|
| `core:backend-contract` | nothing |
| `core:oauth` | `core:backend-contract` |
| `core:crypto` | `core:oauth` |
| `core:storage` | `core:oauth`, `core:backend-contract` |
| `core:network` | `core:oauth`, `core:backend-contract` |
| `platform:browser` | `core:oauth` |
| `feature:auth` | `core:oauth` |
| `composeApp` | all of the above |
| `androidApp` | `composeApp` |

## 4. Core types (`core:oauth`)

### Provider configuration

```kotlin
@JvmInline
value class ProviderId private constructor(val value: String) {
    companion object {
        fun of(raw: String): ProviderId {
            val normalized = raw.trim().lowercase()
            require(normalized.isNotEmpty()) { "Provider id must not be blank" }
            return ProviderId(normalized)
        }
    }
}

data class OAuthProviderConfig(
    val id: ProviderId,
    val displayName: String,
    val authorizationEndpoint: String,
    val clientId: String,
    val scopes: List<String>,
    val extraAuthParams: Map<String, String> = emptyMap(),
)

interface ProviderRegistry {
    fun all(): List<OAuthProviderConfig>
    fun get(id: ProviderId): OAuthProviderConfig?
}
```

Replaces `providerRegistry.js`. Initially an in-memory registry fed by app config; could later be fetched from the backend without changing callers.

### PKCE, state and URL building

```kotlin
data class PkceChallenge(val verifier: String, val challenge: String)

class PkceGenerator(private val random: SecureRandom, private val sha256: Sha256) {
    fun generate(): PkceChallenge {
        val verifier = random.string(length = 128, alphabet = UNRESERVED)
        val digest = sha256.hash(verifier.encodeToByteArray())
        return PkceChallenge(verifier, digest.encodeBase64Url())
    }
}

data class AuthorizationRequest(val url: String, val pending: PendingAuthorization)

class AuthorizationRequestFactory(
    private val pkce: PkceGenerator,
    private val random: SecureRandom,
    private val clock: Clock,
) {
    fun create(config: OAuthProviderConfig, redirectUri: String): AuthorizationRequest
}
```

The challenge is computed over raw bytes and base64url-encoded without padding. This fixes the current inconsistency, where native returns base64 and web returns an `ArrayBuffer`.

### Pending session and callback validation

```kotlin
data class PendingAuthorization(
    val provider: ProviderId,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val createdAt: Instant,
)

sealed interface CallbackResult {
    data class Success(val code: String, val state: String) : CallbackResult
    data class ProviderError(val error: String, val description: String?) : CallbackResult
    data object Malformed : CallbackResult
}

object CallbackParser {
    fun parse(url: String): CallbackResult
}
```

### Orchestrator

```kotlin
sealed interface AuthOutcome {
    data class Authenticated(val provider: ProviderId, val tokens: TokenSet) : AuthOutcome
    data object Cancelled : AuthOutcome
    data object Redirecting : AuthOutcome          // web: page is navigating away
    data class Failed(val reason: AuthFailure) : AuthOutcome
}

sealed interface AuthFailure {
    data object UnknownProvider : AuthFailure
    data object StateMismatch : AuthFailure
    data object SessionExpired : AuthFailure
    data class ProviderDenied(val error: String) : AuthFailure
    data class ExchangeRejected(val code: BackendErrorCode) : AuthFailure
    data class Network(val cause: Throwable) : AuthFailure
}

class OAuthClient(
    registry: ProviderRegistry,
    requests: AuthorizationRequestFactory,
    pending: PendingAuthStore,
    launcher: AuthRedirectLauncher,
    exchange: TokenExchangeApi,
    tokens: TokenStore,
    redirectUri: String,
) {
    suspend fun signIn(provider: ProviderId): AuthOutcome   // phase 1 (completes inline on mobile)
    suspend fun resume(callbackUrl: String): AuthOutcome    // phase 2 (web reload, Android deep link)
    suspend fun signOut(provider: ProviderId)
}
```

`signIn` persists the pending session, then asks the launcher to open the URL. On Android and iOS the launcher may hand the callback URL straight back, so `signIn` calls the same private `complete` function that `resume` uses. On web the launcher returns `Redirecting` and the app calls `resume` on next load. One code path validates state, consumes the verifier (single use) and calls the backend.

## 5. Ports (all in `core:oauth`) and their adapters

```kotlin
interface SecureRandom { fun bytes(n: Int): ByteArray; fun string(length: Int, alphabet: String): String }
interface Sha256 { fun hash(input: ByteArray): ByteArray }

interface PendingAuthStore {
    suspend fun save(session: PendingAuthorization)
    suspend fun take(state: String): PendingAuthorization?   // atomic read-and-delete
}
interface TokenStore {
    suspend fun save(provider: ProviderId, tokens: TokenSet)
    suspend fun load(provider: ProviderId): TokenSet?
    suspend fun clear(provider: ProviderId)
}

sealed interface LaunchResult {
    data class Callback(val url: String) : LaunchResult
    data object Cancelled : LaunchResult
    data object Redirecting : LaunchResult
}
interface AuthRedirectLauncher {
    suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult
}

interface TokenExchangeApi {
    suspend fun exchange(request: ExchangeRequest): ExchangeResult
}
sealed interface ExchangeResult {
    data class Ok(val response: ExchangeResponse) : ExchangeResult
    data class Rejected(val code: BackendErrorCode, val message: String) : ExchangeResult
}
```

| | Android | iOS | Web |
|---|---|---|---|
| Crypto (`core:crypto`) | cryptography-kotlin (or JCA) | cryptography-kotlin (CryptoKit-backed) | cryptography-kotlin (WebCrypto) |
| Pending store (`core:storage`) | Tink AES-256-GCM, master key in Android Keystore (`EncryptedSharedPreferences` / `security-crypto` is deprecated, not used) | Keychain | `sessionStorage` |
| Token store (`core:storage`) | Tink AES-256-GCM, master key in Android Keystore (same adapter) | Keychain | in-memory, or backend-set `HttpOnly` cookie |
| Launcher (`platform:browser`) | Custom Tabs, then deep link into `resume` | `ASWebAuthenticationSession` | `location.assign()`, returns `Redirecting` |

Secure storage is implemented in-house behind the `PendingAuthStore` and `TokenStore` ports (decision: no third-party KMP storage library). Encryption and corruption handling live in common code behind small internal seams (`BlobCipher`, `BlobStore`) and are tested on the JVM with fakes; each platform only supplies a thin adapter (Android: Tink 1.24.0 `AndroidKeysetManager` plus private SharedPreferences; iOS: Keychain, pending).

## 6. Backend contract (`core:backend-contract`) and network (`core:network`)

Mirror the Go types exactly:

```kotlin
@Serializable
data class ExchangeRequest(
    val provider: String,
    val code: String,
    val codeVerifier: String,
    val redirectUri: String? = null,     // requires backend change, see section 8
)

@Serializable
data class TokenSet(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
    val tokenType: String? = null,
    val scope: String? = null,
)

@Serializable data class ExchangeResponse(val provider: String, val tokens: TokenSet)
@Serializable data class ErrorResponse(val code: String, val message: String)

enum class BackendErrorCode(val wire: String) {
    InvalidRequest("invalid_request"),
    UnsupportedProvider("unsupported_provider"),
    ProviderExchangeFailed("provider_exchange_failed"),
    Unknown("")
}
```

The Ktor implementation of `TokenExchangeApi` takes `baseUrl` as a constructor argument and never reads global config. Unreachable-network errors surface as exceptions, which `OAuthClient` maps to `AuthFailure.Network`.

## 7. Feature and UI layer (`feature:auth`, `composeApp`)

- `AuthViewModel` exposes `StateFlow<AuthUiState>` (`SignedOut`, `InProgress`, `SignedIn(provider)`, `Error(message)`) with `signIn(providerId)`, `onIncomingUrl(url)` and `signOut()`.
- Screens: `ProviderPickerScreen` (lists the registry, nothing hard-coded), `AuthCallbackHandler` (invisible composable that calls `resume` on start if the URL contains a callback), `SignedInScreen` (placeholder landing page).
- Navigation: minimal Compose Navigation, or Decompose for testable navigation state.
- DI: manual constructor wiring in one `AppGraph` class. Introduce Koin only if wiring becomes painful.
- Compose Multiplatform is added in phase 5. Pin a version verified against the Kotlin version in `libs.versions.toml`.

## 8. Required backend adjustments (Go stays Go)

1. **Redirect URI per client.** The handler reads a single `REDIRECT_URI` env var, but web and mobile use different URIs, and the redirect URI in the token exchange must match the authorization request. Accept an optional `redirectUri` and validate it against an allowlist from config.
2. **Provider registry on the server.** Replace the hard-coded `exchangeWithSpotify` with a `ProviderExchanger` interface per provider and a map keyed by ID.
3. **Optional `/api/refresh` endpoint** (refresh tokens also need the client secret). Not needed for v1, but design `TokenSet` and `TokenStore` so it slots in.
4. **Token delivery on web.** Browser storage is the weakest option. A cleaner design has the backend set an `HttpOnly` cookie. Decide before building the web target.
5. **State validation** stays entirely client side (it is a client-side CSRF defence). The backend stays ignorant of it.

## 9. Mapping from the current code

| Current file | Becomes |
|---|---|
| `providerRegistry.js` | `ProviderRegistry`, `ProviderId` (`core:oauth`) |
| `authContract.js` | `ExchangeRequest` + request validation in `OAuthClient` |
| `sha256.*.js` | `Sha256` implementations (`core:crypto`) |
| `generateRandomString` | `SecureRandom.string` |
| `spotifyLogin.js` | `AuthorizationRequestFactory` + `AuthRedirectLauncher` + `ProviderPickerScreen` |
| `callback.js` | `CallbackParser` + `OAuthClient.resume` + `AuthCallbackHandler` |
| `storage.*.js` | `PendingAuthStore`, `TokenStore` |
| `LandingPage.js` | `SignedInScreen` |
| Expo template screens | Deleted |

### Existing defects the rewrite should not carry over

- `sha256.js` requires `./sha256`, which is itself.
- Native hash returns base64 while web returns an `ArrayBuffer`, and the test expects a bogus hex string.
- The PKCE verifier uses `Math.random`, which is not cryptographically secure.
- `state` is generated but never validated on callback.
- Tokens are never stored after the exchange.
- `callback.js` calls `${backendUri}exchange` while the Go route is `/api/exchange`.
- `callback.js` uses `window.location` on native.

## 10. Testing strategy

- **`core:oauth` (JVM, `commonTest`):** fakes for `SecureRandom`, `Sha256`, `Clock` and stores. Assert the RFC 7636 test vector for the PKCE challenge, URL contents, state mismatch rejection, single-use verifiers, expired sessions, and every `AuthFailure` branch.
- **`core:network`:** Ktor `MockEngine` fed with fixture JSON copied from the Go tests (success, `invalid_grant`, 502 with a typed error). Keep fixtures in a shared directory so a contract change fails both sides.
- **`feature:auth`:** ViewModel tests with Turbine over a fake `OAuthClient`.
- **Platform:** one small instrumented or UI test per target for the launcher and storage only.
- **Quality gates:** module boundary check (done), then ktlint or detekt and Kover coverage on `core:*`. Look up current versions before adding.

## 11. Delivery flow (deployment)

- **Android:** `./gradlew bundleRelease` produces an AAB. Upload via Play Console, or automate with Gradle Play Publisher or fastlane.
- **iOS:** Gradle builds the shared framework, a thin `iosApp` Xcode project hosts it, and `xcodebuild` archives and signs. Needs macOS (GitHub macOS runners, Xcode Cloud, Codemagic or Bitrise). Use fastlane (`match`, `pilot`) for signing and TestFlight.
- **Web:** decide between Compose for Web (Wasm, least mature target) and sharing logic only with a separate UI.
- **No OTA updates.** Every fix goes through store review. Mitigate with staged rollouts, a remote feature-flag service and crash reporting from day one.
- **Cost:** Apple and Google developer fees are the same either way. Expo's free tier likely covers a side project, and KMP is close to zero if a Mac is available for iOS builds. Recheck pricing before deciding.

## 12. Delivery phases

| # | Phase | Status |
|---|---|---|
| 1 | **Scaffold.** Version catalog, convention plugins, empty modules, module boundary check, CI on Linux, Gradle wrapper. | Done (verify first green CI run) |
| 2 | **Core logic.** `core:oauth` and ports, PKCE, URL building, callback parsing, `OAuthClient` state machine, `core:crypto` interfaces and fakes, full unit tests. Delete the smoke test. | Done (JVM tests green; `core:crypto` has no adapters yet, fakes live in `core:oauth` commonTest; real adapters need a crypto library decision) |
| 3 | **Contract and network.** DTOs, `TokenExchangeApi` over Ktor, MockEngine tests. Backend changes from section 8 in parallel. | Done (Go backend changes from section 8 are outside this repo) |
| 4 | **Storage.** In-memory fake first, then Android and iOS secure implementations. | Partially done: in-memory stores and Android Tink/Keystore adapter done (adapter not yet compiled or run, needs Android SDK/CI/device); iOS Keychain pending |
| 5 | **Android end to end.** Custom Tabs launcher, deep link, Compose UI. Prove the full flow against the local Go server. | |
| 6 | **iOS end to end.** `ASWebAuthenticationSession` launcher, Xcode host project, macOS CI job, fastlane. | |
| 7 | **Web.** Redirect launcher and the `resume` path. Decide Compose for Web versus logic-only sharing. | |
| 8 | **Hardening.** Session expiry cleanup, error UX, token refresh via backend, crash reporting, module READMEs. | |

## 13. Open decisions

- Web UI technology (Compose for Web versus logic-only sharing), to settle before phase 7.
- Token handling on web (HttpOnly cookie versus in-memory).
- Where provider configs live long term: bundled in the app or fetched from the backend.
- Crypto libraries for `core:crypto` adapters (decide at phase 5/6).
- ~~Secure storage library~~ Resolved: in-house adapters behind the ports; Android uses Google Tink (`tink-android` 1.24.0, actively maintained) with Android Keystore. `EncryptedSharedPreferences` is deprecated and excluded. iOS uses the Keychain directly.

## 14. Environment notes

- JDK 17+ required. Gradle 9.1.0 via the committed wrapper (AGP 9.0 needs Gradle 9.1+). Do not run the system Gradle on this repo; always use `./gradlew`.
- Kotlin 2.3.21 and AGP 9.0.0 were chosen from JetBrains' compatibility table. Re-check it before upgrading either.
- GitHub is the source of truth: PRDs in the Wiki, delivery tracking in Issues and Projects (one issue per phase is a good start).
