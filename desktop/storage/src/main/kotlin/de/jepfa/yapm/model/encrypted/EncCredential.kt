package de.jepfa.yapm.model.encrypted

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.UUID

data class PasswordData(
    var password: Encrypted,
    var isObfuscated: Boolean,
    var lastPassword: Encrypted?,
    var isLastPasswordObfuscated: Boolean?,
)

data class TimeData(
    var modifyTimestamp: Long?,
    var expiresAt: Encrypted,
)

data class OtpData(
    var encOtpAuthUri: Encrypted,
)

/**
 * Desktop port of the Android `EncCredential`. All sensitive fields remain
 * [Encrypted]; use `VaultSession` to decrypt them for display.
 */
data class EncCredential(
    var id: Int?,
    var uid: UUID?,
    override var name: Encrypted,
    var website: Encrypted,
    var user: Encrypted,
    var additionalInfo: Encrypted,
    var labels: Encrypted, // enc(comma-separated label ids)
    var passwordData: PasswordData,
    var timeData: TimeData,
    var otpData: OtpData?,
    var pinned: Boolean,
) : EncNamed {

    constructor(
        id: Int?,
        uid: String?,
        nameBase64: String,
        additionalInfoBase64: String,
        userBase64: String,
        passwordBase64: String,
        lastPasswordBase64: String?,
        websiteBase64: String,
        labelsBase64: String,
        expiresAtBase64: String?,
        isObfuscated: Boolean,
        isLastPasswordObfuscated: Boolean?,
        otpDataBase64: String?,
        modifyTimestamp: Long?,
        pinned: Boolean,
    ) : this(
        id,
        uid?.let { UUID.fromString(it) },
        Encrypted.fromBase64String(nameBase64),
        Encrypted.fromBase64String(websiteBase64),
        Encrypted.fromBase64String(userBase64),
        Encrypted.fromBase64String(additionalInfoBase64),
        Encrypted.fromBase64String(labelsBase64),
        PasswordData(
            Encrypted.fromBase64String(passwordBase64),
            isObfuscated,
            lastPasswordBase64?.let { Encrypted.fromBase64String(it) },
            isLastPasswordObfuscated,
        ),
        TimeData(
            modifyTimestamp,
            expiresAtBase64?.let { Encrypted.fromBase64String(it) } ?: Encrypted.empty(),
        ),
        otpDataBase64?.let { OtpData(Encrypted.fromBase64String(it)) },
        pinned,
    )

    fun isPersistent(): Boolean = id != null

    fun toJson(): JsonObject = JsonObject().apply {
        id?.let { addProperty(ATTRIB_ID, it) }
        uid?.let { addProperty(ATTRIB_UID, it.toString()) }
        addProperty(ATTRIB_NAME, name.toBase64String())
        addProperty(ATTRIB_ADDITIONAL_INFO, additionalInfo.toBase64String())
        addProperty(ATTRIB_USER, user.toBase64String())
        addProperty(ATTRIB_PASSWORD, passwordData.password.toBase64String())
        addProperty(ATTRIB_IS_OBFUSCATED, passwordData.isObfuscated)
        passwordData.lastPassword?.let { addProperty(ATTRIB_LAST_PASSWORD, it.toBase64String()) }
        passwordData.isLastPasswordObfuscated?.let {
            addProperty(ATTRIB_IS_LAST_PASSWORD_OBFUSCATED, it)
        }
        addProperty(ATTRIB_WEBSITE, website.toBase64String())
        addProperty(ATTRIB_LABELS, labels.toBase64String())
        addProperty(ATTRIB_EXPIRES_AT, timeData.expiresAt.toBase64String())
        addProperty(ATTRIB_PINNED, pinned)
        timeData.modifyTimestamp?.let { addProperty(ATTRIB_MODIFY_TIMESTAMP, it) }
        otpData?.encOtpAuthUri?.let { addProperty(ATTRIB_OTP_DATA, it.toBase64String()) }
    }

    companion object {
        const val ATTRIB_ID = "id"
        const val ATTRIB_UID = "uid"
        const val ATTRIB_NAME = "name"
        const val ATTRIB_ADDITIONAL_INFO = "additionalInfo"
        const val ATTRIB_USER = "user"
        const val ATTRIB_PASSWORD = "password"
        const val ATTRIB_LAST_PASSWORD = "lastPassword"
        const val ATTRIB_WEBSITE = "website"
        const val ATTRIB_LABELS = "labels"
        const val ATTRIB_EXPIRES_AT = "expiresAt"
        const val ATTRIB_IS_OBFUSCATED = "isObfuscated"
        const val ATTRIB_IS_LAST_PASSWORD_OBFUSCATED = "isLastPasswordObfuscated"
        const val ATTRIB_OTP_DATA = "otpData"
        const val ATTRIB_MODIFY_TIMESTAMP = "modifyTimestamp"
        const val ATTRIB_PINNED = "pinned"

        fun fromJson(json: JsonElement): EncCredential? {
            return try {
                val o = json.asJsonObject
                EncCredential(
                    o.get(ATTRIB_ID)?.asInt,
                    o.get(ATTRIB_UID)?.takeIf { !it.isJsonNull }?.asString,
                    o.get(ATTRIB_NAME).asString,
                    o.get(ATTRIB_ADDITIONAL_INFO).asString,
                    o.get(ATTRIB_USER).asString,
                    o.get(ATTRIB_PASSWORD).asString,
                    o.get(ATTRIB_LAST_PASSWORD)?.takeIf { !it.isJsonNull }?.asString,
                    o.get(ATTRIB_WEBSITE).asString,
                    o.get(ATTRIB_LABELS).asString,
                    o.get(ATTRIB_EXPIRES_AT)?.takeIf { !it.isJsonNull }?.asString,
                    o.get(ATTRIB_IS_OBFUSCATED)?.asBoolean ?: false,
                    o.get(ATTRIB_IS_LAST_PASSWORD_OBFUSCATED)?.takeIf { !it.isJsonNull }?.asBoolean,
                    o.get(ATTRIB_OTP_DATA)?.takeIf { !it.isJsonNull }?.asString,
                    o.get(ATTRIB_MODIFY_TIMESTAMP)?.takeIf { !it.isJsonNull }?.asLong,
                    o.get(ATTRIB_PINNED)?.asBoolean ?: false,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
