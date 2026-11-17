package de.jepfa.yapm.io

import com.google.gson.JsonParser
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.EmpService
import de.jepfa.yapm.util.B64

/**
 * Reads vault metadata needed to recover the master password from an EMP before
 * running the normal import.
 */
object AndroidVaultReader {

    fun recoverMasterPassword(
        jsonText: String,
        emp: Encrypted,
        codeword: Password?,
    ): Password {
        val root = try {
            JsonParser.parseString(jsonText).asJsonObject
        } catch (e: Exception) {
            throw VaultImportException("Not a valid ANOTHERpass vault export", e)
        }

        val saltText = root.get(AndroidVaultFormat.JSON_VAULT_ID)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?: throw VaultImportException("Missing vaultId in export")
        val salt = Key(B64.decodeMime(saltText))
        if (salt.data.isEmpty()) throw VaultImportException("Empty KDF salt in export")

        val cipher = root.get(AndroidVaultFormat.JSON_CIPHER_ALGORITHM)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.let { CipherAlgorithm.valueOf(it) }
            ?: DEFAULT_CIPHER_ALGORITHM

        return EmpService.recoverMasterPassword(emp, salt, cipher, codeword)
    }
}
