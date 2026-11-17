package de.jepfa.yapm

import de.jepfa.yapm.crypto.Argon2Deriver
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reference vectors generated with `argon2-cffi` (libargon2, version 19).
 * This proves BouncyCastle output matches the Android `argon2kt` library,
 * which wraps the same reference implementation.
 */
class Argon2VectorTest {

    private val password = Password("correct horse battery staple")
    private val salt = Key(ByteArray(16) { it.toByte() })

    @Test
    fun `argon2id matches reference libargon2`() {
        val derived = Argon2Deriver.derive(
            password, salt, KdfConfig(KeyDerivationFunction.ARGON2_ID, 1, 12)
        )
        assertEquals(
            "6ccb821fda349fdd01e2ae999584ffcbca2c248bd36a6f2573c15ac71bbdd84f",
            derived.data.toHex(),
        )
    }

    @Test
    fun `argon2i matches reference libargon2`() {
        val derived = Argon2Deriver.derive(
            password, salt, KdfConfig(KeyDerivationFunction.ARGON2_I, 1, 12)
        )
        assertEquals(
            "31b6439e4ef01cb663ef248c3a5f0a924798b8d48a227bf915d0d21ed687b55e",
            derived.data.toHex(),
        )
    }

    @Test
    fun `argon2d matches reference libargon2`() {
        val derived = Argon2Deriver.derive(
            password, salt, KdfConfig(KeyDerivationFunction.ARGON2_D, 1, 12)
        )
        assertEquals(
            "bc8767ecf391ec5d48e29b0c3df847f69d17d678ce70e1d6b81c8e53f8bf214e",
            derived.data.toHex(),
        )
    }

    @Test
    fun `higher cost argon2id matches reference libargon2`() {
        val derived = Argon2Deriver.derive(
            Password("password"),
            Key(ByteArray(32) { it.toByte() }),
            KdfConfig(KeyDerivationFunction.ARGON2_ID, 2, 64),
        )
        assertEquals(
            "dc9e294de173b8231823339ffa560bd84361fd6dc48b1180828a2320857b0466",
            derived.data.toHex(),
        )
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
