package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFileImportExportHelper(): FileImportExportHelper {
    return remember {
        object : FileImportExportHelper {
            override fun exportFile(fileName: String, content: String, mimeType: String) {
                // Web PWA file export stub
            }

            override fun pickAndImportFile(mimeType: String, onContent: (String) -> Unit) {
                // Web PWA file import stub
            }
        }
    }
}
