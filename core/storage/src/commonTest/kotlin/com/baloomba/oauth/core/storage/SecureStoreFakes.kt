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

/** In-memory fake of the Keychain with the real OSStatus semantics for the codes the store cares about. */
internal class FakeKeychainClient : KeychainClient {
    val items = mutableMapOf<String, ByteArray>()
    var forcedStatus: Int? = null
    var addStatus: Int? = null
    var raceDuplicateOnNextAdd: Boolean = false
    var returnNullDataOnSuccess: Boolean = false
    var addCalls: Int = 0
    var calls: Int = 0
    var updateStatusAfterDuplicate: Int? = null
    private var pendingUpdateStatus: Int? = null

    override fun add(account: String, data: ByteArray): Int {
        calls++
        forcedStatus?.let { return it }
        addCalls++
        addStatus?.let { return it }
        if (raceDuplicateOnNextAdd) {
            raceDuplicateOnNextAdd = false
            items[account] = byteArrayOf()
            pendingUpdateStatus = updateStatusAfterDuplicate
            return KeychainStatus.DUPLICATE_ITEM
        }
        if (account in items) return KeychainStatus.DUPLICATE_ITEM
        items[account] = data
        return KeychainStatus.SUCCESS
    }

    override fun update(account: String, data: ByteArray): Int {
        calls++
        pendingUpdateStatus?.let { return it }
        forcedStatus?.let { return it }
        if (account !in items) return KeychainStatus.ITEM_NOT_FOUND
        items[account] = data
        return KeychainStatus.SUCCESS
    }

    override fun copy(account: String): KeychainReadResult {
        calls++
        forcedStatus?.let { return KeychainReadResult(it, null) }
        val data = items[account] ?: return KeychainReadResult(KeychainStatus.ITEM_NOT_FOUND, null)
        return KeychainReadResult(KeychainStatus.SUCCESS, if (returnNullDataOnSuccess) null else data)
    }

    override fun delete(account: String): Int {
        calls++
        forcedStatus?.let { return it }
        return if (items.remove(account) != null) KeychainStatus.SUCCESS else KeychainStatus.ITEM_NOT_FOUND
    }
}
