package de.jepfa.yapm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.jepfa.yapm.application.VaultService
import de.jepfa.yapm.vault.VaultKeyMaterial
import de.jepfa.yapm.vault.VaultSession
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun VaultScreen(
    modifier: Modifier = Modifier,
    service: VaultService,
    session: VaultSession,
    material: VaultKeyMaterial,
    fingerprint: String,
    keyStoreInfo: String,
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    onLock: () -> Unit,
    onExport: () -> Unit,
) {
    var credentials by remember { mutableStateOf<List<VaultService.CredentialView>>(emptyList()) }
    var labels by remember { mutableStateOf<List<VaultService.LabelView>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<VaultService.CredentialDraft?>(null) }
    var search by remember { mutableStateOf("") }
    var showLabels by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<VaultService.CredentialView?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        credentials = service.listCredentials()
        labels = service.listLabels()
    }

    LaunchedEffect(service) { reload() }

    val labelNames = labels.associate { it.id to it.name }
    val filtered = credentials.filter { credential ->
        search.isBlank() || listOf(credential.name, credential.user, credential.website)
            .any { it.contains(search, ignoreCase = true) }
    }
    val expiredCount = credentials.count { it.isExpired }
    val selected = credentials.firstOrNull { it.id == selectedId }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ANOTHERpass", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Fingerprint $fingerprint · ${material.cipherAlgorithm.displayName} · " +
                        "${material.kdfConfig.kdf.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Keystore: $keyStoreInfo", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    editing = VaultService.CredentialDraft(name = "")
                    selectedId = null
                    banner = null
                }) { Text("New credential") }
                OutlinedButton(onClick = { showLabels = true }) { Text("Labels") }
                OutlinedButton(onClick = { showInfo = true }) { Text("Vault info") }
                OutlinedButton(onClick = onExport) { Text("Export") }
                OutlinedButton(onClick = onLock) { Text("Lock") }
                UiScaleControls(uiScale, onUiScaleChange)
            }
        }

        banner?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .padding(start = 24.dp, end = 12.dp, bottom = 24.dp),
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (expiredCount > 0) {
                    Text(
                        "$expiredCount expired credential(s)",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { credential ->
                        CredentialRow(
                            credential = credential,
                            labelNames = labelNames,
                            selected = selectedId == credential.id,
                            onClick = {
                                selectedId = credential.id
                                editing = null
                                banner = null
                            },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                when {
                    editing != null -> CredentialEditor(
                        initial = editing!!,
                        labels = labels,
                        service = service,
                        onSave = { draft ->
                            runCatching { service.saveCredential(draft) }
                                .onSuccess { savedId ->
                                    editing = null
                                    selectedId = savedId
                                    banner = "Saved"
                                    reload()
                                }
                                .onFailure { banner = "Save failed: ${it.message}" }
                        },
                        onCancel = { editing = null },
                    )

                    selected != null -> CredentialDetail(
                        credential = selected,
                        labels = labels,
                        labelNames = labelNames,
                        onEdit = { editing = selected.toDraft() },
                        onDelete = { deleteTarget = selected },
                        onDuplicate = {
                            runCatching { service.duplicateCredential(selected.id) }
                                .onSuccess { newId ->
                                    newId?.let { selectedId = it }
                                    reload()
                                    banner = "Duplicated"
                                }
                                .onFailure { banner = "Duplicate failed: ${it.message}" }
                        },
                        onCopy = { value, label ->
                            Clipboard.copy(value, scope)
                            banner = "$label copied (cleared in 30s)"
                        },
                    )

                    else -> EmptyState()
                }
            }
        }
    }

    if (showLabels) {
        LabelManagerDialog(
            service = service,
            onChanged = { reload() },
            onDismiss = { showLabels = false },
            onBanner = { banner = it },
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Vault info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Fingerprint: $fingerprint")
                    Text("Cipher: ${material.cipherAlgorithm.displayName}")
                    Text(
                        "KDF: ${material.kdfConfig.kdf.displayName}, " +
                            "${material.kdfConfig.iterations} iterations" +
                            (material.kdfConfig.memCostInMiB?.let { ", $it MiB" } ?: "")
                    )
                    Text("Vault version: ${material.vaultVersion}")
                    Text("Keystore: $keyStoreInfo")
                    Text("Credentials: ${credentials.size}")
                    Text("Labels: ${labels.size}")
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete credential?") },
            text = { Text("\"${target.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { service.deleteCredential(target.id) }
                        .onSuccess {
                            if (selectedId == target.id) selectedId = null
                            deleteTarget = null
                            reload()
                            banner = "Deleted"
                        }
                        .onFailure { banner = "Delete failed: ${it.message}" }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Select a credential", style = MaterialTheme.typography.titleMedium)
        Text(
            "or create a new one",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CredentialRow(
    credential: VaultService.CredentialView,
    labelNames: Map<Int, String>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background, MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                credential.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (credential.pinned) Text("📌", style = MaterialTheme.typography.bodySmall)
            if (credential.isExpired) Text("⏰", style = MaterialTheme.typography.bodySmall)
        }
        if (credential.user.isNotBlank()) {
            Text(credential.user, style = MaterialTheme.typography.bodySmall)
        }
        if (credential.website.isNotBlank()) {
            Text(
                credential.website,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val names = credential.labelIds.mapNotNull { labelNames[it] }
        if (names.isNotEmpty()) {
            Text(
                names.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CredentialDetail(
    credential: VaultService.CredentialView,
    labels: List<VaultService.LabelView>,
    labelNames: Map<Int, String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: (value: String, label: String) -> Unit,
) {
    var reveal by remember(credential.id) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                credential.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (credential.pinned) Text("📌")
            if (credential.isExpired) {
                Text(" expired ", color = MaterialTheme.colorScheme.error)
            }
        }

        DetailRow("User", credential.user) {
            if (credential.user.isNotBlank()) {
                TextButton(onClick = { onCopy(credential.user, "User") }) { Text("Copy") }
            }
        }
        DetailRow("Website", credential.website)
        DetailRow("Password", if (reveal) credential.password else "•".repeat(credential.password.length.coerceAtMost(24))) {
            TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") }
            TextButton(onClick = { onCopy(credential.password, "Password") }) { Text("Copy") }
        }
        if (credential.additionalInfo.isNotBlank()) {
            DetailRow("Notes", credential.additionalInfo)
        }
        if (credential.labelIds.isNotEmpty()) {
            Column {
                Text("Labels", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    credential.labelIds.forEach { id ->
                        val label = labels.firstOrNull { it.id == id }
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(label?.name ?: "#$id") },
                        )
                    }
                }
            }
        }
        credential.expiresAt?.let {
            DetailRow("Expires", formatDate(it))
        }
        credential.otpAuthUri?.takeIf { it.isNotBlank() }?.let {
            DetailRow("OTP URI", it)
        }
        credential.modifyTimestamp?.let {
            Text(
                "Last modified ${formatDate(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("Edit") }
            OutlinedButton(onClick = onDuplicate) { Text("Duplicate") }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (label == "Password") FontFamily.Monospace else FontFamily.Default,
                )
            }
            actions()
        }
    }
}

private fun VaultService.CredentialView.toDraft(): VaultService.CredentialDraft =
    VaultService.CredentialDraft(
        id = id,
        name = name,
        website = website,
        user = user,
        password = password,
        additionalInfo = additionalInfo,
        labelIds = labelIds,
        expiresAt = expiresAt,
        pinned = pinned,
        otpAuthUri = otpAuthUri,
    )

fun formatDate(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0) return ""
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

fun parseDate(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return LocalDate.parse(trimmed)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
