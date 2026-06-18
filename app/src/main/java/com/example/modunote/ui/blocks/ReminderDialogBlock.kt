package com.example.modunote.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.modunote.ui.theme.md_theme_light_onSurfaceVariant
import com.example.modunote.ui.theme.md_theme_link_color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialogBlock(
    currentTime: Long?,
    currentEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Boolean) -> Unit
) {
    val initial = if (currentTime != null && currentTime > System.currentTimeMillis()) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    } else Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }

    var enabled by remember { mutableStateOf(currentEnabled) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.timeInMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
    )

    val timePickerState = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = true
    )

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Anuluj") }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Alarm, null) },
        title = { Text("Ustaw przypomnienie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Włączone"); Switch(enabled, { enabled = it })
                }
                if (enabled) {
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, null, Modifier.size(20.dp), tint = md_theme_link_color)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Data", style = MaterialTheme.typography.labelSmall, color = md_theme_light_onSurfaceVariant)
                                Text(
                                    dateFormatter.format(Date(datePickerState.selectedDateMillis ?: initial.timeInMillis)),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, null, Modifier.size(20.dp), tint = md_theme_link_color)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Godzina", style = MaterialTheme.typography.labelSmall, color = md_theme_light_onSurfaceVariant)
                                Text(
                                    "%02d:%02d".format(timePickerState.hour, timePickerState.minute),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = datePickerState.selectedDateMillis ?: initial.timeInMillis
                    // DatePicker returns UTC midnight, we need to adjust to local and set time
                    val localCal = Calendar.getInstance().apply {
                        timeInMillis = datePickerState.selectedDateMillis ?: initial.timeInMillis
                    }
                    set(Calendar.YEAR, localCal.get(Calendar.YEAR))
                    set(Calendar.MONTH, localCal.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    set(Calendar.MINUTE, timePickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSave(cal.timeInMillis, enabled)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (currentEnabled) TextButton(onClick = { onSave(0L, false) }) { Text("UsuĹ„", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}
