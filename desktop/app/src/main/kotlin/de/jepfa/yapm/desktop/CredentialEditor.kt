package de.jepfa.yapm.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.jepfa.yapm.application.VaultService
import de.jepfa.yapm.service.secretgenerator.SecretStrength
import de.jepfa.yapm.service.secretgenerator.passphrase.PassphraseGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.password.PasswordGeneratorSpec
import de.jepfa.yapm.service.secretgenerator.pin.PinGeneratorSpec
import de.jepfa.yapm.util.Constants
import kotlin.math.roundToInt

@Composable
fun CredentialEditor(
    initial: VaultService.CredentialDraft,
    labels: List<VaultService.LabelView>,
    service: VaultService,
    onSave: (VaultService.CredentialDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var expiresText by remember(initial) { mutableStateOf(formatDate(initial.expiresAt)) }
    var showPassword by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (initial.id == null) "New credential" else "Edit credential",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text("Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.website,
            onValueChange = { draft = draft.copy(website = it) },
            label = { Text("Website") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.user,
            onValueChange = { draft = draft.copy(user = it) },
            label = { Text("User") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Password", style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft.password,
                onValueChange = { draft = draft.copy(password = it) },
                singleLine = true,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showPassword = !showPassword }) {
                Text(if (showPassword) "Hide" else "Show")
            }
            OutlinedButton(onClick = { showGenerator = true }) { Text("Generate") }
        }
        if (draft.password.isNotEmpty()) {
            val strength = service.evaluatePassword(draft.password)
            Text(
                "Strength: ${strength.rating} · ${strength.entropy.toInt()} bits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = draft.additionalInfo,
            onValueChange = { draft = draft.copy(additionalInfo = it) },
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = expiresText,
            onValueChange = { expiresText = it },
            label = { Text("Expires (yyyy-MM-dd, empty = never)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.otpAuthUri ?: "",
            onValueChange = { draft = draft.copy(otpAuthUri = it.ifBlank { null }) },
            label = { Text("OTP auth URI (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (labels.isNotEmpty()) {
            Text("Labels", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                labels.forEach { label ->
                    val selected = label.id in draft.labelIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            draft = draft.copy(
                                labelIds = if (selected) {
                                    draft.labelIds - label.id
                                } else {
                                    (draft.labelIds + label.id)
                                        .take(Constants.MAX_LABELS_PER_CREDENTIAL)
                                },
                            )
                        },
                        label = { Text(label.name) },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.pinned,
                onCheckedChange = { draft = draft.copy(pinned = it) },
            )
            Text("Pin at top")
        }

        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (draft.name.isBlank()) {
                    validation = "Name is required"
                    return@Button
                }
                val expires = try {
                    parseDate(expiresText)
                } catch (_: Exception) {
                    validation = "Invalid date, use yyyy-MM-dd"
                    return@Button
                }
                validation = null
                onSave(draft.copy(expiresAt = expires))
            }) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            service = service,
            onUse = {
                draft = draft.copy(password = it)
                showGenerator = false
            },
            onDismiss = { showGenerator = false },
        )
    }
}

private enum class GeneratorType(val label: String) {
    PASSPHRASE("Pseudo phrase"),
    PASSWORD("Password"),
    PIN("PIN"),
}

@Composable
private fun GeneratorDialog(
    service: VaultService,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(GeneratorType.PASSPHRASE) }
    var strength by remember { mutableStateOf(SecretStrength.NORMAL) }
    var noUpperCase by remember { mutableStateOf(false) }
    var noDigits by remember { mutableStateOf(false) }
    var noSpecial by remember { mutableStateOf(false) }
    var capitalize by remember { mutableStateOf(false) }
    var addDigit by remember { mutableStateOf(false) }
    var addSpecial by remember { mutableStateOf(false) }
    var pinLength by remember { mutableStateOf(6) }
    var nonce by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf("") }

    LaunchedEffect(type, strength, noUpperCase, noDigits, noSpecial, capitalize, addDigit, addSpecial, pinLength, nonce) {
        preview = when (type) {
            GeneratorType.PASSWORD -> service.generatePassword(
                PasswordGeneratorSpec(strength, noUpperCase, noSpecial, noDigits)
            )
            GeneratorType.PASSPHRASE -> service.generatePassphrase(
                PassphraseGeneratorSpec(strength, capitalize, addDigit, addSpecial)
            )
            GeneratorType.PIN -> service.generatePin(PinGeneratorSpec(pinLength))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(
                Modifier.width(560.dp).padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Generate secret", style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GeneratorType.entries.forEach {
                        FilterChip(
                            selected = type == it,
                            onClick = { type = it },
                            label = { Text(it.label) },
                        )
                    }
                }

                if (type != GeneratorType.PIN) {
                    Text("Strength", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SecretStrength.entries.forEach {
                            FilterChip(
                                selected = strength == it,
                                onClick = { strength = it },
                                label = { Text(it.displayName) },
                            )
                        }
                    }
                }

                when (type) {
                    GeneratorType.PASSWORD -> {
                        CheckOption("No uppercase", noUpperCase) { noUpperCase = it }
                        CheckOption("No digits", noDigits) { noDigits = it }
                        CheckOption("No special characters", noSpecial) { noSpecial = it }
                    }

                    GeneratorType.PASSPHRASE -> {
                        CheckOption("Capitalize first word", capitalize) { capitalize = it }
                        CheckOption("Add digit", addDigit) { addDigit = it }
                        CheckOption("Add special character", addSpecial) { addSpecial = it }
                    }

                    GeneratorType.PIN -> {
                        Text("Length: $pinLength")
                        Slider(
                            value = pinLength.toFloat(),
                            onValueChange = { pinLength = it.roundToInt() },
                            valueRange = 4f..12f,
                            steps = 7,
                        )
                    }
                }

                Text("Preview", style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Text(
                        if (type == GeneratorType.PASSPHRASE) groupForDisplay(preview) else preview,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUse(preview) }) { Text("Use") }
                    OutlinedButton(onClick = { nonce++ }) { Text("Regenerate") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun CheckOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

private fun groupForDisplay(value: String): String =
    if (value.length > 16) value.chunked(4).joinToString(" ") else value
