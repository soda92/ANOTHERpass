package de.jepfa.yapm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val UI_SCALES = listOf(
    0.75f, 1.0f, 1.25f, 1.5f, 1.7f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f, 3.5f, 4.0f,
)

internal fun nextUiScale(current: Float): Float =
    UI_SCALES.firstOrNull { it > current + 0.01f } ?: current

internal fun previousUiScale(current: Float): Float =
    UI_SCALES.lastOrNull { it < current - 0.01f } ?: current

@Composable
internal fun UiScaleControls(scale: Float, onScaleChange: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = { onScaleChange(previousUiScale(scale)) }) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "UI ${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { onScaleChange(nextUiScale(scale)) }) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}
