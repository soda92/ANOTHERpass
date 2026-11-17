package de.jepfa.yapm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.jepfa.yapm.application.VaultService

private val PRESET_COLORS = listOf(
    0xFFE57373.toInt(),
    0xFF81C784.toInt(),
    0xFF64B5F6.toInt(),
    0xFFFFB74D.toInt(),
    0xFFBA68C8.toInt(),
    0xFF4DD0E1.toInt(),
    0xFFA1887F.toInt(),
)

@Composable
fun LabelManagerDialog(
    service: VaultService,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
    onBanner: (String) -> Unit,
) {
    var labels by remember { mutableStateOf(service.listLabels()) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PRESET_COLORS.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        labels = service.listLabels()
    }

    fun resetForm() {
        editingId = null
        name = ""
        description = ""
        color = PRESET_COLORS.first()
        error = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
            Column(
                Modifier.width(640.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Labels", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_COLORS.forEach { preset ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(preset), CircleShape)
                                .border(
                                    width = if (preset == color) 3.dp else 1.dp,
                                    color = if (preset == color) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { color = preset }
                        )
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (name.isBlank()) {
                            error = "Name is required"
                        } else {
                            runCatching {
                                service.saveLabel(editingId, name, description, color)
                            }.onSuccess {
                                resetForm()
                                reload()
                                onChanged()
                                onBanner("Label saved")
                            }.onFailure {
                                error = it.message
                            }
                        }
                    }) { Text(if (editingId == null) "Add label" else "Save label") }

                    if (editingId != null) {
                        OutlinedButton(onClick = { resetForm() }) { Text("Cancel edit") }
                    }
                }

                Text("Existing labels", style = MaterialTheme.typography.labelLarge)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(labels, key = { it.id }) { label ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .background(
                                        Color(label.color ?: 0xFF9E9E9E.toInt()),
                                        CircleShape,
                                    )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(label.name, style = MaterialTheme.typography.titleSmall)
                                if (label.description.isNotBlank()) {
                                    Text(
                                        label.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = {
                                editingId = label.id
                                name = label.name
                                description = label.description
                                color = label.color ?: PRESET_COLORS.first()
                                error = null
                            }) { Text("Edit") }
                            TextButton(onClick = {
                                runCatching { service.deleteLabel(label.id) }
                                    .onSuccess {
                                        if (editingId == label.id) resetForm()
                                        reload()
                                        onChanged()
                                        onBanner("Label deleted")
                                    }
                                    .onFailure { error = it.message }
                            }) { Text("Delete") }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
