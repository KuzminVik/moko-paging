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
 * Пагинированная загрузка списка
 *
 * обновленная версия moko-paging, перешли на StateFlow
 *
 * @param dataSource реализация интерфейса PagingDataSource с suspend методом загрузки элементов
 * @param itemKey Лямбда для получения уникального ключа элемента `(Item) -> Any`.
 * Используется для идентификации элементов (аналог equals/hashCode) и дедупликации
 * при слиянии страниц (например, чтобы избежать дублей, если элемент сместился на другую страницу).
 * @param refreshStrategy Стратегия поведения при обновлении (Pull-to-Refresh).
 * Определяет, как поступать с уже загруженными данными при получении первой страницы:
 * - [RefreshStrategy.MergeNewItems]: Пытается сохранить старые данные, добавляя новые в начало.
 * Подходит для append-only списков (логи, чаты). Может приводить к рассинхрону при удалении
 * элементов на бэкенде.
 * - [RefreshStrategy.ReplaceEverything]: Полная замена. При успехе загрузки старый список
 * полностью отбрасывается и заменяется новой первой страницей. Позволяет избежать "моргания"
 * экрана (в отличие от reloadFirstPage), сохраняя старые данные видимыми до момента получения новых.
 * @param nextPageListener обработчик завершения загрузки следующей страницы,
 * вызывать для показа ошибки либо дополнительной обработки успеха
 * @param refreshListener обработчик завершения загрузки обновления списка
 * @param initValue начальное значение списка
 * */
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
     * Стэйт пагинированного списка
     *
     * Пример использования: во ViewModel мапить стейт,
     * преобразовывая Throwable в необходимый класс ошибки для вывода на ui
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
     * Загрузка первой страницы данных.
     *
     * В случае загрузки первой страницы считаем что текущий стейт не нужен - сбрасываемся в полную
     * загрузку. Далее в зависимости от успешности загрузки либо перейдем в успех, либо в ошибку.
     *
     * Если в момент вызова уже идет рефреш/загрузка другой страницы - вся эта активность отменяется,
     * загрузка первой страницы имеет максимальный приоритет (пользователь хочет полностью данные с
     * нуля загрузить).
     *
     * Если повторно вызываем когда уже запущено - ничего не делается (ждем предыдущий запущенный
     * результат).
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
     * Загрузка следующей страницы данных.
     *
     * Следующую страницу мы можем загружать только если находимся в состоянии успеха (то есть
     * уже есть какие-то элементы в списке - первая или больше страниц).
     * И если у нас в стейте отражено что список закончен - смысла пытаться подгружать еще элементы
     * нету.
     *
     * Если в момент вызова еще уже загрузка первой страницы - мы ничего не делаем (выше описание).
     * Если же идет загрузка refresh (обновление первой страницы, без полного сброса) - ждем пока
     * оно завершится, чтобы список не деформировался.
     * Если уже идет загрузка новой страницы - ничего не делаем (необходимая операция уже запущена).
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
     * Обновление содержимого списка без сброса в состояние Loading.
     * Позволяет загрузить новые данные, сохраняя на экране текущие (Pull-to-Refresh).
     *
     * @param refreshStrategy Стратегия обновления для текущего вызова.
     * По умолчанию используется стратегия, заданная в конструкторе ([this.refreshStrategy]).
     *
     * Варианты поведения:
     * - [RefreshStrategy.MergeNewItems]:
     * Если новые и старые данные пересекаются (есть одинаковые элементы) — старый список
     * сохраняется, новые элементы добавляются в начало.
     * Если пересечения нет — происходит полная замена списка.
     * - [RefreshStrategy.ReplaceEverything]:
     * Полная замена списка новыми данными. Старые данные остаются на экране до момента
     * успешной загрузки новых, затем мгновенно заменяются.
     * Используется, например, при изменении фильтров, когда объединение старых и новых
     * данных некорректно.
     *
     * Условия запуска:
     * - Выполняется только если данные уже загружены (состояние [RemoteState.Success]).
     * - Если уже идет обновление ([refreshJob]) — ожидает его завершения.
     * - Если идет загрузка следующей страницы ([loadNextPageJob]) — ожидает её завершения,
     * чтобы избежать коллизий и деформации списка.
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
     * Метод для ручного обновления списка снаружи
     *
     * Выполняет попытку атомарно изменить данные,
     * Если стейт RemoteState.Success, обновит значение списка, не затрагивая другие данные
     * Иначе присвоит значение RemoteState.Success c заданным значением списка элементов
     *
     * Так же остановятся все джобы по загрузке новых данных
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
