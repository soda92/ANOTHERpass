package de.jepfa.yapm.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class UiSettingsTest {

    @Test
    fun `no configuration keeps the base density`() {
        val dir = Files.createTempDirectory("anotherpass-ui")
        assertEquals(1.0f, UiSettings.loadScale(dir, 1.0f, emptyMap()))
        assertEquals(2.0f, UiSettings.loadScale(dir, 2.0f, emptyMap()))
    }

    @Test
    fun `stored scale wins over env and detection`() {
        val dir = Files.createTempDirectory("anotherpass-ui")
        UiSettings.saveScale(dir, 1.75f)
        assertEquals(
            1.75f,
            UiSettings.loadScale(
                dir,
                1.0f,
                mapOf(
                    "ANOTHERPASS_UI_SCALE" to "2.0",
                    "ANOTHERPASS_DETECTED_SCALE" to "1.7",
                ),
            ),
        )
    }

    @Test
    fun `explicit env override is used when nothing is stored`() {
        val dir = Files.createTempDirectory("anotherpass-ui")
        assertEquals(
            2.25f,
            UiSettings.loadScale(
                dir,
                1.0f,
                mapOf(
                    "ANOTHERPASS_UI_SCALE" to "2.25",
                    "ANOTHERPASS_DETECTED_SCALE" to "1.7",
                ),
            ),
        )
    }

    @Test
    fun `detected desktop scale is used as the target`() {
        val dir = Files.createTempDirectory("anotherpass-ui")
        assertEquals(
            1.7f,
            UiSettings.loadScale(
                dir,
                1.0f,
                mapOf("ANOTHERPASS_DETECTED_SCALE" to "1.7"),
            ),
        )
        // If the base density already is 1.7, the target is still 1.7; the
        // composition divides by the base, so the UI is not double-scaled.
        assertEquals(
            1.7f,
            UiSettings.loadScale(
                dir,
                1.7f,
                mapOf("ANOTHERPASS_DETECTED_SCALE" to "1.7"),
            ),
        )
    }
}
