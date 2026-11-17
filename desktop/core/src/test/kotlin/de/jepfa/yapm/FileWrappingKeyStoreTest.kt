package de.jepfa.yapm

import de.jepfa.yapm.crypto.FileWrappingKeyStore
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileWrappingKeyStoreTest {

    @Test
    fun `keys survive reopening the file`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val file = dir.resolve("keys.properties")

        val first = FileWrappingKeyStore(file).getOrCreateKey("ALIAS")
        val second = FileWrappingKeyStore(file).getOrCreateKey("ALIAS")

        assertContentEquals(first.encoded, second.encoded)
    }

    @Test
    fun `different aliases get different keys`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val store = FileWrappingKeyStore(dir.resolve("keys.properties"))

        val a = store.getOrCreateKey("A")
        val b = store.getOrCreateKey("B")
        assertTrue(!a.encoded.contentEquals(b.encoded))
    }

    @Test
    fun `deleted keys are regenerated`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val file = dir.resolve("keys.properties")
        val store = FileWrappingKeyStore(file)

        val first = store.getOrCreateKey("ALIAS").encoded
        store.deleteKey("ALIAS")
        val second = store.getOrCreateKey("ALIAS").encoded

        assertTrue(!first.contentEquals(second))
    }

    @Test
    fun `file is owner read write only`() {
        val dir = Files.createTempDirectory("anotherpass-keystore")
        val file = dir.resolve("keys.properties")
        FileWrappingKeyStore(file).getOrCreateKey("ALIAS")

        val perms = Files.getPosixFilePermissions(file)
        assertNotNull(perms)
        assertTrue(perms == setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    }
}
