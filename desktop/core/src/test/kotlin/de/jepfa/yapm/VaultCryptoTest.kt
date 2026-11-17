package de.jepfa.yapm

import de.jepfa.yapm.crypto.InMemoryWrappingKeyStore
import de.jepfa.yapm.crypto.KeyAlias
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.MasterKeyService
import de.jepfa.yapm.vault.VaultCrypto
import de.jepfa.yapm.vault.VaultKeyMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultCryptoTest {

    private fun pbkdf(iterations: Int = 10_000) =
        KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, iterations, null)

    private fun argon2id() =
        KdfConfig(KeyDerivationFunction.ARGON2_ID, 1, 12)

    @Test
    fun `create and unlock a pbkdf vault`() {
        val store = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password("123456"), Password("master-pass"), pbkdf(),
            CipherAlgorithm.AES_256, store,
        )

        val encrypted = session.encryptString("{\"name\":\"example\"}")
        session.close()

        val unlocked = assertNotNull(
            VaultCrypto.unlock(Password("123456"), Password("master-pass"), material, store)
        )
        assertEquals("{\"name\":\"example\"}", unlocked.decryptString(encrypted))
        assertTrue(unlocked.fingerprint().matches(Regex("[A-Z]{2}-[A-Z]{3}-[A-Z]{2}")))
        unlocked.close()
    }

    @Test
    fun `wrong pin or password does not unlock`() {
        val store = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password("123456"), Password("master-pass"), pbkdf(),
            CipherAlgorithm.AES_128, store,
        )
        session.close()

        assertNull(VaultCrypto.unlock(Password("000000"), Password("master-pass"), material, store))
        assertNull(VaultCrypto.unlock(Password("123456"), Password("wrong"), material, store))
    }

    @Test
    fun `argon2id vault round trips`() {
        val store = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password("654321"), Password("another-master"), argon2id(),
            CipherAlgorithm.CHACHACHA20, store,
        )
        assertEquals(KeyDerivationFunction.ARGON2_ID, material.kdfConfig.kdf)

        val encrypted = session.encryptString("top secret")
        session.close()

        val unlocked = assertNotNull(
            VaultCrypto.unlock(Password("654321"), Password("another-master"), material, store)
        )
        assertEquals("top secret", unlocked.decryptString(encrypted))
        unlocked.close()
    }

    @Test
    fun `legacy v1 vaults use the legacy master key derivation`() {
        val store = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password("111111"), Password("legacy"), pbkdf(),
            CipherAlgorithm.AES_128, store,
            vaultVersion = 1,
        )
        val encrypted = session.encryptString("legacy-data")
        session.close()

        val unlocked = assertNotNull(
            VaultCrypto.unlock(Password("111111"), Password("legacy"), material, store)
        )
        assertEquals("legacy-data", unlocked.decryptString(encrypted))
        unlocked.close()
    }

    @Test
    fun `missing wrapping key does not unlock`() {
        val store = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password("123456"), Password("master-pass"), pbkdf(),
            CipherAlgorithm.AES_256, store,
        )
        session.close()

        store.deleteKey(KeyAlias.MASTER_KEY)
        assertNull(VaultCrypto.unlock(Password("123456"), Password("master-pass"), material, store))
    }

    @Test
    fun `master key inner layer carries the kdf config`() {
        val store = InMemoryWrappingKeyStore()
        val kdf = pbkdf(20_000)
        val salt = de.jepfa.yapm.service.secret.CryptoService.generateRandomKey(128)
        val masterKey = MasterKeyService.generateMasterKey()

        val stored = MasterKeyService.encryptMasterKey(
            masterKey, Password("123456"), Password("master-pass"), salt,
            kdf, CipherAlgorithm.AES_256, store,
        )
        assertEquals(kdf.toEncodedString(), stored.inner.type?.payload)

        // the export contains only the inner layer and is importable elsewhere
        val parsed = Encrypted.fromBase64String(stored.inner.toBase64String())
        assertEquals(kdf.toEncodedString(), parsed.type?.payload)
    }

    @Test
    fun `different vaults produce different material`() {
        val store = InMemoryWrappingKeyStore()
        val (a, sa) = VaultCrypto.create(
            Password("123456"), Password("m"), pbkdf(), CipherAlgorithm.AES_256, store
        )
        val (b, sb) = VaultCrypto.create(
            Password("123456"), Password("m"), pbkdf(), CipherAlgorithm.AES_256, store
        )
        sa.close(); sb.close()
        assertNotEquals(a.salt.toBase64String(), b.salt.toBase64String())
        assertNotEquals(a.storedMasterKey.toBase64String(), b.storedMasterKey.toBase64String())
    }
}
