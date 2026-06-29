package ar.com.ingenieriaalimentacion.data.profile

import ar.com.ingenieriaalimentacion.data.profile.dto.RequestEstablishmentsRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.UpdateProfileRequest
import ar.com.ingenieriaalimentacion.data.profile.local.EstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

class ProfileRepositoryImpl(
    private val api: ProfileApi,
    private val local: EstablishmentLocalStore,
) : ProfileRepository {

    override suspend fun getMe(): Result<Profile> =
        runCatching { api.getMe().toDomain() }
            .recoverCatching { throw ProfileFailure(mapProfileError(it), it) }

    override suspend fun updateMe(update: UpdateProfile): Result<Profile> =
        runCatching {
            api.updateMe(UpdateProfileRequest(update.name, update.lastName)).toDomain()
        }.recoverCatching { throw ProfileFailure(mapProfileError(it), it) }

    override suspend fun getMyEstablishments(): Result<List<EstablishmentWithStatus>> {
        // Cache-first: a warm cache wins so the picker works offline.
        val cached = local.getAll()
        if (cached.isNotEmpty()) return Result.success(cached)

        // Cold cache → network. On success, warm the cache; on failure, propagate (the
        // cache is empty here, so there is nothing to fall back to).
        return fetchMyEstablishments()
            .onSuccess { fresh -> local.replaceAll(fresh) }
    }

    override suspend fun refreshMyEstablishments(): Result<Unit> {
        // Best-effort prefetch: overwrite the cache when the network succeeds; stay quiet offline.
        fetchMyEstablishments().onSuccess { fresh -> local.replaceAll(fresh) }
        return Result.success(Unit)
    }

    override suspend fun clearEstablishmentCache() {
        local.clear()
    }

    private suspend fun fetchMyEstablishments(): Result<List<EstablishmentWithStatus>> =
        runCatching { api.getMyEstablishments().map { it.toDomain() } }
            .recoverCatching { t ->
                // Non-managers get 403 here; surface that as "no establishments" to match
                // the previous behaviour of reading a null establishments field off the profile.
                if (t is ClientRequestException && t.response.status == HttpStatusCode.Forbidden) {
                    emptyList()
                } else {
                    throw ProfileFailure(mapProfileError(t), t)
                }
            }

    override suspend fun getAvailableEstablishments(
        search: String?,
        page: Int,
        pageSize: Int,
    ): Result<PagedEstablishments> =
        runCatching {
            api.getAvailableEstablishments(search, page, pageSize).toDomain()
        }.recoverCatching { throw ProfileFailure(mapProfileError(it), it) }

    override suspend fun requestEstablishmentAccess(
        ids: List<String>,
    ): Result<List<EstablishmentWithStatus>> =
        runCatching {
            api.requestEstablishmentAccess(RequestEstablishmentsRequest(ids)).map { it.toDomain() }
        }.recoverCatching { throw EstablishmentRequestFailure(mapRequestError(it), it) }

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
}

class ProfileFailure(val error: ProfileError, cause: Throwable?) :
    Exception(error.toString(), cause)

class EstablishmentRequestFailure(val error: EstablishmentRequestError, cause: Throwable?) :
    Exception(error.toString(), cause)
