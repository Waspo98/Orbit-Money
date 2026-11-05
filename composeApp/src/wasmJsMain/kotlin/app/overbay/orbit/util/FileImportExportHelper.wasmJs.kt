package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("(fileName, content, mimeType) => { try { const blob = new Blob([content], { type: mimeType || 'application/json' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url); } catch(e) {} }")
private external fun jsExportFile(fileName: String, content: String, mimeType: String)

@JsFun("(mimeType, callback) => { try { const input = document.createElement('input'); input.type = 'file'; if (mimeType && mimeType !== '*/*') input.accept = mimeType; input.onchange = (e) => { const file = e.target.files && e.target.files[0]; if (!file) return; const reader = new FileReader(); reader.onload = () => { if (typeof reader.result === 'string') callback(reader.result); }; reader.readAsText(file); }; input.click(); } catch(e) {} }")
private external fun jsPickFile(mimeType: String, callback: (String) -> Unit)

@Composable
actual fun rememberFileImportExportHelper(): FileImportExportHelper {
    return remember {
        object : FileImportExportHelper {
            override fun exportFile(fileName: String, content: String, mimeType: String) {
                jsExportFile(fileName, content, mimeType)
            }

            override fun pickAndImportFile(mimeType: String, onContent: (String) -> Unit) {
                jsPickFile(mimeType, onContent)
            }
        }
    }
}
