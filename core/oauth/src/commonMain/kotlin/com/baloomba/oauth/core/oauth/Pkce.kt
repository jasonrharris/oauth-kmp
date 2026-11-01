package com.baloomba.oauth.core.oauth

import kotlin.io.encoding.Base64

public data class PkceChallenge(val verifier: String, val challenge: String)

public class PkceGenerator(
    private val random: SecureRandom,
    private val sha256: Sha256,
) {
    public fun generate(): PkceChallenge {
        val verifier = random.string(VERIFIER_LENGTH, UNRESERVED)
        val digest = sha256.hash(verifier.encodeToByteArray())
        return PkceChallenge(verifier, Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(digest))
    }

    public companion object {
        public const val VERIFIER_LENGTH: Int = 128
        public const val UNRESERVED: String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    }
}
