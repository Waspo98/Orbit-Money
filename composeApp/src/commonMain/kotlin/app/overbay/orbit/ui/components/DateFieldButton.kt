package app.overbay.orbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Soft-filled M3E date picker field showing a friendly formatted date ("Oct 26, 2026")
 * and launching a Material 3 DatePickerDialog.
 */
@Composable
fun DateFieldButton(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Date",
    enabled: Boolean = true
) {
    DateFieldButton(
        date = date as LocalDate?,
        onDateSelected = onDateSelected,
        modifier = modifier,
        label = label,
        placeholder = "Select Date",
        onClear = null,
        enabled = enabled
    )
}

/**
 * Nullable M3E date picker field for optional dates (e.g., card opened date, promo end date,
 * annual fee post date). Shows [placeholder] when [date] is null instead of faking today's date,
 * and optionally displays a one-tap clear button when set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFieldButton(
    date: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Date",
    placeholder: String = "Not Set",
    onClear: (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            OrbitFieldLabel(text = label)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = date?.let { LocalDateUtils.formatDisplayDate(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                isError = isError,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                label = null,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (date != null && onClear != null && enabled) {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    onClear()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                },
                singleLine = true,
                shape = OrbitFormDefaults.FieldShape,
                colors = OrbitFormDefaults.textFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = if (date != null && onClear != null) 44.dp else 0.dp)
                    .clip(OrbitFormDefaults.FieldShape)
                    .clickable(enabled = enabled) {
                        haptics.tick()
                        showDialog = true
                    }
            )
        }
    }

    if (showDialog) {
        val seedDate = date ?: LocalDateUtils.todayLocalDate()
        val initialMillis = remember(seedDate) {
            seedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val newDate = Instant.fromEpochMilliseconds(selectedMillis)
                                .toLocalDateTime(TimeZone.UTC).date
                            onDateSelected(newDate)
                        }
                        showDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
