package de.jepfa.yapm

import de.jepfa.yapm.crypto.InMemoryWrappingKeyStore
import de.jepfa.yapm.crypto.KeyAlias
import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.io.AndroidVaultExporter
import de.jepfa.yapm.io.AndroidVaultFormat
import de.jepfa.yapm.io.AndroidVaultImporter
import de.jepfa.yapm.io.VaultImportException
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.encrypted.EncCredential
import de.jepfa.yapm.model.encrypted.EncLabel
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.OtpData
import de.jepfa.yapm.model.encrypted.PasswordData
import de.jepfa.yapm.model.encrypted.TimeData
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.service.secret.KdfParameterService
import de.jepfa.yapm.util.B64
import de.jepfa.yapm.vault.VaultCrypto
import de.jepfa.yapm.vault.VaultSession
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidVaultImportExportTest {

    private fun buildAndroidExport(
        material: de.jepfa.yapm.vault.VaultKeyMaterial,
        session: VaultSession,
        wrapping: InMemoryWrappingKeyStore,
        pin: String,
        master: String,
    ): String {
        val mkHolder = SecretKeyHolder(
            wrapping.getOrCreateKey(KeyAlias.MASTER_KEY),
            DEFAULT_CIPHER_ALGORITHM,
        )
        val inner = CryptoService.decryptEncrypted(mkHolder, material.storedMasterKey)

        val label = EncLabel(
            7,
            UUID.randomUUID(),
            session.encryptString("Work"),
            session.encryptString("work stuff"),
            0xFF0000,
        )
        val credential = EncCredential(
            id = 42,
            uid = UUID.randomUUID(),
            name = session.encryptString("GitHub"),
            website = session.encryptString("https://github.com"),
            user = session.encryptString("octocat"),
            additionalInfo = session.encryptString("work account"),
            labels = session.encryptString("7"),
            passwordData = PasswordData(
                session.encryptPassword(Password("s3cr3t")),
                false,
                null,
                null,
            ),
            timeData = TimeData(1_700_000_000_000L, Encrypted.empty()),
            otpData = OtpData(session.encryptString("otpauth://totp/example")),
            pinned = true,
        )

        val credentialsArray = com.google.gson.JsonArray().apply { add(credential.toJson()) }
        val labelsArray = com.google.gson.JsonArray().apply { add(label.toJson()) }

        return com.google.gson.JsonObject().apply {
            addProperty(AndroidVaultFormat.JSON_APP_VERSION_CODE, 202006)
            addProperty(AndroidVaultFormat.JSON_APP_VERSION_NAME, "2.2.6")
            addProperty(AndroidVaultFormat.JSON_CREATION_DATE, 1_700_000_000_000L)
            addProperty(AndroidVaultFormat.JSON_VAULT_ID, B64.mime(material.salt.data))
            addProperty(AndroidVaultFormat.JSON_VAULT_VERSION, material.vaultVersion)
            addProperty(AndroidVaultFormat.JSON_CIPHER_ALGORITHM, material.cipherAlgorithm.name)
            addProperty(AndroidVaultFormat.JSON_ENC_MK, inner.toBase64String())
            addProperty(
                AndroidVaultFormat.JSON_CREDENTIALS,
                session.encryptString(credentialsArray.toString()).toBase64String(),
            )
            addProperty(AndroidVaultFormat.JSON_CREDENTIALS_COUNT, 1)
            addProperty(
                AndroidVaultFormat.JSON_LABELS,
                session.encryptString(labelsArray.toString()).toBase64String(),
            )
            addProperty(AndroidVaultFormat.JSON_LABELS_COUNT, 1)
            addProperty(
                AndroidVaultFormat.JSON_USERNAME_TEMPLATES,
                session.encryptString("[]").toBase64String(),
            )
        }.toString()
    }

    @Test
    fun `imports an android export and preserves ids and references`() {
        val pin = "123456"
        val master = "master-pass"
        val kdf = KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 10_000, null)

        val sourceStore = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password(pin), Password(master), kdf, CipherAlgorithm.AES_256, sourceStore,
        )
        val export = buildAndroidExport(material, session, sourceStore, pin, master)
        session.close()

        val targetDb = VaultDatabase.inMemory()
        val targetStore = InMemoryWrappingKeyStore()
        val importer = AndroidVaultImporter(VaultRepositories(targetDb))

        val result = importer.import(export, Password(pin), Password(master), targetStore)

        assertEquals(1, result.credentialCount)
        assertEquals(1, result.labelCount)
        assertEquals(0, result.usernameTemplateCount)
        assertEquals(CipherAlgorithm.AES_256, result.material.cipherAlgorithm)
        assertEquals(kdf, result.material.kdfConfig)

        val repos = VaultRepositories(targetDb)
        val credential = assertNotNull(repos.credentials.findById(42))
        assertEquals("GitHub", result.session.decryptString(credential.name))
        assertEquals("7", result.session.decryptString(credential.labels))
        assertEquals("s3cr3t", result.session.decryptPassword(credential.passwordData.password).toString())
        assertEquals("otpauth://totp/example", result.session.decryptString(credential.otpData!!.encOtpAuthUri))

        val label = assertNotNull(repos.labels.findById(7))
        assertEquals("Work", result.session.decryptString(label.name))

        // The re-wrapped material unlocks with the target keystore.
        result.session.close()
        val unlocked = assertNotNull(
            VaultCrypto.unlock(Password(pin), Password(master), result.material, targetStore)
        )
        assertEquals("GitHub", unlocked.decryptString(credential.name))
        unlocked.close()
        targetDb.close()
    }

    @Test
    fun `rejects a wrong password`() {
        val sourceStore = InMemoryWrappingKeyStore()
        val (material, session) = VaultCrypto.create(
            Password("123456"), Password("right"), KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 10_000, null),
            CipherAlgorithm.AES_128, sourceStore,
        )
        val export = buildAndroidExport(material, session, sourceStore, "123456", "right")
        session.close()

        val db = VaultDatabase.inMemory()
        val importer = AndroidVaultImporter(VaultRepositories(db))
        assertFailsWith<VaultImportException> {
            importer.import(export, Password("123456"), Password("wrong"), InMemoryWrappingKeyStore())
        }
        db.close()
    }

    @Test
    fun `rejects an export without the master key`() {
        val json = com.google.gson.JsonObject().apply {
            addProperty(AndroidVaultFormat.JSON_VAULT_ID, B64.mime(ByteArray(16) { 1 }))
            addProperty(AndroidVaultFormat.JSON_CIPHER_ALGORITHM, "AES_256")
        }.toString()
        val db = VaultDatabase.inMemory()
        val importer = AndroidVaultImporter(VaultRepositories(db))
        val error = assertFailsWith<VaultImportException> {
            importer.import(json, Password("123456"), Password("x"), InMemoryWrappingKeyStore())
        }
        assertTrue(error.message!!.contains("master key"))
        db.close()
    }

    @Test
    fun `export then re-import round trips`() {
        val pin = "123456"
        val master = "master-pass"
        val kdf = KdfConfig(KeyDerivationFunction.ARGON2_ID, 1, 12)

        val store1 = InMemoryWrappingKeyStore()
        val db1 = VaultDatabase.inMemory()
        val repos1 = VaultRepositories(db1)
        val (material, session) = VaultCrypto.create(
            Password(pin), Password(master), kdf, CipherAlgorithm.CHACHACHA20, store1,
        )
        val credential = EncCredential(
            id = null,
            uid = UUID.randomUUID(),
            name = session.encryptString("Email"),
            website = session.encryptString("https://mail.example"),
            user = session.encryptString("me@example.com"),
            additionalInfo = session.encryptString(""),
            labels = session.encryptString(""),
            passwordData = PasswordData(session.encryptPassword(Password("hunter2")), false, null, null),
            timeData = TimeData(1_700_000_000_000L, Encrypted.empty()),
            otpData = null,
            pinned = false,
        )
        repos1.credentials.insert(credential)

        val exporter = AndroidVaultExporter(repos1)
        val exported = exporter.export(material, session, store1)

        val db2 = VaultDatabase.inMemory()
        val store2 = InMemoryWrappingKeyStore()
        val imported = AndroidVaultImporter(VaultRepositories(db2))
            .import(exported, Password(pin), Password(master), store2)

        assertEquals(1, imported.credentialCount)
        val loaded = VaultRepositories(db2).credentials.findAll().single()
        assertEquals("Email", imported.session.decryptString(loaded.name))
        assertEquals("hunter2", imported.session.decryptPassword(loaded.passwordData.password).toString())
        assertEquals(CipherAlgorithm.CHACHACHA20, imported.material.cipherAlgorithm)
        assertEquals(kdf, imported.material.kdfConfig)

        imported.session.close()
        session.close()
        db1.close()
        db2.close()
    }
}

class VaultDatabaseTest {

    @Test
    fun `schema is version 10`() {
        val db = VaultDatabase.inMemory()
        val version = db.withConnection { c ->
            c.createStatement().use { st ->
                st.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
            }
        }
        assertEquals(VaultDatabase.DB_VERSION, version)
        db.close()
    }

    @Test
    fun `credential crud works`() {
        val db = VaultDatabase.inMemory()
        val repos = VaultRepositories(db)
        val encrypted = Encrypted.empty()
        val credential = EncCredential(
            id = null,
            uid = UUID.randomUUID(),
            name = encrypted,
            website = encrypted,
            user = encrypted,
            additionalInfo = encrypted,
            labels = encrypted,
            passwordData = PasswordData(encrypted, false, null, null),
            timeData = TimeData(123L, encrypted),
            otpData = null,
            pinned = false,
        )

        val inserted = repos.credentials.insert(credential)
        assertTrue(inserted.id != null)
        assertEquals(1, repos.credentials.count())
        assertNotNull(repos.credentials.findById(inserted.id!!))

        val loaded = repos.credentials.findById(inserted.id!!)!!
        loaded.pinned = true
        assertTrue(repos.credentials.update(loaded))
        assertTrue(repos.credentials.findById(inserted.id!!)!!.pinned)

        assertTrue(repos.credentials.deleteById(inserted.id!!))
        assertEquals(0, repos.credentials.count())
        db.close()
    }
}
