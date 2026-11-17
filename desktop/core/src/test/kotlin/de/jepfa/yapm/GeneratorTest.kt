package de.jepfa.yapm

import de.jepfa.yapm.service.secretgenerator.SecretStrength
import de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGenerator
import de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.password.PasswordGenerator
import de.jepfa.yapm.service.secretgenerator.password.PasswordGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.pin.PinGenerator
import de.jepfa.yapm.service.secretgenerator.pin.PinGeneratorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {

    @Test
    fun `password has the requested length and character classes`() {
        val generator = PasswordGenerator()
        for (strength in SecretStrength.entries) {
            val password = generator.generate(PasswordGeneratorSpec(strength = strength)).toString()
            assertEquals(strength.ordinaryPasswordLength, password.length, "length for $strength")
            assertTrue(password.any { it in 'a'..'z' }, "lower case for $strength")
            assertTrue(password.any { it in 'A'..'Z' }, "upper case for $strength")
            assertTrue(password.any { it in '0'..'9' }, "digit for $strength")
            assertTrue(password.any { it in "!?-,.:/$&@#_;+*" }, "special for $strength")
        }
    }

    @Test
    fun `password options exclude character classes`() {
        val generator = PasswordGenerator()
        val password = generator.generate(
            PasswordGeneratorSpec(
                strength = SecretStrength.NORMAL,
                noUpperCase = true,
                noDigits = true,
                noSpecialChars = true,
            )
        ).toString()

        assertEquals(SecretStrength.NORMAL.ordinaryPasswordLength, password.length)
        assertTrue(password.all { it in 'a'..'z' })
    }

    @Test
    fun `passphrase word count and options`() {
        val generator = PassphraseGenerator()

        val normal = generator.generate(PassphraseGeneratorSpec(strength = SecretStrength.NORMAL)).toString()
        assertEquals(SecretStrength.NORMAL.pseudoPhraseLength, normal.length)

        val withExtras = generator.generate(
            PassphraseGeneratorSpec(
                strength = SecretStrength.TWO_WORDS,
                addDigit = true,
                addSpecialChar = true,
                wordBeginningUpperCase = true,
            )
        ).toString()
        assertEquals(SecretStrength.TWO_WORDS.pseudoPhraseLength + 2, withExtras.length)
        assertTrue(withExtras.first().isUpperCase())
    }

    @Test
    fun `pin has the requested length and only digits`() {
        val generator = PinGenerator()
        for (length in 4..10) {
            val pin = generator.generate(PinGeneratorSpec(length)).toString()
            assertEquals(length, pin.length)
            assertTrue(pin.all { it in '0'..'9' })
        }
    }

    @Test
    fun `combination estimates are positive and grow with strength`() {
        val passwordGenerator = PasswordGenerator()
        val easy = passwordGenerator.calcCombinationCount(
            PasswordGeneratorSpec(strength = SecretStrength.EASY)
        )
        val strong = passwordGenerator.calcCombinationCount(
            PasswordGeneratorSpec(strength = SecretStrength.STRONG)
        )
        assertTrue(easy > 0)
        assertTrue(strong > easy)

        val passphraseGenerator = PassphraseGenerator()
        val oneWord = passphraseGenerator.calcCombinationCount(
            PassphraseGeneratorSpec(strength = SecretStrength.ONE_WORD)
        )
        val fourWords = passphraseGenerator.calcCombinationCount(
            PassphraseGeneratorSpec(strength = SecretStrength.NORMAL)
        )
        assertTrue(oneWord > 0)
        assertTrue(fourWords > oneWord)
    }
}
