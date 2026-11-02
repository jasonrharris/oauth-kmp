package com.baloomba.oauth.core.storage

import kotlinx.coroutines.CancellationException

/**
 * Secure storage could not be opened safely, for example because the Keystore master key is
 * unusable. The message never contains secrets. No cleartext key material is left behind.
 */
public class SecureStorageUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Opens secure storage; when [open] fails with a recoverable error (per [isRecoverable]) the storage is
 * wiped and [open] is retried exactly once. The stored values are unrecoverable once the master key is
 * lost, so the wipe is silent apart from [onRecovery].
 *
 * - Non-recoverable failures (programmer errors) and cancellation are rethrown untouched and nothing is wiped.
 * - If wiping fails, or the retry fails, [SecureStorageUnavailableException] is thrown and storage is wiped
 *   again on a best-effort basis so no cleartext keyset survives.
 */
internal fun <T> openWithRecovery(
    open: () -> T,
    isRecoverable: (Throwable) -> Boolean,
    wipeStorage: () -> Unit,
    deleteMasterKey: () -> Unit,
    onRecovery: (Throwable) -> Unit,
): T {
    val first = try {
        return open()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!isRecoverable(e)) throw e
        e
    }
    onRecovery(first)
    val wipeFailure = runCatching(wipeStorage).exceptionOrNull()
    val deleteFailure = runCatching(deleteMasterKey).exceptionOrNull()
    (wipeFailure ?: deleteFailure)?.let {
        throw SecureStorageUnavailableException("Secure storage reset failed", it)
    }
    try {
        return open()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        runCatching(wipeStorage)
        throw SecureStorageUnavailableException("Secure storage unavailable after reset", e)
    }
}
