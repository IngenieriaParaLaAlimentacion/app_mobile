package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val getMyProfile: GetMyProfileUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.Load -> load()
            ProfileAction.RetryClick -> load()
            ProfileAction.EditClick -> emitEdit()
            ProfileAction.MyEstablishmentsClick ->
                _state.update { it.copy(destination = ProfileDestination.MyEstablishments) }
            ProfileAction.SignOutClick ->
                _state.update { it.copy(showSignOutConfirm = true) }
            ProfileAction.ConfirmSignOut -> performSignOut()
            ProfileAction.DismissSignOutConfirm -> dismissSignOutConfirm()
        }
    }

    /** Re-fetches the profile; used on tab resume so edits/requests reflect immediately. */
    fun refresh() = load()

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun emitEdit() {
        val profile = _state.value.profile ?: return
        _state.update {
            it.copy(
                destination = ProfileDestination.EditProfile(
                    name = profile.name,
                    lastName = profile.lastName,
                    email = profile.email,
                    role = profile.role,
                ),
            )
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = false) }
        viewModelScope.launch {
            getMyProfile().fold(
                onSuccess = { profile ->
                    _state.update { it.copy(profile = profile, isLoading = false, loadError = false) }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.update { it.copy(isLoading = false, loadError = true) }
                },
            )
        }
    }

    private fun dismissSignOutConfirm() {
        if (_state.value.isSigningOut) return
        _state.update { it.copy(showSignOutConfirm = false) }
    }

    private fun performSignOut() {
        if (_state.value.isSigningOut) return
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            signOut()
            _state.update {
                it.copy(
                    isSigningOut = false,
                    showSignOutConfirm = false,
                    destination = ProfileDestination.LoggedOut,
                )
            }
        }
    }
}
