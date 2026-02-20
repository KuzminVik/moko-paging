package dev.icerock.moko.paging

/**
 * List state used by Pagination.
 *
 * @param items loaded list of items
 * @param isRefreshing refresh state, use it to show the pull-to-refresh indicator
 * @param isNextPageLoading next-page loading state, use it to show a loader at the end of the list
 * @param isEndOfList indicator that the full list is loaded; when false, do not call onLoadNextPage
 */
data class PagingState<T>(
    val items: List<T>,
    val isRefreshing: Boolean = false,
    val isNextPageLoading: Boolean = false,
    val isEndOfList: Boolean = false
)
