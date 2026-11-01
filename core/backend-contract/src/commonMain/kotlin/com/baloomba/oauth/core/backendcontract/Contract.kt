package com.baloomba.oauth.core.backendcontract

import kotlinx.serialization.Serializable

/** Body of `POST /api/exchange`. Mirrors the Go `ExchangeRequest`. */
@Serializable
public data class ExchangeRequest(
    val provider: String,
    val code: String,
    val codeVerifier: String,
    val redirectUri: String? = null,
) {
    override fun toString(): String =
        "ExchangeRequest(provider=$provider, code=<redacted>, codeVerifier=<redacted>, redirectUri=$redirectUri)"
}

@Serializable
public data class TokenSet(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
    val tokenType: String? = null,
    val scope: String? = null,
) {
    override fun toString(): String =
        "TokenSet(accessToken=<redacted>, refreshToken=<redacted>, expiresIn=$expiresIn, " +
            "tokenType=$tokenType, scope=$scope)"
}

@Serializable
public data class ExchangeResponse(val provider: String, val tokens: TokenSet)

@Serializable
public data class ErrorResponse(val code: String, val message: String)

public enum class BackendErrorCode(public val wire: String) {
    InvalidRequest("invalid_request"),
    UnsupportedProvider("unsupported_provider"),
    ProviderExchangeFailed("provider_exchange_failed"),
    Unknown("");

    public companion object {
        public fun fromWire(wire: String): BackendErrorCode =
            entries.firstOrNull { it.wire.isNotEmpty() && it.wire == wire } ?: Unknown
    }
}
