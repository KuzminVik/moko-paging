package dev.icerock.moko.paging

/**
 * Стейт списка для Pagination
 *
 * @param items загруженный список элементов
 * @param isRefreshing состояние обновления страницы, использовать для показа pull-to-refresh
 * @param isNextPageLoading состояние загрузки следущей страницы, использовать для показа лоадера в конце списка
 * @param isEndOfList индикатор загрузки всего списка, в случае false не вызывать onLoadNextPage
 * */
data class PagingState<T>(
    val items: List<T>,
    val isRefreshing: Boolean = false,
    val isNextPageLoading: Boolean = false,
    val isEndOfList: Boolean = false
)
