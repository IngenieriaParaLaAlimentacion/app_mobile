package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import ar.com.ingenieriaalimentacion.data.auth.SessionEnded
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun <T> SessionLifecycle.queryForOwner(
    dispatcher: CoroutineDispatcher,
    query: (String) -> T,
): T = local(capture()) { owner ->
    withContext(dispatcher) { query(owner) }
}

internal fun <Row : Any, T> observeForOwner(
    lifecycle: SessionLifecycle,
    dispatcher: CoroutineDispatcher,
    empty: T,
    select: (String) -> Query<Row>,
    read: (Query<Row>) -> T,
): Flow<T> = flow {
    val lease = try {
        lifecycle.capture()
    } catch (_: SessionEnded) {
        emit(empty)
        return@flow
    }
    channelFlow {
        val reads = launch {
            select(lease.ownerId).asFlow().collect { query ->
                send(lifecycle.local(lease) { withContext(dispatcher) { read(query) } })
            }
        }
        lifecycle.status.first { lifecycle.sessionFor(lease) == null }
        reads.cancel()
    }.collect { value ->
        if (lifecycle.sessionFor(lease) != null) emit(value)
    }
}
