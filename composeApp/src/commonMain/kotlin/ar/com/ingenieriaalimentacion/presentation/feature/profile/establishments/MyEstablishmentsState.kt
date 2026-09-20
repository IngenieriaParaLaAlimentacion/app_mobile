package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError

data class MyEstablishmentsState(
    val destination: MyEstablishmentsDestination? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    /** Only the user's accepted (approved) establishments. */
    val establishments: List<EstablishmentWithStatus> = emptyList(),
    /** The establishment the "darse de baja" confirm dialog is currently open for. */
    val pendingWithdraw: EstablishmentWithStatus? = null,
    val isWithdrawing: Boolean = false,
    val successMessage: String? = null,
    val withdrawError: EstablishmentWithdrawalError? = null,
)
