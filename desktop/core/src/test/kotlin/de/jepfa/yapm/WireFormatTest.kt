package de.jepfa.yapm

import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.DEFAULT_CIPHER_ALGORITHM
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.EncryptedType
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.CryptoService
import de.jepfa.yapm.service.secret.KdfParameterService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireFormatTest {

    @Test
    fun `cipher algorithm ordinals are stable`() {
        assertEquals(
            listOf("AES_128", "AES_256", "BLOWFISH_128", "BLOWFISH_256", "CHACHACHA20"),
            CipherAlgorithm.entries.map { it.name },
        )
    }

    @Test
    fun `encrypted serializes to the android wire format`() {
        val encrypted = Encrypted(
            null,
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5, 6),
            DEFAULT_CIPHER_ALGORITHM,
        )
        // base64(AQID) + base64(BAUG) + AES_128 ordinal 0; null type = empty field
        assertEquals(":AQID:BAUG:0", encrypted.toBase64String())
    }

    @Test
    fun `encrypted round trips including type payload`() {
        val encrypted = Encrypted(
            EncryptedType(EncryptedType.Types.ENC_MASTER_KEY, "aBQ_QA"),
            byteArrayOf(9, 8, 7, 6),
            byteArrayOf(5, 4, 3, 2, 1),
            CipherAlgorithm.AES_256,
        )
        val parsed = Encrypted.fromBase64String(encrypted.toBase64String())
        assertEquals(encrypted, parsed)
        assertEquals("EMK#aBQ_QA", parsed.type?.serialize())
        assertEquals(CipherAlgorithm.AES_256.ordinal, parsed.cipherAlgorithm.ordinal)
    }

    @Test
    fun `empty encrypted round trips`() {
        val parsed = Encrypted.fromBase64String(Encrypted.empty().toBase64String())
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `kdf config encodes exactly like android`() {
        assertEquals(
            "pGGoA_",
            KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 100_000, null).toEncodedString(),
        )
        assertEquals(
            "aBQ_QA",
            KdfConfig(KeyDerivationFunction.ARGON2_ID, 5, 64).toEncodedString(),
        )
        assertEquals(
            "pEAAA_",
            KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 65_536, null).toEncodedString(),
        )
    }

    @Test
    fun `kdf config decodes exactly like android`() {
        assertEquals(
            KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 100_000, null),
            KdfConfig.fromEncodedString("pGGoA_"),
        )
        assertEquals(
            KdfConfig(KeyDerivationFunction.ARGON2_ID, 5, 64),
            KdfConfig.fromEncodedString("aBQ_QA"),
        )
        // old PBKDF-only format without the delimiter
        assertEquals(
            KdfConfig(KeyDerivationFunction.BUILT_IN_PBKDF, 100_000, null),
            KdfConfig.fromEncodedString("GGoA"),
        )
        assertNull(KdfConfig.fromEncodedString("pAAAA_")) // below min iterations
    }

    @Test
    fun `conjunct passwords matches the android digest`() {
        val salt = Key(ByteArray(16) { it.toByte() })
        val result = CryptoService.conjunctPasswords(Password("123456"), Password("master"), salt)
        val expected = "78a192ee26acf9bc35dc074bb43a02018d227c192f426b9d74f98b4d43b98f3b"
        assertEquals(expected, result.data.toHex())
    }
}

class CryptoRoundTripTest {

    @Test
    fun `all cipher algorithms round trip a password`() {
        for (algorithm in CipherAlgorithm.entries) {
            val keyBytes = ByteArray(algorithm.keyLength / 8) { (it * 7 + 3).toByte() }
            val holder = CryptoService.generateSecretKey(Key(keyBytes), algorithm)

            val password = Password("correct horse battery staple 123!")
            val encrypted = CryptoService.encryptPassword(holder, password)
            val decrypted = CryptoService.decryptPassword(holder, encrypted)

            assertContentEquals(
                password.toByteArray(),
                decrypted.toByteArray(),
                "round trip failed for $algorithm",
            )
            assertEquals(algorithm, encrypted.cipherAlgorithm)
            assertTrue(encrypted.iv.isNotEmpty(), "IV expected for $algorithm")
        }
    }

    @Test
    fun `common strings and long values round trip`() {
        val holder = CryptoService.generateSecretKey(Key(ByteArray(32)), CipherAlgorithm.AES_256)
        val text = "line1\nline2 \"quoted\" ünïcødé"
        assertEquals(text, CryptoService.decryptCommonString(holder, CryptoService.encryptCommonString(holder, text)))
        assertEquals(
            1_700_000_000_000L,
            CryptoService.decryptLong(holder, CryptoService.encryptLong(holder, 1_700_000_000_000L)),
        )
        assertNull(CryptoService.decryptLong(holder, Encrypted.empty()))
    }

    @Test
    fun `decrypt with wrong algorithm fails safely`() {
        val aes = CryptoService.generateSecretKey(Key(ByteArray(32) { 1 }), CipherAlgorithm.AES_256)
        val encrypted = CryptoService.encryptPassword(aes, Password("secret"))
        val blowfish = CryptoService.generateSecretKey(Key(ByteArray(32) { 1 }), CipherAlgorithm.BLOWFISH_256)
        val decrypted = CryptoService.decryptPassword(blowfish, encrypted)
        assertTrue(!decrypted.isValid())
    }

    @Test
    fun `tampered ciphertext fails safely`() {
        val holder = CryptoService.generateSecretKey(Key(ByteArray(32) { 2 }), CipherAlgorithm.AES_256)
        val encrypted = CryptoService.encryptPassword(holder, Password("secret"))
        val tampered = encrypted.copy(data = encrypted.data.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        assertTrue(!CryptoService.decryptPassword(holder, tampered).isValid())
    }

    @Test
    fun `pbkdf2 respects the requested key length`() {
        val salt = Key(ByteArray(16) { it.toByte() })
        for (algorithm in listOf(CipherAlgorithm.AES_128, CipherAlgorithm.AES_256)) {
            val holder = CryptoService.generatePBESecretKeyForGivenIterations(
                Password("pw"), salt, KdfParameterService.MIN_PBKDF_ITERATIONS, algorithm
            )
            assertEquals(algorithm.keyLength / 8, holder.secretKey.encoded.size)
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
