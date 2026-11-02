@file:OptIn(ExperimentalForeignApi::class)

package com.baloomba.oauth.core.storage

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Real `SecItem*` wiring for [KeychainClient]: generic-password items identified by ([service], account).
 *
 * Items are stored with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (readable by background refresh
 * after the first unlock, not migrated or restored onto a different device;
 * they can still be in a same-device encrypted backup) and with
 * `kSecAttrSynchronizable = false` (no iCloud Keychain sync). Every CoreFoundation object created here is
 * released before returning. Only `OSStatus` codes leave this class; no key or value is ever logged.
 */
internal class SecItemKeychainClient(private val service: String) : KeychainClient {
    override fun add(account: String, data: ByteArray): Int = CfScope.use { scope ->
        val attributes = baseQuery(scope, account)
        attributes.set(kSecValueData, scope.own(createData(data)))
        attributes.set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(attributes, null)
    }

    override fun update(account: String, data: ByteArray): Int = CfScope.use { scope ->
        val query = baseQuery(scope, account)
        val changes = scope.newDictionary()
        changes.set(kSecValueData, scope.own(createData(data)))
        SecItemUpdate(query, changes)
    }

    override fun copy(account: String): KeychainReadResult = CfScope.use { scope ->
        val query = baseQuery(scope, account)
        query.set(kSecReturnData, kCFBooleanTrue)
        query.set(kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            result.value = null
            val status = SecItemCopyMatching(query, result.ptr)
            // SecItemCopyMatching follows the "Copy" rule: the returned CFData is ours to release.
            val data: CFDataRef? = result.value?.reinterpret()
            try {
                KeychainReadResult(status, if (status == KeychainStatus.SUCCESS && data != null) readBytes(data) else null)
            } finally {
                if (data != null) CFRelease(data)
            }
        }
    }

    override fun delete(account: String): Int = CfScope.use { scope ->
        SecItemDelete(baseQuery(scope, account))
    }

    private fun baseQuery(scope: CfScope, account: String): CFMutableDictionaryRef {
        val query = scope.newDictionary()
        query.set(kSecClass, kSecClassGenericPassword)
        query.set(kSecAttrService, scope.own(createString(service)))
        query.set(kSecAttrAccount, scope.own(createString(account)))
        query.set(kSecAttrSynchronizable, kCFBooleanFalse)
        return query
    }

    private fun CFMutableDictionaryRef.set(key: CPointer<out CPointed>?, value: CPointer<out CPointed>?) {
        CFDictionaryAddValue(this, key, value)
    }

    private fun createString(value: String) = checkNotNull(CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)) {
        "CFString allocation failed"
    }

    private fun createData(bytes: ByteArray): CFDataRef = checkNotNull(
        if (bytes.isEmpty()) {
            CFDataCreate(kCFAllocatorDefault, null, 0)
        } else {
            bytes.usePinned { CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret(), bytes.size.toLong()) }
        },
    ) { "CFData allocation failed" }

    private fun readBytes(data: CFDataRef): ByteArray {
        val length = CFDataGetLength(data).toInt()
        if (length == 0) return ByteArray(0)
        return checkNotNull(CFDataGetBytePtr(data)) { "CFData has no bytes" }.reinterpret<ByteVar>().readBytes(length)
    }

    /** Owns the CoreFoundation objects created for one Keychain call and releases them all when it ends. */
    private class CfScope {
        private val owned = mutableListOf<CPointer<out CPointed>>()

        fun <T : CPointer<out CPointed>> own(ref: T): T {
            owned += ref
            return ref
        }

        fun newDictionary(): CFMutableDictionaryRef = own(
            checkNotNull(
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ),
            ) { "CFDictionary allocation failed" },
        )

        companion object {
            inline fun <T> use(block: (CfScope) -> T): T {
                val scope = CfScope()
                try {
                    return block(scope)
                } finally {
                    scope.owned.forEach { CFRelease(it) }
                }
            }
        }
    }
}
