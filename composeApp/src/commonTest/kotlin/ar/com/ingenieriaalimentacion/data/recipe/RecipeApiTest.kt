package ar.com.ingenieriaalimentacion.data.recipe

import ar.com.ingenieriaalimentacion.domain.model.recipe.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class RecipeApiTest {
    private val proposal =
        """{"id":"p","name":"Arroz","portions":30,"ingredients":[{"food_id":"rice","name":"Arroz","quantity":"1.234","unit":"kilogramos"}],"steps":["Hervir."],"nutrient_adequacy_percent":{"hierro":52},"macronutrient_percent":{"carbohidratos":80},"gapa_groups":[],"unmet_constraints":[],"warnings":[],"target_energy_kcal":500,"energy_kcal":300,"cultural_identity":null}"""
    private val request =
        """{"id":"r","establishment_id":"e","children_count":30,"meal_type":"lunch","created_at":"2026-10-17T10:00:00Z","proposals":[$proposal],"selection":null,"user_id":"u","prompt_version":"v1"}"""

    private fun api(
        handler:
            suspend MockRequestHandleScope.(
                io.ktor.client.request.HttpRequestData
            ) -> io.ktor.client.request.HttpResponseData
    ) =
        RecipeApi(
            HttpClient(MockEngine(handler)) {
                install(HttpTimeout)
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = true
                        }
                    )
                }
                defaultRequest { url("https://example.test/") }
            }
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `generation sends only three contract fields and preserves decimal strings`() = runTest {
        val repository =
            RecipeRepositoryImpl(
                api { call ->
                    assertEquals("/api/v1/recipe-recommendations", call.url.encodedPath)
                    val body = Json.parseToJsonElement((call.body as TextContent).text).toString()
                    assertEquals(
                        "{\"establishment_id\":\"e\",\"children_count\":30,\"meal_type\":\"lunch\"}",
                        body,
                    )
                    respond(request, HttpStatusCode.Created, jsonHeaders)
                }
            )
        val result = repository.generate("e", 30, MealType.Lunch).getOrThrow()
        assertEquals("1.234", result.proposals.single().ingredients.single().quantity)
        assertEquals(52.0, result.proposals.single().nutrientAdequacyPercent["hierro"])
    }

    @Test
    fun `history is paginated and asks for chosen recipes`() = runTest {
        val result = api { call ->
            assertEquals("true", call.url.parameters["chosen_only"])
            assertEquals("2", call.url.parameters["page"])
            respond(
                """{"items":[$request],"page":2,"total_pages":3}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
            .history("e", 2)
            .getOrThrow()
        assertEquals(1, result.items.size)
    }

    @Test
    fun `both choose creation and replay are successful`() = runTest {
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.OK)) {
            assertTrue(
                api { call ->
                        assertEquals(
                            "/api/v1/recipe-recommendations/r/proposals/p/choose",
                            call.url.encodedPath,
                        )
                        respond("""{"proposal_id":"p","outflow_id":"out"}""", status, jsonHeaders)
                    }
                    .choose("r", "p")
                    .isSuccess
            )
        }
    }

    @Test
    fun `preview does not send a reuse id or choose call`() = runTest {
        val response = api { call ->
            assertTrue(call.url.encodedPath.endsWith("/reuse-preview"))
            assertEquals("{\"children_count\":15}", (call.body as TextContent).text)
            respond(proposal, HttpStatusCode.OK, jsonHeaders)
        }
            .previewReuse("r", "p", 15)
        assertTrue(response.isSuccess)
    }

    @Test
    fun `reuse sends stable id and count`() = runTest {
        val response = api { call ->
            assertTrue(call.url.encodedPath.endsWith("/reuse"))
            assertEquals(
                "{\"id\":\"stable\",\"children_count\":15}",
                (call.body as TextContent).text,
            )
            respond(request, HttpStatusCode.OK, jsonHeaders)
        }
            .reuse("r", "p", ReuseRecipeDto("stable", 15))
        assertTrue(response.isSuccess)
    }

    @Test
    fun `stock conflict preserves exact available and requested quantities`() = runTest {
        val result = api {
            respond(
                """{"type":"/errors/insufficient-stock","food_id":"rice","requested":"2.000","available":"1.234"}""",
                HttpStatusCode.Conflict,
                jsonHeaders,
            )
        }
            .choose("r", "p")
        val failure = assertIs<RecipeFailure>(result.exceptionOrNull())
        assertEquals(RecipeError.InsufficientStock("rice", "2.000", "1.234"), failure.error)
    }

    @Test
    fun `domain errors remain distinguishable`() = runTest {
        for ((type, error) in
            listOf(
                "recipes/no-usable-stock" to RecipeError.NoUsableStock,
                "recipes/already-chosen" to RecipeError.AlreadyChosen,
                "recipes/idempotency-conflict" to RecipeError.IdempotencyConflict,
            )) {
            val result = api {
                respond("""{"type":"/errors/$type"}""", HttpStatusCode.Conflict, jsonHeaders)
            }
                .choose("r", "p")
            assertEquals(error, assertIs<RecipeFailure>(result.exceptionOrNull()).error)
        }
    }

    @Test
    fun `cancelled request propagates cancellation`() = runTest {
        assertFailsWith<CancellationException> {
            api { throw CancellationException("cancel") }.history("e", 1)
        }
    }

    @Test
    fun `auth validation and server errors map to stable domain failures`() = runTest {
        for ((status, expected) in
            listOf(
                HttpStatusCode.Unauthorized to RecipeError.Unauthorized,
                HttpStatusCode.Forbidden to RecipeError.AccessDenied,
                HttpStatusCode.NotFound to RecipeError.NotFound,
                HttpStatusCode.UnprocessableEntity to RecipeError.InvalidInput,
                HttpStatusCode.InternalServerError to RecipeError.Network,
            )) {
            val result = api { respond("{}", status, jsonHeaders) }.choose("r", "p")
            assertEquals(expected, assertIs<RecipeFailure>(result.exceptionOrNull()).error)
        }
    }

    @Test
    fun `AI errors preserve canonical discriminator without retry`() = runTest {
        val cases = listOf(
            Triple(HttpStatusCode.TooManyRequests, "/errors/ai/quota-exhausted", RecipeError.AiQuotaExhausted),
            Triple(HttpStatusCode.TooManyRequests, "/errors/rate-limited", RecipeError.RateLimited),
            Triple(HttpStatusCode.ServiceUnavailable, "/errors/ai/unavailable", RecipeError.AiUnavailable),
            Triple(HttpStatusCode.TooManyRequests, "/errors/unknown", RecipeError.Network),
            Triple(HttpStatusCode.ServiceUnavailable, "/errors/unknown", RecipeError.Network),
            Triple(HttpStatusCode.BadGateway, "/errors/recipes/generation-failed", RecipeError.GenerationFailed),
        )
        for ((status, type, expected) in cases) {
            var calls = 0
            val result = api {
                calls++
                respond("""{"type":"$type","detail":"private provider prose"}""", status,
                    headersOf(HttpHeaders.ContentType to listOf("application/problem+json"),
                        HttpHeaders.RetryAfter to listOf("12")))
            }.generate(GenerateRecipeDto("e", 30, "lunch"))
            assertEquals(expected, assertIs<RecipeFailure>(result.exceptionOrNull()).error)
            assertEquals(1, calls)
        }
    }

    @Test
    fun `malformed AI error body keeps generic fallback`() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)) {
            val result = api { respond("not json", status, jsonHeaders) }
                .generate(GenerateRecipeDto("e", 30, "lunch"))
            assertEquals(RecipeError.Network, assertIs<RecipeFailure>(result.exceptionOrNull()).error)
        }
    }

    @Test
    fun `cancellation while reading AI problem propagates`() = runTest {
        val channel = io.ktor.utils.io.ByteChannel()
        channel.cancel(CancellationException("cancelled body"))
        assertFailsWith<CancellationException> {
            api { respond(channel, HttpStatusCode.TooManyRequests, jsonHeaders) }
                .generate(GenerateRecipeDto("e", 30, "lunch"))
        }
    }

}
