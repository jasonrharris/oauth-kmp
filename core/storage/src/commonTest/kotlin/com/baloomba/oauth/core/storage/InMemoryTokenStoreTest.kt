package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.backendcontract.TokenSet
import com.baloomba.oauth.core.oauth.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryTokenStoreTest {
    private val spotify = ProviderId.of("spotify")
    private val google = ProviderId.of("google")
    private val a = TokenSet("access-a", "refresh-a", 3600, "Bearer", "s")
    private val b = TokenSet("access-b")

    // S-STORE-8
    @Test
    fun loadReturnsSavedTokens() = runTest {
        val store = InMemoryTokenStore()
        store.save(spotify, a)
        assertEquals(a, store.load(spotify))
    }

    // S-STORE-9
    @Test
    fun loadUnknownProviderReturnsNull() = runTest {
        assertNull(InMemoryTokenStore().load(spotify))
    }

    // S-STORE-10
    @Test
    fun saveOverwritesPreviousTokens() = runTest {
        val store = InMemoryTokenStore()
        store.save(spotify, a)
        store.save(spotify, b)
        assertEquals(b, store.load(spotify))
    }

    // S-STORE-11
    @Test
    fun providersAreIndependent() = runTest {
        val store = InMemoryTokenStore()
        store.save(spotify, a)
        store.save(google, b)
        store.clear(spotify)
        assertNull(store.load(spotify))
        assertEquals(b, store.load(google))
    }

    // S-STORE-12
    @Test
    fun clearUnknownProviderIsNoOp() = runTest {
        val store = InMemoryTokenStore()
        store.save(google, b)
        store.clear(spotify)
        assertEquals(b, store.load(google))
    }

    // S-STORE-13
    @Test
    fun concurrentSavesForDifferentProvidersAreAllRetained() = runTest {
        val store = InMemoryTokenStore()
        val ids = (1..200).map { ProviderId.of("p$it") }
        withContext(Dispatchers.Default) {
            ids.map { id -> async { store.save(id, TokenSet("t-${id.value}")) } }.awaitAll()
        }
        ids.forEach { assertEquals("t-${it.value}", store.load(it)?.accessToken) }
    }
}
