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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import de.jepfa.yapm.io.AndroidVaultReader
import de.jepfa.yapm.io.VaultImportException
import de.jepfa.yapm.model.encrypted.CipherAlgorithm
import de.jepfa.yapm.model.encrypted.Encrypted
import de.jepfa.yapm.model.kdf.KdfConfig
import de.jepfa.yapm.model.kdf.KeyDerivationFunction
import de.jepfa.yapm.model.secret.Password
import de.jepfa.yapm.service.secret.KdfParameterService
import de.jepfa.yapm.service.secret.StoredMasterPasswordService
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

private const val APP_VERSION = "0.1.0"

private val configDir by lazy { DesktopVaultStore.defaultConfigDir() }
private val vaultStore by lazy { DesktopVaultStore(configDir) }
private val databasePath by lazy { configDir.resolve("credentials.db") }

private lateinit var keyStore: WrappingKeyStore
private lateinit var keyStoreLabel: String

private data class CreatedVault(
    val material: VaultKeyMaterial,
    val session: VaultSession,
    val database: VaultDatabase,
    val encryptedMasterPassword: Encrypted?,
)

private data class ImportedVault(
    val material: VaultKeyMaterial,
    val session: VaultSession,
    val database: VaultDatabase,
    val masterPassword: Password,
)

fun main(args: Array<String>) {
    if (args.contains("--version") || args.contains("-v")) {
        println("ANOTHERpass desktop $APP_VERSION")
        return
    }
    if (args.contains("--help") || args.contains("-h")) {
        println(
            """
            ANOTHERpass desktop $APP_VERSION
            Usage: anotherpass [options]

              -h, --help     show this help
              -v, --version  print the version
              --self-test    load native libraries and exit (packaging check)

            Configuration and vault data live in ${'$'}XDG_CONFIG_HOME/anotherpass.
            """.trimIndent()
        )
        return
    }
    if (args.contains("--self-test")) {
        org.jetbrains.skiko.Library.load()
        println("ANOTHERpass desktop $APP_VERSION self-test OK (Skiko loaded)")
        return
    }

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
        val windowState = rememberWindowState(width = 1180.dp, height = 820.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "ANOTHERpass",
            state = windowState,
        ) {
            val baseDensity = LocalDensity.current
            var uiScale by remember {
                mutableStateOf(UiSettings.loadScale(configDir, baseDensity.density))
            }
            val densityMultiplier = (uiScale / baseDensity.density).coerceIn(0.5f, 4.0f)

            LaunchedEffect(uiScale, baseDensity.density) {
                val width = 1180f * uiScale / baseDensity.density
                val height = 820f * uiScale / baseDensity.density
                windowState.size = DpSize(Dp(width), Dp(height))
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * densityMultiplier,
                    fontScale = baseDensity.fontScale * densityMultiplier,
                )
            ) {
                MaterialTheme {
                    App(uiScale) { newScale ->
                        uiScale = newScale
                        UiSettings.saveScale(configDir, newScale)
                    }
                }
            }
        }
    }
}

@Composable
private fun App(uiScale: Float, onUiScaleChange: (Float) -> Unit) {
    var material by remember { mutableStateOf(vaultStore.load()) }
    var session by remember { mutableStateOf<VaultSession?>(null) }
    var database by remember { mutableStateOf<VaultDatabase?>(null) }
    var storedMasterPasswordAvailable by remember {
        mutableStateOf(vaultStore.hasStoredMasterPassword())
    }
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
                    uiScale = uiScale,
                    onUiScaleChange = onUiScaleChange,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("ANOTHERpass", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Desktop port — EMP import and PIN-only unlock",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Keystore: $keyStoreLabel",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        UiScaleButton(uiScale, onUiScaleChange)
                    }
                    Spacer(Modifier.height(4.dp))

                    if (material == null) {
                        FirstRunPanel(
                            busy = busy,
                            onCreate = { pin, master, useArgon2, cipher, storeMasterPassword ->
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
                                            val pinPassword = Password(pin)
                                            val masterPassword = Password(master)
                                            val (newMaterial, newSession) = VaultCrypto.create(
                                                pinPassword, masterPassword, kdf, cipher, keyStore,
                                            )
                                            val encryptedMasterPassword = if (storeMasterPassword) {
                                                StoredMasterPasswordService.protect(masterPassword, keyStore)
                                            } else {
                                                null
                                            }
                                            pinPassword.clear()
                                            masterPassword.clear()
                                            val db = VaultDatabase.open(databasePath)
                                            CreatedVault(
                                                newMaterial, newSession, db, encryptedMasterPassword
                                            )
                                        }
                                    }
                                    result.onSuccess { created ->
                                        vaultStore.save(created.material, created.encryptedMasterPassword)
                                        storedMasterPasswordAvailable =
                                            created.encryptedMasterPassword != null
                                        material = created.material
                                        database = created.database
                                        session = created.session
                                        notice = "Vault created"
                                    }.onFailure {
                                        error = "Could not create vault: ${it.message}"
                                    }
                                    busy = false
                                }
                            },
                            onImport = { vaultFile, empFile, pin, manualMaster, codeword, storeMasterPassword ->
                                busy = true
                                error = null
                                notice = null
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            val jsonText = vaultFile.readText()
                                            val masterPassword = if (empFile != null) {
                                                val empText = QrDecoder.decode(empFile)
                                                val emp = Encrypted.fromBase64String(empText)
                                                val code = codeword
                                                    .takeIf { it.isNotEmpty() }
                                                    ?.let { Password(it) }
                                                AndroidVaultReader.recoverMasterPassword(
                                                    jsonText, emp, code
                                                )
                                            } else {
                                                Password(manualMaster)
                                            }

                                            if (!masterPassword.isValid()) {
                                                throw VaultImportException(
                                                    "Could not decrypt the EMP. Check the image and codeword."
                                                )
                                            }

                                            val db = VaultDatabase.open(databasePath)
                                            val imported = try {
                                                AndroidVaultImporter(VaultRepositories(db))
                                                    .import(
                                                        jsonText,
                                                        Password(pin),
                                                        masterPassword,
                                                        keyStore,
                                                    )
                                            } catch (e: Exception) {
                                                if (empFile != null) {
                                                    throw VaultImportException(
                                                        "Wrong PIN, EMP codeword, or the EMP does not " +
                                                            "belong to this vault",
                                                        e,
                                                    )
                                                }
                                                throw e
                                            }
                                            ImportedVault(
                                                imported.material,
                                                imported.session,
                                                db,
                                                masterPassword,
                                            )
                                        }
                                    }
                                    result.onSuccess { imported ->
                                        val encryptedMasterPassword = if (storeMasterPassword) {
                                            StoredMasterPasswordService.protect(
                                                imported.masterPassword, keyStore
                                            )
                                        } else {
                                            null
                                        }
                                        vaultStore.save(imported.material, encryptedMasterPassword)
                                        storedMasterPasswordAvailable =
                                            encryptedMasterPassword != null
                                        material = imported.material
                                        database = imported.database
                                        session = imported.session
                                        imported.masterPassword.clear()
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
                            storedMasterPassword = storedMasterPasswordAvailable,
                            onUnlock = { pin, master, storeMasterPassword ->
                                busy = true
                                error = null
                                notice = null
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            val masterPassword = if (master != null) {
                                                Password(master)
                                            } else {
                                                val stored = vaultStore.loadStoredMasterPassword()
                                                    ?: throw IllegalStateException(
                                                        "No stored master password is available"
                                                    )
                                                StoredMasterPasswordService.reveal(stored, keyStore).also {
                                                    if (!it.isValid()) {
                                                        throw IllegalStateException(
                                                            "Stored master password is unavailable"
                                                        )
                                                    }
                                                }
                                            }

                                            val sess = VaultCrypto.unlock(
                                                Password(pin), masterPassword, material!!, keyStore,
                                            ) ?: throw IllegalArgumentException(
                                                "Wrong PIN or master password"
                                            )

                                            if (storeMasterPassword && master != null) {
                                                val encrypted = StoredMasterPasswordService.protect(
                                                    masterPassword, keyStore
                                                )
                                                vaultStore.save(material!!, encrypted)
                                            }
                                            masterPassword.clear()
                                            val db = VaultDatabase.open(databasePath)
                                            sess to db
                                        }
                                    }
                                    result.onSuccess { unlocked ->
                                        session = unlocked.first
                                        database = unlocked.second
                                        if (storeMasterPassword && master != null) {
                                            storedMasterPasswordAvailable = true
                                        }
                                    }.onFailure {
                                        error = it.message ?: "Unlock failed"
                                    }
                                    busy = false
                                }
                            },
                            onForgetStored = {
                                val currentMaterial = material
                                if (currentMaterial != null) {
                                    StoredMasterPasswordService.forget(keyStore)
                                    vaultStore.save(currentMaterial, null)
                                    storedMasterPasswordAvailable = false
                                    notice = "Stored master password removed"
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

private fun chooseImageFile(): File? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter(
        "QR image (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"
    )
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
    onCreate: (
        pin: String,
        master: String,
        useArgon2: Boolean,
        cipher: CipherAlgorithm,
        storeMasterPassword: Boolean,
    ) -> Unit,
    onImport: (
        vaultFile: File,
        empFile: File?,
        pin: String,
        manualMaster: String,
        codeword: String,
        storeMasterPassword: Boolean,
    ) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var useArgon2 by remember { mutableStateOf(true) }
    var cipher by remember { mutableStateOf(CipherAlgorithm.AES_256) }
    var createStore by remember { mutableStateOf(true) }

    var importFile by remember { mutableStateOf<File?>(null) }
    var importEmp by remember { mutableStateOf<File?>(null) }
    var importPin by remember { mutableStateOf("") }
    var importMaster by remember { mutableStateOf("") }
    var importCodeword by remember { mutableStateOf("") }
    var importStore by remember { mutableStateOf(true) }

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

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = createStore, onCheckedChange = { createStore = it })
        Text("Store master password for PIN-only unlock")
    }

    Button(
        onClick = { onCreate(pin, master, useArgon2, cipher, createStore) },
        enabled = !busy && pin.length >= Constants.MIN_PIN_LENGTH && master.isNotEmpty(),
    ) {
        Text("Create vault")
    }

    Spacer(Modifier.height(8.dp))
    Text("Import from Android", style = MaterialTheme.typography.titleMedium)
    Text(
        "Choose the vault export. If the export has no master key, also choose the EMP QR image " +
            "and enter its codeword. The master password is recovered and verified during import.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { importFile = chooseOpenFile() }, enabled = !busy) {
            Text("Choose export file…")
        }
        importFile?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { importEmp = chooseImageFile() }, enabled = !busy) {
            Text("Choose EMP QR image…")
        }
        importEmp?.let {
            Text(it.name, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { importEmp = null }) { Text("Clear") }
        }
    }
    OutlinedTextField(
        value = importPin,
        onValueChange = { importPin = it },
        label = { Text("PIN") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (importEmp != null) {
        OutlinedTextField(
            value = importCodeword,
            onValueChange = { importCodeword = it },
            label = { Text("EMP codeword (required if the EMP is protected)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The master password will be recovered from the EMP and verified against the vault.",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        OutlinedTextField(
            value = importMaster,
            onValueChange = { importMaster = it },
            label = { Text("Master password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = importStore, onCheckedChange = { importStore = it })
        Text("Store master password for PIN-only unlock")
    }
    Button(
        onClick = {
            importFile?.let {
                onImport(
                    it,
                    importEmp,
                    importPin,
                    importMaster,
                    importCodeword,
                    importStore,
                )
            }
        },
        enabled = !busy && importFile != null && importPin.isNotEmpty() &&
            (importEmp != null || importMaster.isNotEmpty()),
    ) {
        Text("Import vault")
    }
}

@Composable
private fun UnlockPanel(
    material: VaultKeyMaterial,
    busy: Boolean,
    storedMasterPassword: Boolean,
    onUnlock: (pin: String, master: String?, storeMasterPassword: Boolean) -> Unit,
    onForgetStored: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var storeMasterPassword by remember { mutableStateOf(true) }
    var manual by remember { mutableStateOf(false) }
    val useStored = storedMasterPassword && !manual

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

    if (useStored) {
        Text(
            "Master password is stored in the Secret Service. Unlock with your PIN only.",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        OutlinedTextField(
            value = master,
            onValueChange = { master = it },
            label = { Text("Master password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = storeMasterPassword,
                onCheckedChange = { storeMasterPassword = it },
            )
            Text("Store master password for PIN-only unlock")
        }
    }

    Button(
        onClick = {
            onUnlock(
                pin,
                if (useStored) null else master,
                storeMasterPassword,
            )
        },
        enabled = !busy && pin.isNotEmpty() && (useStored || master.isNotEmpty()),
    ) {
        Text("Unlock")
    }

    if (storedMasterPassword) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (manual) {
                TextButton(onClick = { manual = false }) {
                    Text("Use stored master password")
                }
            } else {
                TextButton(onClick = { manual = true }) {
                    Text("Enter master password manually")
                }
            }
            TextButton(onClick = onForgetStored) {
                Text("Forget stored master password")
            }
        }
    }
}
