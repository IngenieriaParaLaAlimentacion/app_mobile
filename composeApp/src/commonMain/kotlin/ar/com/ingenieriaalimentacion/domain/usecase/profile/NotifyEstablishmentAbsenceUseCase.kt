package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

/**
 * Sends a notice that an establishment is missing from the catalog. Normalizes
 * the fields before submitting: values are trimmed and blank ones become `null`
 * so the backend never receives empty strings (its validation rejects them).
 */
class NotifyEstablishmentAbsenceUseCase(private val repository: ProfileRepository) {

    suspend operator fun invoke(notice: EstablishmentAbsenceNotice): Result<Unit> =
        repository.notifyEstablishmentAbsence(notice.normalized())

    private fun EstablishmentAbsenceNotice.normalized() = EstablishmentAbsenceNotice(
        name = name.normalized(),
        formattedAddress = formattedAddress.normalized(),
        city = city.normalized(),
        province = province.normalized(),
    )

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
