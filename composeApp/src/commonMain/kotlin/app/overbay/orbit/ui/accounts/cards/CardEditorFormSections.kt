package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.theme.CardArtPalette
import app.overbay.orbit.theme.CardArtPreset
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.OrbitTextInputDialog
import app.overbay.orbit.util.LocalOrbitHaptics

private val NETWORKS = listOf("Visa", "Mastercard", "Amex", "Discover")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditorIdentityTab(
    draft: CardEditorDraft,
    controller: CardEditorController,
    householdMembers: List<HouseholdMember>,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showCustomHolderDialog by remember { mutableStateOf(false) }
    var showCustomAuDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OrbitSectionCard(
            title = "Card Identity",
            subtitle = "Product name, issuing bank & payment network",
            showHeaderDivider = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrbitTextField(
                    value = draft.cardName,
                    onValueChange = { controller.updateDraft { d -> d.copy(cardName = it) } },
                    label = { Text("Card Product Name") },
                    placeholder = { Text("e.g. Sapphire Preferred, Freedom Unlimited") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrbitTextField(
                        value = draft.issuer,
                        onValueChange = { controller.updateDraft { d -> d.copy(issuer = it) } },
                        label = { Text("Card Issuer") },
                        placeholder = { Text("e.g. Chase, Amex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OrbitTextField(
                        value = draft.accountName,
                        onValueChange = { controller.updateDraft { d -> d.copy(accountName = it) } },
                        label = { Text("Account Label") },
                        placeholder = { Text("Wallet Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column {
                    OrbitFieldLabel("Payment Network")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NETWORKS.forEach { net ->
                            val sel = draft.network.equals(net, ignoreCase = true)
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    haptics.tick()
                                    controller.updateDraft { it.copy(network = net) }
                                },
                                label = {
                                    Text(
                                        text = net,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // CRITICAL D4: Preset Card Art Swatches ONLY (NO artworkUrl text field)
        OrbitSectionCard(
            title = "Wallet Finish & Color",
            subtitle = if (!draft.artworkUrl.isNullOrBlank()) {
                "Official catalog card art active · choose backdrop accent"
            } else {
                "Select a physical card finish for your wallet"
            },
            showHeaderDivider = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardArtPalette.ALL.chunked(3).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val selected = draft.artKey == preset.key
                            CardArtSwatchTile(
                                preset = preset,
                                selected = selected,
                                onClick = {
                                    haptics.tick()
                                    controller.updateDraft { d ->
                                        d.copy(
                                            artKey = if (selected) null else preset.key,
                                            network = if (d.network.isBlank()) preset.defaultNetwork else d.network
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        OrbitSectionCard(
            title = "Cardholders",
            subtitle = "Primary account owner & authorized users",
            showHeaderDivider = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    OrbitFieldLabel("Primary Cardholder")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        householdMembers.forEach { m ->
                            val sel = draft.cardholderMemberId == m.id || draft.cardholder.equals(m.name, ignoreCase = true)
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    haptics.tick()
                                    if (sel) controller.selectPrimaryCardholder(null, null)
                                    else controller.selectPrimaryCardholder(m.id, m.name)
                                },
                                label = { Text(m.name, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        val isCustomSelected = draft.cardholderMemberId == null &&
                            !draft.cardholder.isNullOrBlank() &&
                            householdMembers.none { it.name.equals(draft.cardholder, ignoreCase = true) }
                        FilterChip(
                            selected = isCustomSelected,
                            onClick = { haptics.tick(); showCustomHolderDialog = true },
                            label = {
                                Text(
                                    text = draft.cardholder?.takeIf { h ->
                                        householdMembers.none { it.name.equals(h, ignoreCase = true) }
                                    } ?: "Other…",
                                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Column {
                    OrbitFieldLabel("Authorized Users")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        householdMembers
                            .filter { m -> m.id != draft.cardholderMemberId && !m.name.equals(draft.cardholder, ignoreCase = true) }
                            .forEach { m ->
                                val isAu = draft.authorizedUserMemberIds.contains(m.id) ||
                                    draft.authorizedUsers.any { it.equals(m.name, ignoreCase = true) }
                                FilterChip(
                                    selected = isAu,
                                    onClick = {
                                        haptics.tick()
                                        controller.toggleAuthorizedMember(m.id, m.name)
                                    },
                                    label = { Text(m.name, fontWeight = if (isAu) FontWeight.Bold else FontWeight.Medium) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        draft.authorizedUsers
                            .filter { custom -> householdMembers.none { it.name.equals(custom, ignoreCase = true) } }
                            .forEach { customName ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        haptics.tick()
                                        controller.removeCustomAuthorizedUser(customName)
                                    },
                                    label = { Text("$customName ×", fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        OrbitActionChip(
                            label = "Add Person",
                            leadingIcon = Icons.Default.Add,
                            onClick = { showCustomAuDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showCustomHolderDialog) {
        OrbitTextInputDialog(
            title = "Cardholder Name",
            initialValue = draft.cardholder.orEmpty(),
            onConfirm = { name ->
                showCustomHolderDialog = false
                controller.selectPrimaryCardholder(null, name)
            },
            onDismiss = { showCustomHolderDialog = false }
        )
    }

    if (showCustomAuDialog) {
        OrbitTextInputDialog(
            title = "Add Authorized User",
            initialValue = "",
            onConfirm = { name ->
                showCustomAuDialog = false
                controller.addCustomAuthorizedUser(name)
            },
            onDismiss = { showCustomAuDialog = false }
        )
    }
}

@Composable
private fun CardArtSwatchTile(
    preset: CardArtPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
    val borderWidth = if (selected) 1.75.dp else 1.dp
    val containerBg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.linearGradient(preset.gradientColors))
                    .padding(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 10.dp, height = 7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(preset.textColor.copy(alpha = 0.35f))
                )
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = preset.label.removePrefix("Sapphire ").let {
                    if (preset == CardArtPalette.SapphireBlue) "Sapphire" else preset.label
                },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
