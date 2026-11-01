package com.baloomba.oauth.core.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PkceGeneratorTest {
    // S-PKCE-1: RFC 7636 appendix B test vector
    @Test
    fun challengeMatchesRfc7636Vector() {
        val pkce = PkceGenerator(FakeSecureRandom(mutableListOf(RFC_VERIFIER)), FakeSha256()).generate()
        assertEquals(RFC_VERIFIER, pkce.verifier)
        assertEquals(RFC_CHALLENGE, pkce.challenge)
    }

    // S-PKCE-2: verifier is 128 unreserved chars from the random port, hashed as raw bytes
    @Test
    fun requestsA128CharUnreservedVerifier() {
        val random = FakeSecureRandom()
        val sha = FakeSha256()
        val pkce = PkceGenerator(random, sha).generate()
        assertEquals(128, random.stringCalls.single().first)
        assertEquals(PkceGenerator.UNRESERVED, random.stringCalls.single().second)
        assertEquals(pkce.verifier, sha.inputs.single().decodeToString())
    }

    // S-PKCE-3: base64url without padding
    @Test
    fun challengeIsUnpadded() {
        val pkce = PkceGenerator(FakeSecureRandom(), FakeSha256()).generate()
        assertFalse(pkce.challenge.contains('='))
        assertFalse(pkce.challenge.contains('+'))
        assertFalse(pkce.challenge.contains('/'))
        assertEquals(43, pkce.challenge.length)
    }
}
