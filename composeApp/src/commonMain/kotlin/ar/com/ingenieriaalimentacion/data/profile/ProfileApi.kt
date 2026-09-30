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

    // TODO(backend#151): assumed contract — admin direct subscription. Same body as the access
    //  request; returns the new links already approved. 409 when already subscribed.
    suspend fun subscribeToEstablishments(
        body: RequestEstablishmentsRequest,
    ): List<EstablishmentWithStatusResponse> =
        client.post("api/v1/users/me/establishments/subscribe") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    // TODO(backend#151): assumed contract — admin unsubscription. Same body as withdraw; the
    //  backend deletes the user_establishment row instead of marking it withdrawn.
    suspend fun unsubscribeFromEstablishments(body: WithdrawEstablishmentsRequest) {
        client.patch("api/v1/users/me/establishments/unsubscribe") {
            contentType(ContentType.Application.Json)
            setBody(body)
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
