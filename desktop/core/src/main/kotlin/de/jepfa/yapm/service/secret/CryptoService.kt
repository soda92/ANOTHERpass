package de.jepfa.yapm.service.secret

import de.jepfa.yapm.crypto.Argon2Deriver
import de.jepfa.yapm.model.Validable.Companion.FAILED_BYTE_ARRAY
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.model.secret.SecretKeyHolder
import de.jepfa.yapm.util.sha256
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM port of the Android `SecretService` crypto primitives.
 *
 * Behaviour and byte output are preserved. The Android-keystore-specific methods
 * were replaced by [de.jepfa.yapm.crypto.WrappingKeyStore].
 */
object CryptoService {

    private val random = SecureRandom()

    fun getSecureRandom(): SecureRandom = random

    fun generateRandomKey(length: Int): Key {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return Key(bytes)
    }

    // ---------------------------------------------------------------------
    // Key derivation
    // ---------------------------------------------------------------------

    /** No KDF (v2 vaults): use the raw key bytes directly. */
    fun generateSecretKey(data: Key, cipherAlgorithm: CipherAlgorithm): SecretKeyHolder {
        val keyBytes = data.data.copyOf(cipherAlgorithm.keyLength / 8)
        val sk = SecretKeySpec(keyBytes, cipherAlgorithm.jceKeyAlgorithm)
        return SecretKeyHolder(sk, cipherAlgorithm)
    }

    /** Used by v1 vaults for downward compatibility. */
    fun generateLegacySecretKey(data: Key, salt: Key, cipherAlgorithm: CipherAlgorithm): SecretKeyHolder =
        generatePBESecretKeyForGivenIterations(
            Password(data), salt, KdfParameterService.LEGACY_PBKDF_ITERATIONS, cipherAlgorithm
        )

    /** Master password token key (low iteration count by design). */
    fun generateSecretKeyForMPT(data: Key, salt: Key, cipherAlgorithm: CipherAlgorithm): SecretKeyHolder =
        generatePBESecretKeyForGivenIterations(
            Password(data), salt, KdfParameterService.MPT_PBKDF_ITERATIONS, cipherAlgorithm
        )

    /** Password obfuscation key. */
    fun generateSecretKeyForObfuscation(
        password: Password,
        salt: Key,
        cipherAlgorithm: CipherAlgorithm,
    ): SecretKeyHolder = generatePBESecretKeyForGivenIterations(password, salt, 1000, cipherAlgorithm)

    /**
     * Derives the key that protects the master key from PIN + master password.
     * Uses Argon2 when configured, otherwise PBKDF2.
     */
    fun generateSecretKeyForMasterKey(
        combinedPinAndMasterPassword: Password,
        salt: Key,
        cipherAlgorithm: CipherAlgorithm,
        kdfConfig: KdfConfig,
    ): SecretKeyHolder {
        return if (kdfConfig.isArgon2()) {
            val argonDerivedKey = Argon2Deriver.derive(combinedPinAndMasterPassword, salt, kdfConfig)
            generateSecretKey(argonDerivedKey, cipherAlgorithm)
        } else {
            generatePBESecretKeyForGivenIterations(
                combinedPinAndMasterPassword,
                salt,
                kdfConfig.iterations,
                cipherAlgorithm,
            )
        }
    }

    fun generatePBESecretKeyForGivenIterations(
        password: Password,
        salt: Key,
        iterations: Int,
        cipherAlgorithm: CipherAlgorithm,
    ): SecretKeyHolder {
        val keySpec = PBEKeySpec(
            password.toEncodedCharArray(),
            salt.data,
            iterations,
            cipherAlgorithm.keyLength,
        )
        val factory = SecretKeyFactory.getInstance(cipherAlgorithm.keyDerivationAlgorithm)
        val generated = try {
            factory.generateSecret(keySpec)
        } finally {
            keySpec.clearPassword()
        }
        // Android keeps the PBKDF2-labelled key; JCE ciphers on desktop want the
        // real algorithm label. The key bytes (and therefore ciphertext) are equal.
        val sk = SecretKeySpec(generated.encoded, cipherAlgorithm.jceKeyAlgorithm)
        try {
            generated.destroy()
        } catch (_: Exception) {
            // best effort
        }
        return SecretKeyHolder(sk, cipherAlgorithm)
    }

    // ---------------------------------------------------------------------
    // Composition / hashing
    // ---------------------------------------------------------------------

    /** SHA-256(salt || pin || masterPassword), mapped back to bytes exactly like Android. */
    fun conjunctPasswords(password1: Password, password2: Password, salt: Key): Password {
        val message = MessageDigest.getInstance("SHA-256")
        message.update(salt.data)
        message.update(password1.toByteArray())
        message.update(password2.toByteArray())
        val digest = message.digest()
        val result = digest.map { it.toInt().toChar() }.toCharArray()
        return Password(result)
    }

    fun conjunctKeys(key1: Key, key2: Key, key3: Key? = null): Key {
        val key = if (key3 != null) {
            key1.toByteArray() + key2.toByteArray() + key3.toByteArray()
        } else {
            key1.toByteArray() + key2.toByteArray()
        }
        return Key(MessageDigest.getInstance("SHA-256").digest(key))
    }

    fun secretKeyToKey(secretKeyHolder: SecretKeyHolder, salt: Key): Key =
        fastHash(secretKeyHolder.secretKey.encoded, salt)

    fun fastHash(data: ByteArray, salt: Key): Key {
        val message = MessageDigest.getInstance("SHA-256")
        message.update(salt.data)
        message.update(data)
        return Key(message.digest())
    }

    fun deriveClientKey(key: SecretKeyHolder, webClientId: String, salt: Key): Key =
        secretKeyToKey(key, conjunctKeys(salt, Key(webClientId.toByteArray())))

    fun masterKeyHash(masterKey: Key): Key = Key(masterKey.data.sha256())

    // ---------------------------------------------------------------------
    // Encrypt / decrypt
    // ---------------------------------------------------------------------

    fun encryptKey(secretKeyHolder: SecretKeyHolder, key: Key): Encrypted =
        encryptData(null, secretKeyHolder, key.data)

    fun encryptKey(
        type: EncryptedType,
        secretKeyHolder: SecretKeyHolder,
        key: Key,
    ): Encrypted = encryptData(type, secretKeyHolder, key.data)

    fun decryptKey(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): Key =
        Key(decryptData(secretKeyHolder, encrypted))

    fun encryptPassword(secretKeyHolder: SecretKeyHolder, password: Password): Encrypted =
        encryptData(null, secretKeyHolder, password.toByteArray())

    fun encryptPassword(
        type: EncryptedType,
        secretKeyHolder: SecretKeyHolder,
        password: Password,
    ): Encrypted = encryptData(type, secretKeyHolder, password.toByteArray())

    fun decryptPassword(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): Password =
        Password(decryptData(secretKeyHolder, encrypted))

    fun encryptCommonString(type: EncryptedType, secretKeyHolder: SecretKeyHolder, string: String): Encrypted =
        encryptData(type, secretKeyHolder, string.toByteArray(Charsets.UTF_8))

    fun encryptCommonString(secretKeyHolder: SecretKeyHolder, string: String): Encrypted =
        encryptData(null, secretKeyHolder, string.toByteArray(Charsets.UTF_8))

    fun decryptCommonString(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): String =
        String(decryptData(secretKeyHolder, encrypted), Charsets.UTF_8)

    fun encryptLong(secretKeyHolder: SecretKeyHolder, long: Long): Encrypted =
        encryptData(null, secretKeyHolder, long.toString().toByteArray(Charsets.UTF_8))

    fun decryptLong(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): Long? {
        if (encrypted.isEmpty()) return null
        return String(decryptData(secretKeyHolder, encrypted), Charsets.UTF_8).toLongOrNull()
    }

    fun encryptEncrypted(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): Encrypted =
        encryptData(encrypted.type, secretKeyHolder, encrypted.toBase64())

    fun decryptEncrypted(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): Encrypted {
        val bytes = decryptData(secretKeyHolder, encrypted)
        if (bytes.contentEquals(FAILED_BYTE_ARRAY) || bytes.isEmpty()) {
            return Encrypted.empty()
        }
        return Encrypted.fromBase64(bytes)
    }

    fun encryptData(type: EncryptedType?, secretKeyHolder: SecretKeyHolder, data: ByteArray): Encrypted {
        val cipher = newCipher(secretKeyHolder.cipherAlgorithm)
        if (secretKeyHolder.cipherAlgorithm.integratedIvSupport) {
            try {
                cipher.init(Cipher.ENCRYPT_MODE, secretKeyHolder.secretKey)
            } catch (_: GeneralSecurityException) {
                // Some providers (e.g. ChaCha20-Poly1305) require an explicit nonce.
                val nonce = ByteArray(12)
                random.nextBytes(nonce)
                cipher.init(Cipher.ENCRYPT_MODE, secretKeyHolder.secretKey, IvParameterSpec(nonce))
            }
        } else {
            val iv = ByteArray(cipher.blockSize)
            random.nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeyHolder.secretKey, IvParameterSpec(iv))
        }
        return Encrypted(type, cipher.iv, cipher.doFinal(data), secretKeyHolder.cipherAlgorithm)
    }

    fun decryptData(secretKeyHolder: SecretKeyHolder, encrypted: Encrypted): ByteArray {
        if (encrypted.isEmpty()) return FAILED_BYTE_ARRAY
        if (secretKeyHolder.cipherAlgorithm != encrypted.cipherAlgorithm) return FAILED_BYTE_ARRAY

        return try {
            val cipher = newCipher(encrypted.cipherAlgorithm)
            if (encrypted.cipherAlgorithm.gcmSupport) {
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeyHolder.secretKey,
                    GCMParameterSpec(128, encrypted.iv),
                )
            } else {
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeyHolder.secretKey,
                    IvParameterSpec(encrypted.iv),
                )
            }
            cipher.doFinal(encrypted.data)
        } catch (_: GeneralSecurityException) {
            FAILED_BYTE_ARRAY
        }
    }

    private fun newCipher(cipherAlgorithm: CipherAlgorithm): Cipher {
        var last: GeneralSecurityException? = null
        for (name in cipherAlgorithm.jceCipherNames()) {
            try {
                return Cipher.getInstance(name)
            } catch (e: GeneralSecurityException) {
                last = e
            }
        }
        throw last ?: GeneralSecurityException("No JCE provider for ${cipherAlgorithm.cipherName}")
    }
}
