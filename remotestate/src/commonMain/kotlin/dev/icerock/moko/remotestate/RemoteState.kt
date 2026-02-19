package dev.icerock.moko.remotestate

import dev.icerock.moko.remotestate.RemoteState.Error
import dev.icerock.moko.remotestate.RemoteState.Loading
import dev.icerock.moko.remotestate.RemoteState.Success
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Сделано именно class, а не interface, чтобы на iOS стороне можно было hashable сделать реализацию.
 * Также типы сделаны обязательно не нуллабельными тоже для iOS - чтобы нуллы не ожидал компилятор везде.
 */
sealed class RemoteState<out T : Any, out E : Any> {
    data object Loading : RemoteState<Nothing, Nothing>()
    data class Success<T : Any>(val data: T) : RemoteState<T, Nothing>()
    data class Error<E : Any>(
        val error: E,
    ) : RemoteState<Nothing, E>()
}

fun <K : Any, T : Any, E : Any> RemoteState<T, E>.mapSuccess(map: (T) -> K): RemoteState<K, E> {
    return when (this) {
        is Success -> Success(map(this.data))
        is Error -> this
        Loading -> Loading
    }
}

fun <K : Any, T : Any, E : Any> RemoteState<T, E>.mapError(map: (E) -> K): RemoteState<T, K> {
    return when (this) {
        is Success -> this
        is Error -> Error(map(this.error))
        Loading -> Loading
    }
}

fun <T : Any, E : Any> RemoteState<T, E>.isLoading(): Boolean = this is Loading

fun <T : Any, E : Any> RemoteState<T, E>.isSuccess(): Boolean = this is Success

val <T : Any, E : Any> RemoteState<T, E>.data: T? get() = (this as? Success<T>)?.data

/**
 * Выполняет попытку атомарно изменить данные в состоянии RemoteState.Success
 *
 * @param function лямбда получения новых данных из текущих
 * @return true если удалось обновить Success состояние. false - если состояние уже не Success.
 */
fun <T : Any, E : Any> MutableStateFlow<RemoteState<T, E>>.tryUpdateSuccess(
    function: (T) -> T
): Boolean {
    while (true) {
        val currentState: Success<T> =
            this.value as? Success<T> ?: return false

        val newState: Success<T> = currentState.copy(
            data = function(currentState.data)
        )
        if (this.compareAndSet(expect = currentState, update = newState)) return true
    }
}
