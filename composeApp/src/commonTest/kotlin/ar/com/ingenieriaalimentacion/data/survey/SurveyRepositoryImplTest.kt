package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyScope
import ar.com.ingenieriaalimentacion.fake.FakeSurveyLocalStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurveyRepositoryImplTest {

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SurveyApi {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://api.example.com/") }
        }
        return SurveyApi(client)
    }

    private fun remoteSurvey(id: String, name: String, isActive: Boolean = true): String = """
        {"id":"$id","name":"$name","scope":"generic","is_active":$isActive,
         "created_by_id":"user-1","question_count":3,"created_at":"2026-01-01T00:00:00Z"}
    """.trimIndent()

    private fun page(items: String, page: Int = 1, totalPages: Int = 1): String = """
        { "items": [$items], "total": 1, "page": $page, "page_size": 20, "total_pages": $totalPages }
    """.trimIndent()

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun respondPage(scope: MockRequestHandleScope, body: String) =
        scope.respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders())

    private fun surveyDetail(id: String): String = """
        {"id":"$id","name":"Detail $id","scope":"generic","is_active":true,"questions":[]}
    """.trimIndent()

    @Test
    fun `online success returns the live page and upserts summaries`() = runTest {
        val store = FakeSurveyLocalStore()
        val api = api { respondPage(this, page(remoteSurvey("s1", "Survey One"))) }

        val result = SurveyRepositoryImpl(api, store).getAvailable(page = 1, pageSize = 20)

        val paged = result.getOrThrow()
        assertEquals(listOf("s1"), paged.items.map { it.id })
        assertEquals(false, paged.fromCache)
        assertEquals(listOf("s1"), store.getSummaries().map { it.id })
    }

    @Test
    fun `page-1 network failure with non-empty cache serves cached active surveys`() = runTest {
        val store = FakeSurveyLocalStore().apply {
            upsertSummaries(
                listOf(
                    Survey(id = "cached-active", name = "Cached Active", scope = SurveyScope.Generic, isActive = true, questionCount = 2),
                    Survey(id = "cached-inactive", name = "Cached Inactive", scope = SurveyScope.Generic, isActive = false, questionCount = 2),
                ),
            )
        }
        val api = api { respondError(HttpStatusCode.InternalServerError) }

        val result = SurveyRepositoryImpl(api, store).getAvailable(page = 1, pageSize = 20)

        val paged = result.getOrThrow()
        assertEquals(listOf("cached-active"), paged.items.map { it.id })
        assertTrue(paged.fromCache)
        assertEquals(1, paged.page)
        assertEquals(1, paged.totalPages)
    }

    @Test
    fun `page-1 network failure with empty cache propagates the failure`() = runTest {
        val store = FakeSurveyLocalStore()
        val api = api { respondError(HttpStatusCode.InternalServerError) }

        val result = SurveyRepositoryImpl(api, store).getAvailable(page = 1, pageSize = 20)

        assertTrue(result.isFailure)
    }

    @Test
    fun `page-2 network failure propagates without falling back to cache`() = runTest {
        val store = FakeSurveyLocalStore().apply {
            upsertSummaries(listOf(Survey(id = "cached", name = "Cached", scope = SurveyScope.Generic, isActive = true, questionCount = 2)))
        }
        val api = api { respondError(HttpStatusCode.InternalServerError) }

        val result = SurveyRepositoryImpl(api, store).getAvailable(page = 2, pageSize = 20)

        assertTrue(result.isFailure)
    }

    @Test
    fun `warm fetches all pages and replaces the snapshot with the union`() = runTest {
        val store = FakeSurveyLocalStore()
        val api = api { request ->
            when (request.url.parameters["page"]) {
                "1" -> respondPage(this, page(remoteSurvey("s1", "Survey One"), page = 1, totalPages = 2))
                "2" -> respondPage(this, page(remoteSurvey("s2", "Survey Two"), page = 2, totalPages = 2))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        SurveyRepositoryImpl(api, store).warmAvailableSurveys()

        assertEquals(1, store.replaceAllSummariesCallCount)
        assertEquals(listOf("s1", "s2"), store.getSummaries().map { it.id }.sorted())
    }

    @Test
    fun `warm aborts the replace when a later page fails`() = runTest {
        val store = FakeSurveyLocalStore()
        val api = api { request ->
            when (request.url.parameters["page"]) {
                "1" -> respondPage(this, page(remoteSurvey("s1", "Survey One"), page = 1, totalPages = 2))
                else -> respondError(HttpStatusCode.InternalServerError)
            }
        }

        SurveyRepositoryImpl(api, store).warmAvailableSurveys()

        assertEquals(0, store.replaceAllSummariesCallCount)
        assertTrue(store.getSummaries().isEmpty())
    }

    @Test
    fun `warm prefetches details only for ids missing detail`() = runTest {
        val store = FakeSurveyLocalStore()
        val detailCalls = mutableListOf<String>()
        val api = api { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/surveys/available") ->
                    respondPage(this, page("${remoteSurvey("s1", "Survey One")},${remoteSurvey("s2", "Survey Two")}", totalPages = 1))
                path.endsWith("/surveys/s2") -> {
                    detailCalls += "s2"
                    respondPage(this, surveyDetail("s2"))
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        // s1 already has a cached detail; s2 doesn't — only s2 should be prefetched.
        store.upsertSummaries(listOf(Survey(id = "s1", name = "Survey One", scope = SurveyScope.Generic, isActive = true, questionCount = 3)))
        store.upsertDetail(SurveyDetail(id = "s1", name = "Survey One", scope = SurveyScope.Generic, isActive = true, questions = emptyList()))

        SurveyRepositoryImpl(api, store).warmAvailableSurveys()

        assertEquals(listOf("s2"), detailCalls)
        assertEquals("s2", store.findDetail("s2")?.id)
    }

    @Test
    fun `warm continues past a 404 detail and caches the rest`() = runTest {
        val store = FakeSurveyLocalStore()
        val api = api { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/surveys/available") ->
                    respondPage(this, page("${remoteSurvey("s1", "Survey One")},${remoteSurvey("s2", "Survey Two")}", totalPages = 1))
                path.endsWith("/surveys/s1") -> respondError(HttpStatusCode.NotFound)
                path.endsWith("/surveys/s2") -> respondPage(this, surveyDetail("s2"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        SurveyRepositoryImpl(api, store).warmAvailableSurveys()

        assertNull(store.findDetail("s1"))
        assertEquals("s2", store.findDetail("s2")?.id)
    }

    @Test
    fun `warm falls back to upsert when truncated by the page cap`() = runTest {
        val store = FakeSurveyLocalStore()
        val api = api { request ->
            val path = request.url.encodedPath
            if (path.endsWith("/surveys/available")) {
                val pageNum = request.url.parameters["page"]?.toInt() ?: 1
                respondPage(this, page(remoteSurvey("s$pageNum", "Survey $pageNum"), page = pageNum, totalPages = 6))
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }

        SurveyRepositoryImpl(api, store).warmAvailableSurveys()

        assertEquals(0, store.replaceAllSummariesCallCount)
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), store.getSummaries().map { it.id }.sorted())
    }
}
