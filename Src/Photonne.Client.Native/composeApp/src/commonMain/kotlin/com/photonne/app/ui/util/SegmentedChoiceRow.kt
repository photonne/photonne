package com.photonne.app.ui.util

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

/** One option in a [SegmentedChoiceRow]. */
data class SegmentOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * Full-width, single-choice segmented control — the Material way to present a
 * short set of mutually-exclusive options so they span the sheet width instead
 * of clustering as loose chips on the left. Options with an [icon][SegmentOption.icon]
 * show it in the leading slot; otherwise the selected segment shows a check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoiceRow(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    // Highlight the active segment with the app's primary colour so the current
    // choice stands out more than Material's default (soft secondaryContainer).
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(option.value) },
                colors = colors,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    if (option.icon != null) {
                        Icon(imageVector = option.icon, contentDescription = null)
                    } else {
                        SegmentedButtonDefaults.Icon(isSelected)
                    }
                },
                label = {
                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}
