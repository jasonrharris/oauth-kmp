package com.baloomba.oauth.core.oauth

import com.baloomba.oauth.core.backendcontract.BackendErrorCode
import com.baloomba.oauth.core.backendcontract.ExchangeRequest
import com.baloomba.oauth.core.backendcontract.TokenSet
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

public sealed interface AuthOutcome {
    public data class Authenticated(val provider: ProviderId, val tokens: TokenSet) : AuthOutcome {
        override fun toString(): String = "Authenticated(provider=$provider, tokens=<redacted>)"
    }
    public data object Cancelled : AuthOutcome
    /** Web: the page is navigating away; call [OAuthClient.resume] on next load. */
    public data object Redirecting : AuthOutcome
    public data class Failed(val reason: AuthFailure) : AuthOutcome
}

public sealed interface AuthFailure {
    public data object UnknownProvider : AuthFailure
    /** Unknown, already used or missing state, including callbacks that cannot be parsed. */
    public data object StateMismatch : AuthFailure
    public data object SessionExpired : AuthFailure
    public data class ProviderDenied(val error: String) : AuthFailure
    public data class ExchangeRejected(val code: BackendErrorCode) : AuthFailure
    public data class Network(val cause: Throwable) : AuthFailure
}

public class OAuthClient(
    private val registry: ProviderRegistry,
    private val requests: AuthorizationRequestFactory,
    private val pending: PendingAuthStore,
    private val launcher: AuthRedirectLauncher,
    private val exchange: TokenExchangeApi,
    private val tokens: TokenStore,
    private val redirectUri: String,
    private val clock: Clock = Clock.System,
    private val sessionTtl: Duration = DEFAULT_SESSION_TTL,
) {
    /** Phase 1. Completes inline when the launcher hands the callback straight back (mobile). */
    public suspend fun signIn(provider: ProviderId): AuthOutcome {
        val config = registry.get(provider) ?: return AuthOutcome.Failed(AuthFailure.UnknownProvider)
        val request = requests.create(config, redirectUri)
        pending.save(request.pending)

        val result = try {
            launcher.launch(request.url, redirectUri)
        } catch (e: Throwable) {
            pending.take(request.pending.state)
            throw e
        }
        return when (result) {
            is LaunchResult.Callback -> complete(result.url)
            LaunchResult.Cancelled -> {
                pending.take(request.pending.state)
                AuthOutcome.Cancelled
            }
            LaunchResult.Redirecting -> AuthOutcome.Redirecting
        }
    }

    /** Phase 2. Validates a callback URL (web reload, Android deep link) and exchanges the code. */
    public suspend fun resume(callbackUrl: String): AuthOutcome = complete(callbackUrl)

    public suspend fun signOut(provider: ProviderId) {
        tokens.clear(provider)
    }

    private suspend fun complete(callbackUrl: String): AuthOutcome {
        val callback = when (val parsed = CallbackParser.parse(callbackUrl)) {
            CallbackResult.Malformed -> return AuthOutcome.Failed(AuthFailure.StateMismatch)
            is CallbackResult.ProviderError -> {
                val state = parsed.state
                val known = !state.isNullOrEmpty() && pending.take(state)?.state == state
                return AuthOutcome.Failed(
                    if (known) AuthFailure.ProviderDenied(parsed.error) else AuthFailure.StateMismatch,
                )
            }
            is CallbackResult.Success -> parsed
        }

        val session = pending.take(callback.state)
        if (session == null || session.state != callback.state) {
            return AuthOutcome.Failed(AuthFailure.StateMismatch)
        }
        if (clock.now() - session.createdAt > sessionTtl) {
            return AuthOutcome.Failed(AuthFailure.SessionExpired)
        }
        if (registry.get(session.provider) == null) {
            return AuthOutcome.Failed(AuthFailure.UnknownProvider)
        }

        val result = try {
            exchange.exchange(
                ExchangeRequest(
                    provider = session.provider.value,
                    code = callback.code,
                    codeVerifier = session.codeVerifier,
                    redirectUri = session.redirectUri,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AuthOutcome.Failed(AuthFailure.Network(e))
        }

        return when (result) {
            is ExchangeResult.Rejected -> AuthOutcome.Failed(AuthFailure.ExchangeRejected(result.code))
            is ExchangeResult.Ok -> {
                tokens.save(session.provider, result.response.tokens)
                AuthOutcome.Authenticated(session.provider, result.response.tokens)
            }
        }
    }

    private companion object {
        val DEFAULT_SESSION_TTL: Duration = 10.minutes
    }
}
