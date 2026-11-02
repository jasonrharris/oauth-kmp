package com.baloomba.oauth.core.storage

/** Reversible fake cipher that authenticates the associated data like an AEAD would. */
internal class FakeCipher : BlobCipher {
    var failEncrypt: Boolean = false

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        check(!failEncrypt) { "encrypt failed" }
        return tag(associatedData) + xor(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        val expected = tag(associatedData)
        require(ciphertext.size >= expected.size && ciphertext.copyOfRange(0, expected.size).contentEquals(expected)) {
            "authentication failed"
        }
        return ciphertext.copyOfRange(expected.size, ciphertext.size).let(::xor)
    }

    private fun xor(b: ByteArray) = ByteArray(b.size) { (b[it].toInt() xor 0x5A).toByte() }

    private fun tag(associatedData: ByteArray): ByteArray =
        byteArrayOf(associatedData.contentHashCode().toByte(), associatedData.size.toByte(), 0x7F)
}

internal class FakeBlobStore : BlobStore {
    val entries = mutableMapOf<String, ByteArray>()
    var failRemove: Boolean = false

    override suspend fun get(key: String): ByteArray? = entries[key]
    override suspend fun put(key: String, value: ByteArray) {
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        check(!failRemove) { "remove failed" }
        entries.remove(key)
    }
}
