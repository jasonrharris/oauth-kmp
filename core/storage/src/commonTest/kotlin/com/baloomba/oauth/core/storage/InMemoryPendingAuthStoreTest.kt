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
import kotlin.test.assertNull
import kotlin.time.Instant

class InMemoryPendingAuthStoreTest {
    private val spotify = ProviderId.of("spotify")

    private fun session(state: String, verifier: String = "v-$state") =
        PendingAuthorization(spotify, state, verifier, "app://cb", Instant.fromEpochSeconds(1))

    // S-STORE-1
    @Test
    fun takeReturnsSavedSession() = runTest {
        val store = InMemoryPendingAuthStore()
        val s = session("a")
        store.save(s)
        assertEquals(s, store.take("a"))
    }

    // S-STORE-2
    @Test
    fun takeIsSingleUse() = runTest {
        val store = InMemoryPendingAuthStore()
        store.save(session("a"))
        store.take("a")
        assertNull(store.take("a"))
    }

    // S-STORE-3
    @Test
    fun takeUnknownStateReturnsNull() = runTest {
        assertNull(InMemoryPendingAuthStore().take("nope"))
    }

    // S-STORE-4
    @Test
    fun savingSameStateOverwrites() = runTest {
        val store = InMemoryPendingAuthStore()
        store.save(session("a", "first"))
        store.save(session("a", "second"))
        assertEquals("second", store.take("a")?.codeVerifier)
        assertNull(store.take("a"))
    }

    // S-STORE-5
    @Test
    fun takeOnlyRemovesRequestedState() = runTest {
        val store = InMemoryPendingAuthStore()
        store.save(session("a"))
        store.save(session("b"))
        store.take("a")
        assertEquals(session("b"), store.take("b"))
    }

    // S-STORE-6
    @Test
    fun concurrentTakeOfSameStateYieldsExactlyOneSession() = runTest {
        val store = InMemoryPendingAuthStore()
        repeat(20) { round ->
            val state = "s$round"
            store.save(session(state))
            val results = withContext(Dispatchers.Default) {
                (1..200).map { async { store.take(state) } }.awaitAll()
            }
            assertEquals(1, results.count { it != null })
        }
    }

    // S-STORE-7
    @Test
    fun concurrentSavesOfDistinctStatesAreAllRetained() = runTest {
        val store = InMemoryPendingAuthStore()
        withContext(Dispatchers.Default) {
            (1..200).map { async { store.save(session("c$it")) } }.awaitAll()
        }
        val taken = (1..200).count { store.take("c$it") != null }
        assertEquals(200, taken)
    }
}
