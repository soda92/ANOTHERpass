package de.jepfa.yapm.util

object Constants {
    const val MASTER_KEY_BYTE_SIZE = 128

    const val INITIAL_VAULT_VERSION = 1
    const val FAST_KEYGEN_VAULT_VERSION = 2
    const val CURRENT_VERSION = FAST_KEYGEN_VAULT_VERSION

    const val MIN_PIN_LENGTH = 6
    const val MAX_CREDENTIAL_PASSWD_LENGTH = 50
    const val MAX_LABELS_PER_CREDENTIAL = 5
}
