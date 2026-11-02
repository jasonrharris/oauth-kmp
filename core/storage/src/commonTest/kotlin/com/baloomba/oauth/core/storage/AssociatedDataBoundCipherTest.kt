package com.baloomba.oauth.core.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class AssociatedDataBoundCipherTest {
    private val cipher = AssociatedDataBoundCipher()
    private val ad = "oauth-kmp/tokens/spotify".encodeToByteArray()

    // S-STORE-I1
    @Test
    fun roundTripsForSameAssociatedData() {
        val plaintext = "payload".encodeToByteArray()
        assertContentEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext, ad), ad))
    }

    // S-STORE-I1
    @Test
    fun roundTripsEmptyPlaintextAndEmptyAssociatedData() {
        assertContentEquals(byteArrayOf(), cipher.decrypt(cipher.encrypt(byteArrayOf(), byteArrayOf()), byteArrayOf()))
    }

    // S-STORE-I2
    @Test
    fun rejectsDifferentAssociatedData() {
        val blob = cipher.encrypt("payload".encodeToByteArray(), ad)
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(blob, "oauth-kmp/tokens/google".encodeToByteArray()) }
    }

    // S-STORE-I2
    @Test
    fun rejectsAssociatedDataThatIsAPrefixOrExtensionOfTheBoundOne() {
        val blob = cipher.encrypt("payload".encodeToByteArray(), ad)
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(blob, ad.copyOf(ad.size - 1)) }
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(blob, ad + byteArrayOf(1)) }
    }

    // S-STORE-I3
    @Test
    fun rejectsTruncatedOrEmptyBlob() {
        val blob = cipher.encrypt("payload".encodeToByteArray(), ad)
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(byteArrayOf(), ad) }
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(blob.copyOf(3), ad) }
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(blob.copyOf(4 + ad.size - 1), ad) }
    }

    // S-STORE-I3
    @Test
    fun rejectsBlobWithAbsurdLengthHeader() {
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(byteArrayOf(0x7F, 0, 0, 0, 1), ad) }
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(byteArrayOf(-1, -1, -1, -1, 1), ad) }
    }
}
