/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.icerock.moko.paging.PagingState

/**
 * Composable wrapper for paged content with pull-to-refresh and auto-load-next.
 *
 * @param state paging state with items and loading flags
 * @param onLoadNextRequested callback for requesting the next page
 * @param onRefresh callback for pull-to-refresh
 * @param listState LazyListState used to detect proximity to the end
 * @param pullToRefreshState PullToRefreshState for the indicator
 * @param pullToRefreshIndicator composable for the pull-to-refresh indicator UI
 * @param modifier modifier applied to the PullToRefreshBox
 * @param content renders the list items
 */
@Suppress("LongMethod", "MagicNumber")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PagingContent(
    state: PagingState<T>,
    onLoadNextRequested: () -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState,
    pullToRefreshState: PullToRefreshState,
    pullToRefreshIndicator: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (List<T>) -> Unit,
) {
    val shouldLoadMore: Boolean by remember {
        derivedStateOf {
            val layoutInfo: LazyListLayoutInfo = listState.layoutInfo
            val lastVisibleIndex: Int? = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val totalCount: Int = layoutInfo.totalItemsCount
            val threshold: Int = PAGING_LOAD_THRESHOLD
            val isCloseToEnd: Boolean = lastVisibleIndex != null &&
                    lastVisibleIndex >= totalCount - threshold
            lastVisibleIndex != 0 && !state.isEndOfList && isCloseToEnd
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !state.isNextPageLoading) {
            onLoadNextRequested()
        }
    }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = pullToRefreshIndicator
    ) {
        content.invoke(state.items)
    }
}

private const val PAGING_LOAD_THRESHOLD: Int = 3
