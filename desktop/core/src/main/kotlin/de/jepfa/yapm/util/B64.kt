package de.jepfa.yapm.util

import java.util.Base64

/**
 * Base64 helpers that reproduce the Android `android.util.Base64` flags used by
 * the app, so the on-disk/wire format stays byte-compatible.
 *
 * - `NO_WRAP or NO_PADDING` -> [noWrapNoPadding]
 * - `DEFAULT` (0)            -> [mime] (padded, line-wrapped)
 */
object B64 {

    private val urlSafeUnpadded: Base64.Encoder = Base64.getEncoder().withoutPadding()
    private val standardUnpadded: Base64.Encoder = Base64.getEncoder().withoutPadding()
    private val standardDecoder: Base64.Decoder = Base64.getDecoder()
    // Android's Base64.DEFAULT wraps at 76 chars with '\n' line separators.
    private val mimeEncoder: Base64.Encoder =
        Base64.getMimeEncoder(76, byteArrayOf('\n'.code.toByte()))
    private val mimeDecoder: Base64.Decoder = Base64.getMimeDecoder()

    /** Equivalent of `Base64.encodeToString(data, NO_WRAP or NO_PADDING)`. */
    fun noWrapNoPadding(data: ByteArray): String = standardUnpadded.encodeToString(data)

    /** Equivalent of `Base64.decode(string, NO_WRAP or NO_PADDING)`. */
    fun decodeNoWrapNoPadding(value: String): ByteArray {
        if (value.isEmpty()) return ByteArray(0)
        return standardDecoder.decode(value)
    }

    /** Equivalent of `Base64.encodeToString(data, Base64.DEFAULT)`. */
    fun mime(data: ByteArray): String = mimeEncoder.encodeToString(data)

    /** Equivalent of `Base64.decode(string, Base64.DEFAULT)`. */
    fun decodeMime(value: String): ByteArray {
        if (value.isEmpty()) return ByteArray(0)
        return mimeDecoder.decode(value)
    }

    @Suppress("unused")
    fun urlSafeNoPadding(data: ByteArray): String = urlSafeUnpadded.encodeToString(data)
}
