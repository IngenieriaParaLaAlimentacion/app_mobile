package ar.com.ingenieriaalimentacion.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-level [CoroutineScope] that outlives any ViewModel. The sync engine runs here so
 * pending uploads keep syncing across screen/ViewModel lifecycles.
 */
class AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
