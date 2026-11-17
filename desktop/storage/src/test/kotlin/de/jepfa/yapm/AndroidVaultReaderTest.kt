package de.jepfa.yapm

import com.google.gson.JsonObject
import de.jepfa.yapm.io.AndroidVaultReader
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.service.secret.EmpService
import de.jepfa.yapm.util.B64
import kotlin.test.Test
import kotlin.test.assertContentEquals

class AndroidVaultReaderTest {

    @Test
    fun `recovers a protected EMP using the json salt and codeword`() {
        val salt = Key(ByteArray(16) { it.toByte() })
        val codeword = Password("test-codeword")
        val master = Password("TestMasterPassword16")

        val empKey = CryptoService.generateSecretKeyForObfuscation(
            Password(salt), EmpService.EMP_SALT, CipherAlgorithm.AES_128
        )
        val obfKey = CryptoService.secretKeyToKey(
            CryptoService.generateSecretKeyForObfuscation(
                codeword, salt, CipherAlgorithm.AES_128
            ),
            salt
        )
        val obfuscated = Password(master.toByteArray().copyOf())
        obfuscated.obfuscate(obfKey)
        val emp = CryptoService.encryptPassword(
            EncryptedType(EncryptedType.Types.ENC_MASTER_PASSWD, EmpService.PROTECTED_PAYLOAD),
            empKey,
            obfuscated,
        )

        val json = JsonObject().apply {
            addProperty("vaultId", B64.mime(salt.data))
            addProperty("cipherAlgoritm", "AES_128")
        }.toString()

        val recovered = AndroidVaultReader.recoverMasterPassword(json, emp, codeword)
        assertContentEquals(master.toByteArray(), recovered.toByteArray())
    }
}
