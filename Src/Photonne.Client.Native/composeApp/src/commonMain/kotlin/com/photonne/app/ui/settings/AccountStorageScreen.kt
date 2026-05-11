package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.storage_no_quota
import com.photonne.app.resources.storage_used_format
import com.photonne.app.resources.storage_used_unbounded_format
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountStorageScreen(viewModel: AccountStorageViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            state.isLoading && state.info == null ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.errorMessage != null && state.info == null ->
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            state.info != null -> {
                val info = state.info!!
                val usedHuman = humanReadableBytes(info.usedBytes)
                val quota = info.quotaBytes
                Text(
                    text = if (quota != null) {
                        stringResource(
                            Res.string.storage_used_format,
                            usedHuman,
                            humanReadableBytes(quota)
                        )
                    } else {
                        stringResource(Res.string.storage_used_unbounded_format, usedHuman)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                state.usagePercent?.let { progress ->
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                } ?: run {
                    Text(
                        stringResource(Res.string.storage_no_quota),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 1024-based with two decimals up to TB; mirrors what the web client renders. */
private fun humanReadableBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = ((value * 100).toLong()).toDouble() / 100.0
    return "$formatted ${units[unitIndex]}"
}
