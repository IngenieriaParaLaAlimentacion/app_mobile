package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementHistoryPage
import kotlinx.coroutines.flow.Flow

/**
 * Ingresos **and** salidas as one feed. Cache-∪-remote only: unlike the other three history
 * repositories there is no local queue half — food movements are written straight to the network
 * and salidas cannot be created on mobile at all.
 */
interface FoodMovementHistoryRepository {
    /** Reactive floor: both cache types, decoded, merged and globally date-sorted. Populated
     *  offline; this is what the screen actually renders. */
    fun observeLocalMerged(): Flow<List<FoodMovement>>

    /** Fetches page [page] of the server-merged feed in a single request, writes both direction
     *  caches, and reports whether a further page exists. Falls back to the cached page when the
     *  request fails; fails only when the request failed AND nothing is cached — that failure is
     *  what the screen's retry surface acts on. */
    suspend fun getPage(page: Int, pageSize: Int): Result<FoodMovementHistoryPage>
}
