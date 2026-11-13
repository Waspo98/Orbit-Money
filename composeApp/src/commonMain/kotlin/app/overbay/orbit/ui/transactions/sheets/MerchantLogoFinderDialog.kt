package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.network.MerchantLogoCandidateDto
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.transactions.components.MerchantLogoImageCache
import app.overbay.orbit.util.LocalOrbitHaptics
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.launch

/**
 * Native Compose Multiplatform modal sheet for finding and overriding vendor/merchant logos.
 * Shared across Transactions (merchant brandmarks) and Accounts (institution brandmarks).
 */
@Composable
fun MerchantLogoFinderDialog(
    transaction: Transaction,
    onSearchBrands: suspend (query: String) -> Result<MerchantLogoSearchResponseDto>,
    onSaveLogoUrl: suspend (logoUrl: String) -> Result<Unit>,
    onUseCategoryIcon: suspend () -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val initialQuery = remember(transaction.id) {
        transaction.effectiveMerchant.ifBlank { transaction.merchantLogo?.merchantName ?: "" }
    }
    BrandLogoFinderDialog(
        targetKey = "txn:${transaction.id}",
        subjectName = transaction.effectiveMerchant.ifBlank { "this merchant" },
        initialQuery = initialQuery,
        initialImageUrl = transaction.merchantLogo?.url,
        searchFieldLabel = "Merchant Search",
        searchPlaceholder = "Merchant name",
        fallbackButtonLabel = "Use Category Icon",
        onSearchBrands = onSearchBrands,
        onSaveLogoUrl = onSaveLogoUrl,
        onUseFallbackIcon = onUseCategoryIcon,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandLogoFinderDialog(
    targetKey: Any,
    subjectName: String,
    initialQuery: String,
    initialImageUrl: String?,
    searchFieldLabel: String = "Brand Search",
    searchPlaceholder: String = "Brand or bank name",
    fallbackButtonLabel: String = "Use Account Icon",
    onSearchBrands: suspend (query: String) -> Result<MerchantLogoSearchResponseDto>,
    onSaveLogoUrl: suspend (logoUrl: String) -> Result<Unit>,
    onUseFallbackIcon: suspend () -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var query by remember(targetKey) { mutableStateOf(initialQuery) }
    var imageUrl by remember(targetKey) { mutableStateOf(initialImageUrl ?: "") }
    var candidates by remember(targetKey) { mutableStateOf<List<MerchantLogoCandidateDto>>(emptyList()) }
    var searching by remember(targetKey) { mutableStateOf(false) }
    var saving by remember(targetKey) { mutableStateOf(false) }
    var hasSearched by remember(targetKey) { mutableStateOf(false) }
    var errorMessage by remember(targetKey) { mutableStateOf<String?>(null) }

    fun triggerSearch(searchQuery: String = query) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Enter a search term."
            return
        }
        scope.launch {
            searching = true
            errorMessage = null
            val res = onSearchBrands(trimmed)
            searching = false
            hasSearched = true
            res.onSuccess { dto ->
                candidates = dto.candidates.filter { !it.logoUrl.isNullOrBlank() }
                if (!dto.configured && candidates.isEmpty()) {
                    errorMessage = "Logo search is not configured on this server yet. Paste a direct image URL below."
                }
            }.onFailure { err ->
                candidates = emptyList()
                errorMessage = err.message ?: "Logo search failed."
            }
        }
    }

    LaunchedEffect(targetKey) {
        if (initialQuery.isNotBlank()) {
            triggerSearch(initialQuery)
        }
    }

    fun openDuckDuckGoImages() {
        val searchBase = query.trim().ifBlank { subjectName.ifBlank { "bank" } }
        val encoded = "$searchBase logo".encodeURLQueryComponent()
        runCatching {
            uriHandler.openUri("https://duckduckgo.com/?q=$encoded&iar=images&iax=images&ia=images")
        }
    }

    OrbitModalSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        confirmText = if (saving) "Saving..." else "Save",
        onConfirm = {
            val trimmed = imageUrl.trim()
            if (trimmed.isEmpty()) {
                errorMessage = "Choose a result or paste a direct image URL."
            } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                errorMessage = "Logo URL must start with http:// or https://."
            } else {
                haptics.confirm()
                MerchantLogoImageCache.clearFailed(trimmed)
                scope.launch {
                    saving = true
                    errorMessage = null
                    onSaveLogoUrl(trimmed)
                        .onSuccess { onDismiss() }
                        .onFailure { err ->
                            saving = false
                            errorMessage = err.message ?: "Logo override failed."
                        }
                }
            }
        },
        title = "Find Logo",
        subtitle = "Search brand logos for $subjectName, or paste a direct image URL.",
        confirmEnabled = !saving,
        isSaving = saving,
        containerColor = MaterialTheme.colorScheme.surface,
        bodySpacing = 0.dp
    ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(searchFieldLabel) },
                    placeholder = { Text(searchPlaceholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { triggerSearch(query) }),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        triggerSearch(query)
                    },
                    enabled = !searching && !saving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 6.dp).height(52.dp)
                ) {
                    Text(if (searching) "Searching..." else "Search")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MerchantLogoCandidatesSection(
                candidates = candidates,
                selectedImageUrl = imageUrl,
                searching = searching,
                hasSearched = hasSearched,
                errorMessage = errorMessage,
                onSelectCandidate = { candUrl ->
                    imageUrl = candUrl
                    errorMessage = null
                }
            )

            OutlinedTextField(
                value = imageUrl,
                onValueChange = {
                    imageUrl = it
                    errorMessage = null
                },
                label = { Text("Logo Image URL") },
                placeholder = { Text("https://example.com/logo.png") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            MerchantLogoPreviewAndToolsRow(
                imageUrl = imageUrl,
                merchantInitial = subjectName.take(1).uppercase().ifBlank { "$" },
                saving = saving,
                onOpenDuckDuckGoImages = ::openDuckDuckGoImages,
                onUseCategoryIcon = {
                    scope.launch {
                        saving = true
                        errorMessage = null
                        onUseFallbackIcon()
                            .onSuccess { onDismiss() }
                            .onFailure { err ->
                                saving = false
                                errorMessage = err.message ?: "Could not switch to fallback icon."
                            }
                    }
                },
                fallbackButtonLabel = fallbackButtonLabel
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = OrbitTheme.colors.expense
                )
            }
    }
}
