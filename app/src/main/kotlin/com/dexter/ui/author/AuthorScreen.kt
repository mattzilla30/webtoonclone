package com.dexter.ui.author

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.ui.AppTopBar
import com.dexter.ui.LoadView
import com.dexter.ui.SeriesGrid

@Composable
fun AuthorScreen(viewModel: AuthorViewModel, name: String, onBack: () -> Unit, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            name.ifBlank { "Author" },
            onBack,
            actions = {
                ToggleButton(
                    checked = following,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        viewModel.toggleFollow(name)
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(if (following) "Following" else "Follow") }
            },
        )
        LoadView(state, onRetry = viewModel::load) { series ->
            if (series.isEmpty()) {
                Text(stringResource(R.string.no_series_found), modifier = Modifier.padding(16.dp))
            } else {
                SeriesGrid(series, loadingMore, onLoadMore = viewModel::loadMore, onOpenSeries = onOpenSeries)
            }
        }
    }
}
