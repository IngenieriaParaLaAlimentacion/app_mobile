package ar.com.ingenieriaalimentacion.data.profile

import ar.com.ingenieriaalimentacion.data.profile.dto.EstablishmentAbsenceRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.EstablishmentWithStatusResponse
import ar.com.ingenieriaalimentacion.data.profile.dto.PaginatedAvailableResponse
import ar.com.ingenieriaalimentacion.data.profile.dto.ProfileResponse
import ar.com.ingenieriaalimentacion.data.profile.dto.RequestEstablishmentsRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.UpdateProfileRequest
import ar.com.ingenieriaalimentacion.data.profile.dto.WithdrawEstablishmentsRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Own-profile + available-catalog endpoints. All require an authenticated user —
 * uses the default authed [HttpClient] (no Koin qualifier), like [ar.com.ingenieriaalimentacion.data.subject.SubjectApi].
 */
class ProfileApi(private val client: HttpClient) {

    suspend fun getMe(): ProfileResponse =
        client.get("api/v1/users/me").body()

    suspend fun updateMe(body: UpdateProfileRequest): ProfileResponse =
        client.patch("api/v1/users/me") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun getAvailableEstablishments(
        search: String?,
        page: Int,
        pageSize: Int,
    ): PaginatedAvailableResponse =
        client.get("api/v1/establishments") {
            parameter("available", true)
            parameter("page", page)
            parameter("page_size", pageSize)
            search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
        }.body()

    suspend fun getMyEstablishments(): List<EstablishmentWithStatusResponse> =
        client.get("api/v1/users/me/establishments").body()

    suspend fun requestEstablishmentAccess(
        body: RequestEstablishmentsRequest,
    ): List<EstablishmentWithStatusResponse> =
        client.post("api/v1/users/me/establishments") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun withdrawEstablishmentAccess(
        body: WithdrawEstablishmentsRequest,
    ): List<EstablishmentWithStatusResponse> =
        client.patch("api/v1/users/me/establishments/withdraw") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    /**
     * Admin-only (admin/superadmin). 201 with the links in request order, all approved.
     * Idempotent (already-subscribed ids are skipped) and all-or-nothing (404 if any id is unknown).
     */
    suspend fun subscribeToEstablishments(
        body: RequestEstablishmentsRequest,
    ): List<EstablishmentWithStatusResponse> =
        client.post("api/v1/users/me/establishments/subscriptions") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    /** Admin-only. 204; the links are deleted (not marked withdrawn). Unlinked ids are skipped. */
    suspend fun unsubscribeFromEstablishments(establishmentIds: List<String>) {
        client.delete("api/v1/users/me/establishments/subscriptions") {
            establishmentIds.forEach { parameter("establishment_ids", it) }
        }
    }

    /** 201 on success; the created request's body is irrelevant to the app. */
    suspend fun notifyEstablishmentAbsence(body: EstablishmentAbsenceRequest) {
        client.post("api/v1/establishments/requests") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}
