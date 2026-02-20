package dev.icerock.moko.paging

import dev.icerock.moko.remotestate.RemoteState
import io.github.aakira.napier.Napier
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
        // если уже есть задача загрузки новой страницы - просто ждём её завершения.
        // Корутину завершим только когда задача завершится - чтобы вызывающая сторона точно понимала
        // что загрузка завершилась
        loadFirstPageJob?.let {
            it.join()
            return
        }

        // если есть рефреш/загрузка - отменяем
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
                    Napier.e("can't load first page", exc)
                    _state.value = RemoteState.Error(exc)
                }
            }.apply {
                // зануляем завершенную задачу
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

        // если уже всё выкачали - не надо нам ничего больше делать
        if (currentState.data.isEndOfList) return

        // если уже грузим след страницу - просто ждем результат этой загрузки
        loadNextPageJob?.let {
            it.join()
            return
        }
        // если идет рефреш - ждем пока закончится, только потом действуем сами
        refreshJob?.join()

        coroutineScope {
            loadNextPageJob = launch {
                // Повторно проверяем стейт, так как с предыдущей проверки, другая корутина
                // могла изменить его
                val latest =
                    _state.value as? RemoteState.Success<PagingState<Item>> ?: return@launch
                if (latest.data.isEndOfList) return@launch

                _state.value = latest.withNextPageLoading(true)

                runCatching {
                    val currentList: List<Item> = latest.data.items
                    val nextPageItems: List<Item> = dataSource.loadPage(currentList = currentList)
                    val newState: PagingState<Item> = getNextPageState(currentList, nextPageItems)

                    _state.value = RemoteState.Success(newState)

                    // выдаем полученные значения новой страницы
                    nextPageItems
                }.onFailure { exc ->
                    if (exc is CancellationException) throw exc

                    Napier.e("can't load next page", exc)
                    // Проверяем что текущий стейт, Success, если другая корутина изменила его
                    // ничего не делаем
                    val successState = _state.value as? RemoteState.Success<PagingState<Item>>
                    if (successState != null) {
                        _state.value = successState.withNextPageLoading(false)
                    }
                }.let { result ->
                    nextPageListener(result)
                }
            }.apply {
                // зануляем завершенную задачу
                invokeOnCompletion { loadNextPageJob = null }
            }
        }
    }

    suspend fun reloadFirstPage() {
        // если уже есть задача загрузки первой страницы - отменяем её.
        loadFirstPageJob?.let {
            it.cancel()
            loadFirstPageJob = null
        }

        // если есть рефреш/загрузка - отменяем
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
                    Napier.e("can't load first page", exc)
                    _state.value = RemoteState.Error(exc)
                }
            }.apply {
                // зануляем завершенную задачу
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

        // идет обновление - ждем его результат
        refreshJob?.let {
            it.join()
            return
        }
        // идет загрузка новой страницы - дожидаемся её и погнали
        loadNextPageJob?.join()

        coroutineScope {
            refreshJob = launch {
                // Повторно проверяем стейт, так как с предыдущей проверки, другая корутина
                // могла изменить его
                val currentState = _state.value as? RemoteState.Success<PagingState<Item>>
                    ?: return@launch

                _state.value = currentState.withRefreshing(true)

                runCatching {
                    val newItems: List<Item> = dataSource.loadPage(null)

                    when (refreshStrategy) {
                        RefreshStrategy.ReplaceEverything -> {
                            // Просто берем новые данные. Старое удаляем.
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

                    // передаем полученный список в результат
                    newItems
                }.onFailure { exc ->
                    if (exc is CancellationException) throw exc

                    Napier.e("can't refresh list of services", exc)
                    val latest = _state.value as? RemoteState.Success<PagingState<Item>>
                    if (latest != null) {
                        _state.value = latest.withRefreshing(false)
                    }
                }.let { result ->
                    refreshListener(result)
                }
            }.apply {
                // зануляем завершенную задачу
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
        // убираем элементы которые уже есть в оригинальном списке
        // такая ситуация может происходить когда новые элементы появились в начале списка
        // (на тех страницах что у нас уже загружены)
        val currentKeys = currentList.map(itemKey).toHashSet()
        val filteredItems = nextPageItems.filter { itemKey(it) !in currentKeys }
        val newList: List<Item> = currentList + filteredItems

        return PagingState(
            items = newList,
            // если мы получили в ответ на страницу меньше элементов
            // чем запрашивали - значит список кончился
            isEndOfList = !dataSource.isPageFull(nextPageItems)
        )
    }

    private fun mergeNewItemsState(
        currentState: RemoteState.Success<PagingState<Item>>,
        newItems: List<Item>
    ): PagingState<Item> {
        val currentItems: List<Item> = currentState.data.items

        // Используем itemKey для быстрого поиска
        val currentKeys = currentItems.map(itemKey).toHashSet()

        // Проверяем, есть ли пересечение (хотя бы один элемент из новых уже есть в старых)
        val hasIntersection = newItems.any { itemKey(it) in currentKeys }

        // Если есть новые элементы, но нет пересечения со старыми и старые не пустые -
        // считаем, что лента уехала полностью, делаем полную замену
        if (!hasIntersection && newItems.isNotEmpty() && currentItems.isNotEmpty()) {
            return PagingState(
                items = newItems,
                isEndOfList = !dataSource.isPageFull(newItems)
            )
        }

        // Оставляем только те новые элементы, ключей которых нет в старом списке
        val uniqueNewItems = newItems.filter { item ->
            itemKey(item) !in currentKeys
        }

        val newState: PagingState<Item> = if (uniqueNewItems.isNotEmpty()) {
            // Добавляем уникальные новые в начало + все старые
            // isEndOfList не трогаем, так как старые элементы остались
            PagingState(
                items = uniqueNewItems + currentItems,
                isEndOfList = currentState.data.isEndOfList
            )
        } else {
            // Если ничего нового нет - оставляем всё как было
            // (или заменяем на newItems, если список был пуст)
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
