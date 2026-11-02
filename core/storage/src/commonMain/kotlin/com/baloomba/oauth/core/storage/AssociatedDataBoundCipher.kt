package com.baloomba.oauth.core.storage

/**
 * [BlobCipher] for platforms whose [BlobStore] is already encrypted at rest by the OS (iOS Keychain).
 *
 * It provides NO confidentiality and does no cryptography: it only enforces the associated-data
 * binding contract of [BlobCipher] by prefixing the blob with `[4-byte big-endian length][associatedData]`
 * and rejecting a blob whose prefix does not match on [decrypt]. This guards against a blob being read
 * back under the wrong logical key, and keeps the Encrypted* stores identical across platforms.
 */
internal class AssociatedDataBoundCipher : BlobCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val n = associatedData.size
        val header = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
        return header + associatedData + plaintext
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        require(ciphertext.size >= HEADER_SIZE) { "Blob too short" }
        var length = 0
        for (i in 0 until HEADER_SIZE) length = (length shl 8) or (ciphertext[i].toInt() and 0xFF)
        require(length == associatedData.size && ciphertext.size - HEADER_SIZE >= length) { "Associated data mismatch" }
        for (i in 0 until length) {
            require(ciphertext[HEADER_SIZE + i] == associatedData[i]) { "Associated data mismatch" }
        }
        return ciphertext.copyOfRange(HEADER_SIZE + length, ciphertext.size)
    }

    private companion object {
        const val HEADER_SIZE = 4
    }
}
