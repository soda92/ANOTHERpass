package de.jepfa.yapm.service.secret

import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password

/**
 * Desktop port of the Android EMP (Encrypted Master Password) handling from
 * `MasterPasswordService` / `ExportEncMasterPasswordUseCase`.
 *
 * The EMP is pseudo-encrypted with a key derived from the vault salt and a fixed
 * app salt. If the type payload is "protected", the master password was
 * additionally obfuscated with a user-chosen codeword before encryption.
 */
object EmpService {

    const val PROTECTED_PAYLOAD = "protected"

    // Must match MasterPasswordService.EMP_SALT in the Android app.
    val EMP_SALT = Key(
        byteArrayOf(
            12, 57, 33, 75, 22, -33, 1, 123, -72, -82, 42, 100,
            -18, 54, 92, 23, -89, -21, -1, 95, -51, 11, 4, -99,
        )
    )

    fun isEmp(encrypted: Encrypted): Boolean =
        encrypted.isType(EncryptedType.Types.ENC_MASTER_PASSWD)

    fun isProtected(encrypted: Encrypted): Boolean =
        isEmp(encrypted) && encrypted.type?.payload == PROTECTED_PAYLOAD

    /**
     * Decrypts the EMP and, if protected, deobfuscates it with [codeword].
     * Throws if an EMP is protected but no codeword is supplied.
     */
    fun recoverMasterPassword(
        emp: Encrypted,
        vaultSalt: Key,
        cipherAlgorithm: CipherAlgorithm,
        codeword: Password? = null,
    ): Password {
        require(isEmp(emp)) { "QR data is not an EMP" }

        val empKey = CryptoService.generateSecretKeyForObfuscation(
            Password(vaultSalt),
            EMP_SALT,
            emp.cipherAlgorithm,
        )
        val masterPassword = CryptoService.decryptPassword(empKey, emp)
        if (!masterPassword.isValid()) {
            return masterPassword
        }

        if (isProtected(emp)) {
            val key = requireNotNull(codeword) {
                "The EMP is protected; a codeword is required"
            }
            val obfuscationSK = CryptoService.generateSecretKeyForObfuscation(
                key,
                vaultSalt,
                cipherAlgorithm,
            )
            masterPassword.deobfuscate(CryptoService.secretKeyToKey(obfuscationSK, vaultSalt))
        }

        return masterPassword
    }

    /**
     * A generated pseudo-phrase is 4-character words with a strict
     * vocal/consonant grammar. A wrong codeword produces a 32-character string
     * that almost never passes this test, which makes it a useful check in the
     * import UI.
     */
    fun looksLikePseudoPhrase(password: Password): Boolean {
        val bytes = password.toByteArray()
        if (bytes.isEmpty() || bytes.size % 4 != 0) return false
        val vocals = "aeiouy"

        var i = 0
        while (i < bytes.size) {
            val w0 = (bytes[i].toInt() and 0xFF).toChar()
            val w1 = (bytes[i + 1].toInt() and 0xFF).toChar()
            val w2 = (bytes[i + 2].toInt() and 0xFF).toChar()
            val w3 = (bytes[i + 3].toInt() and 0xFF).toChar()
            if (!isLower(w0) || !isLower(w1) || !isLower(w2) || !isLower(w3)) return false

            if (vocals.indexOf(w0) >= 0) {
                if (!isLower(w1)) return false
            } else if (vocals.indexOf(w1) < 0) {
                return false
            }
            val recentEndsWithVocal = vocals.indexOf(w1) >= 0

            if (vocals.indexOf(w2) >= 0) {
                if (!isLower(w3)) return false
            } else if (vocals.indexOf(w3) < 0 && !(recentEndsWithVocal && w3 == w2)) {
                return false
            }
            i += 4
        }
        return true
    }

    private fun isLower(c: Char) = c in 'a'..'z'
}
