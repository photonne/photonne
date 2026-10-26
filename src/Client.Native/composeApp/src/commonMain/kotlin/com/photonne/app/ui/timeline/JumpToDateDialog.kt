package com.photonne.app.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.data.timeline.TimelineBucketState
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_jump_hint
import com.photonne.app.resources.timeline_jump_month
import com.photonne.app.resources.timeline_jump_month_empty
import com.photonne.app.resources.timeline_jump_title
import com.photonne.app.resources.timeline_jump_year
import com.photonne.app.ui.admin.formatCount
import com.photonne.app.ui.grid.formatLocalizedMonth
import com.photonne.app.ui.grid.formatLocalizedMonthShort
import com.photonne.app.ui.theme.FieldGroupLabel
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.Spacing
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Saltar a un mes con fotos: los años en chips (con su total) y los doce meses
 * del elegido con su recuento, como en la web. Los meses vacíos se ven pero no
 * se pueden tocar. Sale del esqueleto del timeline, que ya trae los recuentos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToDateDialog(
    buckets: List<TimelineBucketState>,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit
) {
    val years = remember(buckets) { jumpYears(buckets) }
    var selectedYear by rememberSaveable { mutableIntStateOf(years.firstOrNull()?.year ?: 0) }
    val year = years.firstOrNull { it.year == selectedYear } ?: years.firstOrNull()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(
                title = stringResource(Res.string.timeline_jump_title),
                subtitle = stringResource(Res.string.timeline_jump_hint)
            )
            if (year == null) return@Column

            FieldGroupLabel(stringResource(Res.string.timeline_jump_year))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                years.forEach { option ->
                    FilterChip(
                        selected = option.year == year.year,
                        onClick = { selectedYear = option.year },
                        label = { Text("${option.year} · ${formatCount(option.total)}") }
                    )
                }
            }

            year.months.chunked(4).forEachIndexed { row, counts ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    counts.forEachIndexed { column, count ->
                        val month = row * 4 + column + 1
                        MonthCell(
                            date = LocalDate(year.year, month, 1),
                            count = count,
                            onClick = { onConfirm(jumpTarget(year.year, month)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = formatLocalizedMonth(date)
    val description = if (count > 0) {
        pluralStringResource(Res.plurals.timeline_jump_month, count, name, formatCount(count))
    } else {
        stringResource(Res.string.timeline_jump_month_empty, name)
    }
    OutlinedCard(
        onClick = onClick,
        enabled = count > 0,
        // Sin fotos: borde tenue, pero el nombre se sigue leyendo.
        border = if (count > 0) BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatLocalizedMonthShort(date),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (count > 0) formatCount(count) else "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
