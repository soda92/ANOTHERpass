package de.jepfa.yapm.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Copy to the system clipboard and clear it again after a delay, unless the
 * clipboard already contains something else.
 */
object Clipboard {

    fun copy(text: String, scope: CoroutineScope, clearAfterMs: Long = 30_000) {
        if (text.isEmpty()) return
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)

        scope.launch {
            delay(clearAfterMs)
            runCatching {
                val current = clipboard.getData(DataFlavor.stringFlavor) as? String
                if (current == text) {
                    clipboard.setContents(StringSelection(""), null)
                }
            }
        }
    }
}
