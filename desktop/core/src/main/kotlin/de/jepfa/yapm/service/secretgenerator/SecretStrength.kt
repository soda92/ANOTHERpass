package de.jepfa.yapm.service.secretgenerator

enum class SecretStrength(
    val pseudoPhraseLength: Int,
    val ordinaryPasswordLength: Int,
    val displayName: String,
) {
    ONE_WORD(4, 4, "One word"),
    TWO_WORDS(8, 8, "Two words"),
    EASY(12, 10, "Easy"),
    NORMAL(16, 12, "Normal"),
    STRONG(20, 16, "Strong"),
    ULTRA(24, 20, "Ultra"),
    EXTREME(28, 24, "Extreme"),
    HYPER(32, 28, "Hyper"),
}
