/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.paging.PagingState
import dev.icerock.moko.remotestate.RemoteState

/**
 * Composable that renders paging UI based on RemoteState.
 *
 * @param state RemoteState with PagingState data or error
 * @param listState LazyListState used by PagingContent to detect end-of-list
 * @param onRefresh callback for pull-to-refresh
 * @param onLoadNextRequested callback for requesting the next page
 * @param loadingContent UI shown for RemoteState.Loading
 * @param errorContent UI shown for RemoteState.Error
 * @param itemsContent UI shown for RemoteState.Success with non-empty data
 * @param pullToRefreshModifier modifier applied to PullToRefreshBox
 * @param pullToRefreshIndicatorColor indicator color
 * @param pullToRefreshContainerColor indicator container color
 * @param emptyContent optional UI shown when the list is empty
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any, E : Any> PagingStateContent(
    state: RemoteState<PagingState<T>, E>,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onLoadNextRequested: () -> Unit,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable (E) -> Unit,
    itemsContent: @Composable (List<T>) -> Unit,
    pullToRefreshModifier: Modifier = Modifier,
    pullToRefreshIndicatorColor: Color = PullToRefreshDefaults.indicatorColor,
    pullToRefreshContainerColor: Color = PullToRefreshDefaults.containerColor,
    emptyContent: (@Composable () -> Unit)? = null,
) {
    when (state) {
        RemoteState.Loading -> {
            loadingContent.invoke()
        }

        is RemoteState.Error<E> -> {
            errorContent.invoke(state.error)
        }

        is RemoteState.Success<PagingState<T>> -> {
            if (state.data.items.isEmpty() && emptyContent != null) {
                emptyContent.invoke()
            } else {
                val pullToRefreshState = rememberPullToRefreshState()

                PagingContent(
                    modifier = pullToRefreshModifier,
                    state = state.data,
                    listState = listState,
                    pullToRefreshState = pullToRefreshState,
                    pullToRefreshIndicator = {
                        Indicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = state.data.isRefreshing,
                            containerColor = pullToRefreshContainerColor,
                            color = pullToRefreshIndicatorColor,
                            state = pullToRefreshState
                        )
                    },
                    onLoadNextRequested = onLoadNextRequested,
                    onRefresh = onRefresh,
                ) { items: List<T> ->
                    itemsContent(items)
                }
            }
        }
    }
}
