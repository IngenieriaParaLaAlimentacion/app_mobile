package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.FOOD_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeProblem
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeRequest
import ar.com.ingenieriaalimentacion.data.food.dto.ReceiptProposalDto
import ar.com.ingenieriaalimentacion.data.network.AI_QUOTA_EXHAUSTED_TYPE
import ar.com.ingenieriaalimentacion.data.network.AI_UNAVAILABLE_TYPE
import ar.com.ingenieriaalimentacion.data.network.RATE_LIMITED_TYPE
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReceiptIntakeApi(private val client: HttpClient, private val json: Json) {
    suspend fun propose(establishmentId: String, photo: ReceiptPhoto): ReceiptProposalDto {
        val response = client.post("api/v1/food-intakes/from-photo") {
            timeout { requestTimeoutMillis = 90_000; socketTimeoutMillis = 90_000 }
            setBody(MultiPartFormDataContent(formData {
                append("establishment_id", establishmentId)
                append("image", photo.bytes, imageHeaders(photo))
            }))
        }
        if (response.status.value != 200) throw response.toReceiptFailure()
        return response.body()
    }

    suspend fun confirm(request: FoodIntakeRequest, photo: ReceiptPhoto) {
        val response = client.post("api/v1/food-intakes/from-photo/confirm") {
            timeout { requestTimeoutMillis = 90_000; socketTimeoutMillis = 90_000 }
            setBody(MultiPartFormDataContent(formData {
                append("data", json.encodeToString(FoodIntakeRequest.serializer(), request))
                append("image", photo.bytes, imageHeaders(photo))
            }))
        }
        if (response.status.value != 200 && response.status.value != 201) {
            throw response.toReceiptFailure()
        }
    }

    private fun imageHeaders(photo: ReceiptPhoto) = Headers.build {
        append(HttpHeaders.ContentType, photo.contentType)
        append(HttpHeaders.ContentDisposition, "filename=\"${photo.fileName}\"")
    }

    private suspend fun HttpResponse.toReceiptFailure(): ReceiptFailure {
        val type = try {
            body<FoodIntakeProblem>().type
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val kind = when (status.value) {
            429 -> when (type) {
                AI_QUOTA_EXHAUSTED_TYPE -> ReceiptError.AiQuotaExhausted
                RATE_LIMITED_TYPE -> ReceiptError.RateLimited
                else -> ReceiptError.Retryable
            }
            503 -> if (type == AI_UNAVAILABLE_TYPE) ReceiptError.AiUnavailable
                else ReceiptError.Retryable
            401 -> ReceiptError.Unauthorized
            403 -> ReceiptError.AccessDenied
            404 -> if (type == FOOD_NOT_FOUND_TYPE) ReceiptError.FoodNotFound else ReceiptError.EstablishmentNotFound
            400, 422 -> when (type) {
                "/errors/unsupported-image-type" -> ReceiptError.InvalidImage
                "/errors/image-content-mismatch" -> ReceiptError.MismatchedContent
                "/errors/image-too-large" -> ReceiptError.TooLarge
                else -> ReceiptError.InvalidData
            }
            else -> ReceiptError.Retryable
        }
        return ReceiptFailure(kind)
    }
}
