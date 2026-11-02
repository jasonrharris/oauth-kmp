package com.baloomba.oauth.core.storage

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class SecureStorageRecoveryTest {
    private class Recoverable(message: String = "r") : Exception(message)

    private class Harness(private val opens: MutableList<() -> String>) {
        val events = mutableListOf<String>()
        var wipeFails = false
        var deleteFails = false
        var observed: Throwable? = null

        fun run(): String = openWithRecovery(
            open = { events += "open"; opens.removeAt(0)() },
            isRecoverable = { it is Recoverable || it is SecureStorageUnavailableException },
            wipeStorage = { events += "wipe"; if (wipeFails) throw IllegalStateException("wipe boom") },
            deleteMasterKey = { events += "delete"; if (deleteFails) throw IllegalStateException("delete boom") },
            onRecovery = { observed = it },
        )
    }

    private fun fail(t: Throwable): () -> String = { throw t }

    @Test
    fun firstOpenSucceedsWithoutWipe() {
        val h = Harness(mutableListOf({ "aead" }))
        assertEquals("aead", h.run())
        assertEquals(listOf("open"), h.events)
        assertEquals(null, h.observed)
    }

    @Test
    fun firstFailsThenRetryAfterWipeSucceeds() {
        val cause = Recoverable()
        val h = Harness(mutableListOf(fail(cause), { "aead" }))
        assertEquals("aead", h.run())
        assertEquals(listOf("open", "wipe", "delete", "open"), h.events)
        assertSame(cause, h.observed)
    }

    @Test
    fun bothFailThrowsClearlyAndLeavesNothingInCleartext() {
        val h = Harness(mutableListOf(fail(Recoverable("one")), fail(Recoverable("two"))))
        val e = assertFailsWith<SecureStorageUnavailableException> { h.run() }
        assertIs<Recoverable>(e.cause)
        assertEquals(listOf("open", "wipe", "delete", "open", "wipe"), h.events)
    }

    @Test
    fun cleartextKeystoreUnavailableIsWipedAndRetried() {
        val h = Harness(mutableListOf(fail(SecureStorageUnavailableException("no keystore")), { "aead" }))
        assertEquals("aead", h.run())
        assertEquals(listOf("open", "wipe", "delete", "open"), h.events)
    }

    @Test
    fun cleartextKeystoreUnavailableTwiceFailsAndWipes() {
        val h = Harness(
            mutableListOf(
                fail(SecureStorageUnavailableException("no keystore")),
                fail(SecureStorageUnavailableException("no keystore")),
            ),
        )
        assertFailsWith<SecureStorageUnavailableException> { h.run() }
        assertEquals("wipe", h.events.last())
    }

    @Test
    fun wipeFailureAbortsWithoutRetry() {
        val h = Harness(mutableListOf(fail(Recoverable())))
        h.wipeFails = true
        val e = assertFailsWith<SecureStorageUnavailableException> { h.run() }
        assertIs<IllegalStateException>(e.cause)
        assertEquals(listOf("open", "wipe", "delete"), h.events)
    }

    @Test
    fun deleteEntryFailureAbortsWithoutRetry() {
        val h = Harness(mutableListOf(fail(Recoverable())))
        h.deleteFails = true
        assertFailsWith<SecureStorageUnavailableException> { h.run() }
        assertEquals(listOf("open", "wipe", "delete"), h.events)
    }

    @Test
    fun nonRecoverableExceptionIsRethrownAndNotWiped() {
        val bug = IllegalArgumentException("bug")
        val h = Harness(mutableListOf(fail(bug)))
        assertSame(bug, assertFailsWith<IllegalArgumentException> { h.run() })
        assertEquals(listOf("open"), h.events)
    }

    @Test
    fun cancellationIsNeverSwallowedEvenIfPredicateAllowsIt() {
        val c = CancellationException("cancelled")
        val events = mutableListOf<String>()
        val e = assertFailsWith<CancellationException> {
            openWithRecovery(
                open = { throw c },
                isRecoverable = { true },
                wipeStorage = { events += "wipe" },
                deleteMasterKey = { events += "delete" },
                onRecovery = {},
            )
        }
        assertSame(c, e)
        assertEquals(emptyList(), events)
    }
}
