package ar.com.ingenieriaalimentacion.data.profile

import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.ValidationProblem
import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toDto
import ar.com.ingenieriaalimentacion.data.profile.dto.EstablishmentAbsenceRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.RequestEstablishmentsRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.UpdateProfileRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.WithdrawEstablishmentsRequest
import ar.com.ingenieriaalimentacion.data.profile.local.CurrentEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.local.EstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

class ProfileRepositoryImpl(
    private val api: ProfileApi,
    private val local: EstablishmentLocalStore,
    private val currentEstablishment: CurrentEstablishmentLocalStore,
    private val lifecycle: SessionLifecycle,
) : ProfileRepository {

    override suspend fun getMe(): Result<Profile> =
        runCatching { api.getMe().toDomain() }
            .mapFailure { ProfileFailure(mapProfileError(it), it) }

    override suspend fun updateMe(update: UpdateProfile): Result<Profile> =
        runCatching {
            api.updateMe(UpdateProfileRequest(update.name, update.lastName)).toDomain()
        }.mapFailure { ProfileFailure(mapProfileError(it), it) }

    override suspend fun getMyEstablishments(): Result<List<EstablishmentWithStatus>> = lifecycle.scoped {
        // Cache-first: a warm cache wins so the picker works offline.
        val cached = local.getAll()
        if (cached.isNotEmpty()) return@scoped Result.success(cached)

        // Cold cache → network. On success, warm the cache; on failure, propagate (the
        // cache is empty here, so there is nothing to fall back to).
        return@scoped fetchMyEstablishments()
            .onSuccess { fresh -> local.replaceAll(fresh) }
    }

    override suspend fun refreshMyEstablishments(): Result<Unit> = lifecycle.scoped {
        // Best-effort prefetch: overwrite the cache when the network succeeds; stay quiet offline.
        fetchMyEstablishments().onSuccess { fresh -> local.replaceAll(fresh) }
        return@scoped Result.success(Unit)
    }

    override suspend fun clearEstablishmentCache() {
        local.clear()
    }

    override suspend fun getCurrentEstablishmentId(): String? = currentEstablishment.get()

    override suspend fun setCurrentEstablishmentId(establishmentId: String?) {
        currentEstablishment.set(establishmentId)
    }

    private suspend fun fetchMyEstablishments(): Result<List<EstablishmentWithStatus>> =
        runCatching { api.getMyEstablishments().map { it.toDomain() } }
            .fold(onSuccess = { Result.success(it) }, onFailure = { t ->
                if (t is CancellationException) throw t
                // Non-managers get 403 here; surface that as "no establishments" to match
                // the previous behaviour of reading a null establishments field off the profile.
                if (t is ClientRequestException && t.response.status == HttpStatusCode.Forbidden) {
                    Result.success(emptyList())
                } else {
                    Result.failure(ProfileFailure(mapProfileError(t), t))
                }
            })

    override suspend fun getAvailableEstablishments(
        search: String?,
        page: Int,
        pageSize: Int,
    ): Result<PagedEstablishments> =
        runCatching {
            api.getAvailableEstablishments(search, page, pageSize).toDomain()
        }.mapFailure { ProfileFailure(mapProfileError(it), it) }

    override suspend fun requestEstablishmentAccess(
        ids: List<String>,
    ): Result<List<EstablishmentWithStatus>> = lifecycle.scoped {
        runCatching {
            api.requestEstablishmentAccess(RequestEstablishmentsRequest(ids)).map { it.toDomain() }
        }.mapFailure { EstablishmentRequestFailure(mapRequestError(it), it) }
            .onSuccess { created -> mergeIntoCache(created) }
    }

    override suspend fun subscribeToEstablishments(
        ids: List<String>,
    ): Result<List<EstablishmentWithStatus>> = lifecycle.scoped {
        runCatching {
            api.subscribeToEstablishments(RequestEstablishmentsRequest(ids)).map { it.toDomain() }
        }.mapFailure { EstablishmentRequestFailure(mapRequestError(it), it) }
            .onSuccess { subscribed -> mergeIntoCache(subscribed) }
    }

    override suspend fun withdrawFromEstablishment(establishmentId: String): Result<Unit> = lifecycle.scoped {
        runCatching {
            api.withdrawEstablishmentAccess(WithdrawEstablishmentsRequest(listOf(establishmentId)))
        }.mapFailure { EstablishmentWithdrawalFailure(mapWithdrawalError(it), it) }
            .onSuccess { forgetEstablishment(establishmentId) }
            .map { }
    }

    override suspend fun unsubscribeFromEstablishments(ids: List<String>): Result<Unit> = lifecycle.scoped {
        runCatching { api.unsubscribeFromEstablishments(ids) }
            .mapFailure { EstablishmentWithdrawalFailure(mapWithdrawalError(it), it) }
            .onSuccess { ids.forEach { id -> forgetEstablishment(id) } }
    }

    /**
     * Upserts freshly created links into the cache. [getMyEstablishments] is cache-first, so
     * without this a new request or subscription would not show until the next refresh.
     */
    private suspend fun mergeIntoCache(created: List<EstablishmentWithStatus>) {
        val createdIds = created.map { it.id }.toSet()
        local.update { existing -> existing.filterNot { it.id in createdIds } + created }
    }

    /** Drops a link the user left, and deselects it if it was the current establishment. */
    private suspend fun forgetEstablishment(establishmentId: String) {
        local.update { existing -> existing.filterNot { it.id == establishmentId } }
        currentEstablishment.clearIfSelected(establishmentId)
    }

    override suspend fun notifyEstablishmentAbsence(
        notice: EstablishmentAbsenceNotice,
    ): Result<Unit> =
        runCatching {
            api.notifyEstablishmentAbsence(
                EstablishmentAbsenceRequest(
                    name = notice.name,
                    formattedAddress = notice.formattedAddress,
                    city = notice.city,
                    province = notice.province,
                    characterization = notice.characterization?.toDto(),
                ),
            )
        }.mapFailure { EstablishmentAbsenceFailure(mapAbsenceError(it), it) }

    private inline fun <T> Result<T>.mapFailure(map: (Throwable) -> Exception): Result<T> {
        val failure = exceptionOrNull() ?: return this
        if (failure is CancellationException) throw failure
        return Result.failure(map(failure))
    }

    private fun mapProfileError(t: Throwable): ProfileError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.Unauthorized -> ProfileError.Unauthorized
            HttpStatusCode.NotFound -> ProfileError.UserNotFound
            HttpStatusCode.UnprocessableEntity -> ProfileError.ValidationError
            else -> ProfileError.Network
        }
        is ResponseException -> ProfileError.Network
        else -> ProfileError.Unknown(t)
    }

    private fun mapRequestError(t: Throwable): EstablishmentRequestError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.Unauthorized -> EstablishmentRequestError.Unauthorized
            HttpStatusCode.Forbidden -> EstablishmentRequestError.Forbidden
            HttpStatusCode.NotFound -> EstablishmentRequestError.EstablishmentNotFound
            else -> EstablishmentRequestError.Network
        }
        is ResponseException -> EstablishmentRequestError.Network
        else -> EstablishmentRequestError.Unknown(t)
    }

    private fun mapWithdrawalError(t: Throwable): EstablishmentWithdrawalError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.Unauthorized -> EstablishmentWithdrawalError.Unauthorized
            HttpStatusCode.Forbidden -> EstablishmentWithdrawalError.Forbidden
            HttpStatusCode.NotFound -> EstablishmentWithdrawalError.EstablishmentNotFound
            HttpStatusCode.Conflict -> EstablishmentWithdrawalError.NotWithdrawable
            else -> EstablishmentWithdrawalError.Network
        }
        is ResponseException -> EstablishmentWithdrawalError.Network
        else -> EstablishmentWithdrawalError.Unknown(t)
    }

    private suspend fun mapAbsenceError(t: Throwable): EstablishmentAbsenceError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.Unauthorized -> EstablishmentAbsenceError.Unauthorized
            HttpStatusCode.Forbidden -> EstablishmentAbsenceError.Forbidden
            HttpStatusCode.BadRequest,
            HttpStatusCode.UnprocessableEntity,
            -> EstablishmentAbsenceError.Validation(
                field = runCatching { t.response.body<ValidationProblem>() }.getOrNull()?.field,
            )

            else -> EstablishmentAbsenceError.Network
        }
        is ResponseException -> EstablishmentAbsenceError.Network
        else -> EstablishmentAbsenceError.Unknown(t)
    }
}

class ProfileFailure(val error: ProfileError, cause: Throwable?) :
    Exception(error.toString(), cause)

class EstablishmentRequestFailure(val error: EstablishmentRequestError, cause: Throwable?) :
    Exception(error.toString(), cause)

class EstablishmentAbsenceFailure(val error: EstablishmentAbsenceError, cause: Throwable?) :
    Exception(error.toString(), cause)

class EstablishmentWithdrawalFailure(val error: EstablishmentWithdrawalError, cause: Throwable?) :
    Exception(error.toString(), cause)
