package de.jepfa.yapm.model.secret

import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import javax.crypto.SecretKey

/**
 * Desktop port of the Android holder. The Android `Context` and `AndroidKey`
 * fields were removed; key-store concerns live in `crypto.WrappingKeyStore`.
 */
data class SecretKeyHolder(
    val secretKey: SecretKey,
    val cipherAlgorithm: CipherAlgorithm,
) {
    fun destroy() {
        try {
            secretKey.destroy()
        } catch (_: Exception) {
            // Nothing we can do; mirrors the Android implementation.
        }
    }
}
