package com.baloomba.oauth.core.oauth

import kotlin.time.Clock
import kotlin.time.Instant

public data class PendingAuthorization(
    val provider: ProviderId,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val createdAt: Instant,
) {
    override fun toString(): String =
        "PendingAuthorization(provider=$provider, state=$state, codeVerifier=<redacted>, " +
            "redirectUri=$redirectUri, createdAt=$createdAt)"
}

public data class AuthorizationRequest(val url: String, val pending: PendingAuthorization)

public class AuthorizationRequestFactory(
    private val pkce: PkceGenerator,
    private val random: SecureRandom,
    private val clock: Clock,
) {
    public fun create(config: OAuthProviderConfig, redirectUri: String): AuthorizationRequest {
        val challenge = pkce.generate()
        val state = random.string(STATE_LENGTH, PkceGenerator.UNRESERVED)

        val params = buildList {
            add("response_type" to "code")
            add("client_id" to config.clientId)
            add("redirect_uri" to redirectUri)
            if (config.scopes.isNotEmpty()) add("scope" to config.scopes.joinToString(" "))
            add("state" to state)
            add("code_challenge" to challenge.challenge)
            add("code_challenge_method" to "S256")
            addAll(config.extraAuthParams.toList())
        }
        val query = params.joinToString("&") { (k, v) -> "${UrlEncoding.encode(k)}=${UrlEncoding.encode(v)}" }
        val separator = if ('?' in config.authorizationEndpoint) "&" else "?"

        return AuthorizationRequest(
            url = config.authorizationEndpoint + separator + query,
            pending = PendingAuthorization(config.id, state, challenge.verifier, redirectUri, clock.now()),
        )
    }

    private companion object {
        const val STATE_LENGTH = 32
    }
}
