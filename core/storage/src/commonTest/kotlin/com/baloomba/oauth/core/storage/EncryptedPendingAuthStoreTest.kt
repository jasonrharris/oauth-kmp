package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.oauth.PendingAuthorization
import com.baloomba.oauth.core.oauth.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EncryptedPendingAuthStoreTest {
    private val spotify = ProviderId.of("spotify")
    private val cipher = FakeCipher()
    private val blobs = FakeBlobStore()
    private val store = EncryptedPendingAuthStore(cipher, blobs)

    private fun session(state: String) =
        PendingAuthorization(spotify, state, "verifier-$state", "app://cb", Instant.fromEpochSeconds(1_700_000_000, 123))

    // S-STORE-A10
    @Test
    fun takeReturnsSavedSessionLosslessly() = runTest {
        val s = session("a")
        store.save(s)
        assertEquals(s, store.take("a"))
    }

    @Test
    fun takeSurfacesFailedDeleteInsteadOfReturningUsableVerifier() = runTest {
        store.save(session("a"))
        blobs.failRemove = true
        assertFailsWith<IllegalStateException> { store.take("a") }
    }

    // S-STORE-A11
    @Test
    fun takeIsSingleUse() = runTest {
        store.save(session("a"))
        store.take("a")
        assertNull(store.take("a"))
        assertTrue(blobs.entries.isEmpty())
    }

    // S-STORE-A12
    @Test
    fun takeOfUnknownStateIsNull() = runTest {
        assertNull(store.take("nope"))
    }

    // S-STORE-A13
    @Test
    fun concurrentTakeYieldsExactlyOneWinner() = runTest {
        store.save(session("a"))
        val results = withContext(Dispatchers.Default) {
            (1..20).map { async { store.take("a") } }.awaitAll()
        }
        assertEquals(1, results.count { it != null })
    }

    // S-STORE-A14
    @Test
    fun corruptedBlobIsAbsentAndRemoved() = runTest {
        store.save(session("a"))
        blobs.entries[blobs.entries.keys.single()] = byteArrayOf(9, 9)
        assertNull(store.take("a"))
        assertTrue(blobs.entries.isEmpty())
    }

    // S-STORE-A15
    @Test
    fun blobCannotBeSwappedBetweenStates() = runTest {
        store.save(session("a"))
        store.save(session("b"))
        val keyA = blobs.entries.keys.first { it.endsWith("a") }
        val keyB = blobs.entries.keys.first { it.endsWith("b") }
        blobs.entries[keyB] = blobs.entries.getValue(keyA)
        assertNull(store.take("b"))
        assertEquals(session("a"), store.take("a"))
    }

    // S-STORE-A16
    @Test
    fun pendingAndTokenBlobsUseDistinctBindings() = runTest {
        store.save(session("spotify"))
        val tokenStore = EncryptedTokenStore(cipher, blobs)
        val pendingBlob = blobs.entries.getValue(blobs.entries.keys.single())
        blobs.entries[TokenStoreKeys.blobKey(spotify)] = pendingBlob
        assertNull(tokenStore.load(spotify))
    }

    // S-STORE-A17
    @Test
    fun encryptFailurePropagatesAndStoresNothing() = runTest {
        cipher.failEncrypt = true
        assertFailsWith<IllegalStateException> { store.save(session("a")) }
        assertTrue(blobs.entries.isEmpty())
    }

    // S-STORE-A18
    @Test
    fun savingSameStateOverwrites() = runTest {
        store.save(session("a"))
        val updated = session("a").copy(codeVerifier = "other")
        store.save(updated)
        assertEquals(updated, store.take("a"))
    }
}
