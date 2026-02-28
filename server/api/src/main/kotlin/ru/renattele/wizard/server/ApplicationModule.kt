package ru.renattele.wizard.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.renattele.wizard.contracts.v1.CatalogOptionV1
import ru.renattele.wizard.contracts.v1.CatalogPackV1
import ru.renattele.wizard.contracts.v1.CatalogResponseV1
import ru.renattele.wizard.contracts.v1.ExportRequestV1
import ru.renattele.wizard.contracts.v1.ExportResponseV1
import ru.renattele.wizard.contracts.v1.GeneratedFilePreviewV1
import ru.renattele.wizard.contracts.v1.HealthResponseV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.PreviewResponseV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.WIZARD_API_VERSION_V1
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.catalog.ClasspathCatalogProvider
import ru.renattele.wizard.engine.catalog.JavaNetPackFetcher
import ru.renattele.wizard.engine.catalog.MergedCatalogProvider
import ru.renattele.wizard.engine.catalog.RemoteCatalogProvider
import ru.renattele.wizard.engine.generator.DeterministicPatchPipeline
import ru.renattele.wizard.engine.generator.GenerationRequest
import ru.renattele.wizard.engine.resolver.DeterministicResolutionEngine
import ru.renattele.wizard.engine.resolver.ENGINE_VERSION
import ru.renattele.wizard.engine.resolver.ResolutionEngine
import ru.renattele.wizard.engine.security.Sha256ChecksumVerifier
import java.net.URI

object ApplicationModule {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val resolver: ResolutionEngine = DeterministicResolutionEngine()
    private val pipeline = DeterministicPatchPipeline()
    private val sessions = InMemorySessionRepository()
    private val audits = InMemoryAuditRepository()

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

    private val mergedCatalog: CatalogProvider = MergedCatalogProvider(localCatalog, remoteCatalog)

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
                    val catalog = mergedCatalog.loadCatalog()
                    val response = CatalogResponseV1(
                        schemaVersion = "1.0.0",
                        packs = catalog.packs.map { pack ->
                            CatalogPackV1(
                                id = pack.id,
                                version = pack.version,
                                source = pack.source,
                            )
                        },
                        options = catalog.options
                            .sortedBy { it.id }
                            .map { option ->
                                CatalogOptionV1(
                                    id = option.id,
                                    type = option.type,
                                    category = option.category,
                                    displayName = option.displayName,
                                    description = option.description,
                                    defaultEnabled = option.defaultEnabled,
                                    selectedVersion = option.version.recommended,
                                )
                            },
                    )
                    call.respond(response)
                }

                post("/resolve") {
                    val request = call.receive<ResolveRequestV1>()
                    val catalog = mergedCatalog.loadCatalog()
                    val response = resolver.resolve(request, catalog)

                    sessions.save(response.sessionId, response)
                    audits.append(
                        AuditEvent(
                            sessionId = response.sessionId,
                            event = "resolve",
                            details = "resolved=${response.resolvedOptionIds.size};hash=${response.lockfile.resolutionHash}",
                        ),
                    )

                    call.respond(response)
                }

                post("/preview") {
                    val request = call.receive<PreviewRequestV1>()
                    val catalog = mergedCatalog.loadCatalog()
                    val resolveResponse = resolver.resolve(
                        request = ResolveRequestV1(
                            selection = request.selection,
                            strictMode = request.strictMode,
                        ),
                        catalog = catalog,
                    )

                    if (request.strictMode && request.lockfile == null) {
                        throw IllegalArgumentException("Strict mode requires lockfile for preview")
                    }
                    if (request.strictMode && request.lockfile?.resolutionHash != resolveResponse.lockfile.resolutionHash) {
                        throw IllegalArgumentException("Lockfile is stale and must be regenerated")
                    }

                    val generated = pipeline.generate(
                        GenerationRequest(
                            templateId = request.selection.templateId,
                            orderedOptionIds = resolveResponse.orderedOptionIds,
                            catalog = catalog,
                            strictMode = request.strictMode,
                            lockfile = request.lockfile ?: resolveResponse.lockfile,
                        ),
                    )

                    val response = PreviewResponseV1(
                        files = generated.files.entries
                            .sortedBy { it.key }
                            .map { (path, content) -> GeneratedFilePreviewV1(path = path, content = content) },
                        patchReport = generated.patchReport,
                        compatibilityReport = resolveResponse.compatibilityReport,
                    )

                    audits.append(
                        AuditEvent(
                            sessionId = resolveResponse.sessionId,
                            event = "preview",
                            details = "files=${response.files.size}",
                        ),
                    )

                    call.respond(response)
                }

                post("/export") {
                    val request = call.receive<ExportRequestV1>()
                    val catalog = mergedCatalog.loadCatalog()
                    val resolveResponse = resolver.resolve(
                        request = ResolveRequestV1(
                            selection = request.selection,
                            strictMode = request.strictMode,
                        ),
                        catalog = catalog,
                    )

                    if (request.strictMode && request.lockfile == null) {
                        throw IllegalArgumentException("Strict mode requires lockfile for export")
                    }
                    if (request.strictMode && request.lockfile?.resolutionHash != resolveResponse.lockfile.resolutionHash) {
                        throw IllegalArgumentException("Lockfile is stale and must be regenerated")
                    }

                    val generated = pipeline.generate(
                        GenerationRequest(
                            templateId = request.selection.templateId,
                            orderedOptionIds = resolveResponse.orderedOptionIds,
                            catalog = catalog,
                            strictMode = request.strictMode,
                            lockfile = request.lockfile ?: resolveResponse.lockfile,
                        ),
                    )

                    val payload = generated.files.entries
                        .sortedBy { it.key }
                        .joinToString(separator = "\n") { (path, content) -> "--- $path ---\n$content" }
                        .encodeToByteArray()

                    val response = ExportResponseV1(
                        fileName = "wizard-project.${request.format.name.lowercase()}",
                        sizeBytes = payload.size.toLong(),
                        patchReport = generated.patchReport,
                        compatibilityReport = resolveResponse.compatibilityReport,
                    )

                    audits.append(
                        AuditEvent(
                            sessionId = resolveResponse.sessionId,
                            event = "export",
                            details = "bytes=${response.sizeBytes}",
                        ),
                    )

                    call.respond(response)
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

                get("/audit") {
                    call.respond(audits.list())
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
