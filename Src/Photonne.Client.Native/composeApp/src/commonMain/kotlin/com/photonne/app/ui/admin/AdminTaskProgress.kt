package com.photonne.app.ui.admin

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * "Estado de la tarea" card used by every Administración → Sistema task
 * (Indexar, Miniaturas, Duplicados, ML backfill …) so progress, status
 * line and the row of metric chips render the same way everywhere — see
 * the PWA's `MudProgressLinear` blocks for the original layout.
 */
@Composable
fun TaskProgressCard(
    statusText: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    chips: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (chips != null) {
                chips()
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (progress ?: 0f).coerceIn(0f, 1f) },
                color = progressColor,
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
    }
}

/**
 * A small non-interactive pill used to surface task metadata (duration,
 * speed, ETA, completed/en curso, …) in a row above the progress bar.
 * Lighter than a Material AssistChip because we don't want ripple or
 * focus visuals on a purely informational element.
 */
@Composable
fun MetricPill(
    label: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier.wrapContentHeight(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Row container used for the chip cluster above a TaskProgressCard. */
@Composable
fun MetricPillRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * Grid of "big number on top, caption underneath" cells used to surface
 * per-task counters (Encontrados / Nuevos / Generados / …). Items wrap
 * into a 2-column layout on narrow screens via the chunked rendering;
 * this matches the PWA's `MudPaper` grid without pulling in a real
 * `LazyVerticalGrid` for what is always a handful of cells.
 */
@Composable
fun StatGridCard(
    items: List<StatGridItem>,
    columns: Int = 4,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        StatCell(
                            item = item,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad the last row so cells keep their width when
                    // count is not divisible by `columns`.
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(item: StatGridItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.value,
                style = MaterialTheme.typography.headlineSmall,
                color = item.valueColor ?: MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

data class StatGridItem(
    val label: String,
    val value: String,
    val valueColor: Color? = null
)

// ── Pure formatting helpers ────────────────────────────────────────────

/** "1h 2m 3s" / "2m 3s" / "3s" — mirrors the PWA's FormatDuration. */
internal fun formatDurationSeconds(totalSeconds: Long): String {
    if (totalSeconds < 0) return "0s"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** One-decimal rate with a unit suffix, e.g. "12.3 archivos/s". Returns
 *  null when the rate is non-positive so callers can skip rendering. */
internal fun formatRate(perSecond: Double, unitSuffix: String): String? {
    if (perSecond <= 0.0) return null
    val rounded = (perSecond * 10).toLong() / 10.0
    return "$rounded $unitSuffix"
}
