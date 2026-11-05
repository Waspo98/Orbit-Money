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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.network.MerchantLogoCandidateDto
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.transactions.components.MerchantLogoImageCache
import app.overbay.orbit.ui.transactions.components.RemoteLogoImage
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
internal fun MerchantLogoCandidatesSection(
    candidates: List<MerchantLogoCandidateDto>,
    selectedImageUrl: String,
    searching: Boolean,
    hasSearched: Boolean,
    errorMessage: String?,
    onSelectCandidate: (String) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    if (candidates.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            candidates.forEach { candidate ->
                val candUrl = candidate.logoUrl ?: ""
                val isSelected = selectedImageUrl.trim() == candUrl && candUrl.isNotBlank()
                CandidateLogoRow(
                    candidate = candidate,
                    isSelected = isSelected,
                    onClick = {
                        haptics.tick()
                        MerchantLogoImageCache.clearFailed(candUrl)
                        onSelectCandidate(candUrl)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MerchantLogoPreviewAndToolsRow(
    imageUrl: String,
    merchantInitial: String,
    saving: Boolean,
    onOpenDuckDuckGoImages: () -> Unit,
    onUseCategoryIcon: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
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
                                text = merchantInitial,
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
                    onOpenDuckDuckGoImages()
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
                    onUseCategoryIcon()
                },
                enabled = !saving,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Use Category Icon", fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun CandidateLogoRow(
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
