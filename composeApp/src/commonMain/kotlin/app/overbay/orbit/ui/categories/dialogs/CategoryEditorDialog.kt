package app.overbay.orbit.ui.categories.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

private val PRESET_COLORS = listOf(
    "#2E7D32", // Forest Emerald
    "#1976D2", // Blue
    "#00897B", // Teal
    "#E64A19", // Coral
    "#F57C00", // Amber
    "#7B1FA2", // Violet
    "#C2185B", // Rose
    "#546E7A", // Slate
    "#0097A7", // Cyan
    "#689F38"  // Olive
)

private val PRESET_ICONS = CategoryEmojiMapper.PRESET_EMOJIS

@Composable
fun CategoryEditorDialog(
    category: Category?,
    onSave: (Long, String, String, String?, Boolean, Boolean, Boolean) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = category != null && category.id > 0L
    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var selectedColor by remember(category) { mutableStateOf(category?.color ?: PRESET_COLORS.first()) }
    var selectedIcon by remember(category) {
        mutableStateOf(CategoryEmojiMapper.resolveEmoji(category?.icon) ?: PRESET_ICONS.first())
    }
    var isTransfer by remember(category) { mutableStateOf(category?.isTransfer ?: false) }
    var isIncome by remember(category) { mutableStateOf(category?.isIncome ?: false) }
    var mhaEligible by remember(category) { mutableStateOf(category?.mhaDefaultEligible ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Category" else "New Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            CategoryEditorFormFields(
                name = name,
                onNameChange = { name = it },
                selectedColor = selectedColor,
                onColorChange = { selectedColor = it },
                selectedIcon = selectedIcon,
                onIconChange = { selectedIcon = it },
                isIncome = isIncome,
                onIncomeChange = { isIncome = it; if (it) isTransfer = false },
                isTransfer = isTransfer,
                onTransferChange = { isTransfer = it; if (it) isIncome = false },
                mhaEligible = mhaEligible,
                onMhaChange = { mhaEligible = it }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onSave(
                        category?.id ?: 0L,
                        name.trim(),
                        selectedColor,
                        selectedIcon.trim().ifBlank { null },
                        isTransfer,
                        isIncome,
                        mhaEligible
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEditing && onDelete != null) {
                    TextButton(
                        onClick = { haptics.reject(); onDelete(category.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    )
}

@Composable
fun CategoryEditorContent(
    category: Category?,
    onSave: (Long, String, String, String?, Boolean, Boolean, Boolean) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isEditing = category != null && category.id > 0L
    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var selectedColor by remember(category) { mutableStateOf(category?.color ?: PRESET_COLORS.first()) }
    var selectedIcon by remember(category) {
        mutableStateOf(CategoryEmojiMapper.resolveEmoji(category?.icon) ?: PRESET_ICONS.first())
    }
    var isTransfer by remember(category) { mutableStateOf(category?.isTransfer ?: false) }
    var isIncome by remember(category) { mutableStateOf(category?.isIncome ?: false) }
    var mhaEligible by remember(category) { mutableStateOf(category?.mhaDefaultEligible ?: false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isEditing) "Edit Category" else "New Category",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        CategoryEditorFormFields(
            name = name,
            onNameChange = { name = it },
            selectedColor = selectedColor,
            onColorChange = { selectedColor = it },
            selectedIcon = selectedIcon,
            onIconChange = { selectedIcon = it },
            isIncome = isIncome,
            onIncomeChange = { isIncome = it; if (it) isTransfer = false },
            isTransfer = isTransfer,
            onTransferChange = { isTransfer = it; if (it) isIncome = false },
            mhaEligible = mhaEligible,
            onMhaChange = { mhaEligible = it }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onSave(
                        category?.id ?: 0L,
                        name.trim(),
                        selectedColor,
                        selectedIcon.trim().ifBlank { null },
                        isTransfer,
                        isIncome,
                        mhaEligible
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("Save Category") }
        }

        if (isEditing && onDelete != null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { haptics.reject(); onDelete(category.id) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Category")
            }
        }
    }
}

@Composable
private fun CategoryEditorFormFields(
    name: String,
    onNameChange: (String) -> Unit,
    selectedColor: String,
    onColorChange: (String) -> Unit,
    selectedIcon: String,
    onIconChange: (String) -> Unit,
    isIncome: Boolean,
    onIncomeChange: (Boolean) -> Unit,
    isTransfer: Boolean,
    onTransferChange: (Boolean) -> Unit,
    mhaEligible: Boolean,
    onMhaChange: (Boolean) -> Unit
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Name + Icon Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = selectedIcon,
                onValueChange = { onIconChange(it.take(16)) },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.width(84.dp)
            )
            OutlinedTextField(
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isIconSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (isIconSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
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
                    if (isSelected) {
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

        Spacer(modifier = Modifier.height(16.dp))

        // Type Switches
        OrbitSwitchRow(
            title = "Income Category",
            checked = isIncome,
            onCheckedChange = onIncomeChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        OrbitSwitchRow(
            title = "Transfer Category",
            checked = isTransfer,
            onCheckedChange = onTransferChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        OrbitSwitchRow(
            title = "Default MHA Eligible",
            checked = mhaEligible,
            onCheckedChange = onMhaChange
        )
    }
}
