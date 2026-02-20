package dev.icerock.moko.paging

/**
 * Data source interface for Pagination.
 */
interface PagingDataSource<Item> {
    /**
     * Checks whether a page is fully loaded (to determine if we've reached the end of the list).
     */
    fun isPageFull(list: List<Item>): Boolean

    /**
     * Loads a page based on the current data.
     *
     * @param currentList already loaded list items
     *
     * @return the next page
     */
    suspend fun loadPage(currentList: List<Item>?): List<Item>
}

/**
 * PagingDataSource implementation for page/pageSize-based pagination.
 *
 * @param pageSize page size
 * @param loadPage suspend method for page-by-page loading
 */
@Suppress("FunctionName")
fun <Item> PageSizePagingDataSource(
    pageSize: Int,
    loadPage: suspend (page: Int, pageSize: Int) -> List<Item>
): PagingDataSource<Item> {
    return object : PagingDataSource<Item> {
        override fun isPageFull(list: List<Item>): Boolean {
            return list.size == pageSize
        }

        override suspend fun loadPage(currentList: List<Item>?): List<Item> {
            val page: Int = calculateNextPage(
                currentListSize = currentList?.size,
                pageSize = pageSize
            )

            return loadPage(page, pageSize)
        }
    }
}
