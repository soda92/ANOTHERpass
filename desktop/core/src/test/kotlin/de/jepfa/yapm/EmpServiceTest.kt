package de.jepfa.yapm

import de.jepfa.yapm.crypto.InMemoryWrappingKeyStore
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.service.secret.EmpService
import de.jepfa.yapm.service.secret.StoredMasterPasswordService
import de.jepfa.yapm.service.secretgenerator.password.PasswordGenerator
import de.jepfa.yapm.service.secretgenerator.password.PasswordGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGenerator
import de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.SecretStrength
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmpServiceTest {

    private val vaultSalt = Key(ByteArray(16) { it.toByte() })
    private val codeword = Password("test-codeword")
    private val masterPassword = Password("TestMasterPassword16")

    private fun createEmp(protected: Boolean): Encrypted {
        val empKey = CryptoService.generateSecretKeyForObfuscation(
            Password(vaultSalt), EmpService.EMP_SALT, CipherAlgorithm.AES_128
        )
        val payload = if (protected) EmpService.PROTECTED_PAYLOAD else null
        val type = EncryptedType(EncryptedType.Types.ENC_MASTER_PASSWD, payload)

        val plaintext = if (protected) {
            val obfSK = CryptoService.generateSecretKeyForObfuscation(
                codeword, vaultSalt, CipherAlgorithm.AES_128
            )
            val obfKey = CryptoService.secretKeyToKey(obfSK, vaultSalt)
            Password(masterPassword.toByteArray().copyOf()).also { it.obfuscate(obfKey) }
        } else {
            Password(masterPassword.toByteArray().copyOf())
        }

        return CryptoService.encryptPassword(type, empKey, plaintext)
    }

    @Test
    fun `protected EMP round trips with the codeword`() {
        val emp = createEmp(protected = true)
        assertTrue(EmpService.isEmp(emp))
        assertTrue(EmpService.isProtected(emp))

        val recovered = EmpService.recoverMasterPassword(
            emp, vaultSalt, CipherAlgorithm.AES_128, codeword
        )
        assertContentEquals(masterPassword.toByteArray(), recovered.toByteArray())
    }

    @Test
    fun `unprotected EMP does not need a codeword`() {
        val emp = createEmp(protected = false)
        assertFalse(EmpService.isProtected(emp))

        val recovered = EmpService.recoverMasterPassword(
            emp, vaultSalt, CipherAlgorithm.AES_128, null
        )
        assertContentEquals(masterPassword.toByteArray(), recovered.toByteArray())
    }

    @Test
    fun `wrong codeword produces a different master password`() {
        val emp = createEmp(protected = true)
        val recovered = EmpService.recoverMasterPassword(
            emp, vaultSalt, CipherAlgorithm.AES_128, Password("wrong-codeword")
        )
        assertFalse(masterPassword.toByteArray().contentEquals(recovered.toByteArray()))
    }

    @Test
    fun `pseudo phrase shape check accepts generated phrases and rejects random strings`() {
        val generator = PassphraseGenerator()
        val phrase = generator.generate(PassphraseGeneratorSpec(strength = SecretStrength.HYPER))
        assertTrue(EmpService.looksLikePseudoPhrase(phrase))

        val random = Password("bcdfghjklmnpqrstvwxzbcdfghjklm")
        assertFalse(EmpService.looksLikePseudoPhrase(random))

        val passwordGenerator = PasswordGenerator()
        val ordinary = passwordGenerator.generate(
            PasswordGeneratorSpec(strength = SecretStrength.STRONG)
        )
        assertFalse(EmpService.looksLikePseudoPhrase(ordinary))
    }

    @Test
    fun `password obfuscation round trips`() {
        val key = Key(ByteArray(32) { (it * 7 + 3).toByte() })
        val original = Password("abcdefghijklmnopqrstuvwxyz123456")
        val copy = Password(original.toByteArray().copyOf())

        copy.obfuscate(key)
        assertFalse(original.toByteArray().contentEquals(copy.toByteArray()))
        copy.deobfuscate(key)

        assertContentEquals(original.toByteArray(), copy.toByteArray())
    }
}

class StoredMasterPasswordServiceTest {

    @Test
    fun `stores and reveals the master password`() {
        val keyStore = InMemoryWrappingKeyStore()
        val master = Password("MasterPassword123")

        val encrypted = StoredMasterPasswordService.protect(master, keyStore)
        val revealed = StoredMasterPasswordService.reveal(encrypted, keyStore)

        assertContentEquals(master.toByteArray(), revealed.toByteArray())
    }

    @Test
    fun `forgetting the keystore key makes the stored value unreadable`() {
        val keyStore = InMemoryWrappingKeyStore()
        val master = Password("MasterPassword123")
        val encrypted = StoredMasterPasswordService.protect(master, keyStore)

        StoredMasterPasswordService.forget(keyStore)
        val revealed = StoredMasterPasswordService.reveal(encrypted, keyStore)

        assertFalse(revealed.isValid())
    }
}
