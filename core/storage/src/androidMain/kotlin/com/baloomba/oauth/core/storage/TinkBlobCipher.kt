package com.baloomba.oauth.core.storage

import com.google.crypto.tink.Aead

/** Thin adapter from the [BlobCipher] seam to a Tink [Aead]. Tink throws GeneralSecurityException on failure. */
internal class TinkBlobCipher(private val aead: Aead) : BlobCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)
}
