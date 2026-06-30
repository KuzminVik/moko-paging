/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging

import dev.icerock.moko.remotestate.RemoteState
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PaginationTests {

    @Test
    fun `pagination flow test`() = runTest {
        // channel to feed "server responses" during testing; null allows testing
        // the waiting logic
        val channel = Channel<List<Int>?>()

        val pagination: Pagination<Int> = Pagination(
            dataSource = object : PagingDataSource<Int> {
                override fun isPageFull(list: List<Int>): Boolean {
                    return list.isNotEmpty()
                }

                override suspend fun loadPage(currentList: List<Int>?): List<Int> {
                    return channel
                        .receiveAsFlow()
                        .filterNotNull()
                        .first()
                }
            },
            itemKey = { it }
        )

        // initially loading state
        assertIs<RemoteState.Loading>(pagination.state.value)

        // then we start loading the first page; while loading, loading state should be
        pagination.paginationAction(
            action = { loadFirstPage() },
            channel = channel,
            response = listOf(0, 1, 2),
            onLoad = { assertIs<RemoteState.Loading>(it) }
        )

        // once loaded, data should be present
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(0, 1, 2),
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = false,
                ),
                actual = state.data
            )
        }

        // then we load the next page; while loading, isNextPageLoading flag should be true
        pagination.paginationAction(
            action = { loadNextPage() },
            channel = channel,
            response = listOf(3, 4, 5),
            onLoad = { state ->
                assertIs<RemoteState.Success<PagingState<Int>>>(state)
                assertEquals(
                    expected = PagingState(
                        items = listOf(0, 1, 2),
                        isRefreshing = false,
                        isNextPageLoading = true,
                        isEndOfList = false,
                    ),
                    actual = state.data
                )
            }
        )

        // after loading completes, list should be larger
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(0, 1, 2, 3, 4, 5),
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = false,
                ),
                actual = state.data
            )
        }

        // then we do pull-to-refresh; while loading, flag should be set
        pagination.paginationAction(
            action = { refresh() },
            channel = channel,
            response = listOf(-1, 0, 1),
            onLoad = { state ->
                assertIs<RemoteState.Success<PagingState<Int>>>(state)
                assertEquals(
                    expected = PagingState(
                        items = listOf(0, 1, 2, 3, 4, 5),
                        isRefreshing = true,
                        isNextPageLoading = false,
                        isEndOfList = false,
                    ),
                    actual = state.data
                )
            }
        )

        // after loading, list slightly expands
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(-1, 0, 1, 2, 3, 4, 5),
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = false,
                ),
                actual = state.data
            )
        }

        // then we load further but no new pages exist; while loading, flag
        // should light up again
        pagination.paginationAction(
            action = { loadNextPage() },
            channel = channel,
            response = emptyList(),
            onLoad = { state ->
                assertIs<RemoteState.Success<PagingState<Int>>>(state)
                assertEquals(
                    expected = PagingState(
                        items = listOf(-1, 0, 1, 2, 3, 4, 5),
                        isRefreshing = false,
                        isNextPageLoading = true,
                        isEndOfList = false,
                    ),
                    actual = state.data
                )
            }
        )

        // loading complete - list fully loaded
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(-1, 0, 1, 2, 3, 4, 5),
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = true,
                ),
                actual = state.data
            )
        }

        // then we do another pull-to-refresh; while loading, flag should be
        // set but refresh now yields completely new data
        pagination.paginationAction(
            action = { refresh() },
            channel = channel,
            response = listOf(10, 11),
            onLoad = { state ->
                assertIs<RemoteState.Success<PagingState<Int>>>(state)
                assertEquals(
                    expected = PagingState(
                        items = listOf(-1, 0, 1, 2, 3, 4, 5),
                        isRefreshing = true,
                        isNextPageLoading = false,
                        isEndOfList = true,
                    ),
                    actual = state.data
                )
            }
        )

        // after loading, list is replaced and finished list state resets
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(10, 11),
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = false,
                ),
                actual = state.data
            )
        }

        // then we reload data from scratch
        pagination.paginationAction(
            action = { loadFirstPage() },
            channel = channel,
            response = listOf(1, 3),
            onLoad = { state ->
                assertIs<RemoteState.Loading>(state)
            }
        )

        // after loading just a new list
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(1, 3),
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = false,
                ),
                actual = state.data
            )
        }
    }

    @Test
    fun `replaceEverything strategy test`() = runTest {
        val channel = Channel<List<Int>?>()

        // initialize with the required strategy
        val pagination: Pagination<Int> = Pagination(
            dataSource = object : PagingDataSource<Int> {
                override fun isPageFull(list: List<Int>): Boolean = list.isNotEmpty()

                override suspend fun loadPage(currentList: List<Int>?): List<Int> {
                    return channel.receiveAsFlow().filterNotNull().first()
                }
            },
            itemKey = { it },
            refreshStrategy = RefreshStrategy.ReplaceEverything
        )

        // 1. Load first page (initial data)
        pagination.paginationAction(
            action = { loadFirstPage() },
            channel = channel,
            response = listOf(1, 2, 3),
            onLoad = { assertIs<RemoteState.Loading>(it) }
        )

        // verify initial data loaded
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(listOf(1, 2, 3), state.data.items)
        }

        // 2. Perform refresh with new data
        pagination.paginationAction(
            action = { refresh() },
            channel = channel,
            // return data that's completely different
            // (or overlapping - for ReplaceEverything it doesn't matter)
            response = listOf(4, 5),
            onLoad = { state ->
                assertIs<RemoteState.Success<PagingState<Int>>>(state)
                // Important point: during loading (isRefreshing=true)
                // old data is still displayed
                assertEquals(
                    expected = PagingState(
                        items = listOf(1, 2, 3),
                        isRefreshing = true,
                        isNextPageLoading = false,
                        isEndOfList = false,
                    ),
                    actual = state.data
                )
            }
        )

        // 3. Check final result: old data (1, 2, 3) should disappear, only (4, 5) remains
        pagination.state.value.let { state ->
            assertIs<RemoteState.Success<PagingState<Int>>>(state)
            assertEquals(
                expected = PagingState(
                    items = listOf(4, 5), // full replacement
                    isRefreshing = false,
                    isNextPageLoading = false,
                    isEndOfList = false,
                ),
                actual = state.data
            )
        }
    }

    private suspend fun <Item> Pagination<Item>.paginationAction(
        action: suspend Pagination<Item>.() -> Unit,
        channel: Channel<List<Item>?>,
        response: List<Item>,
        onLoad: (RemoteState<PagingState<Item>, Throwable>) -> Unit
    ) {
        val pagination = this
        coroutineScope {
            val result = async {
                pagination.action()
            }
            // wait until we're "waiting for server response"
            channel.send(null)
            // check what's happening at this moment
            onLoad(pagination.state.value)
            // respond from server
            channel.send(response)
            result.await()
        }
    }
}
