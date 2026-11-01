package com.baloomba.oauth.core.oauth

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSha256PkceTest {
    // S-PKCE-1 with a real SHA-256 so the vector is not circular
    @Test
    fun realSha256ReproducesRfc7636Vector() {
        val sha = object : Sha256 {
            override fun hash(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
        }
        val pkce = PkceGenerator(FakeSecureRandom(mutableListOf(RFC_VERIFIER)), sha).generate()
        assertEquals(RFC_CHALLENGE, pkce.challenge)
    }
}
