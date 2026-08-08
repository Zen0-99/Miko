package eu.kanade.presentation.novel.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Colored section header — uses primary color for the title text,
 * matching Miko's visual style with clear section separation.
 * Generous top padding (16dp) separates sections visually.
 */
@Composable
internal fun SectionHeader(text: String, accentColor: Color? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = accentColor ?: MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SubHeader(text: String, accentColor: Color? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = accentColor ?: MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun HeadingItem(textRes: StringResource) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun HeadingItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/**
 * Checkbox row with generous vertical padding (12dp) matching Miko's
 * switch rows which use 8dp+ spacing.
 *
 * @param onCheckedChange optional callback invoked after the preference is
 *   updated. Use this to trigger a config refresh (e.g. `onTextSettingChange`)
 *   so the change is reflected immediately without waiting for the next
 *   manual refresh.
 */
@Composable
internal fun CheckboxItem(
    label: String,
    pref: Preference<Boolean>,
    accentColor: Color? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val checked by pref.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        val accent = accentColor ?: MaterialTheme.colorScheme.primary
        Switch(
            checked = checked,
            onCheckedChange = {
                pref.set(it)
                onCheckedChange?.invoke(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/**
 * Checkbox item with explicit state — for preferences that aren't simple
 * booleans (e.g. enum-backed toggles like reading mode).
 */
@Composable
internal fun ColumnScope.CheckboxItem(
    label: String,
    isChecked: () -> Boolean,
    accentColor: Color? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        val accent = accentColor ?: MaterialTheme.colorScheme.primary
        Switch(
            checked = isChecked(),
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/**
 * Slider item with label + value on top, slider below.
 * Uses 8dp vertical padding for breathing room.
 */
@Composable
internal fun ColumnScope.SliderItem(
    label: String,
    value: Int,
    valueRange: IntRange,
    valueText: String? = null,
    steps: Int = 0,
    accentColor: Color? = null,
    onChange: (Int) -> Unit,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val accentedScheme = MaterialTheme.colorScheme.copy(primary = accent)
        MaterialTheme(colorScheme = accentedScheme) {
            Slider(
                value = value.toFloat(),
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                steps = steps,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    activeTickColor = accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@Composable
internal fun ColumnScope.FloatSliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String? = null,
    steps: Int = 0,
    accentColor: Color? = null,
    onChange: (Float) -> Unit,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val accentedScheme = MaterialTheme.colorScheme.copy(primary = accent)
        MaterialTheme(colorScheme = accentedScheme) {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = { onChange(it) },
                steps = steps,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    activeTickColor = accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SettingsChipRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/**
 * A dropdown selector: label on the left, current value + ">" on the
 * right. Uses 12dp vertical padding for comfortable spacing.
 */
@Composable
internal fun ColumnScope.SettingsDropdown(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ">",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                options.forEachIndexed { index, optionLabel ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Color picker button — shows a colored circle that opens a dropdown with
 * preset colors. Used for custom theme colors.
 */
@Composable
internal fun ColorPickerButton(
    color: Color,
    onColorSelected: (Color) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable { expanded = true }
                .background(color, CircleShape)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Preset colors
            val presets = listOf(
                Color.White to "White",
                Color.Black to "Black",
                Color(0xFF202020) to "Dark gray",
                Color(0xFFF5F0E8) to "Cream",
                Color(0xFFE8D5B7) to "Parchment",
                Color(0xFF1A1A2E) to "Midnight blue",
                Color(0xFF2D4A3E) to "Forest green",
                Color(0xFF3B2D4A) to "Plum",
            )
            presets.forEach { (presetColor, label) ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(presetColor, CircleShape),
                            )
                            Text(label)
                        }
                    },
                    onClick = {
                        onColorSelected(presetColor)
                        expanded = false
                    },
                )
            }
        }
    }
}
