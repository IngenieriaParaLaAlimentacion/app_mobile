package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

data class MyEstablishmentsState(
    val destination: MyEstablishmentsDestination? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    /** Only the user's accepted (approved) establishments. */
    val establishments: List<EstablishmentWithStatus> = emptyList(),
)
