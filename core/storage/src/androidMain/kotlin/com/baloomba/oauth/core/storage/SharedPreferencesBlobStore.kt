package com.baloomba.oauth.core.storage

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/** [BlobStore] over a private [SharedPreferences] file; blobs are Base64 strings. Blocking I/O runs on [Dispatchers.IO]. */
internal class SharedPreferencesBlobStore(private val prefs: SharedPreferences) : BlobStore {
    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val encoded = prefs.getString(key, null) ?: return@withContext null
        try {
            Base64.decode(encoded)
        } catch (_: IllegalArgumentException) {
            prefs.edit().remove(key).commit()
            null
        }
    }

    override suspend fun put(key: String, value: ByteArray) {
        withContext(Dispatchers.IO) {
            check(prefs.edit().putString(key, Base64.encode(value)).commit()) { "Failed to persist secure blob" }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            check(prefs.edit().remove(key).commit()) { "Failed to remove secure blob" }
        }
    }
}
