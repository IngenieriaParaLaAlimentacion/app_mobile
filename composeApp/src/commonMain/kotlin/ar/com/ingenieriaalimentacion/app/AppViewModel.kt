package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {
    private val _initialized = MutableStateFlow(true)
    val initialized = _initialized.asStateFlow()
}
