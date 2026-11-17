package de.jepfa.yapm.model.secret

import de.jepfa.yapm.util.B64
import java.util.Locale

/**
 * Represents a key which is secret to others.
 */
class Key : Secret {

    constructor(data: ByteArray) : super(data)

    fun debugToString(): String = data.contentToString()

    fun toBase64String(): String = B64.mime(data)

    fun toShortenedFingerprint(): String {
        val f = toBase64String()
            .replace(Regex("[^a-z]", RegexOption.IGNORE_CASE), "")
            .substring(0, 7)
            .uppercase(Locale.ROOT)

        return f.substring(0, 2) + "-" + f.substring(2, 5) + "-" + f.substring(5, 7)
    }

    companion object {
        fun empty(): Key = Key(ByteArray(0))
    }
}
