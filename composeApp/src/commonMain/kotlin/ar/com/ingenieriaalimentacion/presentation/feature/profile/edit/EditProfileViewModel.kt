package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.profile.ProfileFailure
import ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.usecase.profile.UpdateMyProfileUseCase
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditProfileViewModel(
    route: Route.EditProfile,
    private val updateMyProfile: UpdateMyProfileUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditProfileState(
            name = route.name,
            lastName = route.lastName,
            email = route.email,
            role = route.role,
            originalName = route.name,
            originalLastName = route.lastName,
        ),
    )
    val state = _state.asStateFlow()

    fun onAction(action: EditProfileAction) {
        when (action) {
            is EditProfileAction.NameChanged ->
                _state.update { it.copy(name = action.value, saveError = null) }

            is EditProfileAction.LastNameChanged ->
                _state.update { it.copy(lastName = action.value, saveError = null) }

            EditProfileAction.SaveClick -> save()
            EditProfileAction.BackClick ->
                _state.update { it.copy(destination = EditProfileDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun save() {
        val current = _state.value
        val update = UpdateProfile(
            name = current.name.takeIf { it != current.originalName },
            lastName = current.lastName.takeIf { it != current.originalLastName },
        )
        if (update.isEmpty) {
            _state.update { it.copy(destination = EditProfileDestination.Back) }
            return
        }

        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            updateMyProfile(update).fold(
                onSuccess = {
                    // Keep isSaving = true until navigation tears down this screen, so the button
                    // doesn't flash back to idle before we pop back to the profile.
                    _state.update { it.copy(destination = EditProfileDestination.Saved) }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = (throwable as? ProfileFailure)?.error ?: ProfileError.Unknown(throwable)
                    _state.update { it.copy(isSaving = false, saveError = error) }
                },
            )
        }
    }
}
