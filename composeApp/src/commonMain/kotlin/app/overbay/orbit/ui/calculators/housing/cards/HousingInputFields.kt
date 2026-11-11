package app.overbay.orbit.ui.calculators.housing.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitFormDefaults
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.formatEditableCurrency
import app.overbay.orbit.ui.components.sanitizeCurrencyInputWithCursor

private fun formatEditablePercent(valuePercent: Double?): String = when {
    valuePercent == null -> ""
    valuePercent == valuePercent.toLong().toDouble() -> valuePercent.toLong().toString()
    else -> valuePercent.toString()
}

private fun sanitizeDecimalWithCursor(input: String, cursorEnd: Int): Pair<String, Int> {
    val sb = StringBuilder(input.length)
    var hasDot = false
    var newCursor = 0
    input.forEachIndexed { index, c ->
        val keep = c.isDigit() || (c == '.' && !hasDot)
        if (keep) {
            sb.append(c)
            if (c == '.') hasDot = true
            if (index < cursorEnd) newCursor++
        }
    }
    return sb.toString() to newCursor.coerceIn(0, sb.length)
}

@Composable
fun HousingMoneyField(
    label: String,
    valueCents: Cents?,
    placeholder: String = "$0",
    onValueChange: (Cents?) -> Unit,
    modifier: Modifier = Modifier
) {
    var tfv by remember {
        val initial = if (valueCents == null) "" else formatEditableCurrency(valueCents)
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    val pendingCents = remember { ArrayDeque<Cents?>() }

    LaunchedEffect(valueCents) {
        val echoIdx = pendingCents.indexOf(valueCents)
        if (echoIdx >= 0) {
            repeat(echoIdx + 1) { pendingCents.removeFirst() }
        } else {
            pendingCents.clear()
            val currentParsed = if (tfv.text.isBlank()) null else MoneyUtils.dollarsToCents(tfv.text)
            if (currentParsed != valueCents) {
                val expected = if (valueCents == null) "" else formatEditableCurrency(valueCents)
                tfv = TextFieldValue(text = expected, selection = TextRange(expected.length))
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitFieldLabel(text = label)
        OutlinedTextField(
            value = tfv,
            onValueChange = { next ->
                if (next.text == tfv.text) {
                    tfv = next
                } else {
                    val (sanitized, nextCursor) = sanitizeCurrencyInputWithCursor(
                        input = next.text,
                        cursorEnd = next.selection.end,
                        allowNegative = false
                    )
                    val parsed = if (sanitized.isBlank()) null else MoneyUtils.dollarsToCents(sanitized)
                    tfv = TextFieldValue(text = sanitized, selection = TextRange(nextCursor))
                    pendingCents.addLast(parsed)
                    onValueChange(parsed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = OrbitFormDefaults.FieldShape,
            colors = OrbitFormDefaults.textFieldColors()
        )
    }
}

@Composable
fun HousingPercentField(
    label: String,
    valuePercent: Double?,
    placeholder: String = "0%",
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    var tfv by remember {
        val initial = formatEditablePercent(valuePercent)
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    val pendingValues = remember { ArrayDeque<Double?>() }

    LaunchedEffect(valuePercent) {
        val echoIdx = pendingValues.indexOf(valuePercent)
        if (echoIdx >= 0) {
            repeat(echoIdx + 1) { pendingValues.removeFirst() }
        } else {
            pendingValues.clear()
            val currentParsed = if (tfv.text.isBlank()) null else tfv.text.toDoubleOrNull()
            if (currentParsed != valuePercent) {
                val expected = formatEditablePercent(valuePercent)
                tfv = TextFieldValue(text = expected, selection = TextRange(expected.length))
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitFieldLabel(text = label)
        OutlinedTextField(
            value = tfv,
            onValueChange = { next ->
                if (next.text == tfv.text) {
                    tfv = next
                } else {
                    val (sanitized, nextCursor) = sanitizeDecimalWithCursor(next.text, next.selection.end)
                    val parsed = if (sanitized.isBlank()) null else sanitized.toDoubleOrNull()
                    tfv = TextFieldValue(text = sanitized, selection = TextRange(nextCursor))
                    pendingValues.addLast(parsed)
                    onValueChange(parsed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            placeholder = { Text(placeholder) },
            trailingIcon = {
                Text(
                    text = "%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = OrbitFormDefaults.FieldShape,
            colors = OrbitFormDefaults.textFieldColors()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousingLoanTermControl(
    value: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isStandard = value == 30 || value == 15
    var isCustom by remember { mutableStateOf(!isStandard && value != null) }
    var customText by remember {
        mutableStateOf(if (!isStandard && value != null) value.toString() else "")
    }

    LaunchedEffect(value) {
        if (!isCustom) {
            if (value != null && value != 30 && value != 15) {
                isCustom = true
                customText = value.toString()
            } else if (value == null) {
                customText = ""
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitFieldLabel(text = "Loan Term (Years)")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isCustom && value == 30,
                onClick = {
                    isCustom = false
                    onChange(30)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = {},
                label = { Text("30y") }
            )
            SegmentedButton(
                selected = !isCustom && value == 15,
                onClick = {
                    isCustom = false
                    onChange(15)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = {},
                label = { Text("15y") }
            )
            SegmentedButton(
                selected = isCustom,
                onClick = {
                    isCustom = true
                    val parsed = customText.toIntOrNull()
                    onChange(parsed)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = {},
                label = { Text("Custom") }
            )
        }

        if (isCustom) {
            Spacer(modifier = Modifier.height(6.dp))
            OrbitTextField(
                value = customText,
                onValueChange = { str ->
                    val digits = str.filter { it.isDigit() }
                    customText = digits
                    val parsed = digits.toIntOrNull()
                    onChange(parsed)
                },
                label = { Text("Custom Years (e.g. 20, 25)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

@Composable
fun HousingResultRow(
    label: String,
    value: String?,
    isBold: Boolean = false,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Text(
            text = value ?: "—",
            style = if (isBold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
