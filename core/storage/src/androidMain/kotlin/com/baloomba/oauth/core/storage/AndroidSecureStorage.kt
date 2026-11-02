package com.baloomba.oauth.core.storage

import android.content.Context
import android.util.Log
import com.baloomba.oauth.core.oauth.PendingAuthStore
import com.baloomba.oauth.core.oauth.TokenStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * Persistent, encrypted [TokenStore] and [PendingAuthStore] for Android.
 *
 * Values are encrypted with AES-256-GCM through Tink. The Tink keyset is itself encrypted by a
 * master key that lives in the Android Keystore and never leaves it. Each ciphertext is bound
 * (as associated data) to its store and logical key. An entry that cannot be decrypted is
 * treated as absent and deleted.
 *
 * Construction touches the Keystore and disk: create it off the main thread and once per process.
 * Exclude the files `oauth_kmp_keyset_prefs` and `oauth_kmp_secure_blobs` from Auto Backup.
 */
public class AndroidSecureStorage(context: Context, masterKeyAlias: String = DEFAULT_MASTER_KEY_ALIAS) {
    private val cipher: BlobCipher
    private val blobs: BlobStore

    init {
        val appContext = context.applicationContext
        cipher = TinkBlobCipher(openAead(appContext, masterKeyAlias))
        blobs = SharedPreferencesBlobStore(appContext.getSharedPreferences(BLOBS_PREFS, Context.MODE_PRIVATE))
    }

    public val tokenStore: TokenStore = EncryptedTokenStore(cipher, blobs)
    public val pendingAuthStore: PendingAuthStore = EncryptedPendingAuthStore(cipher, blobs)

    private companion object {
        const val TAG = "OAuthSecureStorage"
        const val DEFAULT_MASTER_KEY_ALIAS = "oauth_kmp_master_key"
        const val KEYSET_NAME = "oauth_kmp_keyset"
        const val KEYSET_PREFS = "oauth_kmp_keyset_prefs"
        const val BLOBS_PREFS = "oauth_kmp_secure_blobs"

        fun openAead(context: Context, alias: String): Aead {
            AeadConfig.register()
            return openWithRecovery(
                open = { buildAead(context, alias) },
                isRecoverable = { it is GeneralSecurityException || it is IOException || it is SecureStorageUnavailableException },
                wipeStorage = {
                    // commit() returns false on a failed write; a surviving (possibly cleartext) keyset must not be ignored.
                    check(context.getSharedPreferences(KEYSET_PREFS, Context.MODE_PRIVATE).edit().clear().commit()) {
                        "Failed to clear keyset prefs"
                    }
                    check(context.getSharedPreferences(BLOBS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()) {
                        "Failed to clear blob prefs"
                    }
                },
                deleteMasterKey = { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) },
                onRecovery = { Log.w(TAG, "Secure storage reset after ${it::class.java.simpleName}; stored values were discarded") },
            )
        }

        fun buildAead(context: Context, alias: String): Aead {
            val manager = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://$alias")
                .build()
            // Tink may fall back to a CLEARTEXT keyset when the Keystore master key is unusable. Never accept that.
            if (!manager.isUsingKeystore) {
                throw SecureStorageUnavailableException("Android Keystore master key is not usable")
            }
            return manager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }
}
