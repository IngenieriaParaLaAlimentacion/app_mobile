package ar.com.ingenieriaalimentacion.data.network.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Observes device connectivity. This is an *optimization layer* (instant resync on reconnect), not
 * the sync backbone — the periodic foreground tick + exponential backoff guarantee progress even if
 * the observer never fires.
 */
interface ConnectivityObserver {
    /** Distinct-until-changed online/offline stream. */
    val isOnline: Flow<Boolean>

    /** One-shot check of the current connectivity. */
    suspend fun isOnline(): Boolean
}
