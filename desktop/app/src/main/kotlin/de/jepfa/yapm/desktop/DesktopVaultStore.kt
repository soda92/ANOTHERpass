package de.jepfa.yapm.desktop

import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.util.B64
import de.jepfa.yapm.vault.VaultKeyMaterial
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

/**
 * Minimal vault metadata persistence for the desktop app.
 *
 * Phase 2 replaces this with the SQLite-backed storage module. The on-disk
 * encoding already matches the Android fields (salt, encrypted master key,
 * cipher, KDF config, vault version).
 */
class DesktopVaultStore(private val directory: Path) {

    private val file: Path = directory.resolve("vault.properties")

    init {
        Files.createDirectories(directory)
    }

    fun load(): VaultKeyMaterial? {
        if (!Files.exists(file)) return null
        return try {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            val salt = Key(B64.decodeNoWrapNoPadding(props.getProperty("salt")))
            val storedMasterKey = Encrypted.fromBase64String(props.getProperty("storedMasterKey"))
            val cipher = CipherAlgorithm.valueOf(props.getProperty("cipher"))
            val kdf = KdfConfig.fromEncodedString(props.getProperty("kdf")) ?: return null
            val version = props.getProperty("vaultVersion").toInt()
            VaultKeyMaterial(salt, storedMasterKey, cipher, kdf, version)
        } catch (_: Exception) {
            null
        }
    }

    fun loadStoredMasterPassword(): Encrypted? {
        if (!Files.exists(file)) return null
        return try {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            props.getProperty("encryptedMasterPassword")?.let { Encrypted.fromBase64String(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun hasStoredMasterPassword(): Boolean = loadStoredMasterPassword() != null

    fun save(
        material: VaultKeyMaterial,
        encryptedMasterPassword: Encrypted? = loadStoredMasterPassword(),
    ) {
        val props = Properties()
        props.setProperty("salt", B64.noWrapNoPadding(material.salt.data))
        props.setProperty("storedMasterKey", material.storedMasterKey.toBase64String())
        props.setProperty("cipher", material.cipherAlgorithm.name)
        props.setProperty("kdf", material.kdfConfig.toEncodedString())
        props.setProperty("vaultVersion", material.vaultVersion.toString())
        if (encryptedMasterPassword != null) {
            props.setProperty("encryptedMasterPassword", encryptedMasterPassword.toBase64String())
        }
        Files.newOutputStream(file).use { props.store(it, "ANOTHERpass desktop vault metadata") }
        restrictPermissions()
    }

    fun delete() {
        Files.deleteIfExists(file)
    }

    private fun restrictPermissions() {
        try {
            Files.setPosixFilePermissions(
                file,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // best effort
        }
    }

    companion object {
        fun defaultConfigDir(): Path {
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.config")
            return Path.of(base, "anotherpass")
        }
    }
}
