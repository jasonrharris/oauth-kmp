package com.baloomba.oauth.core.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthorizationRequestFactoryTest {
    private val clock = FakeClock()

    private fun factory(random: FakeSecureRandom = FakeSecureRandom(mutableListOf(RFC_VERIFIER, "state-1"))) =
        AuthorizationRequestFactory(PkceGenerator(random, FakeSha256()), random, clock)

    // S-URL-1
    @Test
    fun urlContainsAllRequiredParameters() {
        val request = factory().create(testConfig(), "myapp://callback")
        val expected = "https://accounts.example.com/authorize?response_type=code&client_id=client-123" +
            "&redirect_uri=myapp%3A%2F%2Fcallback&scope=user-read-email%20playlist-read&state=state-1" +
            "&code_challenge=$RFC_CHALLENGE&code_challenge_method=S256"
        assertEquals(expected, request.url)
    }

    // S-URL-2
    @Test
    fun pendingSessionCarriesVerifierStateAndTimestamp() {
        val request = factory().create(testConfig(), "myapp://callback")
        assertEquals(
            PendingAuthorization(ProviderId.of("spotify"), "state-1", RFC_VERIFIER, "myapp://callback", clock.now),
            request.pending,
        )
    }

    // S-URL-3
    @Test
    fun extraParamsAreAppendedAndEncoded() {
        val request = factory().create(testConfig(extra = mapOf("show_dialog" to "true", "login hint" to "a&b")), "r://x")
        assertTrue(request.url.endsWith("&show_dialog=true&login%20hint=a%26b"), request.url)
    }

    // S-URL-4
    @Test
    fun existingQueryInEndpointIsExtended() {
        val request = factory().create(testConfig(endpoint = "https://a.test/auth?tenant=1"), "r://x")
        assertTrue(request.url.startsWith("https://a.test/auth?tenant=1&response_type=code"), request.url)
    }

    // S-URL-5
    @Test
    fun emptyScopesOmitsScopeParameter() {
        val request = factory().create(testConfig(scopes = emptyList()), "r://x")
        assertTrue("scope=" !in request.url)
    }

    // S-URL-6
    @Test
    fun eachRequestGetsFreshState() {
        val f = factory(FakeSecureRandom())
        assertNotEquals(f.create(testConfig(), "r://x").pending.state, f.create(testConfig(), "r://x").pending.state)
    }

    // S-URL-7
    @Test
    fun percentEncodingUsesUppercaseHexAndKeepsUnreserved() {
        assertEquals("a-b.c_d~E9", UrlEncoding.encode("a-b.c_d~E9"))
        assertEquals("%C3%A9%20%2F", UrlEncoding.encode("é /"))
    }
}
