/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging.utils

import dev.icerock.moko.paging.PagingState
import dev.icerock.moko.state.RemoteState

/**
 * Use this function to change the refresh state (isRefreshing) in a PagingState instance
 * without touching other data stored in RemoteState.Success.
 */
fun <T> RemoteState.Success<PagingState<T>>.withRefreshing(
    value: Boolean
): RemoteState.Success<PagingState<T>> = this.copy(data = this.data.copy(isRefreshing = value))

/**
 * Use this function to change the next-page loading state (isNextPageLoading) in a PagingState instance
 * without touching other data stored in RemoteState.Success.
 */
fun <T> RemoteState.Success<PagingState<T>>.withNextPageLoading(
    value: Boolean
): RemoteState.Success<PagingState<T>> = this.copy(data = this.data.copy(isNextPageLoading = value))
