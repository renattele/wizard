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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.renattele.wizard.contracts.v1.ArchitectureModeV1
import ru.renattele.wizard.contracts.v1.ArchitectureModelV1
import ru.renattele.wizard.contracts.v1.CustomComponentTypeV1
import ru.renattele.wizard.contracts.v1.ExportRequestV1
import ru.renattele.wizard.contracts.v1.ExportResponseV1
import ru.renattele.wizard.contracts.v1.ProblemCodeV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.ProjectConfigV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.UserPatchV1
import ru.renattele.wizard.contracts.v1.PatchOperationV1
import ru.renattele.wizard.contracts.v1.WizardSelectionV1

class ApplicationModuleTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `catalog endpoint returns templates and options`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.get("/api/v1/catalog")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"templates\""))
        assertTrue(body.contains("\"options\""))
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
        assertTrue(payload.applyOrder.isNotEmpty())
        assertTrue(payload.lockfile.resolutionHash.isNotBlank())
    }

    @Test
    fun `resolve endpoint integrates preset architecture with selected ui framework`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.post("/api/v1/resolve") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResolveRequestV1.serializer(),
                    ResolveRequestV1(
                        strictMode = false,
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("ui-compose"),
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.PRESET,
                                presetPatternId = "arch-mvvm",
                            ),
                        ),
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        val payload = json.decodeFromString<ResolveResponseV1>(body)
        assertTrue(payload.resolvedOptions.any { it.id == "arch-mvvm" })
        assertTrue(payload.resolvedOptions.any { it.id == "ui-compose" })
        assertFalse(payload.problems.any { it.code == ProblemCodeV1.AMBIGUOUS_CAPABILITY_PROVIDER })
    }

    @Test
    fun `resolve endpoint requires choosing ui framework for preset architecture`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.post("/api/v1/resolve") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResolveRequestV1.serializer(),
                    ResolveRequestV1(
                        strictMode = false,
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.PRESET,
                                presetPatternId = "arch-mvvm",
                            ),
                        ),
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        val payload = json.decodeFromString<ResolveResponseV1>(body)
        assertTrue(payload.resolvedOptions.any { it.id == "arch-mvvm" })
        assertTrue(payload.problems.any { it.code == ProblemCodeV1.AMBIGUOUS_CAPABILITY_PROVIDER })
    }

    @Test
    fun `export endpoint returns archive artifact`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val resolveBody = client.post("/api/v1/resolve") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResolveRequestV1.serializer(),
                    ResolveRequestV1(
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("ui-compose"),
                        ),
                    ),
                ),
            )
        }.bodyAsText()

        val resolved = json.decodeFromString<ResolveResponseV1>(resolveBody)
        val response = client.post("/api/v1/export") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ExportRequestV1.serializer(),
                    ExportRequestV1(
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("ui-compose"),
                        ),
                        lockfile = resolved.lockfile,
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        val payload = json.decodeFromString<ExportResponseV1>(body)
        assertTrue(payload.artifact.archiveBase64.isNotBlank())
    }

    @Test
    fun `preview endpoint applies custom components and user patches`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.post("/api/v1/preview") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PreviewRequestV1.serializer(),
                    PreviewRequestV1(
                        strictMode = false,
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("ui-compose"),
                            contextVars = mapOf("Feature" to "Catalog"),
                            projectConfig = ProjectConfigV1(
                                projectName = "Demo App",
                                packageId = "com.example.demo",
                            ),
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.CUSTOM,
                                customComponentTypes = listOf(
                                    CustomComponentTypeV1(
                                        id = "coordinator",
                                        displayName = "Coordinator",
                                        layer = "presentation",
                                        fileNameTemplate = "feature/\${Feature}/Coordinator.kt",
                                        sourceTemplate = "package \${Package}.feature.\${Feature}\n\nclass \${ComponentName}",
                                        allowedDependencyTypeIds = listOf("interactor"),
                                    ),
                                ),
                            ),
                            customPatches = listOf(
                                UserPatchV1(
                                    sourceId = "readme-note",
                                    operation = PatchOperationV1.APPEND_FILE,
                                    targetPath = "README.md",
                                    content = "\nGenerated for \${ProjectName}",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        assertTrue(body.contains("feature/Catalog/Coordinator.kt"))
        assertTrue(body.contains("Generated for Demo App"))
        assertTrue(body.contains(".wizard/configuration.json"))
    }

    @Test
    fun `preview endpoint applies preset option parameters`() = testApplication {
        application {
            ApplicationModule.module(this)
        }

        val response = client.post("/api/v1/preview") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PreviewRequestV1.serializer(),
                    PreviewRequestV1(
                        strictMode = false,
                        selection = WizardSelectionV1(
                            templateId = "android-app",
                            selectedOptionIds = listOf("arch-mvvm", "ui-compose"),
                            optionParameters = mapOf(
                                "arch-mvvm" to mapOf(
                                    "featureName" to "Orders",
                                    "viewModelName" to "OrdersStore",
                                    "stateName" to "OrdersScreenState",
                                    "eventName" to "OrdersIntent",
                                ),
                                "base-kotlin" to mapOf(
                                    "explicitApiMode" to "strict",
                                    "enableContextParameters" to "true",
                                    "optInAnnotations" to "kotlinx.coroutines.ExperimentalCoroutinesApi",
                                ),
                            ),
                            projectConfig = ProjectConfigV1(
                                projectName = "Demo App",
                                packageId = "com.example.demo",
                            ),
                        ),
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        assertTrue(body.contains("OrdersStore.kt"))
        assertTrue(body.contains("OrdersScreenState.kt"))
        assertTrue(body.contains("OrdersScreen.kt"))
        assertTrue(body.contains(".wizard/kotlin/settings.properties"))
        assertTrue(body.contains("kotlin.enableContextParameters=true"))
        assertTrue(body.contains("kotlin.explicitApiMode=strict"))
    }
}
