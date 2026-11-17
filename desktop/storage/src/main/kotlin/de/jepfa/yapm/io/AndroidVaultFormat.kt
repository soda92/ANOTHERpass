package de.jepfa.yapm.io

/** JSON keys of the Android vault export file. Names (including the typo) are contractual. */
object AndroidVaultFormat {
    const val JSON_APP_VERSION_CODE = "appVersionCode"
    const val JSON_APP_VERSION_NAME = "appVersionName"
    const val JSON_CREATION_DATE = "creationDate"
    const val JSON_VAULT_ID = "vaultId"
    const val JSON_VAULT_VERSION = "vaultVersion"
    const val JSON_CIPHER_ALGORITHM = "cipherAlgoritm"
    const val JSON_ENC_MK = "encMk"
    const val JSON_ENC_SEED = "encSeed"
    const val JSON_CREDENTIALS = "credentials"
    const val JSON_CREDENTIALS_COUNT = "credentialsCount"
    const val JSON_LABELS = "labels"
    const val JSON_LABELS_COUNT = "labelsCount"
    const val JSON_USERNAME_TEMPLATES = "usernameTemplates"
    const val JSON_APP_SETTINGS = "appSettings"
}

class VaultImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
