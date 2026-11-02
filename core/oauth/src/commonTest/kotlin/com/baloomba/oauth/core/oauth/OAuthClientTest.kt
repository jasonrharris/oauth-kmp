package com.baloomba.oauth.core.oauth

import com.baloomba.oauth.core.backendcontract.BackendErrorCode
import com.baloomba.oauth.core.backendcontract.ExchangeRequest
import com.baloomba.oauth.core.backendcontract.ExchangeResponse
import com.baloomba.oauth.core.backendcontract.TokenSet
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class OAuthClientTest {
    private val redirect = "myapp://callback"
    private val spotify = ProviderId.of("spotify")
    private val tokens = TokenSet("access", "refresh", 3600, "Bearer", "s")

    private val clock = FakeClock()
    private val random = FakeSecureRandom(mutableListOf(RFC_VERIFIER, "state-1"))
    private val pending = FakePendingAuthStore()
    private val store = FakeTokenStore()
    private val launcher = FakeLauncher()
    private val api = FakeExchangeApi { ExchangeResult.Ok(ExchangeResponse("spotify", tokens)) }

    private fun client(registry: ProviderRegistry = InMemoryProviderRegistry(listOf(testConfig()))) = OAuthClient(
        registry = registry,
        requests = AuthorizationRequestFactory(PkceGenerator(random, FakeSha256()), random, clock),
        pending = pending,
        launcher = launcher,
        exchange = api,
        tokens = store,
        redirectUri = redirect,
        clock = clock,
    )

    private fun callbackFor(state: String = "state-1", code: String = "code-1") = "$redirect?code=$code&state=$state"

    // S-FLOW-1: mobile inline completion
    @Test
    fun signInCompletesInlineWhenLauncherReturnsCallback() = runTest {
        launcher.result = LaunchResult.Callback(callbackFor())
        val outcome = client().signIn(spotify)
        assertEquals(AuthOutcome.Authenticated(spotify, tokens), outcome)
        assertEquals(tokens, store.saved[spotify])
        assertEquals(
            ExchangeRequest("spotify", "code-1", RFC_VERIFIER, redirect),
            api.requests.single(),
        )
        assertEquals(redirect, launcher.launched.single().second)
        assertTrueUrl(launcher.launched.single().first)
    }

    private fun assertTrueUrl(url: String) {
        assertEquals(true, url.contains("state=state-1") && url.contains("code_challenge=$RFC_CHALLENGE"))
    }

    // S-FLOW-2: web two-phase
    @Test
    fun signInPersistsPendingAndReturnsRedirecting() = runTest {
        launcher.result = LaunchResult.Redirecting
        assertEquals(AuthOutcome.Redirecting, client().signIn(spotify))
        assertEquals(RFC_VERIFIER, pending.sessions.getValue("state-1").codeVerifier)
        assertEquals(emptyList(), api.requests)
    }

    // S-FLOW-3
    @Test
    fun resumeCompletesAfterRedirect() = runTest {
        launcher.result = LaunchResult.Redirecting
        val c = client()
        c.signIn(spotify)
        assertEquals(AuthOutcome.Authenticated(spotify, tokens), c.resume(callbackFor()))
    }

    // S-FLOW-4
    @Test
    fun cancelledLaunchClearsPending() = runTest {
        launcher.result = LaunchResult.Cancelled
        assertEquals(AuthOutcome.Cancelled, client().signIn(spotify))
        assertEquals(emptyMap(), pending.sessions)
        assertEquals(emptyList(), api.requests)
    }

    // S-FLOW-5
    @Test
    fun unknownProviderFailsWithoutLaunching() = runTest {
        val outcome = client(InMemoryProviderRegistry(emptyList())).signIn(spotify)
        assertEquals(AuthOutcome.Failed(AuthFailure.UnknownProvider), outcome)
        assertEquals(emptyList(), launcher.launched)
    }

    // S-FLOW-6
    @Test
    fun unknownStateIsMismatch() = runTest {
        assertEquals(AuthOutcome.Failed(AuthFailure.StateMismatch), client().resume(callbackFor("nope")))
        assertEquals(emptyList(), api.requests)
    }

    // S-FLOW-7: verifier is single use
    @Test
    fun replayedCallbackIsRejected() = runTest {
        launcher.result = LaunchResult.Redirecting
        val c = client()
        c.signIn(spotify)
        assertIs<AuthOutcome.Authenticated>(c.resume(callbackFor()))
        assertEquals(AuthOutcome.Failed(AuthFailure.StateMismatch), c.resume(callbackFor()))
        assertEquals(1, api.requests.size)
    }

    // S-FLOW-8
    @Test
    fun expiredSessionIsRejectedAndConsumed() = runTest {
        launcher.result = LaunchResult.Redirecting
        val c = client()
        c.signIn(spotify)
        clock.advance(11.minutes)
        assertEquals(AuthOutcome.Failed(AuthFailure.SessionExpired), c.resume(callbackFor()))
        assertEquals(emptyMap(), pending.sessions)
        assertEquals(emptyList(), api.requests)
    }

    // S-FLOW-8b: clock moving backwards must not bypass the TTL
    @Test
    fun clockGoingBackwardsExpiresSessionAndDeletesIt() = runTest {
        launcher.result = LaunchResult.Redirecting
        val c = client()
        c.signIn(spotify)
        clock.advance((-1).minutes)
        assertEquals(AuthOutcome.Failed(AuthFailure.SessionExpired), c.resume(callbackFor()))
        assertEquals(emptyMap(), pending.sessions)
        assertEquals(emptyList(), api.requests)
    }

    // S-FLOW-8c: token store failure on save is a failure outcome, session already consumed
    @Test
    fun tokenStoreSaveFailureIsStorageFailureNotException() = runTest {
        launcher.result = LaunchResult.Redirecting
        val boom = IllegalStateException("disk full")
        val failingStore = object : TokenStore {
            override suspend fun save(provider: ProviderId, tokens: TokenSet) = throw boom
            override suspend fun load(provider: ProviderId): TokenSet? = null
            override suspend fun clear(provider: ProviderId) {}
        }
        val c = OAuthClient(
            registry = InMemoryProviderRegistry(listOf(testConfig())),
            requests = AuthorizationRequestFactory(PkceGenerator(random, FakeSha256()), random, clock),
            pending = pending,
            launcher = launcher,
            exchange = api,
            tokens = failingStore,
            redirectUri = redirect,
            clock = clock,
        )
        c.signIn(spotify)
        val outcome = c.resume(callbackFor())
        val failed = assertIs<AuthOutcome.Failed>(outcome)
        assertEquals(boom, assertIs<AuthFailure.Storage>(failed.reason).cause)
        assertEquals(emptyMap(), pending.sessions)
    }

    // S-FLOW-8d: cancellation from token store is not swallowed
    @Test
    fun tokenStoreCancellationIsNotSwallowed() = runTest {
        launcher.result = LaunchResult.Redirecting
        val failingStore = object : TokenStore {
            override suspend fun save(provider: ProviderId, tokens: TokenSet) = throw CancellationException("c")
            override suspend fun load(provider: ProviderId): TokenSet? = null
            override suspend fun clear(provider: ProviderId) {}
        }
        val c = OAuthClient(
            registry = InMemoryProviderRegistry(listOf(testConfig())),
            requests = AuthorizationRequestFactory(PkceGenerator(random, FakeSha256()), random, clock),
            pending = pending,
            launcher = launcher,
            exchange = api,
            tokens = failingStore,
            redirectUri = redirect,
            clock = clock,
        )
        c.signIn(spotify)
        assertFailsWith<CancellationException> { c.resume(callbackFor()) }
    }

    // S-FLOW-9
    @Test
    fun sessionJustInsideTtlIsAccepted() = runTest {
        launcher.result = LaunchResult.Redirecting
        val c = client()
        c.signIn(spotify)
        clock.advance(9.minutes)
        assertIs<AuthOutcome.Authenticated>(c.resume(callbackFor()))
    }

    // S-FLOW-10
    @Test
    fun providerErrorMapsToProviderDenied() = runTest {
        val c = client()
        launcher.result = LaunchResult.Redirecting
        c.signIn(spotify)
        assertEquals(
            AuthOutcome.Failed(AuthFailure.ProviderDenied("access_denied")),
            c.resume("$redirect?error=access_denied&state=state-1"),
        )
    }

    @Test
    fun providerErrorConsumesPendingSession() = runTest {
        val c = client()
        launcher.result = LaunchResult.Redirecting
        c.signIn(spotify)
        assertEquals(
            AuthOutcome.Failed(AuthFailure.ProviderDenied("access_denied")),
            c.resume("$redirect?error=access_denied&state=state-1"),
        )
        assertEquals(emptyMap(), pending.sessions)
    }

    @Test
    fun providerErrorWithUnknownOrMissingStateIsMismatch() = runTest {
        val c = client()
        launcher.result = LaunchResult.Redirecting
        c.signIn(spotify)
        val expected = AuthOutcome.Failed(AuthFailure.StateMismatch)
        assertEquals(expected, c.resume("$redirect?error=access_denied&state=other"))
        assertEquals(expected, c.resume("$redirect?error=access_denied"))
        assertEquals(1, pending.sessions.size)
    }

    @Test
    fun launcherFailureDeletesPendingSession() = runTest {
        launcher.onLaunch = { throw IllegalStateException("boom") }
        assertFailsWith<IllegalStateException> { client().signIn(spotify) }
        assertEquals(emptyMap(), pending.sessions)
    }

    @Test
    fun secretsAreRedactedInToString() {
        val p = PendingAuthorization(spotify, "st", "SECRETVERIFIER", redirect, clock.now())
        val t = TokenSet("SECRETACCESS", "SECRETREFRESH", 1, "Bearer", "s")
        val e = ExchangeRequest("spotify", "SECRETCODE", "SECRETVERIFIER", redirect)
        val a = AuthOutcome.Authenticated(spotify, t)
        listOf(p, t, e, a).forEach { o ->
            val str = o.toString()
            listOf("SECRET").forEach { assertEquals(false, str.contains(it), str) }
        }
    }

    // S-FLOW-11
    @Test
    fun malformedCallbackIsStateMismatch() = runTest {
        assertEquals(AuthOutcome.Failed(AuthFailure.StateMismatch), client().resume("garbage"))
    }

    // S-FLOW-12
    @Test
    fun backendRejectionMapsToExchangeRejectedAndStoresNothing() = runTest {
        api.behaviour = { ExchangeResult.Rejected(BackendErrorCode.ProviderExchangeFailed, "bad") }
        launcher.result = LaunchResult.Callback(callbackFor())
        assertEquals(
            AuthOutcome.Failed(AuthFailure.ExchangeRejected(BackendErrorCode.ProviderExchangeFailed)),
            client().signIn(spotify),
        )
        assertNull(store.saved[spotify])
    }

    // S-FLOW-13
    @Test
    fun transportExceptionMapsToNetwork() = runTest {
        val boom = IllegalStateException("offline")
        api.behaviour = { throw boom }
        launcher.result = LaunchResult.Callback(callbackFor())
        val outcome = client().signIn(spotify)
        val failed = assertIs<AuthOutcome.Failed>(outcome)
        assertEquals(AuthFailure.Network(boom), failed.reason)
        assertNull(store.saved[spotify])
    }

    // S-FLOW-14
    @Test
    fun cancellationIsNotSwallowed() = runTest {
        api.behaviour = { throw CancellationException("cancelled") }
        launcher.result = LaunchResult.Callback(callbackFor())
        assertFailsWith<CancellationException> { client().signIn(spotify) }
    }

    // S-FLOW-15: provider removed between phases
    @Test
    fun providerRemovedBeforeResumeIsUnknownProvider() = runTest {
        launcher.result = LaunchResult.Redirecting
        client().signIn(spotify)
        val outcome = client(InMemoryProviderRegistry(emptyList())).resume(callbackFor())
        assertEquals(AuthOutcome.Failed(AuthFailure.UnknownProvider), outcome)
    }

    // S-FLOW-16
    @Test
    fun signOutClearsTokens() = runTest {
        store.saved[spotify] = tokens
        client().signOut(spotify)
        assertNull(store.saved[spotify])
    }
}
