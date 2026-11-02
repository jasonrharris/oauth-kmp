package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.oauth.PendingAuthStore
import com.baloomba.oauth.core.oauth.TokenStore

/**
 * Persistent [TokenStore] and [PendingAuthStore] for iOS, backed by the Keychain.
 *
 * Entries are generic-password items under [service]. They use `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`:
 * available to background refresh after the first unlock, not synced through iCloud
 * Keychain and not migrated or restored onto a different device (they can still be part of a same-device
 * encrypted backup). The Keychain encrypts at rest, so values are not additionally
 * encrypted; each blob is still bound to its logical key (see [AssociatedDataBoundCipher]).
 *
 * Keychain items survive app uninstall. Reads before the first unlock fail with an exception that carries
 * only the `OSStatus`; the stored value is left untouched.
 */
public class IosSecureStorage(service: String = DEFAULT_SERVICE) {
    private val cipher: BlobCipher = AssociatedDataBoundCipher()
    private val blobs: BlobStore = KeychainBlobStore(SecItemKeychainClient(service))

    public val tokenStore: TokenStore = EncryptedTokenStore(cipher, blobs)
    public val pendingAuthStore: PendingAuthStore = EncryptedPendingAuthStore(cipher, blobs)

    private companion object {
        const val DEFAULT_SERVICE = "com.baloomba.oauth.kmp.secure-storage"
    }
}
