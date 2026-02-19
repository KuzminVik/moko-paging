package com.icerockdev

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icerockdev.library.ListViewModel
import com.icerockdev.library.ListViewModel.ProductItem
import dev.icerock.moko.paging.PagingState
import dev.icerock.moko.remotestate.RemoteState

@Composable
fun PagingScreen(viewModel: ListViewModel) {
    val screenState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onStart()
    }

    when (screenState) {
        RemoteState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is RemoteState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error state text",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.onRefresh() }) {
                        Text(text = "Retry")
                    }
                }
            }
        }

        is RemoteState.Success<PagingState<ProductItem>> -> {
            val pagingState: PagingState<ProductItem> =
                (screenState as RemoteState.Success<PagingState<ProductItem>>).data

            if (pagingState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Empty state text",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.onRefresh() }) {
                            Text(text = "Refresh")
                        }
                    }
                }
            } else {
                PagingContent(
                    pagingState = pagingState,
                    onLoadNextRequested = { viewModel.onLoadNextPage() },
                    onRefresh = {
                        viewModel.onRefresh()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagingContent(
    pagingState: PagingState<ProductItem>,
    onLoadNextRequested: () -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    val shouldLoadMore: Boolean by remember {
        derivedStateOf {
            val layoutInfo: LazyListLayoutInfo = listState.layoutInfo
            val lastVisibleIndex: Int? = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val totalCount: Int = layoutInfo.totalItemsCount
            val threshold: Int = 3
            val endIndex = (totalCount - threshold).coerceAtLeast(0)
            val isCloseToEnd: Boolean = lastVisibleIndex != null &&
                    lastVisibleIndex >= endIndex
            lastVisibleIndex != null &&
                    !pagingState.isEndOfList &&
                    !pagingState.isNextPageLoading &&
                    !pagingState.isRefreshing &&
                    isCloseToEnd
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadNextRequested()
        }
    }

    PullToRefreshBox(
        isRefreshing = pagingState.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = pagingState.isRefreshing,
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary,
                state = pullToRefreshState
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = pagingState.items,
                key = { item -> item.id }
            ) { item ->
                ProductRow(item = item)
            }

            if (pagingState.isNextPageLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(item: ProductItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "#${item.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun PagingContentPreview() {
    PagingContent(
        pagingState = PagingState(
            items = listOf(
                ProductItem(1, "PagingState 1"),
                ProductItem(2, "PagingState 2"),
                ProductItem(3, "PagingState 3"),
                ProductItem(4, "PagingState 4"),
                ProductItem(5, "PagingState 5"),
                ProductItem(6, "PagingState 6"),
                ProductItem(7, "PagingState 7"),
            )
        ),
        onLoadNextRequested = {},
        onRefresh = {}
    )
}
