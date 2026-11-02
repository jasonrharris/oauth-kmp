package com.baloomba.oauth.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a Keychain read: an `OSStatus` and, when it is success, the stored bytes. */
internal class KeychainReadResult(val status: Int, val data: ByteArray?)

/**
 * Thin seam over the Keychain `SecItem*` calls, keyed by account (the blob key). Every call returns the raw
 * `OSStatus`; the mapping of statuses to behaviour lives in [KeychainBlobStore] and is JVM-tested.
 */
internal interface KeychainClient {
    fun add(account: String, data: ByteArray): Int
    fun update(account: String, data: ByteArray): Int
    fun copy(account: String): KeychainReadResult
    fun delete(account: String): Int
}

/** The `OSStatus` values the store reacts to (from `Security/SecBase.h`). */
internal object KeychainStatus {
    const val SUCCESS = 0
    const val DUPLICATE_ITEM = -25299
    const val ITEM_NOT_FOUND = -25300
}

/** A Keychain call failed. Carries only the `OSStatus`; never any key or value. */
internal class KeychainException(val status: Int) : Exception("Keychain operation failed (OSStatus $status)")

/** [BlobStore] over a [KeychainClient]. The Keychain provides encryption at rest; see [AssociatedDataBoundCipher]. */
internal class KeychainBlobStore(private val keychain: KeychainClient) : BlobStore {
    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.Default) {
        requireValidKey(key)
        val result = keychain.copy(key)
        when (result.status) {
            KeychainStatus.SUCCESS -> result.data
            KeychainStatus.ITEM_NOT_FOUND -> null
            else -> throw KeychainException(result.status)
        }
    }

    override suspend fun put(key: String, value: ByteArray) {
        withContext(Dispatchers.Default) {
            requireValidKey(key)
            when (val updated = keychain.update(key, value)) {
                KeychainStatus.SUCCESS -> Unit
                KeychainStatus.ITEM_NOT_FOUND -> addOrUpdate(key, value)
                else -> throw KeychainException(updated)
            }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.Default) {
            requireValidKey(key)
            val status = keychain.delete(key)
            if (status != KeychainStatus.SUCCESS && status != KeychainStatus.ITEM_NOT_FOUND) throw KeychainException(status)
        }
    }

    // CFStringCreateWithCString stops at NUL, so such a key could alias a different item.
    private fun requireValidKey(key: String) {
        require('\u0000' !in key) { "Keychain key must not contain NUL" }
    }

    private fun addOrUpdate(key: String, value: ByteArray) {
        when (val added = keychain.add(key, value)) {
            KeychainStatus.SUCCESS -> Unit
            // Lost a race with a concurrent writer between update and add: the item exists now, so update it.
            KeychainStatus.DUPLICATE_ITEM -> keychain.update(key, value).let {
                if (it != KeychainStatus.SUCCESS) throw KeychainException(it)
            }
            else -> throw KeychainException(added)
        }
    }
}
