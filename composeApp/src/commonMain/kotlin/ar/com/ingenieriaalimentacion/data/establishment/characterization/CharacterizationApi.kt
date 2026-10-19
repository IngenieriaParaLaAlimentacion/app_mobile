package ar.com.ingenieriaalimentacion.data.establishment.characterization

import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.CharacterizationDashboardResponse
import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.CharacterizationReplaceResponse
import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.EstablishmentCharacterizationDto
import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.ValidationProblem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Establishment characterization endpoints. Same status-branching convention as
 * [ar.com.ingenieriaalimentacion.data.food.FoodIntakeApi]: the authed client does not set
 * `expectSuccess`, so Ktor never throws on a non-2xx and we branch on the status explicitly.
 *
 * There is no dedicated read endpoint: the full profile comes with the establishment dashboard,
 * which a field worker can open once their access is approved.
 */
class CharacterizationApi(private val client: HttpClient) {

    suspend fun get(establishmentId: String): CharacterizationOutcome<EstablishmentCharacterizationDto> =
        call({ client.get("api/v1/establishments/$establishmentId/dashboard") }) { response ->
            // An unanswered profile may still come back as null from an older backend.
            response.body<CharacterizationDashboardResponse>().characterization
                ?: EstablishmentCharacterizationDto()
        }

    suspend fun replace(
        establishmentId: String,
        body: EstablishmentCharacterizationDto,
    ): CharacterizationOutcome<String> =
        call({
            client.put("api/v1/establishments/$establishmentId/characterization") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }) { response -> response.body<CharacterizationReplaceResponse>().characterizationStatus }

    private suspend fun <T> call(
        request: suspend () -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> T,
    ): CharacterizationOutcome<T> = runCatching {
        val response = request()
        when (response.status.value) {
            200 -> CharacterizationOutcome.Success(onSuccess(response))
            // The OpenAPI contract says 400; FastAPI answers 422 at runtime. Same meaning.
            400, 422 -> CharacterizationOutcome.InvalidField(response.invalidField())
            401 -> CharacterizationOutcome.Unauthorized
            403 -> CharacterizationOutcome.Forbidden
            404 -> CharacterizationOutcome.NotFound
            else -> CharacterizationOutcome.Network(null)        // 5xx
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        CharacterizationOutcome.Network(t)                         // transport failures
    }

    private suspend fun HttpResponse.invalidField(): String? =
        runCatching { body<ValidationProblem>() }.getOrNull()?.field
}

sealed interface CharacterizationOutcome<out T> {
    data class Success<T>(val value: T) : CharacterizationOutcome<T>
    data class InvalidField(val field: String?) : CharacterizationOutcome<Nothing>
    data object Unauthorized : CharacterizationOutcome<Nothing>
    data object Forbidden : CharacterizationOutcome<Nothing>
    data object NotFound : CharacterizationOutcome<Nothing>
    data class Network(val cause: Throwable?) : CharacterizationOutcome<Nothing>
}
