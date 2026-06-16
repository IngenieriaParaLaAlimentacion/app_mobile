package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeConnectivityObserver(initial: Boolean = false) : ConnectivityObserver {
    private val online = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = online
    override suspend fun isOnline(): Boolean = online.value
}
