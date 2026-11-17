package de.jepfa.yapm.crypto

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Wrapping-key store backed by the freedesktop Secret Service (GNOME Keyring /
 * KWallet) via the `secret-tool` CLI from libsecret.
 *
 * Keys are stored as secrets with the attributes
 * `application=anotherpass`, `purpose=wrapping-key`, `alias=<alias>`.
 * The stored value is `v1:<base64(raw AES-128 key)>`.
 *
 * Using the CLI keeps the build free of JNI/JNA and works with whatever Secret
 * Service implementation the desktop provides. Availability is probed with a
 * store/lookup/clear round-trip, so a missing daemon is detected reliably.
 */
class SecretServiceKeyStore(
    private val executable: String = "secret-tool",
    private val runner: CommandRunner = ProcessCommandRunner,
    private val applicationName: String = "anotherpass",
    private val random: SecureRandom = SecureRandom(),
) : WrappingKeyStore {

    private val cache = ConcurrentHashMap<String, SecretKey>()
    private val lock = Any()

    @Volatile
    private var available: Boolean? = null

    override fun getOrCreateKey(alias: String): SecretKey = synchronized(lock) {
        cache[alias]?.let { return it }

        val stored = lookupRaw(alias)
        if (stored != null) {
            val key = decodeKey(stored)
                ?: throw KeyStoreException("Invalid wrapping key stored for alias '$alias'")
            cache[alias] = key
            return key
        }

        val generated = generateAes128()
        storeRaw(alias, encodeKey(generated))
        cache[alias] = generated
        return generated
    }

    override fun deleteKey(alias: String) {
        synchronized(lock) {
            cache.remove(alias)
            // Best effort: asking secret-tool to clear a missing entry is harmless.
            runCatching { clearRaw(alias) }
        }
    }

    /** Probe once; result is cached for the lifetime of this instance. */
    fun isAvailable(): Boolean {
        available?.let { return it }
        synchronized(lock) {
            available?.let { return it }
            val ok = try {
                probe()
            } catch (_: Exception) {
                false
            }
            available = ok
            return ok
        }
    }

    /**
     * `secret-tool lookup` of a missing key exits 1 with empty stderr when the
     * service is reachable, and 1 with an error on stderr when D-Bus/keyring is
     * unavailable. This avoids writing a probe secret (and avoids unlocking the
     * user's keyring just to detect availability).
     */
    private fun probe(): Boolean {
        val result = runner.run(lookupCommand(PROBE_ALIAS))
        return when {
            result.exitCode == 0 -> true
            result.exitCode == 1 && result.stderr.isBlank() -> true
            else -> false
        }
    }

    private fun lookupRaw(alias: String): String? {
        val result = runner.run(lookupCommand(alias))
        if (result.exitCode != 0) return null
        return String(result.stdout, Charsets.UTF_8).trim().ifEmpty { null }
    }

    private fun storeRaw(alias: String, value: String) {
        val result = runner.run(storeCommand(alias), (value + "\n").toByteArray(Charsets.UTF_8))
        if (result.exitCode != 0) {
            throw KeyStoreException(
                "secret-tool store failed for '$alias': ${result.stderr.trim()}"
            )
        }
    }

    private fun clearRaw(alias: String) {
        val result = runner.run(clearCommand(alias))
        if (result.exitCode != 0) {
            throw KeyStoreException(
                "secret-tool clear failed for '$alias': ${result.stderr.trim()}"
            )
        }
    }

    private fun generateAes128(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128, random)
        return keyGen.generateKey()
    }

    private fun attributes(alias: String): List<String> = listOf(
        "application", applicationName,
        "purpose", "wrapping-key",
        "alias", alias,
    )

    private fun storeCommand(alias: String): List<String> =
        listOf(executable, "store", "--label=ANOTHERpass wrapping key ($alias)") + attributes(alias)

    private fun lookupCommand(alias: String): List<String> =
        listOf(executable, "lookup") + attributes(alias)

    private fun clearCommand(alias: String): List<String> =
        listOf(executable, "clear") + attributes(alias)

    private fun encodeKey(key: SecretKey): String =
        VALUE_PREFIX + Base64.getEncoder().encodeToString(key.encoded)

    private fun decodeKey(value: String): SecretKey? {
        val encoded = value.removePrefix(VALUE_PREFIX)
        return try {
            SecretKeySpec(Base64.getDecoder().decode(encoded), "AES")
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val VALUE_PREFIX = "v1:"
        const val PROBE_ALIAS = "__anotherpass_availability_probe__"
    }
}
