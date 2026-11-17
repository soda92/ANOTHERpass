package de.jepfa.yapm.database.entity

/** Column layout mirrors the Android Room entities (schema v10). */

data class EncCredentialEntity(
    val id: Int?,
    val uid: String?,
    val name: String,
    val additionalInfo: String,
    val user: String,
    val password: String,
    val lastPassword: String?,
    val website: String,
    val labels: String,
    val expiresAt: String?,
    val isObfuscated: Boolean,
    val isLastPasswordObfuscated: Boolean,
    val otpData: String?,
    val modifyTimestamp: Long,
    val pinned: Boolean,
)

data class EncLabelEntity(
    val id: Int?,
    val uid: String?,
    val name: String,
    val description: String,
    val color: Int?,
)

data class EncUsernameTemplateEntity(
    val id: Int?,
    val username: String,
    val description: String,
    val generatorType: String,
)

data class EncWebExtensionEntity(
    val id: Int?,
    val webClientId: String,
    val title: String,
    val extensionPublicKey: String,
    val sharedBaseKey: String,
    val linked: Boolean,
    val enabled: Boolean,
    val bypassIncomingRequests: Boolean,
    val lastUsedTimestamp: Long?,
)
