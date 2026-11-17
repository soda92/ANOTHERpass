package de.jepfa.yapm.application

import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.model.Validable
import de.jepfa.yapm.model.encrypted.EncCredential
import de.jepfa.yapm.model.encrypted.EncLabel
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.encrypted.OtpData
import de.jepfa.yapm.model.encrypted.PasswordData
import de.jepfa.yapm.model.encrypted.TimeData
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.util.Constants
import de.jepfa.yapm.vault.VaultSession
import java.util.UUID

/**
 * Application-level credential and label CRUD over an unlocked vault.
 *
 * Converts between plain values used by the UI and the encrypted entity fields
 * stored in SQLite, preserving Android-compatible behaviour such as the
 * "last password" history and label references.
 */
class VaultService(
    private val repositories: VaultRepositories,
    private val session: VaultSession,
) {

    data class LabelView(
        val id: Int,
        val name: String,
        val description: String,
        val color: Int?,
    )

    data class CredentialView(
        val id: Int,
        val name: String,
        val website: String,
        val user: String,
        val password: String,
        val additionalInfo: String,
        val labelIds: List<Int>,
        val expiresAt: Long?,
        val isExpired: Boolean,
        val pinned: Boolean,
        val modifyTimestamp: Long?,
        val otpAuthUri: String?,
    )

    data class CredentialDraft(
        val id: Int? = null,
        val name: String,
        val website: String = "",
        val user: String = "",
        val password: String = "",
        val additionalInfo: String = "",
        val labelIds: List<Int> = emptyList(),
        val expiresAt: Long? = null,
        val pinned: Boolean = false,
        val otpAuthUri: String? = null,
    )

    // ------------------------------------------------------------------
    // Credentials
    // ------------------------------------------------------------------

    fun listCredentials(): List<CredentialView> =
        repositories.credentials.findAll()
            .map(::toView)
            .sortedWith(
                compareBy<CredentialView> { !it.pinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )

    fun findCredential(id: Int): CredentialView? =
        repositories.credentials.findById(id)?.let(::toView)

    fun saveCredential(draft: CredentialDraft): Int {
        require(draft.name.isNotBlank()) { "Credential name must not be blank" }

        val existing = draft.id?.let { repositories.credentials.findById(it) }
        val now = System.currentTimeMillis()

        val passwordEncrypted = session.encryptPassword(Password(draft.password))
        val passwordChanged = existing == null || decrypt(existing.passwordData.password) != draft.password
        val lastPassword = when {
            existing == null -> null
            passwordChanged -> existing.passwordData.password
            else -> existing.passwordData.lastPassword
        }

        val labelIds = draft.labelIds
            .distinct()
            .take(Constants.MAX_LABELS_PER_CREDENTIAL)
        val expiresAt = draft.expiresAt
            ?.takeIf { it > 0 }
            ?.let { session.encryptLong(it) }
            ?: Encrypted.empty()

        val credential = EncCredential(
            id = draft.id,
            uid = existing?.uid ?: UUID.randomUUID(),
            name = session.encryptString(draft.name),
            website = session.encryptString(draft.website),
            user = session.encryptString(draft.user),
            additionalInfo = session.encryptString(draft.additionalInfo),
            labels = session.encryptString(labelIds.joinToString(",")),
            passwordData = PasswordData(
                password = passwordEncrypted,
                isObfuscated = false,
                lastPassword = lastPassword,
                isLastPasswordObfuscated = existing?.passwordData?.isLastPasswordObfuscated ?: false,
            ),
            timeData = TimeData(now, expiresAt),
            otpData = draft.otpAuthUri
                ?.takeIf { it.isNotBlank() }
                ?.let { OtpData(session.encryptString(it)) },
            pinned = draft.pinned,
        )

        return if (existing == null) {
            repositories.credentials.insert(credential).id!!
        } else {
            check(repositories.credentials.update(credential)) { "Credential ${draft.id} no longer exists" }
            credential.id!!
        }
    }

    fun deleteCredential(id: Int): Boolean =
        repositories.credentials.deleteById(id)

    fun duplicateCredential(id: Int): Int? {
        val original = repositories.credentials.findById(id) ?: return null
        val copy = original.copy(
            id = null,
            uid = UUID.randomUUID(),
            name = session.encryptString("${decrypt(original.name)} (copy)"),
        )
        return repositories.credentials.insert(copy).id
    }

    // ------------------------------------------------------------------
    // Labels
    // ------------------------------------------------------------------

    fun listLabels(): List<LabelView> =
        repositories.labels.findAll()
            .map { LabelView(it.id ?: 0, decrypt(it.name), decrypt(it.description), it.color) }
            .sortedBy { it.name.lowercase() }

    fun saveLabel(id: Int?, name: String, description: String, color: Int?): Int {
        require(name.isNotBlank()) { "Label name must not be blank" }
        val existing = id?.let { repositories.labels.findById(it) }
        val label = EncLabel(
            id = id,
            uid = existing?.uid ?: UUID.randomUUID(),
            name = session.encryptString(name),
            description = session.encryptString(description),
            color = color,
        )
        return if (existing == null) {
            repositories.labels.insert(label).id!!
        } else {
            check(repositories.labels.update(label)) { "Label $id no longer exists" }
            id
        }
    }

    /** Deletes a label and removes its id from every credential that references it. */
    fun deleteLabel(id: Int) {
        repositories.db.transaction {
            repositories.credentials.findAll().forEach { credential ->
                val current = decrypt(credential.labels)
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                if (id in current) {
                    val updated = credential.copy(
                        labels = session.encryptString((current - id).joinToString(","))
                    )
                    repositories.credentials.update(updated)
                }
            }
            repositories.labels.deleteById(id)
        }
    }

    // ------------------------------------------------------------------
    // Generation / strength
    // ------------------------------------------------------------------

    fun generatePassword(spec: de.jepfa.yapm.service.secretgenerator.password.PasswordGeneratorSpec): String =
        de.jepfa.yapm.service.secretgenerator.password.PasswordGenerator()
            .generate(spec)
            .decodeToCharArray()
            .concatToString()

    fun generatePassphrase(spec: de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGeneratorSpec): String =
        de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGenerator()
            .generate(spec)
            .decodeToCharArray()
            .concatToString()

    fun generatePin(spec: de.jepfa.yapm.service.secretgenerator.pin.PinGeneratorSpec): String =
        de.jepfa.yapm.service.secretgenerator.pin.PinGenerator()
            .generate(spec)
            .decodeToCharArray()
            .concatToString()

    fun evaluatePassword(password: String): PasswordStrength.Result =
        PasswordStrength.evaluate(Password(password))

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private fun toView(credential: EncCredential): CredentialView {
        val expiresAt = runCatching { session.decryptLong(credential.timeData.expiresAt) }.getOrNull()
        return CredentialView(
            id = credential.id ?: 0,
            name = decrypt(credential.name),
            website = decrypt(credential.website),
            user = decrypt(credential.user),
            password = decrypt(credential.passwordData.password),
            additionalInfo = decrypt(credential.additionalInfo),
            labelIds = decrypt(credential.labels)
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() },
            expiresAt = expiresAt,
            isExpired = expiresAt != null && expiresAt > 0 && expiresAt < System.currentTimeMillis(),
            pinned = credential.pinned,
            modifyTimestamp = credential.timeData.modifyTimestamp,
            otpAuthUri = credential.otpData?.let { decrypt(it.encOtpAuthUri) },
        )
    }

    private fun decrypt(encrypted: Encrypted): String {
        val text = runCatching { session.decryptString(encrypted) }.getOrDefault("")
        return if (text == Validable.FAILED_STRING) "" else text
    }
}
