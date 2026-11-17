package de.jepfa.yapm.crypto

import javax.crypto.SecretKey

/**
 * Abstraction over the platform keystore. On Android this is `AndroidKeyStore`;
 * on Linux it will be Secret Service (libsecret) with a file-backed fallback.
 *
 * All aliases hold AES-128/GCM wrapping keys, matching Android's
 * `SecretService.getAndroidSecretKey` behaviour.
 */
interface WrappingKeyStore {
    fun getOrCreateKey(alias: String): SecretKey
    fun deleteKey(alias: String)
}

/** Keystore aliases, kept identical to the Android names for familiarity. */
object KeyAlias {
    const val TRANSPORT = "YAPM/keyAlias:TRANSPORT"
    const val SALT = "YAPM/keyAlias:SALT"
    const val MASTER_KEY = "YAPM/keyAlias:MK"
    const val MASTER_PASSWORD = "YAPM/keyAlias:MP"
    const val MASTER_PASSWORD_WITH_AUTH = "YAPM/keyAlias:MP_WA"
    const val MASTER_PASSWORD_TOKEN = "YAPM/keyAlias:MPT"
}
