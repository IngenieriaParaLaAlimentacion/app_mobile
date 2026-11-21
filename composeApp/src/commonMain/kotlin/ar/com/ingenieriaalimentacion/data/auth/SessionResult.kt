package ar.com.ingenieriaalimentacion.data.auth

import kotlinx.coroutines.CancellationException

internal suspend fun <T> SessionLifecycle.scopedResult(work: suspend () -> T): Result<T> =
    runCatching { scoped(work) }.onFailure { if (it is CancellationException) throw it }
