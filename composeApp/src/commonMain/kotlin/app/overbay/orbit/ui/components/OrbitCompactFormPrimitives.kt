package app.overbay.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Compact pill dropdown selector for inline rule grammar (Field, Operator, Action) and dense
 * full-width selectors without an extra external label row or 56dp Material text-field padding.
 */
@Composable
fun <T> OrbitCompactDropdownPill(
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    isPlaceholder: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = OrbitTheme.colors.cardBorder
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalOrbitHaptics.current
    val shape = RoundedCornerShape(if (fillWidth) 12.dp else 10.dp)

    Box(modifier = if (fillWidth) modifier.fillMaxWidth() else modifier) {
        Surface(
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .let { if (fillWidth) it.fillMaxWidth() else it }
                .heightIn(min = if (fillWidth) 42.dp else 34.dp)
                .clip(shape)
                .clickable {
                    haptics.tick()
                    expanded = true
                }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (fillWidth) 12.dp else 10.dp,
                    vertical = if (fillWidth) 9.dp else 6.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (fillWidth) Arrangement.SpaceBetween else Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedText,
                    style = if (fillWidth) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (fillWidth && isPlaceholder) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (fillWidth) Modifier.weight(1f) else Modifier
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        OrbitDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(optionLabel(item)) },
                    onClick = {
                        haptics.tick()
                        onOptionSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Compact 42dp single-line text input sharing [rememberSyncedTextField] echo protection.
 */
@Composable
fun OrbitCompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val (tfv, handleValueChange) = rememberSyncedTextField(value = value, onValueChange = onValueChange)
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val border = if (isFocused) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (tfv.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = tfv,
                    onValueChange = handleValueChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = keyboardOptions,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = trailingContent
                )
            }
        }
    }
}

/**
 * Compact 42dp currency input formatted into integer [Long] cents.
 */
@Composable
fun OrbitCompactCurrencyField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    inlineLabel: String? = null,
    prefixSymbol: String = "$",
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    var tfv by remember {
        val initial = formatEditableCurrency(cents)
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    val pendingCents = remember { ArrayDeque<Long>() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(cents) {
        val echoIdx = pendingCents.indexOf(cents)
        if (echoIdx >= 0) {
            repeat(echoIdx + 1) { pendingCents.removeFirst() }
        } else {
            pendingCents.clear()
            if (MoneyUtils.dollarsToCents(tfv.text) != cents) {
                val formatted = formatEditableCurrency(cents)
                tfv = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val border = if (isFocused) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!inlineLabel.isNullOrBlank()) {
                Text(
                    text = inlineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = prefixSymbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (tfv.text.isEmpty()) {
                    Text(
                        text = "0.00",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                BasicTextField(
                    value = tfv,
                    onValueChange = { next ->
                        val textChanged = next.text != tfv.text
                        if (!textChanged) {
                            tfv = next
                        } else {
                            val (sanitized, nextCursor) = sanitizeCurrencyInputWithCursor(
                                input = next.text,
                                cursorEnd = next.selection.end,
                                allowNegative = false
                            )
                            val parsed = MoneyUtils.dollarsToCents(sanitized)
                            tfv = TextFieldValue(text = sanitized, selection = TextRange(nextCursor))
                            pendingCents.addLast(parsed)
                            onCentsChange(parsed)
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (!focusState.isFocused && MoneyUtils.dollarsToCents(tfv.text) != cents) {
                                val formatted = formatEditableCurrency(cents)
                                tfv = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                            }
                        }
                )
            }
        }
    }
}
