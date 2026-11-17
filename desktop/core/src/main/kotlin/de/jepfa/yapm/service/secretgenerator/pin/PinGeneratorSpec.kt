package de.jepfa.yapm.service.secretgenerator.pin

import de.jepfa.yapm.service.secretgenerator.GeneratorSpec

data class PinGeneratorSpec(
    val length: Int = 4,
) : GeneratorSpec
