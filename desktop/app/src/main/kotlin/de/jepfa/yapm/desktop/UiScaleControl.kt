package de.jepfa.yapm.desktop

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

private val UI_SCALES = listOf(
    1.0f, 1.25f, 1.5f, 1.7f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f, 3.5f, 4.0f,
)

internal fun nextUiScale(current: Float): Float =
    UI_SCALES.firstOrNull { it > current + 0.01f } ?: UI_SCALES.first()

@Composable
internal fun UiScaleButton(scale: Float, onScaleChange: (Float) -> Unit) {
    TextButton(onClick = { onScaleChange(nextUiScale(scale)) }) {
        Text("UI ${(scale * 100).roundToInt()}%")
    }
}
