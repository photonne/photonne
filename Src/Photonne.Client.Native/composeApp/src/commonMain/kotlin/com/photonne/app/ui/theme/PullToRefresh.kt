package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wraps [content] with Material3's pull-to-refresh gesture so the native
 * "swipe down" gesture triggers a reload across every list/grid screen.
 * Replaces the per-screen toolbar refresh icon.
 *
 * [indicatorTopPadding] pushes the spinner down on screens whose content runs
 * edge to edge under a floating chrome: without it the indicator does its
 * whole travel behind the capsule and the pull looks like it did nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotonneRefreshableScreen(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    indicatorTopPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTopPadding),
                isRefreshing = isRefreshing,
                state = state
            )
        }
    ) {
        content()
    }
}
