package com.photonne.app.ui.collections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_section_pinned
import com.photonne.app.resources.albums_title
import com.photonne.app.resources.collections_customize
import com.photonne.app.resources.collections_customize_reset
import com.photonne.app.resources.collections_customize_subtitle
import com.photonne.app.resources.collections_move_down
import com.photonne.app.resources.collections_move_up
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.folders_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.memories_strip_title
import com.photonne.app.resources.people_title
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun CollectionSection.titleRes(): StringResource = when (this) {
    CollectionSection.Memories -> Res.string.memories_strip_title
    CollectionSection.Pinned -> Res.string.albums_section_pinned
    CollectionSection.People -> Res.string.people_title
    CollectionSection.Favorites -> Res.string.favorites_title
    CollectionSection.Map -> Res.string.map_title
    CollectionSection.Albums -> Res.string.albums_title
    CollectionSection.Folders -> Res.string.folders_title
    CollectionSection.Scenes -> Res.string.explore_section_scenes
    CollectionSection.Objects -> Res.string.explore_section_objects
}

/**
 * "Personalizar Colecciones": cada sección con su casilla de visible y flechas
 * para subirla o bajarla. Flechas y no arrastre: caben en una hoja, se usan
 * con una mano y TalkBack las anuncia sin gestos especiales. Los cambios se
 * aplican al momento; "Restablecer" vuelve al orden que se adapta al uso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsCustomizeSheet(
    order: List<CollectionSection>,
    hidden: Set<CollectionSection>,
    customized: Boolean,
    onMove: (CollectionSection, Int) -> Unit,
    onToggleHidden: (CollectionSection, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg)
        ) {
            SheetHeader(
                title = stringResource(Res.string.collections_customize),
                subtitle = stringResource(Res.string.collections_customize_subtitle),
                actions = {
                    if (customized) {
                        TextButton(onClick = onReset) {
                            Text(stringResource(Res.string.collections_customize_reset))
                        }
                    }
                }
            )
            order.forEachIndexed { index, section ->
                val title = stringResource(section.titleRes())
                val visible = section !in hidden
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = visible,
                        onCheckedChange = { checked -> onToggleHidden(section, !checked) }
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onMove(section, -1) }, enabled = index > 0) {
                        Icon(
                            Icons.Outlined.KeyboardArrowUp,
                            contentDescription = stringResource(Res.string.collections_move_up) + " · " + title
                        )
                    }
                    IconButton(onClick = { onMove(section, 1) }, enabled = index < order.lastIndex) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.collections_move_down) + " · " + title
                        )
                    }
                }
            }
        }
    }
}
