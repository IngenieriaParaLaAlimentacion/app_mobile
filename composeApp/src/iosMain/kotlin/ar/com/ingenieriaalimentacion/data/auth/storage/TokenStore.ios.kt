package ar.com.ingenieriaalimentacion.data.auth.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS implementation following Apple's official Keychain Services guidance:
 * https://developer.apple.com/documentation/security/keychain-services
 *
 * - Item class: `kSecClassGenericPassword`
 * - Identity: (`kSecAttrService`, `kSecAttrAccount`) tuple
 * - Accessibility: `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — readable in
 *   background after first unlock; bound to this device (excluded from iCloud Keychain
 *   and device-to-device backup migration), which is correct for a session token.
 * - Add path: `SecItemAdd`; on `errSecDuplicateItem` fall back to `SecItemUpdate`.
 *
 * CF bridging pattern adapted from Liftric KVault (MIT-licensed):
 * https://github.com/Liftric/KVault/blob/master/src/iosMain/kotlin/com/liftric/kvault/impl/KVaultImpl.kt
 */
@OptIn(ExperimentalForeignApi::class)
actual class TokenStore {

    actual suspend fun get(): String? {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        try {
            val query = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            return memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                when (status) {
                    errSecSuccess -> {
                        (CFBridgingRelease(result.value) as? NSData)?.let {
                            NSString.create(it, NSUTF8StringEncoding) as String?
                        }
                    }
                    errSecItemNotFound -> null
                    else -> null
                }
            }
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
        }
    }

    actual suspend fun set(value: String) {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val data = CFBridgingRetain(
            NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding),
        )
        try {
            val addQuery = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
            val addStatus = SecItemAdd(addQuery, null)
            if (addStatus == errSecDuplicateItem) {
                val matchQuery = createDict(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to service,
                    kSecAttrAccount to account,
                )
                val attributesToUpdate = createDict(
                    kSecValueData to data,
                )
                SecItemUpdate(matchQuery, attributesToUpdate)
            }
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
            CFBridgingRelease(data)
        }
    }

    actual suspend fun clear() {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        try {
            val query = createDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
            )
            SecItemDelete(query)
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
        }
    }

    private fun createDict(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, pairs.size.convert(), null, null)
        pairs.forEach { (k, v) -> CFDictionaryAddValue(dict, k, v) }
        return dict?.also { CFAutorelease(it) }
    }

    private companion object {
        const val SERVICE = "ar.com.ingenieriaalimentacion.auth"
        const val ACCOUNT = "session"
    }
}
