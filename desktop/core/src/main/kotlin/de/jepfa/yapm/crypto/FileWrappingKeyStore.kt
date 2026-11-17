package de.jepfa.yapm.crypto

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * File-backed fallback keystore.
 *
 * SECURITY TRADE-OFF: keys are stored unencrypted in a `0600` file. This exists so
 * the app works on systems without a Secret Service (headless/SSH/containers).
 * Prefer a libsecret-backed implementation when a keyring is available.
 */
class FileWrappingKeyStore(
    private val file: Path,
    private val random: SecureRandom = SecureRandom(),
) : WrappingKeyStore {

    private val lock = Any()

    init {
        file.parent?.let { Files.createDirectories(it) }
        ensureSecureFile()
    }

    override fun getOrCreateKey(alias: String): SecretKey = synchronized(lock) {
        val props = load()
        val stored = props.getProperty(alias)
        if (stored != null) {
            return SecretKeySpec(Base64.getDecoder().decode(stored), "AES")
        }

        val key = generateAes128()
        props.setProperty(alias, Base64.getEncoder().encodeToString(key.encoded))
        store(props)
        return key
    }

    override fun deleteKey(alias: String) {
        synchronized(lock) {
            val props = load()
            props.remove(alias)
            store(props)
        }
    }

    private fun generateAes128(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128, random)
        return keyGen.generateKey()
    }

    private fun load(): Properties {
        val props = Properties()
        if (Files.exists(file)) {
            Files.newInputStream(file).use { props.load(it) }
        }
        return props
    }

    private fun store(props: Properties) {
        ensureSecureFile()
        Files.newOutputStream(file).use { props.store(it, "ANOTHERpass desktop wrapping keys (0600)") }
        restrictPermissions()
    }

    private fun ensureSecureFile() {
        if (!Files.exists(file)) {
            Files.createFile(file)
            restrictPermissions()
        }
    }

    private fun restrictPermissions() {
        try {
            Files.setPosixFilePermissions(
                file,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX filesystem; best effort only.
        }
    }
}
