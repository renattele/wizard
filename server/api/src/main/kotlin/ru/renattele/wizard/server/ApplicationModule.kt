package ru.renattele.wizard.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.renattele.wizard.contracts.v1.ExportRequestV1
import ru.renattele.wizard.contracts.v1.HealthResponseV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.WIZARD_API_VERSION_V1
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.catalog.ClasspathCatalogProvider
import ru.renattele.wizard.engine.catalog.JavaNetPackFetcher
import ru.renattele.wizard.engine.catalog.MergedCatalogProvider
import ru.renattele.wizard.engine.catalog.RemoteCatalogProvider
import ru.renattele.wizard.engine.configuration.ENGINE_VERSION
import ru.renattele.wizard.engine.security.Sha256ChecksumVerifier
import java.net.URI

object ApplicationModule {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val localCatalog: CatalogProvider = ClasspathCatalogProvider(
        resourcePaths = listOf(
            "packs/pack-core.json",
            "packs/pack-android.json",
            "packs/pack-compose.json",
            "packs/pack-arch.json",
        ),
    )

    private val remoteCatalog: CatalogProvider = run {
        val remoteIndexes = System.getenv("WIZARD_REMOTE_INDEXES")
            ?.split(',')
            ?.mapNotNull { value -> value.trim().takeIf { it.isNotBlank() } }
            ?.map(URI::create)
            .orEmpty()
        if (remoteIndexes.isEmpty()) {
            EmptyCatalogProvider
        } else {
            RemoteCatalogProvider(
                indexUris = remoteIndexes,
                fetcher = JavaNetPackFetcher(),
                checksumVerifier = Sha256ChecksumVerifier(),
            )
        }
    }

    private val apiService = WizardApiService(MergedCatalogProvider(localCatalog, remoteCatalog))

    fun module(app: Application) = with(app) {
        install(DefaultHeaders)
        install(CallId)
        install(CallLogging)
        install(CORS) {
            anyHost()
            allowHeader("Content-Type")
            allowHeader("X-Request-ID")
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ErrorEnvelope("BAD_REQUEST", cause.message ?: "Invalid request"))
            }
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, ErrorEnvelope("INTERNAL_ERROR", cause.message ?: "Unexpected error"))
            }
        }

        routing {
            route("/api/v1") {
                get("/health") {
                    call.respond(
                        HealthResponseV1(
                            status = "ok",
                            engineVersion = ENGINE_VERSION,
                        ),
                    )
                }

                get("/catalog") {
                    call.respond(apiService.catalog())
                }

                post("/resolve") {
                    val request = call.receive<ResolveRequestV1>()
                    call.respond(apiService.resolve(request))
                }

                post("/preview") {
                    val request = call.receive<PreviewRequestV1>()
                    call.respond(apiService.preview(request))
                }

                post("/export") {
                    val request = call.receive<ExportRequestV1>()
                    call.respond(apiService.export(request))
                }

                get("/openapi") {
                    val contract = mapOf(
                        "openapi" to "3.1.0",
                        "info" to mapOf("title" to "Wizard API", "version" to WIZARD_API_VERSION_V1),
                        "paths" to mapOf(
                            "/api/v1/catalog" to mapOf("get" to mapOf("summary" to "Catalog")),
                            "/api/v1/resolve" to mapOf("post" to mapOf("summary" to "Resolve")),
                            "/api/v1/preview" to mapOf("post" to mapOf("summary" to "Preview")),
                            "/api/v1/export" to mapOf("post" to mapOf("summary" to "Export")),
                        ),
                    )
                    call.respond(contract)
                }
            }
        }
    }
}

@Serializable
data class ErrorEnvelope(
    val code: String,
    val message: String,
)
