package de.jepfa.yapm.io

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.jepfa.yapm.crypto.KeyAlias
import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.util.B64
import de.jepfa.yapm.vault.VaultKeyMaterial
import de.jepfa.yapm.vault.VaultSession

/**
 * Produces a JSON file that the Android app can import with
 * `ImportVaultUseCase`, including the inner (passphrase-only) master key so the
 * export can bootstrap a vault on another device.
 */
class AndroidVaultExporter(private val repositories: VaultRepositories) {

    fun export(
        material: VaultKeyMaterial,
        session: VaultSession,
        wrappingKeyStore: WrappingKeyStore,
    ): String {
        val mkHolder = SecretKeyHolder(
            wrappingKeyStore.getOrCreateKey(KeyAlias.MASTER_KEY),
            DEFAULT_CIPHER_ALGORITHM,
        )
        val innerMasterKey = CryptoService.decryptEncrypted(mkHolder, material.storedMasterKey)

        val credentials = repositories.credentials.findAll()
        val labels = repositories.labels.findAll()
        val templates = repositories.usernameTemplates.findAll()

        val credentialsArray = JsonArray().apply {
            credentials.forEach { add(it.toJson()) }
        }
        val labelsArray = JsonArray().apply {
            labels.forEach { add(it.toJson()) }
        }
        val templatesArray = JsonArray().apply {
            templates.forEach { add(it.toJson()) }
        }

        return JsonObject().apply {
            addProperty(AndroidVaultFormat.JSON_APP_VERSION_CODE, 0)
            addProperty(AndroidVaultFormat.JSON_APP_VERSION_NAME, "desktop")
            addProperty(AndroidVaultFormat.JSON_CREATION_DATE, System.currentTimeMillis())
            addProperty(
                AndroidVaultFormat.JSON_VAULT_ID,
                B64.mime(material.salt.data),
            )
            addProperty(AndroidVaultFormat.JSON_VAULT_VERSION, material.vaultVersion)
            addProperty(
                AndroidVaultFormat.JSON_CIPHER_ALGORITHM,
                material.cipherAlgorithm.name,
            )
            addProperty(AndroidVaultFormat.JSON_ENC_MK, innerMasterKey.toBase64String())

            addProperty(
                AndroidVaultFormat.JSON_CREDENTIALS,
                session.encryptString(credentialsArray.toString()).toBase64String(),
            )
            addProperty(AndroidVaultFormat.JSON_CREDENTIALS_COUNT, credentials.size)

            addProperty(
                AndroidVaultFormat.JSON_LABELS,
                session.encryptString(labelsArray.toString()).toBase64String(),
            )
            addProperty(AndroidVaultFormat.JSON_LABELS_COUNT, labels.size)

            addProperty(
                AndroidVaultFormat.JSON_USERNAME_TEMPLATES,
                session.encryptString(templatesArray.toString()).toBase64String(),
            )
        }.toString()
    }
}
