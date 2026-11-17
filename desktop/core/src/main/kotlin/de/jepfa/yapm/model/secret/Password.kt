package de.jepfa.yapm.model.secret

import de.jepfa.yapm.model.Clearable
import de.jepfa.yapm.util.B64
import de.jepfa.yapm.util.Loop
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

private const val MASK_CHAR = '*'
private const val MASK_LENGTH = 16

/**
 * Ported from the Android app. Passwords are byte arrays (UTF-8), not Strings,
 * to reduce accidental heap retention. The `Editable` constructor was removed
 * because it is Android-only; desktop callers use [String] or [CharArray].
 */
class Password : Secret, CharSequence {

    enum class FormattingStyle {
        IN_WORDS, IN_WORDS_MULTI_LINE, RAW;

        fun prev(): FormattingStyle {
            var prevIdx = ordinal - 1
            if (prevIdx < 0) prevIdx = values().size - 1
            return values()[prevIdx]
        }

        fun next(): FormattingStyle = values()[(ordinal + 1) % values().size]

        fun isMultiLine(): Boolean = this == IN_WORDS_MULTI_LINE

        companion object {
            val DEFAULT = IN_WORDS

            fun createFromFlags(multiLine: Boolean, formatted: Boolean) =
                if (formatted) {
                    if (multiLine) IN_WORDS_MULTI_LINE else DEFAULT
                } else {
                    RAW
                }
        }
    }

    /**
     * Represents a formatted real AND encoded password to increase readability.
     */
    class FormattedPassword() : CharSequence, Clearable {
        private val wordLength = 4
        private val charList = ArrayList<Char>(32)

        private constructor(vararg sequences: CharSequence) : this() {
            sequences.forEach { charList.addAll(it.toMutableList()) }
        }

        private constructor(sequence: CharSequence) : this() {
            charList.addAll(sequence.toMutableList())
        }

        private constructor(charList: MutableList<Char>) : this() {
            this.charList.addAll(charList)
        }

        override val length: Int
            get() = charList.size

        override fun get(index: Int): Char = charList[index]

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            FormattedPassword(charList.subList(startIndex, endIndex))

        operator fun plus(string: String): FormattedPassword {
            charList.addAll(string.toCharArray().asList())
            return this
        }

        operator fun plus(char: Char): FormattedPassword {
            charList.add(char)
            return this
        }

        override fun clear() {
            charList.clear()
        }

        /** Avoid: creates a String that remains in the heap. */
        override fun toString(): String = String(charList.toCharArray())

        companion object {
            internal fun create(
                formattingStyle: FormattingStyle,
                maskPassword: Boolean,
                useShortMask: Boolean,
                password: Password
            ): FormattedPassword {

                if (maskPassword && useShortMask) {
                    return FormattedPassword(MASK_CHAR.toString().repeat(6))
                }
                val isNumeric = password.isNumeric()
                if (!maskPassword && isNumeric && password.length < 10 &&
                    formattingStyle == FormattingStyle.IN_WORDS_MULTI_LINE
                ) {
                    return formatNumeric(password)
                }

                val decoded = password.decodeToCharArray()

                val multiLine = formattingStyle.isMultiLine()
                val raw = formattingStyle == FormattingStyle.RAW
                val formattedPasswordLength = if (maskPassword) MASK_LENGTH else decoded.size
                val formattedPassword = FormattedPassword()
                val wordLength = if (isNumeric && !maskPassword) 2 else formattedPassword.wordLength

                for (i in 0 until formattedPasswordLength) {
                    if (!raw && i != 0 && i % wordLength == 0) {
                        if (i % (wordLength * 2) == 0) {
                            if (multiLine) {
                                formattedPassword + System.lineSeparator()
                            } else {
                                formattedPassword + "  "
                            }
                        } else {
                            formattedPassword + " "
                        }
                    }

                    formattedPassword + (if (maskPassword) MASK_CHAR else decoded[i])
                }

                return formattedPassword
            }

            private fun formatNumeric(pin: Password): FormattedPassword {
                return when (pin.length) {
                    5 -> FormattedPassword(pin.subSequence(0, 2), " ", pin.subSequence(2, 3), " ", pin.subSequence(3, 5))
                    6 -> FormattedPassword(pin.subSequence(0, 3), " ", pin.subSequence(3, 6))
                    7 -> FormattedPassword(pin.subSequence(0, 2), " ", pin.subSequence(2, 5), " ", pin.subSequence(5, 7))
                    8 -> FormattedPassword(pin.subSequence(0, 4), " ", pin.subSequence(4, 8))
                    9 -> FormattedPassword(pin.subSequence(0, 3), " ", pin.subSequence(3, 6), " ", pin.subSequence(6, 9))
                    else -> FormattedPassword(pin)
                }
            }
        }
    }

    /** Use only for testing / non-secret input. */
    constructor(passwd: String) : this(passwd.toByteArray(StandardCharsets.UTF_8))
    constructor(key: Key) : this(key.data)
    constructor(chars: CharArray) : this(chars.map { it.code.toByte() }.toByteArray())
    constructor(bytes: ByteArray) : super(bytes)

    /**
     * 1:1 mapped from the underlying ByteArray (sign-extends bytes > 0x7F).
     * Ported verbatim from the Android implementation to keep KDF compatibility.
     */
    fun toEncodedCharArray(): CharArray = data.map { it.toInt().toChar() }.toCharArray()

    fun add(other: Char) {
        val buffer = data + other.code.toByte()
        clear()
        data = buffer
    }

    fun replace(index: Int, other: Char) {
        data[index] = other.code.toByte()
    }

    fun isNumeric(): Boolean =
        data.all { it >= '0'.code.toByte() && it <= '9'.code.toByte() }

    fun obfuscate(key: Key) {
        Loop.loopPassword(this, key, forwards = true)
    }

    fun deobfuscate(key: Key) {
        Loop.loopPassword(this, key, forwards = false)
    }

    fun toFormattedPassword() =
        toFormattedPassword(FormattingStyle.DEFAULT, maskPassword = false, useShortMask = false)

    fun toFormattedPassword(
        formattingStyle: FormattingStyle,
        maskPassword: Boolean,
        useShortMask: Boolean
    ) = FormattedPassword.create(formattingStyle, maskPassword, useShortMask, this)

    fun toRawFormattedPassword() =
        toFormattedPassword(FormattingStyle.RAW, maskPassword = false, useShortMask = false)

    override val length: Int
        get() = data.size

    override fun isEmpty(): Boolean = data.isEmpty()

    override fun get(index: Int) = toEncodedCharArray()[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        Password(data.copyOfRange(startIndex, endIndex))

    fun toBase64String(): String = B64.noWrapNoPadding(data)

    override fun toString() = toRawFormattedPassword().toString()

    fun decodeToCharArray(): CharArray {
        val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(data))
        val charArray = CharArray(charBuffer.limit())
        charBuffer.get(charArray)
        return charArray
    }

    companion object {
        fun empty(): Password = Password("")

        fun fromBase64String(string: String): Password =
            Password(B64.decodeNoWrapNoPadding(string))

        fun encodeToByteArray(charArray: CharArray): ByteArray {
            val charBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(charArray))
            val byteArray = ByteArray(charBuffer.limit())
            charBuffer.get(byteArray)
            return byteArray
        }
    }
}
