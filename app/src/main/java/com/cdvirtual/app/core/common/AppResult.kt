package com.cdvirtual.app.core.common

/** Resultado padrão para operações que podem falhar (importação, processamento, I/O). */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
