package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.oauth.ProviderId

/**
 * Authenticated encryption seam. Platform adapters (Tink on Android) implement it.
 *
 * [decrypt] MUST throw when the ciphertext is corrupted, was produced for different
 * [associatedData], or cannot be decrypted for any other reason.
 */
internal interface BlobCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

/** Durable private key-value storage for opaque (already encrypted) blobs. */
internal interface BlobStore {
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, value: ByteArray)
    suspend fun remove(key: String)
}

/** Storage keys and associated-data bindings, so a ciphertext only decrypts for its own entry. */
internal object TokenStoreKeys {
    fun blobKey(provider: ProviderId): String = "token:${provider.value}"
    fun associatedData(provider: ProviderId): ByteArray = "oauth-kmp/tokens/${provider.value}".encodeToByteArray()
}

internal object PendingAuthStoreKeys {
    fun blobKey(state: String): String = "pending:$state"
    fun associatedData(state: String): ByteArray = "oauth-kmp/pending/$state".encodeToByteArray()
}
