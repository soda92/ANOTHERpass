package de.jepfa.yapm.model.kdf

/** Argon2 variant, decoupled from the Android-only argon2kt enum. */
enum class Argon2Variant { ID, I, D }

/**
 * The [id] strings are persisted inside the encrypted master key type payload
 * and must stay identical to the Android values.
 */
enum class KeyDerivationFunction(
    val id: String,
    val displayName: String,
    val argon2Variant: Argon2Variant?,
) {
    BUILT_IN_PBKDF("p", "PBKDF2", null),
    ARGON2_ID("a", "Argon2id", Argon2Variant.ID),
    ARGON2_I("i", "Argon2i", Argon2Variant.I),
    ARGON2_D("d", "Argon2d", Argon2Variant.D),
    ;

    fun isArgon2() = argon2Variant != null

    companion object {
        fun getById(id: String): KeyDerivationFunction {
            if (id.isEmpty()) return BUILT_IN_PBKDF
            return entries.first { it.id == id }
        }
    }
}

val DEFAULT_KDF: KeyDerivationFunction = KeyDerivationFunction.BUILT_IN_PBKDF
val PREFERRED_KDF: KeyDerivationFunction = KeyDerivationFunction.ARGON2_ID
