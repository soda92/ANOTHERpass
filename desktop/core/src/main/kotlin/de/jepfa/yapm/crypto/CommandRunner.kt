package de.jepfa.yapm.crypto

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class KeyStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class CommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
)

/** Small seam so the Secret Service integration can be tested without D-Bus. */
interface CommandRunner {
    fun run(command: List<String>, stdin: ByteArray? = null): CommandResult
}

object ProcessCommandRunner : CommandRunner {

    override fun run(command: List<String>, stdin: ByteArray?): CommandResult {
        val process = ProcessBuilder(command).start()
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        // Drain both pipes concurrently to avoid deadlocking on a full buffer.
        val outThread = Thread { runCatching { process.inputStream.copyTo(out) } }
        val errThread = Thread { runCatching { process.errorStream.copyTo(err) } }
        outThread.isDaemon = true
        errThread.isDaemon = true
        outThread.start()
        errThread.start()

        try {
            process.outputStream.use { os ->
                if (stdin != null) os.write(stdin)
            }
        } catch (_: Exception) {
            // The child may have exited without reading stdin.
        }

        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw KeyStoreException("Command timed out: ${command.firstOrNull()}")
        }

        outThread.join(2_000)
        errThread.join(2_000)

        return CommandResult(
            process.exitValue(),
            out.toByteArray(),
            err.toString(Charsets.UTF_8),
        )
    }
}
