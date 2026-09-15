package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/**
 * The house `getMyEstablishments()` + `getCurrentEstablishment()` pairing, with the ambiguity
 * removed. [GetCurrentEstablishmentUseCase] stays as it is for Home and the three form screens —
 * they deliberately degrade to "no pre-fill" and must not start branching on a load failure.
 */
class ResolveCurrentEstablishmentUseCase(private val repository: ProfileRepository) {

    suspend operator fun invoke(): CurrentEstablishmentResult =
        repository.getMyEstablishments().fold(
            onSuccess = { all ->
                val approved = all.filter { it.status == EstablishmentStatus.Approved }
                val storedId = repository.getCurrentEstablishmentId()
                    ?: return CurrentEstablishmentResult.NoneSelected(approved)
                approved.firstOrNull { it.id == storedId }
                    ?.let { CurrentEstablishmentResult.Selected(it) }
                    ?: CurrentEstablishmentResult.StaleSelection(approved)
            },
            onFailure = { CurrentEstablishmentResult.LoadFailed(it) },
        )
}
