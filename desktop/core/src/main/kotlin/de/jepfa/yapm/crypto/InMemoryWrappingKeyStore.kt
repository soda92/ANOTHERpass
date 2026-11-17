package de.jepfa.yapm.crypto

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Test / headless implementation. Keys live only for the JVM process lifetime.
 * Not for production use.
 */
class InMemoryWrappingKeyStore(
    private val random: SecureRandom = SecureRandom(),
) : WrappingKeyStore {

    private val keys = ConcurrentHashMap<String, SecretKey>()

    override fun getOrCreateKey(alias: String): SecretKey =
        keys.computeIfAbsent(alias) { generateAes128() }

    private fun generateAes128(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128, random)
        return keyGen.generateKey()
    }

    override fun deleteKey(alias: String) {
        keys.remove(alias)
    }
}
