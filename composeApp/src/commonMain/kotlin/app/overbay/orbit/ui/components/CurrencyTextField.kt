package app.overbay.orbit.ui.components

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.LocalOrbitHaptics

private fun sanitizeCurrencyInput(input: String, allowNegative: Boolean): String = buildString {
    var hasDecimal = false
    var decimalPlaces = 0
    input.forEachIndexed { index, c ->
        if (c.isDigit()) {
            if (!hasDecimal || decimalPlaces < 2) {
                append(c)
                if (hasDecimal) decimalPlaces++
            }
        } else if (c == '.' && !hasDecimal) {
            append(c)
            hasDecimal = true
        } else if (c == '-' && index == 0 && allowNegative) {
            append(c)
        }
    }
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
    allowNegative: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    var rawText by remember {
        val initial = if (cents == 0L) "" else MoneyUtils.centsToDollarString(cents)
        mutableStateOf(initial)
    }

    LaunchedEffect(cents) {
        if (MoneyUtils.dollarsToCents(rawText) != cents) {
            rawText = if (cents == 0L) "" else MoneyUtils.centsToDollarString(cents)
        }
    }

    OutlinedTextField(
        value = rawText,
        onValueChange = { input ->
            val sanitized = sanitizeCurrencyInput(input, allowNegative)
            rawText = sanitized
            onCentsChange(MoneyUtils.dollarsToCents(sanitized))
        },
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        label = if (label != null) {
            { Text(label) }
        } else null,
        placeholder = { Text("0.00") },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
    var rawText by remember {
        val initial = if (cents == 0L) "" else MoneyUtils.centsToDollarString(cents)
        mutableStateOf(initial)
    }

    LaunchedEffect(cents) {
        if (MoneyUtils.dollarsToCents(rawText) != cents) {
            rawText = if (cents == 0L) "" else MoneyUtils.centsToDollarString(cents)
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
                    value = rawText,
                    onValueChange = { input ->
                        val sanitized = sanitizeCurrencyInput(input, allowNegative = false)
                        rawText = sanitized
                        onCentsChange(MoneyUtils.dollarsToCents(sanitized))
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (rawText.isEmpty()) {
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
    val containerColor = when {
        selected && isIncomePill -> MaterialTheme.colorScheme.primaryContainer
        selected && !isIncomePill -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        selected && isIncomePill -> MaterialTheme.colorScheme.onPrimaryContainer
        selected && !isIncomePill -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
