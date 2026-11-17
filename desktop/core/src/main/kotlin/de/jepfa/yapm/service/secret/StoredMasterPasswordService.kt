package de.jepfa.yapm.service.secret

import de.jepfa.yapm.crypto.KeyAlias
import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder

/**
 * Stores the master password in the platform keystore, mirroring Android's
 * optional "store master password" feature. When present, the desktop unlock
 * flow only needs the PIN; the master password is unwrapped from the keystore.
 *
 * Security trade-off: this reduces the two factors to the PIN plus access to the
 * unlocked keyring. It is opt-in per vault.
 */
object StoredMasterPasswordService {

    fun protect(masterPassword: Password, keyStore: WrappingKeyStore): Encrypted {
        val holder = SecretKeyHolder(
            keyStore.getOrCreateKey(KeyAlias.MASTER_PASSWORD),
            DEFAULT_CIPHER_ALGORITHM,
        )
        return CryptoService.encryptPassword(holder, masterPassword)
    }

    fun reveal(encrypted: Encrypted, keyStore: WrappingKeyStore): Password {
        val holder = SecretKeyHolder(
            keyStore.getOrCreateKey(KeyAlias.MASTER_PASSWORD),
            DEFAULT_CIPHER_ALGORITHM,
        )
        return CryptoService.decryptPassword(holder, encrypted)
    }

    fun forget(keyStore: WrappingKeyStore) {
        keyStore.deleteKey(KeyAlias.MASTER_PASSWORD)
    }
}
