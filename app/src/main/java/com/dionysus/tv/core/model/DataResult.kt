package com.dionysus.tv.core.model

/**
 * A lightweight result wrapper for repository/data-source calls so the UI can
 * distinguish loading, success, and error without leaking exceptions upward.
 */
sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : DataResult<Nothing>

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    companion object {
        inline fun <T> catching(block: () -> T): DataResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Error(t.message ?: t::class.simpleName ?: "Unknown error", t)
        }
    }
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Error -> this
}
