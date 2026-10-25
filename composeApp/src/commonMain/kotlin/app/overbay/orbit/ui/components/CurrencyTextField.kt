package app.overbay.orbit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils

/**
 * Currency text input field formatting decimal strings into integer [Cents].
 * Pre-filled with dollar prefix and handles negative amounts if enabled.
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
    var rawText by remember(cents) {
        val initial = if (cents == 0L) "" else MoneyUtils.centsToDollarString(cents)
        mutableStateOf(initial)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = rawText,
            onValueChange = { input ->
                // Clean input: digits, optional leading minus (if allowed), and at most one decimal point
                val sanitized = buildString {
                    var hasDecimal = false
                    input.forEachIndexed { index, c ->
                        if (c.isDigit()) {
                            append(c)
                        } else if (c == '.' && !hasDecimal) {
                            append(c)
                            hasDecimal = true
                        } else if (c == '-' && index == 0 && allowNegative) {
                            append(c)
                        }
                    }
                }
                rawText = sanitized
                val parsedCents = MoneyUtils.dollarsToCents(sanitized)
                onCentsChange(parsedCents)
            },
            modifier = Modifier.fillMaxWidth(),
            label = if (label != null) {
                { Text(label) }
            } else null,
            leadingIcon = {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            isError = isError || errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Decimal
            ),
            singleLine = true
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}
