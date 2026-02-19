package dev.icerock.moko.paging

import dev.icerock.moko.remotestate.RemoteState

/**
 * Используйте эту функцию для изменения состояния обновления (isRefreshing) в экземпляре PagingState,
 * не затрагивая другие данные, находящиеся в объекте RemoteState.Success
 */
fun <T> RemoteState.Success<PagingState<T>>.withRefreshing(
    value: Boolean
): RemoteState.Success<PagingState<T>> = this.copy(data = this.data.copy(isRefreshing = value))

/**
 * Используйте эту функцию для изменения состояния обновления (isNextPageLoading) в экземпляре PagingState,
 * не затрагивая другие данные, находящиеся в объекте RemoteState.Success
 */
fun <T> RemoteState.Success<PagingState<T>>.withNextPageLoading(
    value: Boolean
): RemoteState.Success<PagingState<T>> = this.copy(data = this.data.copy(isNextPageLoading = value))
