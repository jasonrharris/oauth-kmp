# core:storage
Implementations of the `PendingAuthStore` and `TokenStore` ports.
Depends on: `core:oauth`, `core:backend-contract`.

## In-memory (all platforms)
`InMemoryPendingAuthStore` and `InMemoryTokenStore` keep state in process memory. **Not persisted**: use them for tests, previews and web. `take` is an atomic, single-use read-and-delete.

## Secure persistent stores
Common code (`commonMain`) holds the logic: `EncryptedPendingAuthStore` and `EncryptedTokenStore`. They serialize with kotlinx-serialization, encrypt with an AEAD, and bind each ciphertext to its store and logical key (associated data), so blobs cannot be swapped between entries. A corrupted or undecryptable entry is treated as absent and removed. `take` removes the entry before decrypting, so a verifier is never usable twice. Secrets are never logged.

Platform adapters implement two small internal seams, `BlobCipher` and `BlobStore`:

| Platform | Status | Adapter |
|---|---|---|
| Android | Implemented (`AndroidSecureStorage`) | Tink AES-256-GCM (`tink-android`), keyset encrypted by an Android Keystore master key, blobs in a private SharedPreferences file. `EncryptedSharedPreferences` is deprecated and not used. Exclude `oauth_kmp_keyset_prefs` and `oauth_kmp_secure_blobs` from Auto Backup (snippet below). |
| iOS | Pending | Keychain |
| Web | Not planned here | `sessionStorage` or in-memory |

Testing: the common logic is covered by JVM tests with fake cipher/blob store. The Android adapter needs the Android SDK and a device or emulator (Keystore) and is not covered by JVM tests.

## Android: exclude secure storage from backup
The Keystore master key never leaves the device, so restored keyset or blob files are useless and the stored tokens would be lost. Exclude both files (`oauth_kmp_keyset_prefs`, `oauth_kmp_secure_blobs`) from Auto Backup and device transfer. The consuming app must add this; the library cannot.

`AndroidManifest.xml`:
```xml
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ... />
```

`res/xml/backup_rules.xml` (Android 11 and below):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="oauth_kmp_keyset_prefs.xml" />
    <exclude domain="sharedpref" path="oauth_kmp_secure_blobs.xml" />
</full-backup-content>
```

`res/xml/data_extraction_rules.xml` (Android 12+, cloud backup and device transfer):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="oauth_kmp_keyset_prefs.xml" />
        <exclude domain="sharedpref" path="oauth_kmp_secure_blobs.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="oauth_kmp_keyset_prefs.xml" />
        <exclude domain="sharedpref" path="oauth_kmp_secure_blobs.xml" />
    </device-transfer>
</data-extraction-rules>
```

## Android: failure behaviour
`AndroidSecureStorage` refuses a keyset that Tink would store unencrypted (`isUsingKeystore` must be true). If the keyset cannot be opened with a recoverable error (`GeneralSecurityException`, `IOException`, or Keystore unusable), storage and the master key are wiped, a non-secret warning is logged, and opening is retried once. If it still fails, `SecureStorageUnavailableException` is thrown and nothing is left in cleartext. Other exceptions and cancellation propagate without a wipe. The recovery logic lives in common code (`openWithRecovery`) and is JVM-tested.

## Known gaps (tracked for phase 8)
- Abandoned pending-auth entries are never cleaned up.
- `AndroidSecureStorage` needs a factory or singleton to enforce one instance per process.
- Stored payloads have no version field for format migration.
- No rollback protection: an old valid blob can be restored.
- `TokenSet` carries no issue timestamp.
