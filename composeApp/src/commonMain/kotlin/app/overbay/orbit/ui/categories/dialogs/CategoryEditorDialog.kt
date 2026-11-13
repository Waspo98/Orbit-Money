package app.overbay.orbit.ui.categories.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchGroupCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

private val PRESET_COLORS = listOf(
    "#2E7D32", "#1976D2", "#00897B", "#E64A19", "#F57C00",
    "#7B1FA2", "#C2185B", "#546E7A", "#0097A7", "#689F38"
)
private val PRESET_ICONS = CategoryEmojiMapper.PRESET_EMOJIS

@Composable
fun CategoryEditorDialog(
    category: Category?,
    onSave: (Long, String, String, String?, Boolean, Boolean, Boolean) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onViewTransactions: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    showMhaOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        CategoryEditorContent(category, onSave, onDelete, onViewTransactions, onDismiss, showMhaOption)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorContent(
    category: Category?,
    onSave: (Long, String, String, String?, Boolean, Boolean, Boolean) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onViewTransactions: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    showMhaOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = category != null && category.id > 0L
    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var selectedColor by remember(category) { mutableStateOf(category?.color ?: PRESET_COLORS.first()) }
    var selectedIcon by remember(category) {
        mutableStateOf(CategoryEmojiMapper.resolveEmoji(category?.icon) ?: PRESET_ICONS.first())
    }
    var mhaEligible by remember(category) { mutableStateOf(category?.mhaDefaultEligible ?: false) }
    var categoryKind by remember(category) {
        mutableStateOf(when {
            category?.isIncome == true -> "income"
            category?.isTransfer == true -> "transfer"
            else -> "expense"
        })
    }

    OrbitModalScaffold(
        onDismiss = onDismiss,
        confirmText = "Save Category",
        onConfirm = {
            haptics.confirm()
            val isTransfer = category?.isTransfer ?: (categoryKind == "transfer")
            val isIncome = category?.isIncome ?: (categoryKind == "income")
            onSave(category?.id ?: 0L, name.trim(), selectedColor, selectedIcon.trim().ifBlank { null }, isTransfer, isIncome, mhaEligible)
        },
        modifier = modifier,
        title = if (isEditing) "Edit Category" else "New Category",
        confirmEnabled = name.isNotBlank(),
        headerTrailingActions = if (category != null && onDelete != null) {
            {
                OrbitActionChip(
                    label = "Delete",
                    onClick = {
                        haptics.reject()
                        onDelete(category.id)
                    },
                    icon = Icons.Default.Delete,
                    isDestructive = true
                )
            }
        } else null,
        bodySpacing = 0.dp
    ) {

        val previewComposeColor = remember(selectedColor) {
            selectedColor.toComposeColor(Color(0xFF2E7D32))
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(previewComposeColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(selectedIcon.ifBlank { "#" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = previewComposeColor)
                }
                Text(
                    text = name.ifBlank { "Category Name" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (!isEditing) {
            val kinds = listOf("expense" to "Expense", "income" to "Income", "transfer" to "Transfer")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { idx, (key, label) ->
                    SegmentedButton(
                        selected = categoryKind == key,
                        onClick = { haptics.tick(); categoryKind = key },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = kinds.size)
                    ) { Text(label) }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        CategoryEditorFormFields(
            category = category,
            name = name,
            onNameChange = { name = it },
            selectedColor = selectedColor,
            onColorChange = { selectedColor = it },
            selectedIcon = selectedIcon,
            onIconChange = { selectedIcon = it },
            mhaEligible = mhaEligible,
            onMhaChange = { mhaEligible = it },
            showMhaOption = showMhaOption
        )
        if (category != null && onViewTransactions != null) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { haptics.tick(); onViewTransactions(category.id) },
                enabled = category.transactionCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("See Transactions")
            }
        }
    }
}

@Composable
private fun CategoryEditorFormFields(
    category: Category?,
    name: String,
    onNameChange: (String) -> Unit,
    selectedColor: String,
    onColorChange: (String) -> Unit,
    selectedIcon: String,
    onIconChange: (String) -> Unit,
    mhaEligible: Boolean,
    onMhaChange: (Boolean) -> Unit,
    showMhaOption: Boolean = false
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Name + Icon Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.overbay.orbit.ui.components.OrbitTextField(
                value = selectedIcon,
                onValueChange = { onIconChange(it.take(16)) },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.width(84.dp)
            )
            app.overbay.orbit.ui.components.OrbitTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Category Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Emoji Swatches
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PRESET_ICONS.forEach { emoji ->
                val isIconSelected = selectedIcon == emoji
                val iconBgColor by animateColorAsState(
                    targetValue = if (isIconSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "catIconBg"
                )
                val iconBorderColor by animateColorAsState(
                    targetValue = if (isIconSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "catIconBorder"
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconBgColor,
                    border = BorderStroke(1.5.dp, iconBorderColor),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable {
                            haptics.tick()
                            onIconChange(emoji)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Color Swatches
        Text(
            text = "COLOR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PRESET_COLORS.forEach { hex ->
                val color = Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
                val isSelected = selectedColor.equals(hex, ignoreCase = true)

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable {
                            haptics.tick()
                            onColorChange(hex)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn(OrbitSprings.FloatSpatialFast, initialScale = 0.65f) + fadeIn(OrbitSprings.FloatEffectsFast),
                        exit = scaleOut(OrbitSprings.FloatSpatialFast, targetScale = 0.65f) + fadeOut(OrbitSprings.FloatEffectsFast)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Custom Hex Color Input with live preview swatch
        val parsedCustomColor = remember(selectedColor) {
            runCatching {
                val cleanHex = selectedColor.trim().removePrefix("#")
                if (cleanHex.length == 6) Color(cleanHex.toLong(16) or 0xFF000000) else null
            }.getOrNull()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(parsedCustomColor ?: MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (parsedCustomColor == null) Text("?", style = MaterialTheme.typography.labelSmall)
            }
            OrbitTextField(
                value = selectedColor,
                onValueChange = { input ->
                    onColorChange(if (input.startsWith("#")) input.take(7) else "#${input.take(6)}")
                },
                label = { Text("Custom Hex Color (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category Behavior Information & MHA Setting
        if (category != null && (category.isIncome || category.isTransfer)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Income and transfer behavior stays unchanged here. This editor only updates the name, emoji, color, and MHA default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showMhaOption) {
            Spacer(modifier = Modifier.height(16.dp))
            OrbitSwitchGroupCard {
                OrbitSwitchRow("Default MHA Eligible", checked = mhaEligible, onCheckedChange = onMhaChange, showContainer = false)
            }
        }
    }
}
