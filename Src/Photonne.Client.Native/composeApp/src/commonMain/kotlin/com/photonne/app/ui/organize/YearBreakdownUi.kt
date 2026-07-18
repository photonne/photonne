package com.photonne.app.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.MoveOutcome
import com.photonne.app.data.models.YearCount

/**
 * Compact "2026 · 34   2025 · 12" chips describing how assets split across capture
 * years. Wraps to multiple rows on narrow screens (each chip is a short token, so
 * nothing can wrap character-by-character). Shared by the live move preview (both
 * the condition screen and the manual picker) and the post-move summary.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearBreakdownChips(items: List<YearCount>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { yc ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "${yc.year} · ${yc.count}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Post-move confirmation: how many assets moved and how they landed across Year
 * subfolders. Shown only when the move organized by year (otherwise there's no
 * split worth reporting).
 */
@Composable
fun MoveSummaryDialog(outcome: MoveOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movidas ${outcome.moved} fotos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Repartidas en estas carpetas de año:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                YearBreakdownChips(outcome.yearBreakdown)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        },
    )
}
