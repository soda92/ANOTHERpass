package de.jepfa.yapm

import de.jepfa.yapm.crypto.CommandResult
import de.jepfa.yapm.crypto.CommandRunner
import de.jepfa.yapm.crypto.KeyStoreException
import de.jepfa.yapm.crypto.KeyStoreFactory
import de.jepfa.yapm.crypto.ProcessCommandRunner
import de.jepfa.yapm.crypto.SecretServiceKeyStore
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** In-memory simulation of the `secret-tool` CLI. */
private class FakeSecretTool(var available: Boolean = true) : CommandRunner {

    val values = mutableMapOf<String, String>()
    val commands = mutableListOf<List<String>>()

    override fun run(command: List<String>, stdin: ByteArray?): CommandResult {
        commands += command
        if (command.getOrNull(1) == "--version") {
            return if (available) {
                CommandResult(0, "secret-tool 0.21\n".toByteArray(), "")
            } else {
                CommandResult(127, ByteArray(0), "secret-tool: not found")
            }
        }
        if (!available) {
            return CommandResult(1, ByteArray(0), "Cannot autolaunch D-Bus without X11 \$DISPLAY")
        }

        val aliasIndex = command.indexOf("alias")
        if (aliasIndex < 0 || aliasIndex + 1 >= command.size) {
            return CommandResult(2, ByteArray(0), "bad arguments")
        }
        val alias = command[aliasIndex + 1]

        return when (command.getOrNull(1)) {
            "store" -> {
                values[alias] = String(stdin ?: ByteArray(0), Charsets.UTF_8).trim()
                CommandResult(0, ByteArray(0), "")
            }

            "lookup" -> values[alias]?.let {
                CommandResult(0, (it + "\n").toByteArray(Charsets.UTF_8), "")
            } ?: CommandResult(1, ByteArray(0), "")

            "clear" -> {
                values.remove(alias)
                CommandResult(0, ByteArray(0), "")
            }

            else -> CommandResult(2, ByteArray(0), "unknown command")
        }
    }
}

class SecretServiceKeyStoreTest {

    @Test
    fun `stores keys in the secret service and reloads them`() {
        val runner = FakeSecretTool()
        val first = SecretServiceKeyStore("secret-tool", runner)
        assertTrue(first.isAvailable())

        val key = first.getOrCreateKey("YAPM/keyAlias:MK")
        val second = SecretServiceKeyStore("secret-tool", runner)
        val reloaded = second.getOrCreateKey("YAPM/keyAlias:MK")

        assertContentEquals(key.encoded, reloaded.encoded)
        assertTrue(runner.values["YAPM/keyAlias:MK"]!!.startsWith("v1:"))
    }

    @Test
    fun `delete regenerates a different key`() {
        val runner = FakeSecretTool()
        val store = SecretServiceKeyStore("secret-tool", runner)
        val first = store.getOrCreateKey("ALIAS").encoded
        store.deleteKey("ALIAS")
        val second = store.getOrCreateKey("ALIAS").encoded
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `unavailable when secret service is missing`() {
        val runner = FakeSecretTool(available = false)
        val store = SecretServiceKeyStore("secret-tool", runner)

        assertFalse(store.isAvailable())
        assertFailsWith<KeyStoreException> { store.getOrCreateKey("ALIAS") }
    }

    @Test
    fun `uses the documented attributes and label`() {
        val runner = FakeSecretTool()
        val store = SecretServiceKeyStore("secret-tool", runner)
        store.getOrCreateKey("ALIAS")

        val storeCommand = runner.commands.first { it.getOrNull(1) == "store" }
        assertTrue(storeCommand.contains("--label=ANOTHERpass wrapping key (ALIAS)"))
        assertEquals(
            listOf("application", "anotherpass", "purpose", "wrapping-key", "alias", "ALIAS"),
            storeCommand.takeLast(6),
        )
    }

    @Test
    fun `availability probe does not write secrets`() {
        val runner = FakeSecretTool()
        val store = SecretServiceKeyStore("secret-tool", runner)
        assertTrue(store.isAvailable())
        assertTrue(runner.commands.none { it.getOrNull(1) == "store" })
        assertTrue(runner.values.isEmpty())
    }

    @Test
    fun `real process runner talks to a fake secret-tool binary`() {
        val dir = Files.createTempDirectory("fake-secret-tool")
        val script = dir.resolve("secret-tool")
        val storeDir = dir.resolve("store")
        Files.writeString(script, fakeSecretToolScript(storeDir))
        Files.setPosixFilePermissions(
            script,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )

        val executable = script.toAbsolutePath().toString()
        val first = SecretServiceKeyStore(executable, ProcessCommandRunner)
        assertTrue(first.isAvailable())
        val key = first.getOrCreateKey("ALIAS")

        val second = SecretServiceKeyStore(executable, ProcessCommandRunner)
        assertContentEquals(key.encoded, second.getOrCreateKey("ALIAS").encoded)

        second.deleteKey("ALIAS")
        val regenerated = second.getOrCreateKey("ALIAS")
        assertFalse(key.encoded.contentEquals(regenerated.encoded))
    }
}

private fun fakeSecretToolScript(storeDir: java.nio.file.Path): String = """
    #!/usr/bin/env bash
    set -euo pipefail
    store_dir='$storeDir'
    mkdir -p "${'$'}store_dir"
    cmd="${'$'}{1:-}"
    shift || true
    if [ "${'$'}cmd" = "--version" ]; then
      echo "secret-tool 0.21"
      exit 0
    fi
    alias_val=""
    prev=""
    for a in "${'$'}@"; do
      if [ "${'$'}prev" = "alias" ]; then alias_val="${'$'}a"; fi
      prev="${'$'}a"
    done
    if [ -z "${'$'}alias_val" ]; then
      echo "missing alias" >&2
      exit 2
    fi
    file="${'$'}store_dir/${'$'}(printf '%s' "${'$'}alias_val" | tr '/: ' '___')"
    case "${'$'}cmd" in
      store) cat > "${'$'}file" ;;
      lookup) if [ -f "${'$'}file" ]; then cat "${'$'}file"; else exit 1; fi ;;
      clear) rm -f "${'$'}file" ;;
      *) echo "unknown command" >&2; exit 2 ;;
    esac
""".trimIndent() + "\n"

class KeyStoreFactoryTest {

    @Test
    fun `prefers secret service when no file keys exist`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val runner = FakeSecretTool()
        val choice = KeyStoreFactory.create(
            dir, emptyMap(), SecretServiceKeyStore("secret-tool", runner)
        )
        assertEquals(KeyStoreFactory.Kind.SECRET_SERVICE, choice.kind)
        assertTrue(choice.store.getOrCreateKey("ALIAS").encoded.isNotEmpty())
    }

    @Test
    fun `falls back to file when secret service is unavailable`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val choice = KeyStoreFactory.create(
            dir, emptyMap(), SecretServiceKeyStore("secret-tool", FakeSecretTool(available = false))
        )
        assertEquals(KeyStoreFactory.Kind.FILE, choice.kind)
        assertTrue(choice.reason.contains("unavailable"))
    }

    @Test
    fun `existing file keys prevent switching to secret service`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        Files.writeString(dir.resolve(KeyStoreFactory.FILE_NAME), "YAPM/keyAlias:MK=abc\n")

        val choice = KeyStoreFactory.create(
            dir, emptyMap(), SecretServiceKeyStore("secret-tool", FakeSecretTool())
        )
        assertEquals(KeyStoreFactory.Kind.FILE, choice.kind)
        assertTrue(choice.reason.contains("existing"))
    }

    @Test
    fun `explicit file override is honoured`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val choice = KeyStoreFactory.create(
            dir,
            mapOf(KeyStoreFactory.ENV_KEYSTORE to "file"),
            SecretServiceKeyStore("secret-tool", FakeSecretTool()),
        )
        assertEquals(KeyStoreFactory.Kind.FILE, choice.kind)
    }

    @Test
    fun `explicit secret service override fails loudly when unavailable`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        assertFailsWith<KeyStoreException> {
            KeyStoreFactory.create(
                dir,
                mapOf(KeyStoreFactory.ENV_KEYSTORE to "secret-service"),
                SecretServiceKeyStore("secret-tool", FakeSecretTool(available = false)),
            )
        }
    }
}
