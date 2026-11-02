package com.baloomba.oauth.core.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KeychainBlobStoreTest {
    private val keychain = FakeKeychainClient()
    private val store = KeychainBlobStore(keychain)
    private val bytes = byteArrayOf(1, 2, 3)

    // S-STORE-I4
    @Test
    fun putThenGetRoundTrips() = runTest {
        store.put("token:spotify", bytes)
        assertContentEquals(bytes, store.get("token:spotify"))
    }

    // S-STORE-I4
    @Test
    fun getOfMissingItemIsNull() = runTest {
        assertNull(store.get("token:spotify"))
    }

    // S-STORE-I5
    @Test
    fun putOverExistingItemUpdatesIt() = runTest {
        store.put("k", bytes)
        store.put("k", byteArrayOf(9))
        assertContentEquals(byteArrayOf(9), store.get("k"))
        assertEquals(1, keychain.addCalls)
    }

    // S-STORE-I5
    @Test
    fun putRetriesAsUpdateWhenAddReportsDuplicateAfterNotFound() = runTest {
        keychain.raceDuplicateOnNextAdd = true
        store.put("k", bytes)
        assertContentEquals(bytes, store.get("k"))
    }

    // S-STORE-I6
    @Test
    fun removeDeletesAndIsIdempotent() = runTest {
        store.put("k", bytes)
        store.remove("k")
        store.remove("k")
        assertNull(store.get("k"))
    }

    // S-STORE-I7
    @Test
    fun unexpectedStatusOnGetThrowsWithOnlyTheStatusNumber() = runTest {
        keychain.items["k"] = bytes
        keychain.forcedStatus = -25308
        val e = assertFailsWith<KeychainException> { store.get("k") }
        assertEquals(-25308, e.status)
        assertEquals("Keychain operation failed (OSStatus -25308)", e.message)
    }

    // S-STORE-I7
    @Test
    fun unexpectedStatusOnPutAddRemoveThrows() = runTest {
        keychain.forcedStatus = -34018
        assertEquals(-34018, assertFailsWith<KeychainException> { store.put("k", bytes) }.status)
        assertEquals(-34018, assertFailsWith<KeychainException> { store.remove("k") }.status)
    }

    // S-STORE-I7
    @Test
    fun failedAddAfterNotFoundThrows() = runTest {
        keychain.addStatus = -108
        assertEquals(-108, assertFailsWith<KeychainException> { store.put("k", bytes) }.status)
    }

    // S-STORE-I7
    @Test
    fun successWithoutDataIsTreatedAsAbsent() = runTest {
        keychain.returnNullDataOnSuccess = true
        keychain.items["k"] = bytes
        assertNull(store.get("k"))
    }

    // S-STORE-I7
    @Test
    fun failedUpdateAfterDuplicateRetryThrows() = runTest {
        keychain.raceDuplicateOnNextAdd = true
        keychain.updateStatusAfterDuplicate = -25308
        assertEquals(-25308, assertFailsWith<KeychainException> { store.put("k", bytes) }.status)
    }

    // S-STORE-I4
    @Test
    fun emptyValueRoundTrips() = runTest {
        store.put("k", byteArrayOf())
        assertContentEquals(byteArrayOf(), store.get("k"))
    }

    // S-STORE-I8
    @Test
    fun keyContainingNulIsRejectedWithoutCallingTheClient() = runTest {
        val key = "a\u0000b"
        assertFailsWith<IllegalArgumentException> { store.put(key, bytes) }
        assertFailsWith<IllegalArgumentException> { store.get(key) }
        assertFailsWith<IllegalArgumentException> { store.remove(key) }
        assertEquals(0, keychain.calls)
    }
}
