package app.overbay.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.delay

@Composable
fun OrbitFieldLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(start = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

object OrbitFormDefaults {
    val FieldShape: Shape = RoundedCornerShape(14.dp)
    val CardShape: Shape = RoundedCornerShape(18.dp)
    val HeroCardShape: Shape = RoundedCornerShape(20.dp)

    @Composable
    fun textFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.22f),
        errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = OrbitTheme.colors.cardBorder,
        disabledBorderColor = OrbitTheme.colors.cardBorder.copy(alpha = 0.5f)
    )
}

@Composable
internal fun rememberSyncedTextField(
    value: String,
    onValueChange: (String) -> Unit
): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var tfv by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var lastExternalValue by remember { mutableStateOf(value) }
    val pendingEmissions = remember { ArrayDeque<String>() }

    if (value != lastExternalValue) {
        val echoIdx = pendingEmissions.indexOf(value)
        if (echoIdx >= 0) {
            repeat(echoIdx + 1) { pendingEmissions.removeFirst() }
        } else {
            val hadPendingUserEdit = pendingEmissions.isNotEmpty()
            pendingEmissions.clear()
            if (tfv.text != value) {
                val nextCursor = if (hadPendingUserEdit) {
                    tfv.selection.end.coerceIn(0, value.length)
                } else {
                    value.length
                }
                tfv = TextFieldValue(text = value, selection = TextRange(nextCursor))
            }
        }
        lastExternalValue = value
    }

    LaunchedEffect(tfv.text, value) {
        if (tfv.text != value) {
            delay(200)
            if (tfv.text != value) {
                pendingEmissions.clear()
                val clamped = tfv.selection.end.coerceIn(0, value.length)
                tfv = TextFieldValue(text = value, selection = TextRange(clamped))
            }
        }
    }

    val handleValueChange: (TextFieldValue) -> Unit = { next ->
        val textChanged = next.text != tfv.text
        tfv = next
        if (textChanged) {
            pendingEmissions.addLast(next.text)
            onValueChange(next.text)
        }
    }

    return tfv to handleValueChange
}

@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffixText: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1
) {
    val resolvedTrailing: (@Composable () -> Unit)? = when {
        trailingIcon != null -> trailingIcon
        !suffixText.isNullOrBlank() -> {
            {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = suffixText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        else -> null
    }

    val (tfv, handleValueChange) = rememberSyncedTextField(value = value, onValueChange = onValueChange)

    Column(modifier = modifier) {
        if (label != null) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .padding(start = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    label()
                }
            }
        }
        OutlinedTextField(
            value = tfv,
            onValueChange = handleValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = null,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = resolvedTrailing,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = OrbitFormDefaults.FieldShape,
            colors = OrbitFormDefaults.textFieldColors()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OrbitDropdownField(
    label: String?,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    nullOptionLabel: String? = null,
    onNullSelected: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            OrbitFieldLabel(text = label)
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                haptics.tick()
                expanded = it
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = null,
                leadingIcon = leadingIcon,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = OrbitFormDefaults.FieldShape,
                colors = OrbitFormDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = OrbitFormDefaults.FieldShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            if (nullOptionLabel != null && onNullSelected != null) {
                DropdownMenuItem(
                    text = { Text(nullOptionLabel) },
                    onClick = {
                        haptics.tick()
                        onNullSelected()
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(optionLabel(item)) },
                    onClick = {
                        haptics.tick()
                        onOptionSelected(item)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
        }
    }
}

@Composable
fun OrbitSwitchGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = OrbitFormDefaults.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun OrbitSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    showContainer: Boolean = true
) {
    val haptics = LocalOrbitHaptics.current
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    haptics.tick()
                    onCheckedChange(!checked)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = {
                    haptics.tick()
                    onCheckedChange(it)
                }
            )
        }
    }

    if (showContainer) {
        Surface(
            shape = OrbitFormDefaults.FieldShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = modifier
                .fillMaxWidth()
                .clip(OrbitFormDefaults.FieldShape)
        ) {
            rowContent()
        }
    } else {
        Surface(
            color = Color.Transparent,
            modifier = modifier.fillMaxWidth()
        ) {
            rowContent()
        }
    }
}
