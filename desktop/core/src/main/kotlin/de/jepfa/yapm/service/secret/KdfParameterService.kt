package de.jepfa.yapm.service.secret

object KdfParameterService {

    const val MIN_PBKDF_ITERATIONS = 10_000
    const val LEGACY_PBKDF_ITERATIONS = 65_536
    const val MPT_PBKDF_ITERATIONS = 65_536
    const val DEFAULT_PBKDF_ITERATIONS = 100_000
    const val MAX_PBKDF_ITERATIONS = 2_000_000 + MIN_PBKDF_ITERATIONS

    const val MIN_ARGON_ITERATIONS = 1
    const val DEFAULT_ARGON_ITERATIONS = 5
    const val MAX_ARGON_ITERATIONS = 10

    const val MIN_ARGON_MIB = 12
    const val DEFAULT_ARGON_MIB = 64
    const val MAX_ARGON_MIB = 256 + MIN_ARGON_MIB

    const val DEFAULT_ARGON2_PARALLELISM = 4
}
