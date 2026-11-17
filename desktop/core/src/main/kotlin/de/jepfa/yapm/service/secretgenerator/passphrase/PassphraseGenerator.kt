package de.jepfa.yapm.service.secretgenerator.passphrase

import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secretgenerator.GeneratorBase
import de.jepfa.yapm.service.secretgenerator.SecretStrength
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

val DEFAULT_VOCALS = "aeiouy"
val DEFAULT_CONSONANTS = "bcdfghjklmnpqrstvwxz"

/**
 * Generates human-readable "pseudo phrases": short pronounceable 4-character
 * words concatenated together. Output is identical to the Android generator for
 * the same SecureRandom sequence.
 */
class PassphraseGenerator(
    private val vocals: String = DEFAULT_VOCALS,
    private val consonants: String = DEFAULT_CONSONANTS,
    private val digits: String = DEFAULT_DIGITS,
    private val specialChars: String = DEFAULT_SPECIAL_CHARS,
    private val extendedSpecialChars: String = EXTENDED_SPECIAL_CHARS,
    secureRandom: SecureRandom? = null,
) : GeneratorBase<PassphraseGeneratorSpec>(secureRandom ?: SecureRandom()) {

    override fun generate(spec: PassphraseGeneratorSpec): Password {
        val buffer = Password(CharArray(0))

        for (i in 0 until strengthToWordCount(spec.strength)) {
            buffer.add(generateWord())
        }

        if (spec.wordBeginningUpperCase) {
            buffer.replace(0, buffer.get(0).uppercaseChar())
        }
        if (spec.addDigit) {
            buffer.add(random(digits))
        }
        if (spec.addSpecialChar) {
            buffer.add(
                if (spec.useExtendedSpecialChars) random(specialChars + extendedSpecialChars)
                else random(specialChars)
            )
        }

        return buffer
    }

    private fun generateWord(): Password {
        val word = generateTuple()
        val recentEndsWithVocal = isVocal(word.last())
        word.add(generateTuple(recentEndsWithVocal))
        return word
    }

    private fun generateTuple(recentEndsWithVocal: Boolean = false): Password {
        val buffer = CharArray(2)
        buffer[0] = random(alphabet())
        val char = buffer[0]
        var material = when (isVocal(char)) {
            true -> alphabet()
            else -> vocals
        }
        if (recentEndsWithVocal && isConsonant(char)) {
            material += char
        }
        buffer[1] = random(material)
        return Password(buffer)
    }

    override fun calcCombinationCount(spec: PassphraseGeneratorSpec): Double {
        val vocalLength = vocals.length.toDouble()
        val consonantLength = consonants.length.toDouble()
        val alphabetLength = alphabet().length.toDouble()

        val vocalChance = vocalLength / alphabetLength
        val consonantChance = consonantLength / alphabetLength

        val tupleCombinations =
            alphabetLength * ((vocalChance * alphabetLength) + (consonantChance * vocalLength))
        val tupleCombinationsWithVocalAtLast = vocalLength * alphabetLength

        val wordCombinations = floor(
            (tupleCombinations * tupleCombinations) +
                (tupleCombinationsWithVocalAtLast * consonantLength)
        )
        var totalCombinations = ceil(
            wordCombinations.pow(strengthToWordCount(spec.strength).toDouble())
        )

        if (spec.addDigit) totalCombinations *= digits.length
        if (spec.addSpecialChar) {
            totalCombinations *= if (spec.useExtendedSpecialChars) {
                (extendedSpecialChars.length + specialChars.length)
            } else {
                specialChars.length
            }
        }
        return totalCombinations
    }

    private fun isVocal(char: Char): Boolean = vocals.contains(char, ignoreCase = true)

    private fun isConsonant(char: Char): Boolean = consonants.contains(char, ignoreCase = true)

    private fun strengthToWordCount(strength: SecretStrength): Int = strength.pseudoPhraseLength / 4

    private fun alphabet() = vocals + consonants
}
