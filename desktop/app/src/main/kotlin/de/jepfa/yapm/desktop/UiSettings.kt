package de.jepfa.yapm.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

/**
 * Persists the user's desired effective UI scale.
 *
 * The value is the target scale (for example 1.7 for a 170% UI), not a
 * multiplier relative to the system density. The composition converts it to a
 * `LocalDensity` multiplier by dividing by the actual base density, so the UI
 * looks the same whether or not Java already honours the desktop scale.
 */
object UiSettings {

    private const val FILE_NAME = "ui.properties"
    private const val SCALE_KEY = "scale"
    private val SCALE_RANGE = 0.5f..4.0f

    fun loadScale(
        directory: Path,
        baseDensity: Float,
        env: Map<String, String> = System.getenv(),
    ): Float {
        readScale(directory)?.let { return it }

        env["ANOTHERPASS_UI_SCALE"]
            ?.toFloatOrNull()
            ?.takeIf { it in SCALE_RANGE }
            ?.let { return it }

        // Detected KDE/GNOME scale exported by the launcher.
        env["ANOTHERPASS_DETECTED_SCALE"]
            ?.toFloatOrNull()
            ?.takeIf { it in SCALE_RANGE }
            ?.let { return it }

        // No configuration: keep the system density as-is.
        return baseDensity.coerceIn(SCALE_RANGE)
    }

    fun saveScale(directory: Path, scale: Float) {
        Files.createDirectories(directory)
        val file = directory.resolve(FILE_NAME)
        val props = Properties()
        props.setProperty(SCALE_KEY, scale.toString())
        Files.newOutputStream(file).use {
            props.store(it, "ANOTHERpass desktop UI settings")
        }
        try {
            Files.setPosixFilePermissions(
                file,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // best effort
        }
    }

    private fun readScale(directory: Path): Float? {
        val file = directory.resolve(FILE_NAME)
        if (!Files.exists(file)) return null
        return try {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            props.getProperty(SCALE_KEY)
                ?.toFloatOrNull()
                ?.takeIf { it in SCALE_RANGE }
        } catch (_: Exception) {
            null
        }
    }
}
