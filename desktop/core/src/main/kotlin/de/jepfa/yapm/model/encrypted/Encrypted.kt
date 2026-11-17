package de.jepfa.yapm.model.encrypted

import de.jepfa.yapm.util.B64

/**
 * Wire format (must stay compatible with Android):
 *
 * `type:ivBase64:dataBase64:cipherOrdinal`
 *
 * with base64 encoded as `NO_WRAP or NO_PADDING`.
 */
data class Encrypted(
    val type: EncryptedType?,
    val iv: ByteArray,
    val data: ByteArray,
    val cipherAlgorithm: CipherAlgorithm,
) {

    fun isType(otherType: EncryptedType.Types): Boolean = type?.type == otherType

    fun isEmpty() = data.isEmpty() && iv.isEmpty()

    fun debugToString(): String =
        "[type=$type, iv=${iv.contentToString()}, data=${data.contentToString()}]"

    fun toBase64String(): String = toBase64String(this)

    fun toBase64(): ByteArray = toBase64(this)

    override fun toString(): String = toBase64String()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Encrypted
        if (type != other.type) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!data.contentEquals(other.data)) return false
        if (cipherAlgorithm != other.cipherAlgorithm) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + cipherAlgorithm.hashCode()
        return result
    }

    companion object {
        private const val BASE64_PAIR_DELIMITER = ':'

        fun empty(): Encrypted =
            Encrypted(null, ByteArray(0), ByteArray(0), DEFAULT_CIPHER_ALGORITHM)

        fun toBase64String(encrypted: Encrypted): String {
            val type = encrypted.type?.serialize() ?: ""
            val iv = B64.noWrapNoPadding(encrypted.iv)
            val data = B64.noWrapNoPadding(encrypted.data)
            return type + BASE64_PAIR_DELIMITER + iv + BASE64_PAIR_DELIMITER + data +
                BASE64_PAIR_DELIMITER + encrypted.cipherAlgorithm.ordinal
        }

        fun fromEncryptedBase64StringWithCheck(string: String): Encrypted? =
            try {
                fromBase64String(string)
            } catch (_: Exception) {
                null
            }

        fun fromBase64String(string: String): Encrypted {
            val splitted = string.split(BASE64_PAIR_DELIMITER)
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val type = splitted[0]
            val iv = B64.decodeNoWrapNoPadding(splitted[1])
            val data = B64.decodeNoWrapNoPadding(splitted[2])
            var algo = DEFAULT_CIPHER_ALGORITHM
            if (splitted.size >= 4) {
                algo = CipherAlgorithm.values()[splitted[3].toInt()]
            }
            return Encrypted(EncryptedType.of(type), iv, data, algo)
        }

        fun toBase64(encrypted: Encrypted): ByteArray =
            toBase64String(encrypted).toByteArray(Charsets.UTF_8)

        fun fromBase64(byteArray: ByteArray): Encrypted =
            fromBase64String(String(byteArray, Charsets.UTF_8))
    }
}
