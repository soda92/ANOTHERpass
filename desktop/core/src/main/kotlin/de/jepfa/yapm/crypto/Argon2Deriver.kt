package de.jepfa.yapm.crypto

import de.jepfa.yapm.model.kdf.Argon2Variant
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.KdfParameterService
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Replaces the Android-only `argon2kt` JNI library with BouncyCastle.
 *
 * Output is identical to libargon2 for the same parameters (v1.3, parallelism 4,
 * memory in KiB, 32-byte hash), so Argon2id vaults remain interchangeable.
 */
object Argon2Deriver {

    private const val HASH_LENGTH_BYTES = 32

    fun derive(password: Password, salt: Key, kdfConfig: KdfConfig): Key {
        require(kdfConfig.isArgon2()) { "KDF is not Argon2" }
        val memMiB = requireNotNull(kdfConfig.memCostInMiB) { "Argon2 memory cost is missing" }

        val type = when (kdfConfig.kdf.argon2Variant ?: Argon2Variant.ID) {
            Argon2Variant.ID -> Argon2Parameters.ARGON2_id
            Argon2Variant.I -> Argon2Parameters.ARGON2_i
            Argon2Variant.D -> Argon2Parameters.ARGON2_d
        }

        val params = Argon2Parameters.Builder(type)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(kdfConfig.iterations)
            .withMemoryAsKB(memMiB * 1024)
            .withParallelism(KdfParameterService.DEFAULT_ARGON2_PARALLELISM)
            .withSalt(salt.data)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(HASH_LENGTH_BYTES)
        generator.generateBytes(password.data, out)
        return Key(out)
    }
}
