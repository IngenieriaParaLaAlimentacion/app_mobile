package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import ar.com.ingenieriaalimentacion.domain.model.auth.EstablishmentLinkMode
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError

data class MyEstablishmentsState(
    val mode: EstablishmentLinkMode = EstablishmentLinkMode.RequestAccess,
    val destination: MyEstablishmentsDestination? = null,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    /** The user's live links (approved, pending, rejected); withdrawn/revoked are filtered out. */
    val establishments: List<EstablishmentWithStatus> = emptyList(),
    /** The establishment the "darse de baja" confirm dialog is currently open for. */
    val pendingWithdraw: EstablishmentWithStatus? = null,
    val isWithdrawing: Boolean = false,
    val successMessage: String? = null,
    val withdrawError: EstablishmentWithdrawalError? = null,
    /** True once a link was added or removed here, so the parent shell refreshes Home. */
    val establishmentsChanged: Boolean = false,
)
