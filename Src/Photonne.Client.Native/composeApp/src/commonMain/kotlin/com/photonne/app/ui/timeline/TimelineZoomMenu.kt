package com.photonne.app.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PhotoSizeSelectActual
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.photonne.app.data.settings.TimelineZoomLevel
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_zoom_action
import com.photonne.app.resources.timeline_zoom_day_large
import com.photonne.app.resources.timeline_zoom_day_medium
import com.photonne.app.resources.timeline_zoom_day_small
import com.photonne.app.resources.timeline_zoom_month
import com.photonne.app.resources.timeline_zoom_year
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimelineZoomMenuAction(
    current: TimelineZoomLevel,
    onSelect: (TimelineZoomLevel) -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Outlined.PhotoSizeSelectActual,
                contentDescription = stringResource(Res.string.timeline_zoom_action)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            TimelineZoomLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(stringResource(level.labelRes())) },
                    leadingIcon = if (level == current) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        menuOpen = false
                        onSelect(level)
                    }
                )
            }
        }
    }
}

private fun TimelineZoomLevel.labelRes(): StringResource = when (this) {
    TimelineZoomLevel.Year -> Res.string.timeline_zoom_year
    TimelineZoomLevel.Month -> Res.string.timeline_zoom_month
    TimelineZoomLevel.DayLarge -> Res.string.timeline_zoom_day_large
    TimelineZoomLevel.DayMedium -> Res.string.timeline_zoom_day_medium
    TimelineZoomLevel.DaySmall -> Res.string.timeline_zoom_day_small
}
