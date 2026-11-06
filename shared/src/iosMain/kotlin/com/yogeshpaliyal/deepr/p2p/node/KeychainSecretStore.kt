@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.yogeshpaliyal.deepr.p2p.node

import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Pair keys sealed with a random 32-byte key kept in the Keychain as a generic password.
 * `…ThisDeviceOnly`: the item never moves to another device or backup, so a copied DB can't be opened (clone detection).
 */
class KeychainSecretStore(crypto: P2pCrypto) : AesKeySecretStore(crypto, loadOrCreateKey()) {
    internal companion object {
        private const val SERVICE = "com.yogeshpaliyal.deepr.p2p"
        private const val ACCOUNT = "secret-key"
        private const val KEY_LEN = 32

        fun loadOrCreateKey(): ByteArray = readKey() ?: createKey()

        private fun readKey(): ByteArray? =
            memScoped {
                val out = alloc<CFTypeRefVar>()
                val status =
                    withQuery(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) {
                        SecItemCopyMatching(it, out.ptr)
                    }
                if (status != errSecSuccess) return@memScoped null
                (CFBridgingRelease(out.value) as? NSData)?.toByteArray()?.takeIf { it.size == KEY_LEN }
            }

        private fun createKey(): ByteArray {
            val key = ByteArray(KEY_LEN)
            val rc = key.usePinned { SecRandomCopyBytes(kSecRandomDefault, KEY_LEN.convert(), it.addressOf(0)) }
            check(rc == errSecSuccess) { "SecRandomCopyBytes failed: $rc" }
            val data = CFBridgingRetain(key.toNSData())
            val status =
                try {
                    withQuery(
                        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        kSecValueData to data,
                    ) { SecItemAdd(it, null) }
                } finally {
                    CFRelease(data)
                }
            // Another caller may have created it first: use the stored one.
            if (status == errSecDuplicateItem) return readKey() ?: error("Keychain item exists but can't be read")
            check(status == errSecSuccess) { "SecItemAdd failed: $status" }
            return key
        }

        /** Builds class/service/account + [extra] as a CFDictionary, runs [block], releases everything. */
        private fun <T> withQuery(
            vararg extra: Pair<CFStringRef?, CFTypeRef?>,
            block: (CFDictionaryRef?) -> T,
        ): T {
            val service = CFBridgingRetain(SERVICE)
            val account = CFBridgingRetain(ACCOUNT)
            val dict =
                CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            try {
                CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionaryAddValue(dict, kSecAttrService, service)
                CFDictionaryAddValue(dict, kSecAttrAccount, account)
                extra.forEach { (k, v) -> CFDictionaryAddValue(dict, k, v) }
                return block(dict)
            } finally {
                CFRelease(dict)
                CFRelease(service)
                CFRelease(account)
            }
        }

        private fun ByteArray.toNSData(): NSData =
            usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

        private fun NSData.toByteArray(): ByteArray {
            val out = ByteArray(length.toInt())
            if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
            return out
        }
    }
}
