package de.jepfa.yapm.application

import de.jepfa.yapm.crypto.InMemoryWrappingKeyStore
import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secretgenerator.SecretStrength
import de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.password.PasswordGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.pin.PinGeneratorSpec
import de.jepfa.yapm.vault.VaultCrypto
import de.jepfa.yapm.vault.VaultSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultServiceTest {

    private data class Fixture(
        val db: VaultDatabase,
        val repositories: VaultRepositories,
        val session: VaultSession,
        val service: VaultService,
    ) {
        fun close() {
            session.close()
            db.close()
        }
    }

    private fun fixture(): Fixture {
        val db = VaultDatabase.inMemory()
        val repositories = VaultRepositories(db)
        val (_, session) = VaultCrypto.create(
            Password("123456"),
            Password("master"),
            KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 10_000, null),
            CipherAlgorithm.AES_256,
            InMemoryWrappingKeyStore(),
        )
        return Fixture(db, repositories, session, VaultService(repositories, session))
    }

    @Test
    fun `credential crud preserves fields and last password`() {
        val f = fixture()
        try {
            val labelId = f.service.saveLabel(null, "Work", "work stuff", 0xFF0000)
            val id = f.service.saveCredential(
                VaultService.CredentialDraft(
                    name = "GitHub",
                    website = "https://github.com",
                    user = "octocat",
                    password = "s3cr3t",
                    additionalInfo = "2FA enabled",
                    labelIds = listOf(labelId),
                    expiresAt = System.currentTimeMillis() + 86_400_000,
                    pinned = true,
                )
            )

            val view = assertNotNull(f.service.findCredential(id))
            assertEquals("GitHub", view.name)
            assertEquals("https://github.com", view.website)
            assertEquals("octocat", view.user)
            assertEquals("s3cr3t", view.password)
            assertEquals("2FA enabled", view.additionalInfo)
            assertEquals(listOf(labelId), view.labelIds)
            assertTrue(view.pinned)
            assertFalse(view.isExpired)

            f.service.saveCredential(
                VaultService.CredentialDraft(
                    id = id,
                    name = "GitHub",
                    website = "https://github.com",
                    user = "octocat",
                    password = "new-secret",
                    additionalInfo = "2FA enabled",
                    labelIds = listOf(labelId),
                    pinned = false,
                )
            )

            val updated = assertNotNull(f.service.findCredential(id))
            assertEquals("new-secret", updated.password)
            assertFalse(updated.pinned)

            val entity = assertNotNull(f.repositories.credentials.findById(id))
            val last = assertNotNull(entity.passwordData.lastPassword)
            assertEquals(
                "s3cr3t",
                f.session.decryptPassword(last).decodeToCharArray().concatToString(),
            )

            assertTrue(f.service.deleteCredential(id))
            assertNull(f.service.findCredential(id))
        } finally {
            f.close()
        }
    }

    @Test
    fun `expired credentials are flagged`() {
        val f = fixture()
        try {
            val id = f.service.saveCredential(
                VaultService.CredentialDraft(
                    name = "Old",
                    password = "x",
                    expiresAt = System.currentTimeMillis() - 60_000,
                )
            )
            assertTrue(assertNotNull(f.service.findCredential(id)).isExpired)
        } finally {
            f.close()
        }
    }

    @Test
    fun `deleting a label removes it from credentials`() {
        val f = fixture()
        try {
            val labelId = f.service.saveLabel(null, "Work", "", 0x00FF00)
            val id = f.service.saveCredential(
                VaultService.CredentialDraft(name = "Mail", password = "x", labelIds = listOf(labelId))
            )
            assertEquals(listOf(labelId), assertNotNull(f.service.findCredential(id)).labelIds)

            f.service.deleteLabel(labelId)

            assertTrue(f.service.listLabels().isEmpty())
            assertTrue(assertNotNull(f.service.findCredential(id)).labelIds.isEmpty())
        } finally {
            f.close()
        }
    }

    @Test
    fun `duplicate creates an independent copy`() {
        val f = fixture()
        try {
            val id = f.service.saveCredential(
                VaultService.CredentialDraft(name = "Original", user = "u", password = "p")
            )
            val copyId = assertNotNull(f.service.duplicateCredential(id))
            assertTrue(copyId != id)

            val copy = assertNotNull(f.service.findCredential(copyId))
            assertEquals("Original (copy)", copy.name)
            assertEquals("p", copy.password)

            f.service.deleteCredential(id)
            assertNotNull(f.service.findCredential(copyId))
        } finally {
            f.close()
        }
    }

    @Test
    fun `generators produce the requested shapes`() {
        val f = fixture()
        try {
            assertEquals(
                SecretStrength.NORMAL.ordinaryPasswordLength,
                f.service.generatePassword(PasswordGeneratorSpec(strength = SecretStrength.NORMAL)).length,
            )
            assertEquals(
                SecretStrength.NORMAL.pseudoPhraseLength,
                f.service.generatePassphrase(PassphraseGeneratorSpec(strength = SecretStrength.NORMAL)).length,
            )
            assertEquals(6, f.service.generatePin(PinGeneratorSpec(6)).length)
        } finally {
            f.close()
        }
    }

    @Test
    fun `password strength grows with length and alphabet`() {
        val weak = PasswordStrength.evaluate(Password("aaaa"))
        val strong = PasswordStrength.evaluate(Password("aB3!xY9#kL2@qW8%"))
        assertTrue(weak.entropy < strong.entropy)
        assertTrue(weak.rating == "very poor")
        assertTrue(strong.entropy > 80)
        assertEquals(0.0, PasswordStrength.evaluate(Password("")).entropy)
    }
}
