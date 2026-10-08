package com.example.terminalmodule.model

sealed interface TerminalResult<out T> {
    data class Success<out T>(val value: T) : TerminalResult<T>
    data class Failure(val error: TerminalError) : TerminalResult<Nothing>
}

inline fun <T, R> TerminalResult<T>.map(transform: (T) -> R): TerminalResult<R> = when (this) {
    is TerminalResult.Success -> TerminalResult.Success(transform(value))
    is TerminalResult.Failure -> this
}

inline fun <T> TerminalResult<T>.onSuccess(block: (T) -> Unit): TerminalResult<T> {
    if (this is TerminalResult.Success) block(value)
    return this
}

inline fun <T> TerminalResult<T>.onFailure(block: (TerminalError) -> Unit): TerminalResult<T> {
    if (this is TerminalResult.Failure) block(error)
    return this
}

fun <T> TerminalResult<T>.getOrNull(): T? = (this as? TerminalResult.Success)?.value
