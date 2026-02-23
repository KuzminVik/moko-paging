/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package com.icerockdev

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icerockdev.library.ListViewModel
import com.icerockdev.library.ListViewModel.ProductItem
import dev.icerock.moko.paging.PagingState
import dev.icerock.moko.paging.compose.PagingStateContent
import dev.icerock.moko.remotestate.RemoteState
import dev.icerock.moko.remotestate.data

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagingScreen(viewModel: ListViewModel) {
    val screenState by viewModel.state.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.onStart()
    }

    PagingStateContent(
        state = screenState,
        listState = listState,
        onRefresh = viewModel::onRefresh,
        onLoadNextRequested = viewModel::onLoadNextPage,
        loadingContent = {
            LoadingContent()
        },
        errorContent = { _ ->
            ErrorContent(
                onRefresh = viewModel::onRefresh
            )
        },
        emptyContent = {
            EmptyContent(
                onRefresh = viewModel::onRefresh
            )
        },
        itemsContent = { items ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = items,
                    key = { item -> item.id }
                ) { item ->
                    ProductRow(item = item)
                }

                if (screenState.data?.isNextPageLoading == true) {
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
    )
}

@Composable
private fun EmptyContent(
    onRefresh: () -> Unit
) {
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
            TextButton(onClick = onRefresh) {
                Text(text = "Refresh")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    onRefresh: () -> Unit
) {
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
            TextButton(onClick = onRefresh) {
                Text(text = "Retry")
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PagingStateContentPreview() {
    val listState = rememberLazyListState()
    val state = RemoteState.Success(
        data = PagingState(
            items = listOf(
                ProductItem(1, "PagingState 1"),
                ProductItem(2, "PagingState 2"),
                ProductItem(3, "PagingState 3"),
                ProductItem(4, "PagingState 4"),
                ProductItem(5, "PagingState 5"),
                ProductItem(6, "PagingState 6"),
                ProductItem(7, "PagingState 7"),
            )
        )
    )

    PagingStateContent(
        state = state,
        listState = listState,
        onLoadNextRequested = {},
        onRefresh = {},
        loadingContent = {},
        errorContent = { _ -> },
        itemsContent = { items ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = items,
                    key = { item -> item.id }
                ) { item ->
                    ProductRow(item = item)
                }

                if (state.data.isNextPageLoading) {
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
    )
}
