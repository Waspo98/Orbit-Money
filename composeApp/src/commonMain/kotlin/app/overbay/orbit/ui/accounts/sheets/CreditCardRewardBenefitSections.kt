package app.overbay.orbit.ui.accounts.sheets

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

data class CardArtPreset(val label: String, val network: String, val url: String)

val PRESET_CARD_ARTS = listOf(
    CardArtPreset("Sapphire Blue", "Visa", "https://assets.orbit.money/cards/sapphire-blue.png"),
    CardArtPreset("Amex Gold", "Amex", "https://assets.orbit.money/cards/amex-gold.png"),
    CardArtPreset("Capital Silver", "Mastercard", "https://assets.orbit.money/cards/venture-silver.png"),
    CardArtPreset("Apple Titanium", "Mastercard", "https://assets.orbit.money/cards/apple-card.png"),
    CardArtPreset("Discover Plum", "Discover", "https://assets.orbit.money/cards/discover-plum.png")
)

val NETWORKS = listOf("Visa", "Mastercard", "Amex", "Discover")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardArtworkAndNetworkSection(
    network: String,
    onNetworkChange: (String) -> Unit,
    imageUrl: String,
    onImageUrlChange: (String) -> Unit,
    cardName: String,
    issuerName: String,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Network & Card Art", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        // Network selection chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NETWORKS.forEach { net ->
                val isSelected = network.equals(net, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder),
                    modifier = Modifier.clickable {
                        haptics.tick()
                        onNetworkChange(net)
                    }
                ) {
                    Text(
                        text = net,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Preview Badge & Image URL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = network.uppercase().take(4).ifBlank { "CARD" },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (cardName.isNotBlank()) {
                        Text(
                            text = cardName.take(6),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            OrbitTextField(
                value = imageUrl,
                onValueChange = onImageUrlChange,
                label = { Text("Card Artwork URL") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Preset quick-picks
        Text("Quick-pick Preset Art:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRESET_CARD_ARTS.forEach { preset ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable {
                        haptics.tick()
                        onImageUrlChange(preset.url)
                        if (network.isBlank()) onNetworkChange(preset.network)
                    }
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorizedUsersSection(
    users: List<String>,
    onUsersChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var newUserText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Authorized Users", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbitTextField(
                value = newUserText,
                onValueChange = { newUserText = it },
                label = { Text("Add user (e.g. Partner, Child)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newUserText.isNotBlank()) {
                        haptics.tick()
                        onUsersChange(users + newUserText.trim())
                        newUserText = ""
                    }
                }
            ) {
                Text("Add")
            }
        }

        if (users.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                users.forEachIndexed { idx, user ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Text(user, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = {
                                    haptics.reject()
                                    val updated = users.toMutableList().apply { removeAt(idx) }
                                    onUsersChange(updated)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RewardCategoriesEditorSection(
    rewards: List<RewardCategory>,
    onRewardsChange: (List<RewardCategory>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var catName by remember { mutableStateOf("") }
    var multiplierText by remember { mutableStateOf("2.0") }
    var notesText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reward Categories (${rewards.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        rewards.forEachIndexed { index, r ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${r.multiplier}x • ${r.label.ifBlank { r.category }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        r.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    }
                    IconButton(onClick = {
                        haptics.reject()
                        val updated = rewards.toMutableList().apply { removeAt(index) }
                        onRewardsChange(updated)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Add reward row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OrbitTextField(value = catName, onValueChange = { catName = it }, label = { Text("Category/Label") }, singleLine = true, modifier = Modifier.weight(1.8f))
            OrbitTextField(value = multiplierText, onValueChange = { multiplierText = it }, label = { Text("Mult") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = {
                val mult = multiplierText.toDoubleOrNull() ?: 1.0
                if (catName.isNotBlank()) {
                    haptics.tick()
                    onRewardsChange(rewards + RewardCategory(category = catName.trim(), label = catName.trim(), multiplier = mult, notes = notesText.takeIf { it.isNotBlank() }))
                    catName = ""
                    notesText = ""
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Add")
            }
        }
    }
}

@Composable
fun CardBenefitsEditorSection(
    benefits: List<CardBenefit>,
    onBenefitsChange: (List<CardBenefit>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var title by remember { mutableStateOf("") }
    var creditValCents by remember { mutableStateOf(0L) }
    var descText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Statement Credits & Benefits (${benefits.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        benefits.forEachIndexed { index, b ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        val valStr = b.annualCreditValueCents?.let { MoneyUtils.formatCents(it) } ?: ""
                        if (valStr.isNotBlank()) Text("Annual Credit: $valStr", style = MaterialTheme.typography.bodySmall, color = OrbitColorTokens.Green)
                    }
                    IconButton(onClick = {
                        haptics.reject()
                        val updated = benefits.toMutableList().apply { removeAt(index) }
                        onBenefitsChange(updated)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Add benefit row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbitTextField(value = title, onValueChange = { title = it }, label = { Text("Benefit Title") }, singleLine = true, modifier = Modifier.weight(1.8f))
            Box(modifier = Modifier.weight(1.2f)) {
                CurrencyTextField(cents = creditValCents, onCentsChange = { creditValCents = it }, label = "Credit $")
            }
            Button(onClick = {
                val amt = if (creditValCents > 0L) creditValCents else null
                if (title.isNotBlank()) {
                    haptics.tick()
                    onBenefitsChange(benefits + CardBenefit(title = title.trim(), annualCreditValueCents = amt, description = descText.takeIf { it.isNotBlank() }))
                    title = ""
                    creditValCents = 0L
                    descText = ""
                }
            }) {
                Text("Add")
            }
        }
    }
}
