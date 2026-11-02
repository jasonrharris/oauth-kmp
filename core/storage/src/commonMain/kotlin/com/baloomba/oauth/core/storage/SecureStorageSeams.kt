package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.oauth.ProviderId

/**
 * Encryption seam. Platform adapters implement it: Tink AEAD on Android (authenticated encryption), and
 * [AssociatedDataBoundCipher] on iOS (no cryptography; at-rest protection comes from the Keychain).
 *
 * [decrypt] MUST throw when the ciphertext was produced for different [associatedData]. An AEAD
 * implementation also throws on corrupted ciphertext; the iOS implementation does not authenticate, so
 * corruption there surfaces when the plaintext is decoded, which the stores treat as an absent entry.
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
