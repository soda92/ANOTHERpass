package de.jepfa.yapm.vault

import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.service.secret.MasterKeyService
import de.jepfa.yapm.util.Constants
import java.io.Closeable

/**
 * Everything that has to be persisted to reopen a vault. On Android this is split
 * across encrypted SharedPreferences; on desktop it becomes the vault settings file.
 */
data class VaultKeyMaterial(
    val salt: Key,
    val storedMasterKey: Encrypted,
    val cipherAlgorithm: CipherAlgorithm,
    val kdfConfig: KdfConfig,
    val vaultVersion: Int,
)

/** An unlocked vault: holds the secret-key holder used to encrypt vault data. */
class VaultSession(
    val masterKeySK: SecretKeyHolder,
    val masterKeyHash: Key,
    val salt: Key,
    val cipherAlgorithm: CipherAlgorithm,
) : Closeable {

    fun encryptString(plain: String): Encrypted =
        CryptoService.encryptCommonString(masterKeySK, plain)

    fun decryptString(encrypted: Encrypted): String =
        CryptoService.decryptCommonString(masterKeySK, encrypted)

    fun encryptPassword(password: Password): Encrypted =
        CryptoService.encryptPassword(masterKeySK, password)

    fun decryptPassword(encrypted: Encrypted): Password =
        CryptoService.decryptPassword(masterKeySK, encrypted)

    /** Short, human-recognisable vault fingerprint (mirrors Android's shortened master key hash). */
    fun fingerprint(): String = masterKeyHash.toShortenedFingerprint()

    override fun close() {
        masterKeySK.destroy()
        masterKeyHash.clear()
        salt.clear()
    }
}

object VaultCrypto {

    /**
     * Creates a brand-new vault and returns both the material to persist and an
     * unlocked session.
     */
    fun create(
        pin: Password,
        masterPassword: Password,
        kdfConfig: KdfConfig,
        cipherAlgorithm: CipherAlgorithm,
        wrappingKeyStore: WrappingKeyStore,
        vaultVersion: Int = Constants.CURRENT_VERSION,
    ): Pair<VaultKeyMaterial, VaultSession> {
        val salt = CryptoService.generateRandomKey(MasterKeyService.MASTER_KEY_BYTE_SIZE)
        val masterKey = MasterKeyService.generateMasterKey()

        val stored = MasterKeyService.encryptMasterKey(
            masterKey,
            pin,
            masterPassword,
            salt,
            kdfConfig,
            cipherAlgorithm,
            wrappingKeyStore,
        )

        val masterKeyHash = CryptoService.masterKeyHash(masterKey)
        val masterSK = if (vaultVersion == Constants.INITIAL_VAULT_VERSION) {
            CryptoService.generateLegacySecretKey(masterKey, salt, cipherAlgorithm)
        } else {
            CryptoService.generateSecretKey(masterKey, cipherAlgorithm)
        }
        masterKey.clear()

        val material = VaultKeyMaterial(
            salt = salt,
            storedMasterKey = stored.stored,
            cipherAlgorithm = cipherAlgorithm,
            kdfConfig = kdfConfig,
            vaultVersion = vaultVersion,
        )
        // The session gets its own copy so close() cannot wipe the persisted material.
        val session = VaultSession(masterSK, masterKeyHash, Key(salt.data.copyOf()), cipherAlgorithm)
        return material to session
    }

    /**
     * Unlocks an existing vault. Returns `null` when PIN/password are wrong or the
     * wrapping key is missing.
     */
    fun unlock(
        pin: Password,
        masterPassword: Password,
        material: VaultKeyMaterial,
        wrappingKeyStore: WrappingKeyStore,
    ): VaultSession? {
        val passphraseSK = MasterKeyService.getMasterPassPhraseSecretKey(
            pin,
            masterPassword,
            material.salt,
            material.cipherAlgorithm,
            material.kdfConfig,
        )
        val useLegacy = material.vaultVersion == Constants.INITIAL_VAULT_VERSION
        val pair = MasterKeyService.getMasterSecretKey(
            passphraseSK,
            material.salt,
            material.storedMasterKey,
            useLegacy,
            wrappingKeyStore,
        ) ?: return null

        return VaultSession(
            masterKeySK = pair.first,
            masterKeyHash = pair.second,
            salt = Key(material.salt.data.copyOf()),
            cipherAlgorithm = material.cipherAlgorithm,
        )
    }
}
