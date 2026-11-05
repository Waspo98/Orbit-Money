package app.overbay.orbit.util

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.StandardCharsets

@Composable
actual fun rememberFileImportExportHelper(): FileImportExportHelper {
    val context = LocalContext.current
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingImportCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && pendingExportContent != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingExportContent!!.toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }
            } catch (_: Exception) {}
        }
        pendingExportContent = null
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && pendingImportCallback != null) {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                    val text = reader.readText()
                    pendingImportCallback?.invoke(text)
                }
            } catch (_: Exception) {}
        }
        pendingImportCallback = null
    }

    return remember(context) {
        object : FileImportExportHelper {
            override fun exportFile(fileName: String, content: String, mimeType: String) {
                pendingExportContent = content
                try {
                    createDocLauncher.launch(fileName)
                } catch (_: Exception) {
                    pendingExportContent = null
                }
            }

            override fun pickAndImportFile(mimeType: String, onContent: (String) -> Unit) {
                pendingImportCallback = onContent
                try {
                    val types = if (mimeType == "*/*") arrayOf("*/*") else arrayOf(mimeType)
                    openDocLauncher.launch(types)
                } catch (_: Exception) {
                    pendingImportCallback = null
                }
            }
        }
    }
}
