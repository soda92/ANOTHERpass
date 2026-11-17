package de.jepfa.yapm.service.secret

import de.jepfa.yapm.crypto.KeyAlias
import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.encrypted.EncryptedType.Types.ENC_MASTER_KEY
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.util.sha256

/**
 * Port of the Android `MasterKeyService`.
 *
 * The master key is encrypted twice:
 *  1. inner: with the PIN+master-password passphrase key ([ENC_MASTER_KEY]),
 *  2. outer: with the platform keystore key `MK`.
 *
 * Vault exports contain only the inner layer, which is why desktop imports work.
 */
object MasterKeyService {

    const val MASTER_KEY_BYTE_SIZE = 128

    data class StoredMasterKey(val inner: Encrypted, val stored: Encrypted)

    fun getMasterPassPhraseSecretKey(
        masterPin: Password,
        masterPassword: Password,
        salt: Key,
        cipherAlgorithm: CipherAlgorithm,
        kdfConfig: KdfConfig,
    ): SecretKeyHolder {
        val masterPassPhrase = CryptoService.conjunctPasswords(masterPin, masterPassword, salt)
        val masterPassPhraseSK = CryptoService.generateSecretKeyForMasterKey(
            masterPassPhrase, salt, cipherAlgorithm, kdfConfig
        )
        masterPassPhrase.clear()
        return masterPassPhraseSK
    }

    fun getMasterSecretKey(
        masterPassPhraseSK: SecretKeyHolder,
        salt: Key,
        storedEncMasterKey: Encrypted,
        useLegacyGeneration: Boolean,
        wrappingKeyStore: WrappingKeyStore,
    ): Pair<SecretKeyHolder, Key>? {
        val masterKey = decryptMasterKey(masterPassPhraseSK, storedEncMasterKey, wrappingKeyStore) ?: return null

        val masterKeyHash = Key(masterKey.data.sha256())

        val masterSK = if (useLegacyGeneration) {
            // v1 vaults: extra legacy KDF, kept for compatibility only.
            CryptoService.generateLegacySecretKey(masterKey, salt, masterPassPhraseSK.cipherAlgorithm)
        } else {
            // v2+: the KDF already happened on the passphrase key.
            CryptoService.generateSecretKey(masterKey, masterPassPhraseSK.cipherAlgorithm)
        }
        masterKey.clear()

        return Pair(masterSK, masterKeyHash)
    }

    fun generateMasterKey(): Key =
        CryptoService.generateRandomKey(MASTER_KEY_BYTE_SIZE)

    fun decryptMasterKey(
        masterPassPhraseSK: SecretKeyHolder,
        storedEncMasterKey: Encrypted,
        wrappingKeyStore: WrappingKeyStore,
    ): Key? {
        return try {
            val mkSK = wrappingKeyHolder(wrappingKeyStore)
            val encMasterKey = CryptoService.decryptEncrypted(mkSK, storedEncMasterKey)
            val masterKey = CryptoService.decryptKey(masterPassPhraseSK, encMasterKey)
            if (!masterKey.isValid()) null else masterKey
        } catch (_: Exception) {
            // Wrong key, corrupted payload, or a rotated/regenerated keystore key.
            null
        }
    }

    fun encryptMasterKey(
        masterKey: Key,
        pin: Password,
        masterPasswd: Password,
        salt: Key,
        kdfConfig: KdfConfig,
        cipherAlgorithm: CipherAlgorithm,
        wrappingKeyStore: WrappingKeyStore,
    ): StoredMasterKey {
        val mkSK = wrappingKeyHolder(wrappingKeyStore)

        val masterPassphrase = CryptoService.conjunctPasswords(pin, masterPasswd, salt)
        val masterPassphraseSK = CryptoService.generateSecretKeyForMasterKey(
            masterPassphrase, salt, cipherAlgorithm, kdfConfig
        )
        masterPassphrase.clear()

        val kdfEncoded = kdfConfig.toEncodedString()
        val inner = CryptoService.encryptKey(
            EncryptedType(ENC_MASTER_KEY, kdfEncoded),
            masterPassphraseSK,
            masterKey,
        )
        val stored = CryptoService.encryptEncrypted(mkSK, inner)

        masterPassphraseSK.destroy()
        return StoredMasterKey(inner, stored)
    }

    private fun wrappingKeyHolder(wrappingKeyStore: WrappingKeyStore): SecretKeyHolder =
        SecretKeyHolder(
            wrappingKeyStore.getOrCreateKey(KeyAlias.MASTER_KEY),
            DEFAULT_CIPHER_ALGORITHM,
        )
}
