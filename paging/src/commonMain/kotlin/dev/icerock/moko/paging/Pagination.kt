/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging

import dev.icerock.moko.paging.utils.withNextPageLoading
import dev.icerock.moko.paging.utils.withRefreshing
import dev.icerock.moko.remotestate.RemoteState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Paginated list loader.
 *
 * Updated version of moko-paging, migrated to StateFlow.
 *
 * @param dataSource implementation of PagingDataSource with a suspend load method
 * @param itemKey lambda returning a unique item key `(Item) -> Any`.
 * Used for item identity (equals/hashCode analogue) and deduplication
 * when merging pages (for example, to avoid duplicates if an item moved to another page).
 * @param refreshStrategy refresh behavior (Pull-to-Refresh).
 * Defines how to handle already loaded data when the first page is fetched:
 * - [RefreshStrategy.MergeNewItems]: Tries to keep old data by adding new items to the beginning.
 * Suitable for append-only lists (logs, chats). Can lead to desync when items are deleted
 * on the backend.
 * - [RefreshStrategy.ReplaceEverything]: Full replacement. On successful load the old list
 * is discarded and replaced by the new first page. Helps avoid UI "blink"
 * (unlike reloadFirstPage), keeping old data visible until new data arrives.
 * @param nextPageListener callback invoked when the next page load completes,
 * use it to show errors or handle success
 * @param refreshListener callback invoked when the refresh load completes
 * @param initValue initial list value
 */
class Pagination<Item>(
    private val dataSource: PagingDataSource<Item>,
    private val itemKey: (Item) -> Any,
    private val refreshStrategy: RefreshStrategy = RefreshStrategy.MergeNewItems,
    private val nextPageListener: (Result<List<Item>>) -> Unit = {},
    private val refreshListener: (Result<List<Item>>) -> Unit = {},
    initValue: List<Item>? = null
) {
    private val _state = MutableStateFlow<RemoteState<PagingState<Item>, Throwable>>(
        initValue
            ?.let { RemoteState.Success(PagingState(items = it)) }
            ?: RemoteState.Loading
    )

    /**
     * State of the paginated list.
     *
     * Usage example: map the state in a ViewModel,
     * converting Throwable to the error class required for UI output
     *    pagination.state
     *       .map { state ->
     *            state.mapError { it.mapThrowable<Throwable, StringDesc>() }
     *        }
     */
    val state: StateFlow<RemoteState<PagingState<Item>, Throwable>> = _state.asStateFlow()

    private var loadFirstPageJob: Job? = null
    private var refreshJob: Job? = null
    private var loadNextPageJob: Job? = null

    /**
     * Loads the first page of data.
     *
     * When loading the first page, the current state is discarded and we reset to full loading.
     * Then, depending on the result, we move to success or error.
     *
     * If a refresh or another page load is running at the moment of the call, all that activity
     * is canceled. Loading the first page has the highest priority (the user wants a full reload
     * from scratch).
     *
     * If called again while already running, nothing happens (we wait for the previous result).
     */
    suspend fun loadFirstPage() {
        // if there is already a task to load a new page, we are just waiting for it to be completed.
        // Coroutines we will complete it only when the task is completed - so that the caller understands exactly
        // that the download has completed
        loadFirstPageJob?.let {
            it.join()
            return
        }

        // if there is a refresh/download, we cancel it.
        refreshJob?.let {
            it.cancel()
            refreshJob = null
        }
        loadNextPageJob?.let {
            it.cancel()
            loadNextPageJob = null
        }

        coroutineScope {
            loadFirstPageJob = launch {
                _state.value = RemoteState.Loading

                @Suppress("TooGenericExceptionCaught")
                try {
                    val items: List<Item> = dataSource.loadPage(null)
                    _state.value = RemoteState.Success(
                        data = PagingState(
                            items = items,
                            isEndOfList = dataSource.isPageFull(items).not()
                        )
                    )
                } catch (exc: CancellationException) {
                    throw exc
                } catch (exc: Exception) {
                    _state.value = RemoteState.Error(exc)
                }
            }.apply {
                // resetting a completed task
                invokeOnCompletion { loadFirstPageJob = null }
            }
        }
    }

    /**
     * Loads the next page of data.
     *
     * We can load the next page only if we are in the success state (i.e., there are already
     * items in the list - one or more pages). If the state indicates the list is finished,
     * there is no point in loading more.
     *
     * If the first page is still loading when called, we do nothing (see above).
     * If a refresh is running (updating the first page without a full reset), we wait for it
     * to finish to avoid distorting the list.
     * If a next page load is already running, we do nothing (the required operation is in flight).
     */
    @Suppress("ReturnCount")
    suspend fun loadNextPage() {
        val currentState: RemoteState.Success<PagingState<Item>> =
            _state.value as? RemoteState.Success<PagingState<Item>> ?: return

        // If everything has already been uploaded, we don't need to do anything else
        if (currentState.data.isEndOfList) return

        // if we are already uploading the next page, we are just waiting for the result of this download
        loadNextPageJob?.let {
            it.join()
            return
        }
        // if there is a refresh, we wait until it ends, only then we act on our own
        refreshJob?.join()

        coroutineScope {
            loadNextPageJob = launch {
                // We re-check the state, because from the previous check, another coroutine
                // could have changed him
                val latest =
                    _state.value as? RemoteState.Success<PagingState<Item>> ?: return@launch
                if (latest.data.isEndOfList) return@launch

                _state.value = latest.withNextPageLoading(true)

                runCatching {
                    val currentList: List<Item> = latest.data.items
                    val nextPageItems: List<Item> = dataSource.loadPage(currentList = currentList)
                    val newState: PagingState<Item> = getNextPageState(currentList, nextPageItems)

                    _state.value = RemoteState.Success(newState)

                    // We give out the received values of the new page
                    nextPageItems
                }.onFailure { exc ->
                    if (exc is CancellationException) throw exc

                    // We check that the current state is Success, if another coroutine has changed it
                    // we're not doing anything
                    val successState = _state.value as? RemoteState.Success<PagingState<Item>>

                    if (successState != null) {
                        _state.value = successState.withNextPageLoading(false)
                    }
                }.let { result ->
                    nextPageListener(result)
                }
            }.apply {
                // resetting a completed task
                invokeOnCompletion { loadNextPageJob = null }
            }
        }
    }

    suspend fun reloadFirstPage() {
        // if there is already a task to load the first page, cancel it.
        loadFirstPageJob?.let {
            it.cancel()
            loadFirstPageJob = null
        }

        // if there is a refresh/download, we cancel it.
        refreshJob?.let {
            it.cancel()
            refreshJob = null
        }
        loadNextPageJob?.let {
            it.cancel()
            loadNextPageJob = null
        }

        coroutineScope {
            loadFirstPageJob = launch {
                _state.value = RemoteState.Loading

                @Suppress("TooGenericExceptionCaught")
                try {
                    val items: List<Item> = dataSource.loadPage(null)
                    _state.value = RemoteState.Success(
                        data = PagingState(
                            items = items,
                            isEndOfList = dataSource.isPageFull(items).not()
                        )
                    )
                } catch (exc: CancellationException) {
                    throw exc
                } catch (exc: Exception) {
                    _state.value = RemoteState.Error(exc)
                }
            }.apply {
                // resetting a completed task
                invokeOnCompletion { loadFirstPageJob = null }
            }
        }
    }

    /**
     * Refreshes the list contents without resetting to the Loading state.
     * Loads new data while keeping the current items visible (Pull-to-Refresh).
     *
     * @param refreshStrategy refresh strategy for this call.
     * By default, uses the strategy configured in the constructor ([this.refreshStrategy]).
     *
     * Behavior variants:
     * - [RefreshStrategy.MergeNewItems]:
     * If new and old data overlap (there are identical items) the old list is preserved
     * and new items are prepended. If there is no overlap, the list is fully replaced.
     * - [RefreshStrategy.ReplaceEverything]:
     * Fully replaces the list with new data. Old data stays on screen until the new
     * data is successfully loaded, then it is replaced immediately.
     * Used, for example, when filters change and merging old and new data is incorrect.
     *
     * Launch conditions:
     * - Runs only if data is already loaded (state is [RemoteState.Success]).
     * - If a refresh is already running ([refreshJob]), waits for it to complete.
     * - If a next page load is running ([loadNextPageJob]), waits for it to complete
     * to avoid collisions and list distortion.
     */
    suspend fun refresh(refreshStrategy: RefreshStrategy = this.refreshStrategy) {
        if (_state.value !is RemoteState.Success<*>) return

        // An update is underway - we are waiting for its result.
        refreshJob?.let {
            it.join()
            return
        }
        // A new page is loading, so we wait for it and let's go.
        loadNextPageJob?.join()

        coroutineScope {
            refreshJob = launch {
                // We re-check the state, since from the previous check, another coroutine could have changed it
                val currentState = _state.value as? RemoteState.Success<PagingState<Item>>
                    ?: return@launch

                _state.value = currentState.withRefreshing(true)

                runCatching {
                    val newItems: List<Item> = dataSource.loadPage(null)

                    when (refreshStrategy) {
                        RefreshStrategy.ReplaceEverything -> {
                            // We just take new data. We are deleting the old one.
                            val isEndOfList = !dataSource.isPageFull(newItems)

                            _state.value = RemoteState.Success(
                                data = PagingState(
                                    items = newItems,
                                    isEndOfList = isEndOfList
                                )
                            )
                        }

                        RefreshStrategy.MergeNewItems -> {
                            val newState: PagingState<Item> = mergeNewItemsState(
                                currentState = currentState,
                                newItems = newItems
                            )

                            _state.value = RemoteState.Success(newState)
                        }
                    }

                    // Passing the received list to the result.
                    newItems
                }.onFailure { exc ->
                    if (exc is CancellationException) throw exc

                    val latest = _state.value as? RemoteState.Success<PagingState<Item>>
                    if (latest != null) {
                        _state.value = latest.withRefreshing(false)
                    }
                }.let { result ->
                    refreshListener(result)
                }
            }.apply {
                // resetting a completed task
                invokeOnCompletion { refreshJob = null }
            }
        }
    }

    /**
     * Method for manually updating the list from the outside.
     *
     * Attempts to update the data atomically.
     * If the state is RemoteState.Success, updates the list value without touching other data.
     * Otherwise sets RemoteState.Success with the provided list value.
     *
     * Also cancels all jobs that load new data.
     */
    fun setData(items: List<Item>?) {
        loadFirstPageJob?.cancel()
        refreshJob?.cancel()
        loadNextPageJob?.cancel()

        _state.update { currentState ->
            when (currentState) {
                is RemoteState.Success<PagingState<Item>> -> {
                    val newPagingState = currentState.data.copy(items = items ?: emptyList())
                    currentState.copy(data = newPagingState)
                }

                else -> RemoteState.Success(
                    data = PagingState(
                        items ?: emptyList()
                    )
                )
            }
        }
    }

    private fun getNextPageState(
        currentList: List<Item>,
        nextPageItems: List<Item>
    ): PagingState<Item> {
        // removing the items that are already in the original list
        // This situation may occur when new items appear at the top of the list.
        // (on the pages that we have already uploaded)
        val currentKeys = currentList.map(itemKey).toHashSet()
        val filteredItems = nextPageItems.filter { itemKey(it) !in currentKeys }
        val newList: List<Item> = currentList + filteredItems

        return PagingState(
            items = newList,
            // if we received fewer items in response to the page what was requested means that the list is over
            isEndOfList = !dataSource.isPageFull(nextPageItems)
        )
    }

    private fun mergeNewItemsState(
        currentState: RemoteState.Success<PagingState<Item>>,
        newItems: List<Item>
    ): PagingState<Item> {
        val currentItems: List<Item> = currentState.data.items

        // We use ItemKey for quick search
        val currentKeys = currentItems.map(itemKey).toHashSet()

        // Checking if there is an intersection (at least one of the new elements already exists in the old ones)
        val hasIntersection = newItems.any { itemKey(it) in currentKeys }

        // If there are new elements, but there is no intersection with the old ones and the old ones
        // are not empty, we assume that the tape is completely gone, we make a complete replacement.
        if (!hasIntersection && newItems.isNotEmpty() && currentItems.isNotEmpty()) {
            return PagingState(
                items = newItems,
                isEndOfList = !dataSource.isPageFull(newItems)
            )
        }

        // We leave only those new items whose keys are not in the old list.
        val uniqueNewItems = newItems.filter { item ->
            itemKey(item) !in currentKeys
        }

        val newState: PagingState<Item> = if (uniqueNewItems.isNotEmpty()) {
            // Adding unique new ones to the beginning + all the old ones
            // We do not touch the isEndOfList, as the old elements remain.
            PagingState(
                items = uniqueNewItems + currentItems,
                isEndOfList = currentState.data.isEndOfList
            )
        } else {
            // If there is nothing new, we leave everything as it was.
            // (or replace it with NewItems if the list was empty)
            if (currentItems.isEmpty()) {
                PagingState(
                    items = newItems,
                    isEndOfList = !dataSource.isPageFull(newItems)
                )
            } else {
                currentState.data
            }
        }
        return newState
    }
}
