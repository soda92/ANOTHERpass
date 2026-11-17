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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.jepfa.yapm.crypto.KeyStoreFactory
import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.io.AndroidVaultExporter
import de.jepfa.yapm.io.AndroidVaultImporter
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.EncCredential
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.KdfParameterService
import de.jepfa.yapm.util.Constants
import de.jepfa.yapm.vault.VaultCrypto
import de.jepfa.yapm.vault.VaultKeyMaterial
import de.jepfa.yapm.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val configDir = DesktopVaultStore.defaultConfigDir()
private val vaultStore = DesktopVaultStore(configDir)
private val databasePath = configDir.resolve("credentials.db")

private lateinit var keyStore: WrappingKeyStore
private lateinit var keyStoreLabel: String

private data class LoadedVault(
    val credentials: List<EncCredential>,
    val labelNames: Map<Int, String>,
)

fun main() {
    val choice = try {
        KeyStoreFactory.create(configDir)
    } catch (e: Exception) {
        System.err.println("Cannot initialize keystore: ${e.message}")
        kotlin.system.exitProcess(1)
    }
    keyStore = choice.store
    keyStoreLabel = when (choice.kind) {
        KeyStoreFactory.Kind.SECRET_SERVICE -> "Secret Service"
        KeyStoreFactory.Kind.FILE -> "file fallback"
    } + " — ${choice.reason}"

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ANOTHERpass",
            state = rememberWindowState(width = 720.dp, height = 780.dp),
        ) {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    var material by remember { mutableStateOf(vaultStore.load()) }
    var session by remember { mutableStateOf<VaultSession?>(null) }
    var database by remember { mutableStateOf<VaultDatabase?>(null) }
    var loaded by remember { mutableStateOf(LoadedVault(emptyList(), emptyMap())) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            session?.close()
            database?.close()
        }
    }

    fun applyUnlocked(db: VaultDatabase, sess: VaultSession, data: LoadedVault) {
        database?.close()
        database = db
        session = sess
        loaded = data
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ANOTHERpass", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Desktop port — phase 3 (Secret Service keystore)",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Keystore: $keyStoreLabel", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))

            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Working…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            when {
                session != null -> UnlockedPanel(
                    session = session!!,
                    material = material,
                    loaded = loaded,
                    onExport = { file ->
                        val currentSession = session ?: return@UnlockedPanel
                        val currentMaterial = material ?: return@UnlockedPanel
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val db = database ?: VaultDatabase.open(databasePath)
                                    val text = AndroidVaultExporter(VaultRepositories(db))
                                        .export(currentMaterial, currentSession, keyStore)
                                    file.writeText(text)
                                }
                            }
                            notice = result.fold(
                                { "Exported vault to ${file.name}" },
                                { error = "Export failed: ${it.message}"; null },
                            )
                            busy = false
                        }
                    },
                    onLock = {
                        session?.close()
                        database?.close()
                        session = null
                        database = null
                        loaded = LoadedVault(emptyList(), emptyMap())
                        error = null
                        notice = null
                    },
                )

                material == null -> FirstRunPanel(
                    busy = busy,
                    onCreate = { pin, master, useArgon2, cipher ->
                        busy = true
                        error = null
                        notice = null
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    val kdf = if (useArgon2) {
                                        KdfConfig(
                                            KeyDerivationFunction.ARGON2_ID,
                                            KdfParameterService.DEFAULT_ARGON_ITERATIONS,
                                            KdfParameterService.DEFAULT_ARGON_MIB,
                                        )
                                    } else {
                                        KdfConfig(
                                            KeyDerivationFunction.BUILT_IN_PBKDF,
                                            KdfParameterService.DEFAULT_PBKDF_ITERATIONS,
                                            null,
                                        )
                                    }
                                    val (newMaterial, newSession) = VaultCrypto.create(
                                        Password(pin), Password(master), kdf, cipher, keyStore,
                                    )
                                    val db = VaultDatabase.open(databasePath)
                                    Triple(newMaterial, newSession, db)
                                }
                            }
                            result.onSuccess { (newMaterial, newSession, db) ->
                                vaultStore.save(newMaterial)
                                material = newMaterial
                                applyUnlocked(db, newSession, loadData(db, newSession))
                                notice = "Vault created"
                            }.onFailure {
                                error = "Could not create vault: ${it.message}"
                            }
                            busy = false
                        }
                    },
                    onImport = { file, pin, master ->
                        busy = true
                        error = null
                        notice = null
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    val db = VaultDatabase.open(databasePath)
                                    val imported = AndroidVaultImporter(VaultRepositories(db))
                                        .import(file.readText(), Password(pin), Password(master), keyStore)
                                    Triple(imported.material, imported.session, db)
                                }
                            }
                            result.onSuccess { (newMaterial, newSession, db) ->
                                vaultStore.save(newMaterial)
                                material = newMaterial
                                applyUnlocked(db, newSession, loadData(db, newSession))
                                notice = "Imported Android vault"
                            }.onFailure {
                                error = "Import failed: ${it.message}"
                            }
                            busy = false
                        }
                    },
                )

                else -> UnlockPanel(
                    material = material!!,
                    busy = busy,
                    onUnlock = { pin, master ->
                        busy = true
                        error = null
                        notice = null
                        scope.launch {
                            val unlocked = withContext(Dispatchers.Default) {
                                runCatching {
                                    val sess = VaultCrypto.unlock(
                                        Password(pin), Password(master), material!!, keyStore,
                                    ) ?: return@runCatching null
                                    val db = VaultDatabase.open(databasePath)
                                    Triple(db, sess, loadData(db, sess))
                                }.getOrNull()
                            }
                            if (unlocked == null) {
                                error = "Wrong PIN or master password."
                            } else {
                                applyUnlocked(unlocked.first, unlocked.second, unlocked.third)
                            }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

private fun loadData(db: VaultDatabase, session: VaultSession): LoadedVault {
    val repos = VaultRepositories(db)
    val labels = repos.labels.findAll().associate { (it.id ?: 0) to session.decryptString(it.name) }
    return LoadedVault(repos.credentials.findAll(), labels)
}

private fun chooseOpenFile(): File? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter("ANOTHERpass export (*.json, *.txt)", "json", "txt")
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun chooseSaveFile(): File? {
    val chooser = JFileChooser()
    chooser.selectedFile = File("anotherpass-export.json")
    chooser.fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

@Composable
private fun FirstRunPanel(
    busy: Boolean,
    onCreate: (pin: String, master: String, useArgon2: Boolean, cipher: CipherAlgorithm) -> Unit,
    onImport: (file: File, pin: String, master: String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var useArgon2 by remember { mutableStateOf(true) }
    var cipher by remember { mutableStateOf(CipherAlgorithm.AES_256) }

    var importFile by remember { mutableStateOf<File?>(null) }
    var importPin by remember { mutableStateOf("") }
    var importMaster by remember { mutableStateOf("") }

    Text("Create a new vault", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it },
        label = { Text("PIN (min. ${Constants.MIN_PIN_LENGTH} digits)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = master,
        onValueChange = { master = it },
        label = { Text("Master password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Key derivation", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = useArgon2, onClick = { useArgon2 = true }, label = { Text("Argon2id") })
        FilterChip(selected = !useArgon2, onClick = { useArgon2 = false }, label = { Text("PBKDF2") })
    }

    Text("Cipher", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(CipherAlgorithm.AES_128, CipherAlgorithm.AES_256, CipherAlgorithm.CHACHACHA20).forEach {
            FilterChip(
                selected = cipher == it,
                onClick = { cipher = it },
                label = { Text(it.displayName) },
            )
        }
    }

    Button(
        onClick = { onCreate(pin, master, useArgon2, cipher) },
        enabled = !busy && pin.length >= Constants.MIN_PIN_LENGTH && master.isNotEmpty(),
    ) {
        Text("Create vault")
    }

    Spacer(Modifier.height(8.dp))
    Text("Import from Android", style = MaterialTheme.typography.titleMedium)
    Text(
        "On the phone: Export vault → enable \"include master key\". Then open that file here.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { importFile = chooseOpenFile() }, enabled = !busy) {
            Text("Choose export file…")
        }
        importFile?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
    }
    OutlinedTextField(
        value = importPin,
        onValueChange = { importPin = it },
        label = { Text("PIN") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = importMaster,
        onValueChange = { importMaster = it },
        label = { Text("Master password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { importFile?.let { onImport(it, importPin, importMaster) } },
        enabled = !busy && importFile != null && importPin.isNotEmpty() && importMaster.isNotEmpty(),
    ) {
        Text("Import vault")
    }
}

@Composable
private fun UnlockPanel(
    material: VaultKeyMaterial,
    busy: Boolean,
    onUnlock: (pin: String, master: String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }

    Text("Unlock vault", style = MaterialTheme.typography.titleMedium)
    Text(
        "Cipher: ${material.cipherAlgorithm.displayName} · KDF: ${material.kdfConfig.kdf.displayName} · v${material.vaultVersion}",
        style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it },
        label = { Text("PIN") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = master,
        onValueChange = { master = it },
        label = { Text("Master password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = { onUnlock(pin, master) },
        enabled = !busy && pin.isNotEmpty() && master.isNotEmpty(),
    ) {
        Text("Unlock")
    }
}

@Composable
private fun UnlockedPanel(
    session: VaultSession,
    material: VaultKeyMaterial?,
    loaded: LoadedVault,
    onExport: (File) -> Unit,
    onLock: () -> Unit,
) {
    Text("Vault unlocked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text("Fingerprint: ${session.fingerprint()}")
    material?.let {
        Text(
            "Cipher: ${it.cipherAlgorithm.displayName} · KDF: ${it.kdfConfig.kdf.displayName} · v${it.vaultVersion}",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { chooseSaveFile()?.let(onExport) }) { Text("Export for Android…") }
        OutlinedButton(onClick = onLock) { Text("Lock vault") }
    }

    Spacer(Modifier.height(8.dp))
    Text("Credentials (${loaded.credentials.size})", style = MaterialTheme.typography.titleMedium)

    if (loaded.credentials.isEmpty()) {
        Text("No credentials yet. Import an Android export or add some in a later phase.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(loaded.credentials) { credential ->
                CredentialCard(credential, session, loaded.labelNames)
            }
        }
    }
}

@Composable
private fun CredentialCard(
    credential: EncCredential,
    session: VaultSession,
    labelNames: Map<Int, String>,
) {
    fun decrypt(value: de.jepfa.yapm.model.encrypted.Encrypted): String =
        runCatching { session.decryptString(value) }.getOrDefault("<undecryptable>")

    val name = decrypt(credential.name)
    val user = decrypt(credential.user)
    val website = decrypt(credential.website)
    val labelIds = decrypt(credential.labels)
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
    val labels = labelIds.mapNotNull { labelNames[it] }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleSmall)
                if (credential.pinned) Text("📌")
            }
            if (user.isNotBlank()) Text(user, style = MaterialTheme.typography.bodySmall)
            if (website.isNotBlank()) Text(website, style = MaterialTheme.typography.bodySmall)
            if (labels.isNotEmpty()) {
                SelectionContainer {
                    Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
