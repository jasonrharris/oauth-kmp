package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.oauth.PendingAuthStore
import com.baloomba.oauth.core.oauth.PendingAuthorization
import com.baloomba.oauth.core.oauth.ProviderId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** Storage-only DTO, so `core:oauth` public API stays free of serialization concerns. */
@Serializable
internal class PendingAuthorizationDto(
    val provider: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val createdAt: String,
) {
    fun toDomain(): PendingAuthorization =
        PendingAuthorization(ProviderId.of(provider), state, codeVerifier, redirectUri, Instant.parse(createdAt))

    companion object {
        fun from(s: PendingAuthorization) =
            PendingAuthorizationDto(s.provider.value, s.state, s.codeVerifier, s.redirectUri, s.createdAt.toString())
    }
}

/**
 * [PendingAuthStore] that persists encrypted sessions in a [BlobStore] through a [BlobCipher].
 * [take] is an atomic read-and-delete: the entry is removed before it is decrypted, so a
 * corrupted entry is also consumed and a verifier can never be used twice.
 */
internal class EncryptedPendingAuthStore(
    private val cipher: BlobCipher,
    private val blobs: BlobStore,
) : PendingAuthStore {
    private val mutex = Mutex()

    override suspend fun save(session: PendingAuthorization) {
        val plaintext = json.encodeToString(PendingAuthorizationDto.serializer(), PendingAuthorizationDto.from(session))
            .encodeToByteArray()
        val blob = cipher.encrypt(plaintext, PendingAuthStoreKeys.associatedData(session.state))
        mutex.withLock { blobs.put(PendingAuthStoreKeys.blobKey(session.state), blob) }
    }

    override suspend fun take(state: String): PendingAuthorization? = mutex.withLock {
        val key = PendingAuthStoreKeys.blobKey(state)
        val blob = blobs.get(key) ?: return@withLock null
        blobs.remove(key)
        try {
            val plaintext = cipher.decrypt(blob, PendingAuthStoreKeys.associatedData(state))
            val dto = json.decodeFromString(PendingAuthorizationDto.serializer(), plaintext.decodeToString())
            dto.toDomain().takeIf { it.state == state }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
