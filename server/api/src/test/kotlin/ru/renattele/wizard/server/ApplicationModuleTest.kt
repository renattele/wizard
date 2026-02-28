package ru.renattele.wizard.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.WizardSelectionV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationModuleTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `catalog endpoint returns options`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.get("/api/v1/catalog")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("pack-core"))
    }

    @Test
    fun `preview strict mode requires lockfile`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.post("/api/v1/preview") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PreviewRequestV1.serializer(),
                    PreviewRequestV1(
                        strictMode = true,
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("ui-compose"),
                        ),
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `resolve endpoint returns lockfile and deterministic order`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.post("/api/v1/resolve") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResolveRequestV1.serializer(),
                    ResolveRequestV1(
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("arch-mvvm"),
                        ),
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        val payload = json.decodeFromString<ResolveResponseV1>(body)
        assertTrue(payload.orderedOptionIds.isNotEmpty())
        assertTrue(payload.lockfile.resolutionHash.isNotBlank())
    }
}
