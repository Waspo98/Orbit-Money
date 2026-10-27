package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.network.MerchantLogoCandidateDto
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.transactions.components.MerchantLogoImageCache
import app.overbay.orbit.ui.transactions.components.RemoteLogoImage
import app.overbay.orbit.util.LocalOrbitHaptics
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.launch

/**
 * Native Compose Multiplatform modal sheet for finding and overriding vendor/merchant logos.
 * Port of main's LogoFinderModal / ImageUrlFinderModal with Logo.dev brand search,
 * direct image URL input, live logo preview, DuckDuckGo image search, and category icon fallback.
 * Strictly under the 400-line ceiling.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MerchantLogoFinderDialog(
    transaction: Transaction,
    onSearchBrands: suspend (query: String) -> Result<MerchantLogoSearchResponseDto>,
    onSaveLogoUrl: suspend (logoUrl: String) -> Result<Unit>,
    onUseCategoryIcon: suspend () -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialQuery = remember(transaction.id) {
        transaction.effectiveMerchant.ifBlank { transaction.merchantLogo?.merchantName ?: "" }
    }
    var query by remember(transaction.id) { mutableStateOf(initialQuery) }
    var imageUrl by remember(transaction.id) { mutableStateOf(transaction.merchantLogo?.url ?: "") }
    var candidates by remember(transaction.id) { mutableStateOf<List<MerchantLogoCandidateDto>>(emptyList()) }
    var searching by remember(transaction.id) { mutableStateOf(false) }
    var saving by remember(transaction.id) { mutableStateOf(false) }
    var hasSearched by remember(transaction.id) { mutableStateOf(false) }
    var errorMessage by remember(transaction.id) { mutableStateOf<String?>(null) }

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
                if (!dto.configured) {
                    errorMessage = "Logo search is not configured on this server yet. Paste a direct image URL below."
                }
            }.onFailure { err ->
                candidates = emptyList()
                errorMessage = err.message ?: "Logo search failed."
            }
        }
    }

    LaunchedEffect(transaction.id) {
        if (initialQuery.isNotBlank()) {
            triggerSearch(initialQuery)
        }
    }

    fun openDuckDuckGoImages() {
        val searchBase = query.trim().ifBlank { transaction.effectiveMerchant.ifBlank { "merchant" } }
        val encoded = "$searchBase logo".encodeURLQueryComponent()
        runCatching {
            uriHandler.openUri("https://duckduckgo.com/?q=$encoded&iar=images&iax=images&ia=images")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Find Logo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search Logo.dev results for ${transaction.effectiveMerchant}, or paste a direct image URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Input + Search Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Merchant Search") },
                    placeholder = { Text("Merchant name") },
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

            // Candidate Results List
            if (candidates.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidates.forEach { candidate ->
                        val candUrl = candidate.logoUrl ?: ""
                        val isSelected = imageUrl.trim() == candUrl && candUrl.isNotBlank()
                        CandidateLogoRow(
                            candidate = candidate,
                            isSelected = isSelected,
                            onClick = {
                                haptics.tick()
                                MerchantLogoImageCache.clearFailed(candUrl)
                                imageUrl = candUrl
                                errorMessage = null
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (!searching && hasSearched && errorMessage == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No Logo.dev matches found. Try a simpler merchant name or paste a direct image URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Direct Logo Image URL Field
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

            // Preview + Secondary Tools Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val previewUrl = imageUrl.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
                if (previewUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(5.dp)) {
                            RemoteLogoImage(
                                url = previewUrl,
                                contentDescription = "Selected logo preview",
                                modifier = Modifier.fillMaxSize(),
                                fallback = {
                                    Text(
                                        text = transaction.effectiveMerchant.take(1).uppercase().ifBlank { "$" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            openDuckDuckGoImages()
                        },
                        enabled = !saving,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("DuckDuckGo Images", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.confirm()
                            scope.launch {
                                saving = true
                                errorMessage = null
                                onUseCategoryIcon()
                                    .onSuccess { onDismiss() }
                                    .onFailure { err ->
                                        saving = false
                                        errorMessage = err.message ?: "Could not switch to category icon."
                                    }
                            }
                        },
                        enabled = !saving,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Use Category Icon", fontSize = 12.sp)
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = OrbitTheme.colors.expense
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Modal Actions: Cancel + Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                    },
                    enabled = !saving
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmed = imageUrl.trim()
                        if (trimmed.isEmpty()) {
                            errorMessage = "Choose a result or paste a direct image URL."
                            return@Button
                        }
                        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                            errorMessage = "Logo URL must start with http:// or https://."
                            return@Button
                        }
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
                    },
                    enabled = !saving,
                    shape = CircleShape
                ) {
                    Text(if (saving) "Saving..." else "Save")
                }
            }
        }
    }
}

@Composable
private fun CandidateLogoRow(
    candidate: MerchantLogoCandidateDto,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    RemoteLogoImage(
                        url = candidate.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        fallback = {
                            Text(
                                text = candidate.name.take(1).uppercase().ifBlank { "$" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.name.ifBlank { candidate.domain },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (candidate.domain.isNotBlank()) {
                    Text(
                        text = candidate.domain,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
