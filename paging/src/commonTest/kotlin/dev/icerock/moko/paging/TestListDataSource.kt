/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging

class TestListDataSource(val pageSize: Int, val totalPagesCount: Int) : PagingDataSource<Int> {
    private val dataList = (0 until pageSize * totalPagesCount).toList()

    override fun isPageFull(list: List<Int>): Boolean = list.size == pageSize

    override suspend fun loadPage(currentList: List<Int>?): List<Int> {
        val offset = currentList?.size ?: 0
        val endIndex = (offset + pageSize).coerceAtMost(dataList.size)

        return dataList.subList(offset, endIndex)
    }
}
