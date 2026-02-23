/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

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
     * @return the next page
     */
    suspend fun loadPage(currentList: List<Item>?): List<Item>
}

/**
 * PagingDataSource implementation for page/pageSize-based pagination.
 *
 * @param pageSize page size
 * @param calculateNextPage returns the number of next page
 * @param loadPage suspend method for page-by-page loading
 */
@Suppress("FunctionName")
fun <Item> PageSizePagingDataSource(
    pageSize: Int,
    calculateNextPage: (List<Item>?) -> Int,
    loadPage: suspend (page: Int, pageSize: Int) -> List<Item>
): PagingDataSource<Item> {
    return object : PagingDataSource<Item> {
        override fun isPageFull(list: List<Item>): Boolean {
            return list.size == pageSize
        }

        override suspend fun loadPage(currentList: List<Item>?): List<Item> {
            val nextPage: Int = calculateNextPage(currentList)

            return loadPage(nextPage, pageSize)
        }
    }
}
