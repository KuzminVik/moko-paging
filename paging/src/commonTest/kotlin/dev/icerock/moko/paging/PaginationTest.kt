/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging

import dev.icerock.moko.remotestate.data
import dev.icerock.moko.remotestate.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PaginationTest : BaseTestsClass() {

    var paginationDataSource = TestListDataSource(3, 5)

    @BeforeTest
    fun setup() {
        paginationDataSource = TestListDataSource(3, 5)
    }

    @Test
    fun `load first page`() = runTest {
        val pagination = createPagination()

        pagination.loadFirstPage()

        assertTrue {
            pagination.state.value.isSuccess()
        }
        assertTrue {
            pagination.state.value.data?.items?.compareWith(listOf(0, 1, 2)) == true
        }
    }

    @Test
    fun `load next page`() = runTest {
        val pagination = createPagination()

        pagination.loadFirstPage()
        pagination.loadNextPage()

        assertTrue {
            pagination.state.value.data?.items?.compareWith(listOf(0, 1, 2, 3, 4, 5)) == true
        }

        pagination.loadNextPage()

        assertTrue {
            pagination.state.value.data?.items?.compareWith(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)) == true
        }
    }

    @Test
    fun `refresh pagination`() = runTest {
        val pagination = createPagination()

        pagination.loadFirstPage()
        pagination.loadNextPage()
        pagination.refresh(RefreshStrategy.ReplaceEverything)

        assertTrue {
            pagination.state.value.data?.items?.compareWith(listOf(0, 1, 2)) == true
        }
    }

    @Test
    fun `set data`() = runTest {
        val pagination = createPagination()

        pagination.loadFirstPage()
        pagination.loadNextPage()

        val setList = listOf(5, 2, 3, 1, 4)
        pagination.setData(setList)

        assertTrue {
            pagination.state.value.data?.items?.compareWith(setList) == true
        }
    }

    @Test
    fun `double refresh`() = runTest {
        var counter = 0
        val pagination = Pagination(
            dataSource = object : PagingDataSource<Int> {
                override fun isPageFull(list: List<Int>): Boolean = list.size == 4

                override suspend fun loadPage(currentList: List<Int>?): List<Int> {
                    val load = counter++
                    println("start load new page with $currentList")
                    delay(100)
                    println("respond new list $load")
                    return listOf(1, 2, 3, 4)
                }
            },
            itemKey = { it },
            nextPageListener = { },
            refreshListener = { }
        )

        println("start load first page")
        pagination.loadFirstPage()
        println("end load first page")

        println("start double refresh")
        val r1 = async {
            pagination.refresh()
            println("first refresh end")
        }
        val r2 = async {
            pagination.refresh()
            println("second refresh end")
        }

        r1.await()
        r2.await()
    }

    private fun createPagination(
        nextPageListener: (Result<List<Int>>) -> Unit = {},
        refreshListener: (Result<List<Int>>) -> Unit = {}
    ) = Pagination(
        dataSource = paginationDataSource,
        itemKey = { it },
        nextPageListener = nextPageListener,
        refreshListener = refreshListener
    )
}
