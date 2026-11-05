package app.overbay.orbit

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    hideLoadingScrim()
    ComposeViewport(body) {
        App()
    }
}

private fun hideLoadingScrim() {
    js("window.hideOrbitLoadingScrim && window.hideOrbitLoadingScrim()")
}
