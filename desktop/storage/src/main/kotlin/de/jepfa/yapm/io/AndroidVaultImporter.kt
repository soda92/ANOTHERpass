package de.jepfa.yapm.io

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.jepfa.yapm.crypto.KeyAlias
import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.model.Validable
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.encrypted.EncLabel
import de.jepfa.yapm.model.encrypted.EncUsernameTemplate
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.service.secret.KdfParameterService
import de.jepfa.yapm.service.secret.MasterKeyService
import de.jepfa.yapm.util.B64
import de.jepfa.yapm.util.Constants
import de.jepfa.yapm.vault.VaultKeyMaterial
import de.jepfa.yapm.vault.VaultSession

/**
 * Imports a vault export produced by the Android app ("Export vault" **with**
 * master key) into a fresh desktop vault database.
 *
 * The flow mirrors `ImportVaultUseCase.readAndImport`:
 *  - `vaultId` is the KDF salt (base64)
 *  - `encMk` is the master key encrypted only with the PIN+password passphrase key
 *  - `credentials` / `labels` / `usernameTemplates` are JSON arrays encrypted
 *    with the master secret key
 */
class AndroidVaultImporter(private val repositories: VaultRepositories) {

    data class Result(
        val material: VaultKeyMaterial,
        val session: VaultSession,
        val credentialCount: Int,
        val labelCount: Int,
        val usernameTemplateCount: Int,
    )

    fun import(
        jsonText: String,
        pin: Password,
        masterPassword: Password,
        wrappingKeyStore: WrappingKeyStore,
    ): Result {
        val root = try {
            JsonParser.parseString(jsonText).asJsonObject
        } catch (e: Exception) {
            throw VaultImportException("Not a valid ANOTHERpass vault export", e)
        }

        val saltText = root.string(AndroidVaultFormat.JSON_VAULT_ID)
            ?: throw VaultImportException("Missing vaultId in export")
        val salt = Key(B64.decodeMime(saltText))
        if (salt.data.isEmpty()) throw VaultImportException("Empty KDF salt in export")

        val cipher = root.string(AndroidVaultFormat.JSON_CIPHER_ALGORITHM)
            ?.let { CipherAlgorithm.valueOf(it) }
            ?: DEFAULT_CIPHER_ALGORITHM
        val vaultVersion = root.int(AndroidVaultFormat.JSON_VAULT_VERSION)
            ?: Constants.INITIAL_VAULT_VERSION
        if (vaultVersion > Constants.CURRENT_VERSION) {
            throw VaultImportException("Unsupported vault version $vaultVersion")
        }

        val encMkText = root.string(AndroidVaultFormat.JSON_ENC_MK)
            ?: throw VaultImportException(
                "Export does not contain the master key. Re-export from Android with " +
                    "\"include master key\" enabled."
            )
        val encMk = Encrypted.fromBase64String(encMkText)

        // Older exports have no KDF payload: Android fell back to the legacy PBKDF count.
        val kdfConfig = encMk.type?.payload?.let { KdfConfig.fromEncodedString(it) }
            ?: KdfConfig(
                KeyDerivationFunction.BUILT_IN_PBKDF,
                KdfParameterService.LEGACY_PBKDF_ITERATIONS,
                null,
            )

        val passphraseSK = MasterKeyService.getMasterPassPhraseSecretKey(
            pin, masterPassword, salt, cipher, kdfConfig
        )
        val masterKey = CryptoService.decryptKey(passphraseSK, encMk)
        if (!masterKey.isValid()) {
            throw VaultImportException("Wrong PIN or master password")
        }

        val masterKeyHash = CryptoService.masterKeyHash(masterKey)
        val masterSK = if (vaultVersion == Constants.INITIAL_VAULT_VERSION) {
            CryptoService.generateLegacySecretKey(masterKey, salt, cipher)
        } else {
            CryptoService.generateSecretKey(masterKey, cipher)
        }
        masterKey.clear()

        // Re-wrap the inner master key with this machine's keystore key.
        val mkHolder = SecretKeyHolder(
            wrappingKeyStore.getOrCreateKey(KeyAlias.MASTER_KEY),
            DEFAULT_CIPHER_ALGORITHM,
        )
        val storedMasterKey = CryptoService.encryptEncrypted(mkHolder, encMk)

        val material = VaultKeyMaterial(
            salt = salt,
            storedMasterKey = storedMasterKey,
            cipherAlgorithm = cipher,
            kdfConfig = kdfConfig,
            vaultVersion = vaultVersion,
        )
        val session = VaultSession(
            masterKeySK = masterSK,
            masterKeyHash = masterKeyHash,
            salt = Key(salt.data.copyOf()),
            cipherAlgorithm = cipher,
        )

        val credentials = decryptArray(root, AndroidVaultFormat.JSON_CREDENTIALS, masterSK)
        val labels = decryptArray(root, AndroidVaultFormat.JSON_LABELS, masterSK)
        val templates = decryptArray(root, AndroidVaultFormat.JSON_USERNAME_TEMPLATES, masterSK)

        var credentialCount = 0
        var labelCount = 0
        var templateCount = 0

        repositories.db.transaction {
            labels?.forEach { element ->
                EncLabel.fromJson(element)?.let {
                    repositories.labels.insert(it)
                    labelCount++
                }
            }
            templates?.forEach { element ->
                EncUsernameTemplate.fromJson(element)?.let {
                    repositories.usernameTemplates.insert(it)
                    templateCount++
                }
            }
            credentials?.forEach { element ->
                de.jepfa.yapm.model.encrypted.EncCredential.fromJson(element)?.let {
                    repositories.credentials.insert(it)
                    credentialCount++
                }
            }
        }

        return Result(material, session, credentialCount, labelCount, templateCount)
    }

    private fun decryptArray(root: JsonObject, key: String, skh: SecretKeyHolder): JsonArray? {
        val element = root.get(key) ?: return null
        if (element.isJsonArray) return element.asJsonArray
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null

        val text = element.asString
        if (text.isBlank()) return null

        val plain = try {
            CryptoService.decryptCommonString(skh, Encrypted.fromBase64String(text))
        } catch (e: Exception) {
            throw VaultImportException("Cannot decrypt $key", e)
        }
        if (plain == Validable.FAILED_STRING) {
            throw VaultImportException("Cannot decrypt $key (wrong key or corrupted export)")
        }
        return JsonParser.parseString(plain).asJsonArray
    }
}

private fun JsonObject.string(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.int(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asInt
