package de.jepfa.yapm.service.secretgenerator.password

import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secretgenerator.GeneratorBase
import java.security.SecureRandom

class PasswordGenerator(
    private val upperCase: String = DEFAULT_ALPHA_CHARS_UPPER_CASE,
    private val lowerCase: String = DEFAULT_ALPHA_CHARS_LOWER_CASE,
    private val digits: String = DEFAULT_DIGITS,
    private val specialChars: String = DEFAULT_SPECIAL_CHARS,
    private val extendedSpecialChars: String = EXTENDED_SPECIAL_CHARS,
    secureRandom: SecureRandom? = null,
) : GeneratorBase<PasswordGeneratorSpec>(secureRandom ?: SecureRandom()) {

    override fun generate(spec: PasswordGeneratorSpec): Password {
        while (true) {
            val buffer = generatePassword(spec)
            if (matchSpec(spec, buffer)) {
                return Password(buffer)
            }
        }
    }

    override fun calcCombinationCount(spec: PasswordGeneratorSpec): Double {
        val material = extractMaterial(spec)
        val specials = getSpecialCharsToUse(spec)
        val length = spec.strength.ordinaryPasswordLength.toDouble()
        var combinations = Math.pow(material.length.toDouble(), length)

        // Always require lower case.
        val noLowerCase = Math.pow((material.length - lowerCase.length).toDouble(), length)
        combinations -= noLowerCase

        if (!spec.noUpperCase) {
            combinations -= Math.pow((material.length - upperCase.length).toDouble(), length)
            combinations += Math.pow(
                (material.length - lowerCase.length - upperCase.length).toDouble(), length
            )
        }
        if (!spec.noDigits) {
            combinations -= Math.pow((material.length - digits.length).toDouble(), length)
            combinations += Math.pow(
                (material.length - lowerCase.length - digits.length).toDouble(), length
            )
        }
        if (!spec.noSpecialChars) {
            combinations -= Math.pow((material.length - specials.length).toDouble(), length)
            combinations += Math.pow(
                (material.length - lowerCase.length - specials.length).toDouble(), length
            )
        }

        if (!spec.noUpperCase && !spec.noDigits) {
            combinations += Math.pow(
                (material.length - upperCase.length - digits.length).toDouble(), length
            )
        }
        if (!spec.noUpperCase && !spec.noSpecialChars) {
            combinations += Math.pow(
                (material.length - upperCase.length - specials.length).toDouble(), length
            )
        }
        if (!spec.noDigits && !spec.noSpecialChars) {
            combinations += Math.pow(
                (material.length - digits.length - specials.length).toDouble(), length
            )
        }

        if (!spec.noUpperCase && !spec.noDigits && !spec.noSpecialChars) {
            combinations -= Math.pow(lowerCase.length.toDouble(), length)
            combinations -= Math.pow(upperCase.length.toDouble(), length)
            combinations -= Math.pow(digits.length.toDouble(), length)
            combinations -= Math.pow(specials.length.toDouble(), length)
        }

        return combinations
    }

    private fun getSpecialCharsToUse(spec: PasswordGeneratorSpec): String =
        if (spec.useExtendedSpecialChars) specialChars + extendedSpecialChars else specialChars

    private fun matchSpec(spec: PasswordGeneratorSpec, buffer: CharArray): Boolean {
        val specials = getSpecialCharsToUse(spec)
        if (!containsChar(buffer, lowerCase)) return false
        if (!spec.noUpperCase && !containsChar(buffer, upperCase)) return false
        if (!spec.noDigits && !containsChar(buffer, digits)) return false
        if (!spec.noSpecialChars && !containsChar(buffer, specials)) return false
        return true
    }

    private fun containsChar(buffer: CharArray, material: String): Boolean =
        buffer.any { material.contains(it) }

    private fun generatePassword(spec: PasswordGeneratorSpec): CharArray {
        val buffer = CharArray(spec.strength.ordinaryPasswordLength)
        val material = extractMaterial(spec)
        for (i in buffer.indices) {
            buffer[i] = random(material)
        }
        return buffer
    }

    private fun extractMaterial(spec: PasswordGeneratorSpec): String {
        var material = lowerCase
        if (!spec.noDigits) material += digits
        if (!spec.noSpecialChars) {
            material += specialChars
            if (spec.useExtendedSpecialChars) material += extendedSpecialChars
        }
        if (!spec.noUpperCase) material += upperCase
        return material
    }
}
