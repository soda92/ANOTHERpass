package de.jepfa.yapm.model.encrypted

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class EncUsernameTemplate(
    var id: Int?,
    var username: Encrypted,
    var description: Encrypted,
    var generatorType: Encrypted,
) {

    enum class GeneratorType {
        NONE,
        EMAIL_EXTENSION_CREDENTIAL_NAME_BASED,
        EMAIL_EXTENSION_RANDOM_BASED,
        EMAIL_EXTENSION_BOTH,
    }

    constructor(
        id: Int?,
        usernameBase64: String,
        descriptionBase64: String,
        generatorTypeBase64: String,
    ) : this(
        id,
        Encrypted.fromBase64String(usernameBase64),
        Encrypted.fromBase64String(descriptionBase64),
        Encrypted.fromBase64String(generatorTypeBase64),
    )

    fun isPersistent(): Boolean = id != null

    fun toJson(): JsonObject = JsonObject().apply {
        id?.let { addProperty(ATTRIB_ID, it) }
        addProperty(ATTRIB_USERNAME, username.toBase64String())
        addProperty(ATTRIB_DESCRIPTION, description.toBase64String())
        addProperty(ATTRIB_GENERATOR_TYPE, generatorType.toBase64String())
    }

    companion object {
        const val ATTRIB_ID = "id"
        const val ATTRIB_USERNAME = "username"
        const val ATTRIB_DESCRIPTION = "description"
        const val ATTRIB_GENERATOR_TYPE = "generatorType"

        fun fromJson(json: JsonElement): EncUsernameTemplate? {
            return try {
                val o = json.asJsonObject
                EncUsernameTemplate(
                    o.get(ATTRIB_ID)?.asInt,
                    o.get(ATTRIB_USERNAME).asString,
                    o.get(ATTRIB_DESCRIPTION).asString,
                    o.get(ATTRIB_GENERATOR_TYPE).asString,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
