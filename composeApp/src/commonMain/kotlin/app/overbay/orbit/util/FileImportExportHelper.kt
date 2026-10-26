package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

interface FileImportExportHelper {
    fun exportFile(fileName: String, content: String, mimeType: String = "application/json")
    fun pickAndImportFile(mimeType: String = "*/*", onContent: (String) -> Unit)
}

val LocalFileImportExportHelper = staticCompositionLocalOf<FileImportExportHelper> {
    object : FileImportExportHelper {
        override fun exportFile(fileName: String, content: String, mimeType: String) {}
        override fun pickAndImportFile(mimeType: String, onContent: (String) -> Unit) {}
    }
}

@Composable
expect fun rememberFileImportExportHelper(): FileImportExportHelper
