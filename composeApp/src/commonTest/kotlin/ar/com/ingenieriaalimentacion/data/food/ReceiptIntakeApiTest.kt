package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodLineRequest
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.InternalAPI
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalAPI::class)
class ReceiptIntakeApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val photo = ReceiptPhoto(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1, 2),
        "image/jpeg", "recibo.jpg")
    private val request = FoodIntakeRequest("intake-1", "e1", "2026-09-06T12:00:00Z", null,
        "photo", listOf(FoodLineRequest("food-1", "1.250")))

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ReceiptIntakeApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
            defaultRequest { url("https://api.example.com/") }
        }
        return ReceiptIntakeApi(client, json)
    }

    private fun problemHeaders() = headersOf(HttpHeaders.ContentType, "application/problem+json")
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun multipartCarriesOriginalBytesAndExpectedFields() = runTest {
        val captures = mutableListOf<List<PartData>>()
        val api = api { req ->
            captures += (req.body as MultiPartFormDataContent).parts
            if (req.url.encodedPath.endsWith("/confirm")) respond("{}", HttpStatusCode.Created, jsonHeaders())
            else respond("""{"establishment_id":"e1","lines":[]}""", HttpStatusCode.OK, jsonHeaders())
        }
        api.propose("e1", photo)
        api.confirm(request, photo)
        assertEquals(2, captures.size)
        assertEquals("e1", (captures[0][0] as PartData.FormItem).value)
        val firstImage = captures[0][1] as PartData.BinaryItem
        val secondImage = captures[1][1] as PartData.BinaryItem
        assertContentEquals(photo.bytes, firstImage.provider().readByteArray())
        assertContentEquals(photo.bytes, secondImage.provider().readByteArray())
        assertTrue(firstImage.headers[HttpHeaders.ContentDisposition]!!.contains("name=image"))
        assertEquals("image/jpeg", firstImage.headers[HttpHeaders.ContentType])
        val data = (captures[1][0] as PartData.FormItem).value
        assertTrue(data.contains("\"source\":\"photo\""))
        assertTrue(data.contains("\"quantity\":1.250"))
    }

    @Test fun acceptsIdempotentReplay() = runTest {
        api { respond("{}", HttpStatusCode.OK, jsonHeaders()) }.confirm(request, photo)
    }

    @Test fun mapsProblemTypesAndStatuses() = runTest {
        val cases = listOf(
            Triple(HttpStatusCode.UnprocessableEntity, "/errors/image-too-large", ReceiptError.TooLarge),
            Triple(HttpStatusCode.UnprocessableEntity, "/errors/image-content-mismatch", ReceiptError.MismatchedContent),
            Triple(HttpStatusCode.Forbidden, "", ReceiptError.AccessDenied),
            Triple(HttpStatusCode.Unauthorized, "", ReceiptError.Unauthorized),
            Triple(HttpStatusCode.NotFound, "/errors/food-not-found", ReceiptError.FoodNotFound),
            Triple(HttpStatusCode.BadGateway, "", ReceiptError.Retryable),
        )
        for ((status, type, expected) in cases) {
            val client = api { respond("""{"type":"$type"}""", status, problemHeaders()) }
            assertEquals(expected, assertFailsWith<ReceiptFailure> { client.confirm(request, photo) }.kind)
        }
    }

    @Test fun cancellationPropagates() = runTest {
        val client = api { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { client.propose("e1", photo) }
    }
}
