/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package com.icerockdev.library

import dev.icerock.moko.mvvm.flow.CStateFlow
import dev.icerock.moko.mvvm.flow.cStateFlow
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import dev.icerock.moko.paging.PageSizePagingDataSource
import dev.icerock.moko.paging.Pagination
import dev.icerock.moko.paging.PagingState
import dev.icerock.moko.paging.RefreshStrategy
import dev.icerock.moko.remotestate.RemoteState
import dev.icerock.moko.remotestate.mapError
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.min

class ListViewModel : ViewModel() {
    private val pagination: Pagination<ProductItem> = Pagination(
        dataSource = PageSizePagingDataSource(
            pageSize = PAGE_SIZE,
            loadPage = ::loadPage
        ),
        itemKey = { item -> item.id },
        refreshStrategy = RefreshStrategy.ReplaceEverything,
        nextPageListener = { result ->
            result.onFailure {
                Napier.e("can't load next page", it)
            }
        },
        refreshListener = { result ->
            result.onFailure {
                Napier.e("can't load refresh", it)
            }
        }
    )

    val state: CStateFlow<RemoteState<PagingState<ProductItem>, Throwable>> =
        pagination.state.map { state ->
            state.mapError { it }
        }.cStateIn(viewModelScope, initValue = RemoteState.Loading)

    fun onStart() {
        viewModelScope.launch {
            pagination.loadFirstPage()
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            if (pagination.state.value is RemoteState.Success<*>) {
                pagination.refresh()
            } else {
                pagination.loadFirstPage()
            }
        }
    }

    fun onLoadNextPage() {
        viewModelScope.launch {
            pagination.loadNextPage()
        }
    }

    private suspend fun loadPage(page: Int, pageSize: Int): List<ProductItem> {
        // delay simulated loading
        delay(REFRESH_DELAY_MS)
        val startIndex = page * pageSize

        if (startIndex >= TOTAL_ITEMS) return emptyList()

        val endIndex = min(startIndex + pageSize, TOTAL_ITEMS)

        return (startIndex until endIndex).map { index ->
            val id = index + 1L
            ProductItem(id = id, title = "Product #$id")
        }
    }

    data class ProductItem(
        val id: Long,
        val title: String
    )

    private companion object {
        const val PAGE_SIZE = 20
        const val TOTAL_ITEMS = 120
        const val REFRESH_DELAY_MS = 300L
    }
}

/**
 * Сокращенный вариант создания CStateFlow из Flow
 */
fun <T> Flow<T>.cStateIn(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.Eagerly,
    initValue: T,
): CStateFlow<T> = this.stateIn(
    scope = scope,
    started = started,
    initialValue = initValue
).cStateFlow()
