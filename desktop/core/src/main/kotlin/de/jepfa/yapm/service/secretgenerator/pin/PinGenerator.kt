package de.jepfa.yapm.service.secretgenerator.pin

import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secretgenerator.GeneratorBase
import java.security.SecureRandom
import kotlin.math.pow

class PinGenerator(
    private val digits: String = DEFAULT_DIGITS,
    secureRandom: SecureRandom? = null,
) : GeneratorBase<PinGeneratorSpec>(secureRandom ?: SecureRandom()) {

    override fun generate(spec: PinGeneratorSpec): Password {
        val buffer = CharArray(spec.length)
        for (i in buffer.indices) {
            buffer[i] = random(digits)
        }
        return Password(buffer)
    }

    override fun calcCombinationCount(spec: PinGeneratorSpec): Double =
        digits.length.toDouble().pow(spec.length.toDouble())
}
