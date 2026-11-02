package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.backendcontract.TokenSet
import com.baloomba.oauth.core.oauth.ProviderId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedTokenStoreTest {
    private val spotify = ProviderId.of("spotify")
    private val google = ProviderId.of("google")
    private val cipher = FakeCipher()
    private val blobs = FakeBlobStore()
    private val store = EncryptedTokenStore(cipher, blobs)
    private val tokens = TokenSet("access-secret", "refresh-secret", 3600, "Bearer", "a b")

    // S-STORE-A1
    @Test
    fun savedTokensRoundTrip() = runTest {
        store.save(spotify, tokens)
        assertEquals(tokens, store.load(spotify))
    }

    // S-STORE-A2
    @Test
    fun loadOfUnknownProviderIsNull() = runTest {
        assertNull(store.load(spotify))
    }

    // S-STORE-A3
    @Test
    fun persistedBytesDoNotContainPlaintextSecrets() = runTest {
        store.save(spotify, tokens)
        val raw = blobs.entries.values.single().decodeToString()
        assertFalse("access-secret" in raw)
        assertFalse("refresh-secret" in raw)
    }

    // S-STORE-A4
    @Test
    fun clearRemovesOnlyThatProvider() = runTest {
        store.save(spotify, tokens)
        store.save(google, tokens.copy(accessToken = "g"))
        store.clear(spotify)
        assertNull(store.load(spotify))
        assertEquals("g", store.load(google)?.accessToken)
    }

    // S-STORE-A5
    @Test
    fun blobCannotBeSwappedBetweenProviders() = runTest {
        store.save(spotify, tokens)
        store.save(google, tokens.copy(accessToken = "g"))
        val spotifyKey = blobs.entries.keys.first { "spotify" in it }
        val googleKey = blobs.entries.keys.first { "google" in it }
        blobs.entries[googleKey] = blobs.entries.getValue(spotifyKey)
        assertNull(store.load(google))
        assertFalse(googleKey in blobs.entries.keys)
    }

    // S-STORE-A6
    @Test
    fun corruptedBlobIsAbsentAndRemoved() = runTest {
        store.save(spotify, tokens)
        val key = blobs.entries.keys.single()
        blobs.entries[key] = byteArrayOf(1, 2, 3)
        assertNull(store.load(spotify))
        assertTrue(blobs.entries.isEmpty())
    }

    // S-STORE-A7
    @Test
    fun undecodablePlaintextIsAbsentAndRemoved() = runTest {
        val key = TokenStoreKeys.blobKey(spotify)
        blobs.put(key, cipher.encrypt("not json".encodeToByteArray(), TokenStoreKeys.associatedData(spotify)))
        assertNull(store.load(spotify))
        assertTrue(blobs.entries.isEmpty())
    }

    // S-STORE-A8
    @Test
    fun encryptFailurePropagatesAndStoresNothing() = runTest {
        cipher.failEncrypt = true
        assertFailsWith<IllegalStateException> { store.save(spotify, tokens) }
        assertTrue(blobs.entries.isEmpty())
    }

    // S-STORE-A9
    @Test
    fun saveOverwritesPreviousTokens() = runTest {
        store.save(spotify, tokens)
        store.save(spotify, tokens.copy(accessToken = "new"))
        assertEquals("new", store.load(spotify)?.accessToken)
    }
}
