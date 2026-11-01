package com.baloomba.oauth.core.oauth

import com.baloomba.oauth.core.backendcontract.TokenSet

/** Port: cryptographically secure randomness. Implemented in `core:crypto`. */
public interface SecureRandom {
    public fun bytes(n: Int): ByteArray

    /** Returns [length] characters drawn uniformly from [alphabet]. */
    public fun string(length: Int, alphabet: String): String
}

/** Port: SHA-256. Implemented in `core:crypto`. */
public interface Sha256 {
    public fun hash(input: ByteArray): ByteArray
}

public interface PendingAuthStore {
    public suspend fun save(session: PendingAuthorization)

    /** Atomic read-and-delete, so a verifier can be used once. */
    public suspend fun take(state: String): PendingAuthorization?
}

public interface TokenStore {
    public suspend fun save(provider: ProviderId, tokens: TokenSet)
    public suspend fun load(provider: ProviderId): TokenSet?
    public suspend fun clear(provider: ProviderId)
}

public sealed interface LaunchResult {
    public data class Callback(val url: String) : LaunchResult
    public data object Cancelled : LaunchResult
    public data object Redirecting : LaunchResult
}

public interface AuthRedirectLauncher {
    public suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult
}
