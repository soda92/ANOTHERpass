package de.jepfa.yapm.util

import de.jepfa.yapm.model.secret.Key
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secretgenerator.GeneratorBase.Companion.DEFAULT_DIGITS
import de.jepfa.yapm.service.secretgenerator.GeneratorBase.Companion.DEFAULT_OBFUSCATIONABLE_SPECIAL_CHARS
import de.jepfa.yapm.service.secretgenerator.passphrase.DEFAULT_CONSONANTS
import de.jepfa.yapm.service.secretgenerator.passphrase.DEFAULT_VOCALS

/**
 * Port of the Android `Loop` helper used for password obfuscation/deobfuscation.
 * The character sets and iteration order must match the Android implementation
 * exactly, otherwise protected EMPs cannot be recovered.
 */
class Loop<T>(data: List<T>) {

    private val data: List<T>
    private val index = HashMap<T, Int>()

    init {
        this.data = ArrayList(data)
        for (i in data.indices) {
            index[data[i]] = i
        }
    }

    fun applies(t: T): Boolean = data.contains(t)

    @Throws(IllegalStateException::class)
    fun forwards(from: T, count: Int): T {
        if (!index.containsKey(from)) {
            throw IllegalStateException("Not part of this loop: $from")
        }
        if (count < 0) {
            return backwards(from, -count)
        }
        val pos = index[from]!!
        val next = (pos + count) % data.size
        return data[next]
    }

    @Throws(IllegalStateException::class)
    fun backwards(from: T, count: Int): T {
        var c = count
        if (!index.containsKey(from)) {
            throw IllegalStateException("Not part of this loop: $from")
        }
        if (c < 0) {
            return forwards(from, -c)
        }
        c %= data.size
        val pos = index[from]!!
        val next = (pos + (data.size - c)) % data.size
        return data[next]
    }

    companion object {
        val DIGITS_LOOP = Loop(DEFAULT_DIGITS.toCharArray().asList())
        val VOCALS_LOOP = Loop(DEFAULT_VOCALS.toCharArray().asList())
        val CONSONANTS_LOOP = Loop(DEFAULT_CONSONANTS.toCharArray().asList())
        val SPECIAL_CHARS_LOOP = Loop(DEFAULT_OBFUSCATIONABLE_SPECIAL_CHARS.toCharArray().asList())

        /** Key length must be at least the password length. */
        fun loopPassword(password: Password, key: Key, forwards: Boolean = true) {
            if (key.data.size >= password.data.size) {
                password.data.forEachIndexed { index, byte ->
                    val loopCount = key.data[index] * (if (forwards) 1 else -1)
                    loopReplace(DIGITS_LOOP, password, index, byte.toChar(), loopCount)
                    loopReplace(VOCALS_LOOP, password, index, byte.toChar(), loopCount)
                    loopReplace(CONSONANTS_LOOP, password, index, byte.toChar(), loopCount)
                    loopReplace(SPECIAL_CHARS_LOOP, password, index, byte.toChar(), loopCount)
                }
            }
        }

        private fun loopReplace(
            loop: Loop<Char>,
            password: Password,
            index: Int,
            c: Char,
            loopCount: Int,
        ) {
            val isUpperCase = c.isUpperCase()
            val c1 = c.lowercaseChar()
            if (loop.applies(c1)) {
                var c2 = loop.forwards(c1, loopCount)
                if (isUpperCase) {
                    c2 = c2.uppercaseChar()
                }
                password.replace(index, c2)
            }
        }
    }
}
