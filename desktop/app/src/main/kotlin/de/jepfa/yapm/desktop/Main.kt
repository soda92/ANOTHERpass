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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import de.jepfa.yapm.application.VaultService
import de.jepfa.yapm.crypto.KeyStoreFactory
import de.jepfa.yapm.crypto.WrappingKeyStore
import de.jepfa.yapm.database.VaultDatabase
import de.jepfa.yapm.database.VaultRepositories
import de.jepfa.yapm.io.AndroidVaultExporter
import de.jepfa.yapm.io.AndroidVaultImporter
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
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
            state = rememberWindowState(width = 1180.dp, height = 820.dp),
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

    fun lock() {
        session?.close()
        database?.close()
        session = null
        database = null
        error = null
        notice = null
    }

    val service = remember(database, session) {
        if (database != null && session != null) {
            VaultService(VaultRepositories(database!!), session!!)
        } else {
            null
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (busy || error != null || notice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (busy) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }

            val currentSession = session
            if (currentSession != null && service != null) {
                VaultScreen(
                    modifier = Modifier.weight(1f),
                    service = service,
                    session = currentSession,
                    material = material!!,
                    fingerprint = currentSession.fingerprint(),
                    keyStoreInfo = keyStoreLabel,
                    onLock = ::lock,
                    onExport = {
                        val db = database ?: return@VaultScreen
                        chooseSaveFile()?.let { file ->
                            busy = true
                            error = null
                            notice = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        AndroidVaultExporter(VaultRepositories(db))
                                            .export(material!!, currentSession, keyStore)
                                            .also { file.writeText(it) }
                                    }
                                }
                                notice = result.fold(
                                    { "Exported vault to ${file.name}" },
                                    { error = "Export failed: ${it.message}"; null },
                                )
                                busy = false
                            }
                        }
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("ANOTHERpass", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Desktop port — phase 4 (credential CRUD)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Keystore: $keyStoreLabel", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))

                    if (material == null) {
                        FirstRunPanel(
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
                                        database = db
                                        session = newSession
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
                                        database = db
                                        session = newSession
                                        notice = "Imported Android vault"
                                    }.onFailure {
                                        error = "Import failed: ${it.message}"
                                    }
                                    busy = false
                                }
                            },
                        )
                    } else {
                        UnlockPanel(
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
                                            sess to db
                                        }.getOrNull()
                                    }
                                    if (unlocked == null) {
                                        error = "Wrong PIN or master password."
                                    } else {
                                        session = unlocked.first
                                        database = unlocked.second
                                    }
                                    busy = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun chooseOpenFile(): File? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter("ANOTHERpass export (*.json, *.txt)", "json", "txt")
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseSaveFile(): File? {
    val chooser = JFileChooser()
    chooser.selectedFile = File("anotherpass-export.json")
    chooser.fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
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
