package de.jepfa.yapm.desktop

import de.jepfa.yapm.crypto.FileWrappingKeyStore
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.vault.VaultCrypto
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopVaultStoreTest {

    @Test
    fun `vault metadata survives save and reload and unlocks`() {
        val dir = Files.createTempDirectory("anotherpass-desktop")
        val keyStore = FileWrappingKeyStore(dir.resolve("keys.properties"))
        val store = DesktopVaultStore(dir)

        val kdf = KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 10_000, null)
        val (material, session) = VaultCrypto.create(
            Password("123456"),
            Password("master-pass"),
            kdf,
            CipherAlgorithm.AES_256,
            keyStore,
        )
        val encrypted = session.encryptString("hello desktop")
        session.close()

        store.save(material)

        val reloaded = assertNotNull(store.load())
        assertEquals(material.cipherAlgorithm, reloaded.cipherAlgorithm)
        assertEquals(material.kdfConfig, reloaded.kdfConfig)
        assertEquals(material.vaultVersion, reloaded.vaultVersion)
        assertEquals(material.salt.toBase64String(), reloaded.salt.toBase64String())

        val unlocked = assertNotNull(
            VaultCrypto.unlock(Password("123456"), Password("master-pass"), reloaded, keyStore)
        )
        assertEquals("hello desktop", unlocked.decryptString(encrypted))
        unlocked.close()
    }

    @Test
    fun `stored master password survives save and can be cleared`() {
        val dir = Files.createTempDirectory("anotherpass-desktop")
        val keyStore = FileWrappingKeyStore(dir.resolve("keys.properties"))
        val store = DesktopVaultStore(dir)

        val kdf = KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 10_000, null)
        val (material, session) = VaultCrypto.create(
            Password("123456"),
            Password("master-pass"),
            kdf,
            CipherAlgorithm.AES_128,
            keyStore,
        )
        session.close()

        val encryptedMasterPassword = Encrypted(
            null,
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5, 6),
            CipherAlgorithm.AES_128,
        )
        store.save(material, encryptedMasterPassword)
        assertEquals(encryptedMasterPassword, store.loadStoredMasterPassword())
        assertTrue(store.hasStoredMasterPassword())

        store.save(material, null)
        assertNull(store.loadStoredMasterPassword())
    }
}
