package de.jepfa.yapm.model.encrypted

/**
 * IMPORTANT: the enum **order** is part of the wire format (`Encrypted` stores
 * the ordinal). Never reorder or remove entries. Keep in sync with Android.
 */
enum class CipherAlgorithm(
    val keyLength: Int,
    val cipherName: String,
    val keyDerivationAlgorithm: String,
    val gcmSupport: Boolean,
    val integratedIvSupport: Boolean,
    val displayName: String,
) {
    AES_128(128, "AES/GCM/NoPadding", "PBKDF2WithHmacSHA1", true, true, "AES 128"),
    AES_256(256, "AES/GCM/NoPadding", "PBKDF2WithHmacSHA256", true, true, "AES 256"),
    BLOWFISH_128(128, "BLOWFISH/CBC/PKCS5Padding", "PBKDF2WithHmacSHA1", false, false, "Blowfish 128"),
    BLOWFISH_256(256, "BLOWFISH/CBC/PKCS5Padding", "PBKDF2WithHmacSHA1", false, false, "Blowfish 256"),
    CHACHACHA20(256, "ChaCha20/Poly1305/NoPadding", "PBKDF2WithHmacSHA256", false, true, "ChaCha20 256"),
    ;

    /** JCE algorithm label used when rebuilding a [javax.crypto.SecretKey]. */
    val jceKeyAlgorithm: String
        get() = when (this) {
            AES_128, AES_256 -> "AES"
            BLOWFISH_128, BLOWFISH_256 -> "Blowfish"
            CHACHACHA20 -> "ChaCha20"
        }

    /**
     * Candidate JCE transformation names, in preference order. Android's names
     * are tried first; desktop providers may use a slightly different spelling.
     */
    fun jceCipherNames(): List<String> = when (this) {
        AES_128, AES_256 -> listOf("AES/GCM/NoPadding")
        BLOWFISH_128, BLOWFISH_256 -> listOf("Blowfish/CBC/PKCS5Padding")
        CHACHACHA20 -> listOf(
            "ChaCha20-Poly1305",
            "ChaCha20-Poly1305/None/NoPadding",
            "ChaCha20/Poly1305/NoPadding",
        )
    }

    companion object {
        fun supportedValues(): List<CipherAlgorithm> = values().toList()

        fun getPreferredCipher(): CipherAlgorithm = AES_256
    }
}

val DEFAULT_CIPHER_ALGORITHM: CipherAlgorithm = CipherAlgorithm.AES_128
val PREFERRED_CIPHER_ALGORITHM: CipherAlgorithm = CipherAlgorithm.AES_256
