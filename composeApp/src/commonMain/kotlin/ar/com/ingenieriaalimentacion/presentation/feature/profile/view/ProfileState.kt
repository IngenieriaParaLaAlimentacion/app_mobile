package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

import ar.com.ingenieriaalimentacion.domain.model.profile.Profile

data class ProfileState(
    val destination: ProfileDestination? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val profile: Profile? = null,
    val showSignOutConfirm: Boolean = false,
    val isSigningOut: Boolean = false,
)
