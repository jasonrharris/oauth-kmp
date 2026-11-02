package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.backendcontract.TokenSet
import com.baloomba.oauth.core.oauth.ProviderId
import com.baloomba.oauth.core.oauth.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * [TokenStore] that persists AEAD-encrypted [TokenSet]s in a [BlobStore].
 * A corrupted or undecryptable entry is treated as absent and removed.
 */
internal class EncryptedTokenStore(
    private val cipher: BlobCipher,
    private val blobs: BlobStore,
) : TokenStore {
    private val mutex = Mutex()

    override suspend fun save(provider: ProviderId, tokens: TokenSet) {
        val plaintext = json.encodeToString(TokenSet.serializer(), tokens).encodeToByteArray()
        val blob = cipher.encrypt(plaintext, TokenStoreKeys.associatedData(provider))
        mutex.withLock { blobs.put(TokenStoreKeys.blobKey(provider), blob) }
    }

    override suspend fun load(provider: ProviderId): TokenSet? = mutex.withLock {
        val key = TokenStoreKeys.blobKey(provider)
        val blob = blobs.get(key) ?: return@withLock null
        try {
            val plaintext = cipher.decrypt(blob, TokenStoreKeys.associatedData(provider))
            json.decodeFromString(TokenSet.serializer(), plaintext.decodeToString())
        } catch (_: Exception) {
            blobs.remove(key)
            null
        }
    }

    override suspend fun clear(provider: ProviderId) {
        mutex.withLock { blobs.remove(TokenStoreKeys.blobKey(provider)) }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
