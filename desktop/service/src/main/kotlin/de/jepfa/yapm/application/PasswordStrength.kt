package de.jepfa.yapm.application

import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secretgenerator.GeneratorBase
import kotlin.math.log2
import kotlin.math.pow

/**
 * Port of the Android strength heuristic (`ShowPasswordStrengthUseCase`).
 * Estimates the alphabet actually used, then combinations = alphabet^length.
 */
object PasswordStrength {

    data class Result(
        val combinations: Double,
        val entropy: Double,
        val yearsPentium: Double,
        val yearsSuperComputer: Double,
        val rating: String,
    )

    fun evaluate(password: Password): Result {
        val combinations = guessCombinations(password)
        val entropy = if (combinations >= 1.0) log2(combinations) else 0.0
        val yearsPentium =
            combinations / GeneratorBase.BRUTEFORCE_ATTEMPTS_PENTIUM / SECONDS_PER_YEAR
        val yearsSuper =
            combinations / GeneratorBase.BRUTEFORCE_ATTEMPTS_SUPERCOMP / SECONDS_PER_YEAR

        val rating = when {
            entropy >= 128 -> "very strong"
            entropy >= 80 -> "strong"
            entropy >= 70 -> "ok"
            entropy >= 60 -> "weak"
            entropy >= 28 -> "poor"
            else -> "very poor"
        }

        return Result(combinations, entropy, yearsPentium, yearsSuper, rating)
    }

    fun guessCombinations(password: Password): Double {
        val chars = password.decodeToCharArray().concatToString()
        if (chars.isEmpty()) return 0.0

        val classes = listOf(
            GeneratorBase.DEFAULT_ALPHA_CHARS_LOWER_CASE,
            GeneratorBase.DEFAULT_ALPHA_CHARS_UPPER_CASE,
            GeneratorBase.DEFAULT_DIGITS,
            GeneratorBase.DEFAULT_SPECIAL_CHARS,
            GeneratorBase.EXTENDED_SPECIAL_CHARS,
        )
        val alphabetSize = classes
            .filter { cls -> chars.any { it in cls } }
            .sumOf { it.length }

        return alphabetSize.toDouble().pow(chars.length.toDouble())
    }

    private const val SECONDS_PER_YEAR = 31_557_600.0
}
