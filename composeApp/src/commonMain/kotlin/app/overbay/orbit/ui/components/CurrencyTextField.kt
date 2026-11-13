package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

internal fun sanitizeCurrencyInputWithCursor(
    input: String,
    cursorEnd: Int,
    allowNegative: Boolean
): Pair<String, Int> {
    val sb = StringBuilder(input.length)
    var hasDecimal = false
    var decimalPlaces = 0
    var newCursor = 0
    input.forEachIndexed { index, c ->
        val keep = when {
            c.isDigit() -> !hasDecimal || decimalPlaces < 2
            c == '.' && !hasDecimal -> true
            c == '-' && sb.isEmpty() && allowNegative -> true
            else -> false
        }
        if (keep) {
            sb.append(c)
            if (c == '.') hasDecimal = true
            else if (c.isDigit() && hasDecimal) decimalPlaces++
            if (index < cursorEnd) newCursor++
        }
    }
    return sb.toString() to newCursor.coerceIn(0, sb.length)
}

internal fun formatEditableCurrency(cents: Long): String = when {
    cents == 0L -> ""
    cents % 100L == 0L -> (cents / 100L).toString()
    else -> MoneyUtils.centsToDollarString(cents)
}

/**
 * Soft-filled M3E currency text input field formatting decimal strings into integer [Cents].
 */
@Composable
fun CurrencyTextField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Amount",
    prefixSymbol: String = "$",
    isPositiveAccent: Boolean = false,
    allowNegative: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    var tfv by remember {
        val initial = formatEditableCurrency(cents)
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    val pendingCents = remember { ArrayDeque<Long>() }

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

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            OrbitFieldLabel(text = label)
        }
        OutlinedTextField(
            value = tfv,
            onValueChange = { next ->
                val textChanged = next.text != tfv.text
                if (!textChanged) {
                    tfv = next
                } else {
                    val (sanitized, nextCursor) = sanitizeCurrencyInputWithCursor(
                        input = next.text,
                        cursorEnd = next.selection.end,
                        allowNegative = allowNegative
                    )
                    val parsed = MoneyUtils.dollarsToCents(sanitized)
                    tfv = TextFieldValue(text = sanitized, selection = TextRange(nextCursor))
                    pendingCents.addLast(parsed)
                    onCentsChange(parsed)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && MoneyUtils.dollarsToCents(tfv.text) != cents) {
                        val formatted = formatEditableCurrency(cents)
                        tfv = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            label = null,
            placeholder = { Text("0.00") },
            leadingIcon = {
                val badgeBg = when {
                    isPositiveAccent -> OrbitTheme.colors.income.copy(alpha = 0.16f)
                    prefixSymbol.startsWith("-") -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                }
                val badgeText = when {
                    isPositiveAccent -> OrbitTheme.colors.income
                    prefixSymbol.startsWith("-") -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(if (prefixSymbol.length > 1) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = prefixSymbol,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                }
            },
            supportingText = if (errorMessage != null) {
                { Text(errorMessage) }
            } else null,
            isError = isError || errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Decimal
            ),
            singleLine = true,
            shape = OrbitFormDefaults.FieldShape,
            colors = OrbitFormDefaults.textFieldColors()
        )
    }
}

/**
 * Expressive centerpiece hero card combining large currency entry with an Expense / Income pill toggle.
 */
@Composable
fun OrbitAmountTypeCard(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    isExpense: Boolean,
    onIsExpenseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount"
) {
    val haptics = LocalOrbitHaptics.current
    var tfv by remember {
        val initial = formatEditableCurrency(cents)
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    val pendingCents = remember { ArrayDeque<Long>() }

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

    val accentColor = if (isExpense) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = OrbitFormDefaults.HeroCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Recessed Expense / Income Capsule Toggle
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TypeSegmentPill(
                            text = "Expense",
                            selected = isExpense,
                            isIncomePill = false,
                            onClick = {
                                if (!isExpense) {
                                    haptics.tick()
                                    onIsExpenseChange(true)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        TypeSegmentPill(
                            text = "Income",
                            selected = !isExpense,
                            isIncomePill = true,
                            onClick = {
                                if (isExpense) {
                                    haptics.tick()
                                    onIsExpenseChange(false)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(6.dp))
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
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && MoneyUtils.dollarsToCents(tfv.text) != cents) {
                                val formatted = formatEditableCurrency(cents)
                                tfv = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                            }
                        },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (tfv.text.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TypeSegmentPill(
    text: String,
    selected: Boolean,
    isIncomePill: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected && isIncomePill -> MaterialTheme.colorScheme.primaryContainer
            selected && !isIncomePill -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = OrbitSprings.ColorEffectsFast
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected && isIncomePill -> MaterialTheme.colorScheme.onPrimaryContainer
            selected && !isIncomePill -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = OrbitSprings.ColorEffectsFast
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
