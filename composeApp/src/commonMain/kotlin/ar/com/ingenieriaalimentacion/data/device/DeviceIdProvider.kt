package ar.com.ingenieriaalimentacion.data.device

import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Returns the per-install device id, generating + persisting a UUID v4 on first call.
 *
 * Mutex-guarded so two concurrent first-launch callers cannot mint two different ids.
 * Backend uses `(user_id, device_id)` to scope refresh tokens — see auth contract.
 */
@OptIn(ExperimentalUuidApi::class)
class DeviceIdProvider(private val store: DeviceIdStore) {

    private val mutex = Mutex()

    @Volatile
    private var cached: String? = null

    suspend fun getOrCreate(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            val existing = store.get()
            if (existing != null) {
                cached = existing
                existing
            } else {
                val fresh = Uuid.random().toString()
                store.set(fresh)
                cached = fresh
                fresh
            }
        }
    }
}
