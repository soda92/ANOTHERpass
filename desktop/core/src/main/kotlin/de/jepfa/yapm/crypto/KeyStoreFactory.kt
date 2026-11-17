package de.jepfa.yapm.crypto

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Chooses the wrapping-key backend.
 *
 * Safety rule: if a vault already has keys in the `0600` file store, we keep
 * using it. Switching to Secret Service would make the existing vault
 * unopenable, so that requires an explicit future migration step.
 */
object KeyStoreFactory {

    const val ENV_KEYSTORE = "ANOTHERPASS_KEYSTORE"
    const val ENV_SECRET_TOOL = "ANOTHERPASS_SECRET_TOOL"
    const val FILE_NAME = "wrapping-keys.properties"

    enum class Kind { SECRET_SERVICE, FILE }

    data class Choice(
        val store: WrappingKeyStore,
        val kind: Kind,
        val reason: String,
    )

    fun create(
        configDir: Path,
        env: Map<String, String> = System.getenv(),
        secretService: SecretServiceKeyStore = SecretServiceKeyStore(
            executable = env[ENV_SECRET_TOOL]?.takeIf { it.isNotBlank() } ?: "secret-tool",
        ),
    ): Choice {
        val filePath = configDir.resolve(FILE_NAME)
        val override = env[ENV_KEYSTORE]?.trim()?.lowercase(Locale.ROOT)

        if (override == "file") {
            return Choice(FileWrappingKeyStore(filePath), Kind.FILE, "$ENV_KEYSTORE=file")
        }

        // Never abandon keys that an existing vault depends on.
        if (fileHasKeys(filePath)) {
            return Choice(
                FileWrappingKeyStore(filePath),
                Kind.FILE,
                "existing vault keys found in the file keystore",
            )
        }

        val available = secretService.isAvailable()

        if (override == "secret-service" || override == "libsecret") {
            if (!available) {
                throw KeyStoreException(
                    "Secret Service is not available; cannot honour $ENV_KEYSTORE=$override"
                )
            }
            return Choice(secretService, Kind.SECRET_SERVICE, "$ENV_KEYSTORE=$override")
        }

        return if (available) {
            Choice(secretService, Kind.SECRET_SERVICE, "Secret Service detected")
        } else {
            Choice(
                FileWrappingKeyStore(filePath),
                Kind.FILE,
                "Secret Service unavailable; using the 0600 file fallback",
            )
        }
    }

    private fun fileHasKeys(path: Path): Boolean {
        if (!Files.exists(path)) return false
        return try {
            Files.readAllLines(path).any { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains('=')
            }
        } catch (_: Exception) {
            false
        }
    }
}
