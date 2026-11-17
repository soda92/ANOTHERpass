package de.jepfa.yapm.model.encrypted

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.UUID

// Property names are part of the vault backup format. Do not rename.
data class EncLabel(
    var id: Int?,
    val uid: UUID?,
    override var name: Encrypted,
    var description: Encrypted,
    var color: Int?,
) : EncNamed {

    constructor(name: Encrypted, description: Encrypted) :
        this(null, null, name, description, null)

    constructor(
        id: Int?,
        uid: String?,
        nameBase64: String,
        descriptionBase64: String,
        color: Int?,
    ) : this(
        id,
        uid?.let { UUID.fromString(it) },
        Encrypted.fromBase64String(nameBase64),
        Encrypted.fromBase64String(descriptionBase64),
        color,
    )

    fun isPersistent(): Boolean = id != null

    fun toJson(): JsonObject = JsonObject().apply {
        id?.let { addProperty(ATTRIB_ID, it) }
        uid?.let { addProperty(ATTRIB_UID, it.toString()) }
        addProperty(ATTRIB_NAME, name.toBase64String())
        addProperty(ATTRIB_DESC, description.toBase64String())
        color?.let { addProperty(ATTRIB_COLOR, it) }
    }

    companion object {
        const val ATTRIB_ID = "id"
        const val ATTRIB_UID = "uid"
        const val ATTRIB_NAME = "name"
        const val ATTRIB_DESC = "description"
        const val ATTRIB_COLOR = "color"

        fun fromJson(json: JsonElement): EncLabel? {
            return try {
                val o = json.asJsonObject
                EncLabel(
                    o.get(ATTRIB_ID)?.asInt,
                    o.get(ATTRIB_UID)?.takeIf { !it.isJsonNull }?.asString,
                    o.get(ATTRIB_NAME).asString,
                    o.get(ATTRIB_DESC).asString,
                    o.get(ATTRIB_COLOR)?.takeIf { !it.isJsonNull }?.asInt,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
