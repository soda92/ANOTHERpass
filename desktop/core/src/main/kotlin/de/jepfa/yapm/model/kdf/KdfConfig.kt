package de.jepfa.yapm.model.kdf

import de.jepfa.yapm.service.secret.KdfParameterService.MAX_ARGON_ITERATIONS
import de.jepfa.yapm.service.secret.KdfParameterService.MAX_ARGON_MIB
import de.jepfa.yapm.service.secret.KdfParameterService.MAX_PBKDF_ITERATIONS
import de.jepfa.yapm.service.secret.KdfParameterService.MIN_ARGON_ITERATIONS
import de.jepfa.yapm.service.secret.KdfParameterService.MIN_ARGON_MIB
import de.jepfa.yapm.service.secret.KdfParameterService.MIN_PBKDF_ITERATIONS
import de.jepfa.yapm.util.B64
import java.nio.ByteBuffer

private const val NEW_KDF_ENCODING_DELIMITER = '_'

/**
 * Ported from Android. The `persist(context)` method was dropped; persistence is
 * the storage layer's job. Encoding is byte-compatible with `EncryptedType`
 * payloads produced by the Android app.
 */
data class KdfConfig(
    val kdf: KeyDerivationFunction,
    val iterations: Int,
    val memCostInMiB: Int?,
) {

    fun isArgon2() = kdf != KeyDerivationFunction.BUILT_IN_PBKDF

    /** Leading empty `A` padding is removed. */
    fun toEncodedString(): String {
        val memCostAsBase64Trimmed = if (memCostInMiB != null) fromInt(memCostInMiB) else ""
        return "${kdf.id}${fromInt(iterations)}$NEW_KDF_ENCODING_DELIMITER$memCostAsBase64Trimmed"
    }

    private fun fromInt(i: Int): String {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(i).array()
        return B64.noWrapNoPadding(bytes).trimStart('A')
    }

    companion object {
        fun fromEncodedString(encodedString: String): KdfConfig? {
            val kdf: KeyDerivationFunction
            if (encodedString.contains(NEW_KDF_ENCODING_DELIMITER)) {
                val delimiterIndex = encodedString.indexOf(NEW_KDF_ENCODING_DELIMITER)
                kdf = KeyDerivationFunction.getById(encodedString.first().toString())
                if (kdf.isArgon2()) {
                    val iterationsBase64 = encodedString.substring(1, delimiterIndex)
                    val iterations = toInt(iterationsBase64, MIN_ARGON_ITERATIONS, MAX_ARGON_ITERATIONS) ?: return null
                    val costIndex = delimiterIndex + 1
                    if (costIndex >= encodedString.length) return null
                    val memCostBase64 = encodedString.substring(costIndex)
                    val memCost = toInt(memCostBase64, MIN_ARGON_MIB, MAX_ARGON_MIB) ?: return null
                    return KdfConfig(kdf, iterations, memCost)
                } else {
                    val iterationsBase64 = encodedString.substring(1, delimiterIndex)
                    val iterations = toInt(iterationsBase64, MIN_PBKDF_ITERATIONS, MAX_PBKDF_ITERATIONS) ?: return null
                    return KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, iterations, null)
                }
            } else {
                val iterations = toInt(encodedString, MIN_PBKDF_ITERATIONS, MAX_PBKDF_ITERATIONS) ?: return null
                return KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, iterations, null)
            }
        }

        private fun toInt(base64: String, min: Int, max: Int): Int? {
            return try {
                val base64PaddedTo32 = base64.padStart(6, 'A') // length of 6 is 32 bits
                val bytes = B64.decodeNoWrapNoPadding(base64PaddedTo32)
                if (bytes.size > Int.SIZE_BYTES) {
                    null
                } else {
                    val i = ByteBuffer.wrap(bytes).int
                    if (i > max || i < min) null else i
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
