package ru.renattele.wizard.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.renattele.wizard.contracts.v1.ArchitectureModeV1
import ru.renattele.wizard.contracts.v1.ArchitectureModelV1
import ru.renattele.wizard.contracts.v1.CustomComponentTypeV1
import ru.renattele.wizard.contracts.v1.ExportRequestV1
import ru.renattele.wizard.contracts.v1.ExportResponseV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.ProjectConfigV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.WizardSelectionV1
import ru.renattele.wizard.engine.catalog.ClasspathCatalogProvider
import ru.renattele.wizard.engine.catalog.MergedCatalogProvider

class ApplicationModuleTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val templateMarker = Regex("""__[A-Z0-9_]+__""")
    private val localCatalogPaths = listOf(
        "packs/pack-core.json",
        "packs/pack-android.json",
        "packs/pack-compose.json",
        "packs/pack-arch.json",
        "packs/pack-di.json",
        "packs/pack-libraries.json",
        "packs/pack-quality.json",
        "packs/pack-ci.json",
    )

    @Test
    fun `catalog exposes android generator matrix`() = testApplication {
        application { ApplicationModule.module(this) }

        val response = client.get("/api/v1/catalog")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"di-hilt\""))
        assertTrue(body.contains("\"library-room\""))
        assertTrue(body.contains("\"ci-github-actions\""))
        assertTrue(body.contains("\"ui-compose\""))
    }

    @Test
    fun `resolve handles compose mvvm hilt stack`() = testApplication {
        application { ApplicationModule.module(this) }

        val response = client.post("/api/v1/resolve") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResolveRequestV1.serializer(),
                    ResolveRequestV1(
                        strictMode = false,
                        selection = selection(
                            optionIds = listOf(
                                "ui-compose",
                                "arch-mvvm",
                                "di-hilt",
                                "library-retrofit",
                                "library-room",
                                "ci-github-actions",
                            ),
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.PRESET,
                                presetPatternId = "arch-mvvm",
                            ),
                        ),
                    ),
                ),
            )
        }

        val payload = json.decodeFromString(ResolveResponseV1.serializer(), response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(payload.resolvedOptions.any { it.id == "di-hilt" })
        assertTrue(payload.resolvedOptions.any { it.id == "library-room" })
        assertTrue(payload.lockfile.configurationHash.isNotBlank())
    }

    @Test
    fun `preview returns generated modules and files`() = testApplication {
        application { ApplicationModule.module(this) }

        val resolved = resolve(
            selection = selection(
                optionIds = listOf(
                    "ui-compose",
                    "arch-mvvm",
                    "di-hilt",
                    "library-retrofit",
                    "quality-detekt",
                    "ci-github-actions",
                ),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.PRESET,
                    presetPatternId = "arch-mvvm",
                ),
            ),
            client = client,
        )

        val response = client.post("/api/v1/preview") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PreviewRequestV1.serializer(),
                    PreviewRequestV1(
                        selection = selection(
                            optionIds = listOf(
                                "ui-compose",
                                "arch-mvvm",
                                "di-hilt",
                                "library-retrofit",
                                "quality-detekt",
                                "ci-github-actions",
                            ),
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.PRESET,
                                presetPatternId = "arch-mvvm",
                            ),
                        ),
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        assertTrue(body.contains("feature/home/presentation/build.gradle.kts"))
        assertTrue(body.contains("feature/catalog/data/build.gradle.kts"))
        assertTrue(body.contains("AppNavigation.kt"))
        assertTrue(body.contains(".github/workflows/android.yml"))
    }

    @Test
    fun `preview applies custom architecture components per feature layer`() = testApplication {
        application { ApplicationModule.module(this) }

        val resolved = resolve(
            selection = selection(
                optionIds = listOf("ui-compose", "di-koin", "ci-github-actions"),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.CUSTOM,
                    customComponentTypes = listOf(
                        CustomComponentTypeV1(
                            id = "coordinator",
                            displayName = "Coordinator",
                            layer = "presentation",
                            fileNameTemplate = "\${FeatureClass}Coordinator.kt",
                            sourceTemplate = "package \${Package}.feature.\${FeaturePackage}.presentation\n\nclass \${FeatureClass}Coordinator",
                            allowedDependencyTypeIds = listOf("router"),
                        ),
                    ),
                ),
            ),
            client = client,
        )

        val response = client.post("/api/v1/preview") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PreviewRequestV1.serializer(),
                    PreviewRequestV1(
                        selection = selection(
                            optionIds = listOf("ui-compose", "di-koin", "ci-github-actions"),
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.CUSTOM,
                                customComponentTypes = listOf(
                                    CustomComponentTypeV1(
                                        id = "coordinator",
                                        displayName = "Coordinator",
                                        layer = "presentation",
                                        fileNameTemplate = "\${FeatureClass}Coordinator.kt",
                                        sourceTemplate = "package \${Package}.feature.\${FeaturePackage}.presentation\n\nclass \${FeatureClass}Coordinator",
                                        allowedDependencyTypeIds = listOf("router"),
                                    ),
                                ),
                            ),
                        ),
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        assertTrue(body.contains("feature/home/presentation/src/main/kotlin/com/example/demo/feature/home/presentation/HomeCoordinator.kt"))
        assertTrue(body.contains("feature/catalog/presentation/src/main/kotlin/com/example/demo/feature/catalog/presentation/CatalogCoordinator.kt"))
    }

    @Test
    fun `export and download endpoints return zip artifacts`() = testApplication {
        application { ApplicationModule.module(this) }

        val selection = selection(
            optionIds = listOf(
                "ui-xml",
                "arch-mvp",
                "di-dagger2",
                "library-room",
                "ci-gitlab",
            ),
            architecture = ArchitectureModelV1(
                mode = ArchitectureModeV1.PRESET,
                presetPatternId = "arch-mvp",
            ),
        )
        val resolved = resolve(selection, client)

        val exportResponse = client.post("/api/v1/export") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ExportRequestV1.serializer(),
                    ExportRequestV1(
                        selection = selection,
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }
        val payload = json.decodeFromString(ExportResponseV1.serializer(), exportResponse.bodyAsText())
        assertEquals(HttpStatusCode.OK, exportResponse.status)
        assertTrue(payload.artifact.fileName.endsWith(".zip"))
        assertTrue(payload.artifact.archiveBase64.isNotBlank())
        val files = unzip(payload.artifact.archiveBase64)
        assertHealthyExport(files)
        assertTrue(files.getValue("gradle/wrapper/gradle-wrapper.jar").size > 40_000)
        val appBuild = files.getValue("app/build.gradle.kts").decodeToString()
        assertTrue("alias(libs.plugins.kotlinKapt)" in appBuild)

        val downloadResponse = client.post("/api/v1/export/download") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ExportRequestV1.serializer(),
                    ExportRequestV1(
                        selection = selection,
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, downloadResponse.status)
        assertTrue(downloadResponse.headers[HttpHeaders.ContentDisposition]?.contains("attachment") == true)
        assertTrue(downloadResponse.bodyAsText().isNotEmpty())
    }

    @Test
    fun `strict preview rejects stale lockfiles`() = testApplication {
        application { ApplicationModule.module(this) }

        val firstSelection = selection(
            optionIds = listOf("ui-compose", "arch-mvi", "di-koin", "ci-github-actions"),
            architecture = ArchitectureModelV1(
                mode = ArchitectureModeV1.PRESET,
                presetPatternId = "arch-mvi",
            ),
        )
        val resolved = resolve(firstSelection, client)
        val changedSelection = firstSelection.copy(
            projectConfig = firstSelection.projectConfig?.copy(featureNames = listOf("home", "orders")),
        )

        val response = client.post("/api/v1/preview") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PreviewRequestV1.serializer(),
                    PreviewRequestV1(
                        selection = changedSelection,
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `compose hilt export contains healthy gradle structure`() = testApplication {
        application { ApplicationModule.module(this) }

        val selection = selection(
            optionIds = listOf(
                "ui-compose",
                "arch-mvvm",
                "di-hilt",
                "library-retrofit",
                "library-room",
                "quality-detekt",
                "ci-github-actions",
            ),
            architecture = ArchitectureModelV1(
                mode = ArchitectureModeV1.PRESET,
                presetPatternId = "arch-mvvm",
            ),
        )
        val resolved = resolve(selection, client)

        val exportResponse = client.post("/api/v1/export") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ExportRequestV1.serializer(),
                    ExportRequestV1(
                        selection = selection,
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }

        val payload = json.decodeFromString(ExportResponseV1.serializer(), exportResponse.bodyAsText())
        assertEquals(HttpStatusCode.OK, exportResponse.status)
        val files = unzip(payload.artifact.archiveBase64)
        assertHealthyExport(files)
        val appBuild = files.getValue("app/build.gradle.kts").decodeToString()
        val versionCatalog = files.getValue("gradle/libs.versions.toml").decodeToString()
        assertTrue("alias(libs.plugins.hilt)" in appBuild)
        assertTrue("alias(libs.plugins.kotlinKapt)" in appBuild)
        assertTrue("kapt(libs.hilt.compiler)" in appBuild)
        assertTrue("kotlin = \"2.2.21\"" in versionCatalog)
        assertTrue("ksp = \"2.2.21-2.0.5\"" in versionCatalog)
        assertTrue("core/designsystem/src/main/res/values/themes.xml" in files)
    }

    @Test
    fun `compose coil export wires dependency into design system module`() = testApplication {
        application { ApplicationModule.module(this) }

        val selection = selection(
            optionIds = listOf(
                "ui-compose",
                "arch-mvi",
                "di-koin",
                "library-coil",
                "library-room",
                "ci-github-actions",
            ),
            architecture = ArchitectureModelV1(
                mode = ArchitectureModeV1.PRESET,
                presetPatternId = "arch-mvi",
            ),
        )
        val resolved = resolve(selection, client)

        val exportResponse = client.post("/api/v1/export") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ExportRequestV1.serializer(),
                    ExportRequestV1(
                        selection = selection,
                        lockfile = resolved.lockfile,
                        strictMode = true,
                    ),
                ),
            )
        }

        val payload = json.decodeFromString(ExportResponseV1.serializer(), exportResponse.bodyAsText())
        assertEquals(HttpStatusCode.OK, exportResponse.status)
        val files = unzip(payload.artifact.archiveBase64)
        val designSystemBuild = files.getValue("core/designsystem/build.gradle.kts").decodeToString()
        assertTrue("implementation(libs.coil.compose)" in designSystemBuild)
        assertTrue("app/src/main/kotlin/com/example/demo/MainActivity.kt" in files)
        assertTrue(
            "core/designsystem/src/main/kotlin/com/example/demo/core/designsystem/components/NetworkImage.kt" in files,
        )
    }

    @Test
    fun `export matrix stays structurally healthy across supported combinations`() {
        val service = WizardApiService(
            MergedCatalogProvider(
                localProvider = ClasspathCatalogProvider(localCatalogPaths),
                remoteProvider = EmptyCatalogProvider,
            ),
        )

        allSupportedSelections().forEachIndexed { index, selection ->
            val resolved = service.resolve(
                ResolveRequestV1(
                    selection = selection,
                    strictMode = true,
                ),
            )
            val export = service.export(
                ExportRequestV1(
                    selection = selection,
                    lockfile = resolved.lockfile,
                    strictMode = true,
                ),
            )
            val files = unzip(export.artifact.archiveBase64)
            assertHealthyExport(files)
            assertSelectionSpecificExport(selection, files, "case-$index")
        }
    }

    @Test
    fun `smoke assemble representative exported android projects when enabled`() {
        if (!generatedProjectSmokeEnabled()) {
            return
        }
        val service = localWizardApiService()

        assembleExportedProject(
            service = service,
            selection = selection(
                optionIds = listOf(
                    "ui-compose",
                    "arch-mvvm",
                    "di-hilt",
                    "library-retrofit",
                    "library-room",
                    "quality-detekt",
                    "ci-github-actions",
                ),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.PRESET,
                    presetPatternId = "arch-mvvm",
                ),
            ),
            tempPrefix = "compose-hilt",
        )
        assembleExportedProject(
            service = service,
            selection = selection(
                optionIds = listOf(
                    "ui-xml",
                    "arch-mvp",
                    "di-dagger2",
                    "library-room",
                    "ci-gitlab",
                ),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.PRESET,
                    presetPatternId = "arch-mvp",
                ),
            ),
            tempPrefix = "xml-dagger",
        )
        assembleExportedProject(
            service = service,
            selection = selection(
                optionIds = listOf(
                    "ui-compose",
                    "arch-mvi",
                    "di-koin",
                    "library-coil",
                    "library-retrofit",
                    "library-room",
                    "library-timber",
                    "quality-ktlint",
                    "ci-github-actions",
                ),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.PRESET,
                    presetPatternId = "arch-mvi",
                ),
            ),
            tempPrefix = "compose-koin",
        )
    }

    @Test
    fun `smoke assemble full compile matrix when enabled`() {
        if (!generatedProjectFullSmokeEnabled()) {
            return
        }
        val service = localWizardApiService()

        val compileAffectingLibraries = listOf(
            "library-retrofit",
            "library-room",
            "library-coil",
            "library-timber",
        )
        val quality = listOf("quality-detekt", "quality-ktlint")

        listOf("ui-compose", "ui-xml").forEach { ui ->
            listOf("arch-mvvm", "arch-mvi", "arch-mvp").forEach { architectureId ->
                listOf("di-hilt", "di-koin", "di-dagger2").forEach { di ->
                    assembleExportedProject(
                        service = service,
                        selection = selection(
                            optionIds = listOf(ui, architectureId, di, "ci-github-actions") + compileAffectingLibraries + quality,
                            architecture = ArchitectureModelV1(
                                mode = ArchitectureModeV1.PRESET,
                                presetPatternId = architectureId,
                            ),
                            featureNames = listOf("home"),
                        ),
                        tempPrefix = "$ui-$architectureId-$di",
                    )
                }
            }
        }

        listOf("ui-compose", "ui-xml").forEach { ui ->
            listOf("di-hilt", "di-koin", "di-dagger2").forEach { di ->
                assembleExportedProject(
                    service = service,
                    selection = selection(
                        optionIds = listOf(ui, di, "ci-github-actions") + compileAffectingLibraries + quality,
                        architecture = ArchitectureModelV1(
                            mode = ArchitectureModeV1.CUSTOM,
                            customComponentTypes = listOf(
                                CustomComponentTypeV1(
                                    id = "coordinator",
                                    displayName = "Coordinator",
                                    layer = "presentation",
                                    fileNameTemplate = "\${FeatureClass}Coordinator.kt",
                                    sourceTemplate = "package \${Package}.feature.\${FeaturePackage}.presentation\n\nclass \${FeatureClass}Coordinator",
                                    allowedDependencyTypeIds = listOf("router"),
                                ),
                            ),
                        ),
                        featureNames = listOf("home"),
                    ),
                    tempPrefix = "$ui-custom-$di",
                )
            }
        }
    }

    private fun selection(
        optionIds: List<String>,
        architecture: ArchitectureModelV1,
        featureNames: List<String> = listOf("home", "catalog"),
    ): WizardSelectionV1 =
        WizardSelectionV1(
            templateId = "android-app",
            selectedOptionIds = optionIds,
            projectConfig = ProjectConfigV1(
                projectName = "Demo App",
                packageId = "com.example.demo",
                modulePreset = "android-clean",
                featureNames = featureNames,
                minSdk = 24,
                targetSdk = 35,
                uiFramework = if ("ui-xml" in optionIds) "xml" else "compose",
                designSystemPrefix = "T",
                primaryColor = "#6750A4",
                secondaryColor = "#625B71",
                ciTemplate = if ("ci-gitlab" in optionIds) "gitlab" else "github-actions",
                releaseTarget = "git-release-assets",
                releaseArtifactTypes = listOf("apk", "aab"),
                qualityTools = optionIds.filter { it.startsWith("quality-") },
            ),
            architecture = architecture,
        )

    private suspend fun resolve(
        selection: WizardSelectionV1,
        client: io.ktor.client.HttpClient,
    ): ResolveResponseV1 {
        val response = client.post("/api/v1/resolve") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResolveRequestV1.serializer(),
                    ResolveRequestV1(
                        selection = selection,
                        strictMode = true,
                    ),
                ),
            )
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        return json.decodeFromString(ResolveResponseV1.serializer(), body)
    }

    private fun assertHealthyExport(files: Map<String, ByteArray>) {
        val rootBuild = files.getValue("build.gradle.kts").decodeToString()
        val appBuild = files.getValue("app/build.gradle.kts").decodeToString()
        assertTrue(rootBuild.trimStart().startsWith("plugins {"))
        assertTrue("projects.core" !in rootBuild)
        assertTrue("projects.core" !in appBuild)
        assertTrue(files.filterKeys { !it.endsWith(".jar") }.values.none { templateMarker.containsMatchIn(it.decodeToString()) })
    }

    private fun allSupportedSelections(): List<WizardSelectionV1> {
        val uiOptions = listOf("ui-compose", "ui-xml")
        val presetArchitectures = listOf("arch-mvvm", "arch-mvi", "arch-mvp")
        val diOptions = listOf("di-hilt", "di-koin", "di-dagger2")
        val ciOptions = listOf("ci-github-actions", "ci-gitlab")
        val libraryOptions = listOf("library-retrofit", "library-room", "library-coil", "library-timber")
        val qualityOptions = listOf("quality-detekt", "quality-ktlint")

        val selections = mutableListOf<WizardSelectionV1>()
        uiOptions.forEach { ui ->
            presetArchitectures.forEach { architectureId ->
                diOptions.forEach { di ->
                    ciOptions.forEach { ci ->
                        powerSet(libraryOptions).forEach { libraries ->
                            powerSet(qualityOptions).forEach { quality ->
                                selections += selection(
                                    optionIds = listOf(ui, architectureId, di, ci) + libraries + quality,
                                    architecture = ArchitectureModelV1(
                                        mode = ArchitectureModeV1.PRESET,
                                        presetPatternId = architectureId,
                                    ),
                                    featureNames = listOf("home"),
                                )
                            }
                        }
                    }
                }
            }
            diOptions.forEach { di ->
                ciOptions.forEach { ci ->
                    powerSet(libraryOptions).forEach { libraries ->
                        powerSet(qualityOptions).forEach { quality ->
                            selections += selection(
                                optionIds = listOf(ui, di, ci) + libraries + quality,
                                architecture = ArchitectureModelV1(
                                    mode = ArchitectureModeV1.CUSTOM,
                                    customComponentTypes = listOf(
                                        CustomComponentTypeV1(
                                            id = "coordinator",
                                            displayName = "Coordinator",
                                            layer = "presentation",
                                            fileNameTemplate = "\${FeatureClass}Coordinator.kt",
                                            sourceTemplate = "package \${Package}.feature.\${FeaturePackage}.presentation\n\nclass \${FeatureClass}Coordinator",
                                            allowedDependencyTypeIds = listOf("router"),
                                        ),
                                    ),
                                ),
                                featureNames = listOf("home"),
                            )
                        }
                    }
                }
            }
        }
        return selections
    }

    private fun assertSelectionSpecificExport(
        selection: WizardSelectionV1,
        files: Map<String, ByteArray>,
        label: String,
    ) {
        val optionIds = selection.selectedOptionIds.toSet()
        val appBuild = files.getValue("app/build.gradle.kts").decodeToString()
        val rootBuild = files.getValue("build.gradle.kts").decodeToString()
        val designSystemBuild = files.getValue("core/designsystem/build.gradle.kts").decodeToString()
        val databaseBuild = files.getValue("core/database/build.gradle.kts").decodeToString()
        val networkBuild = files.getValue("core/network/build.gradle.kts").decodeToString()
        val configuration = files.getValue(".wizard/configuration.json").decodeToString()

        if ("ui-compose" in optionIds) {
            assertTrue(
                "core/designsystem/src/main/res/values/themes.xml" in files,
                "$label missing compose themes.xml",
            )
            assertTrue(
                "app/src/main/kotlin/com/example/demo/MainActivity.kt" in files,
                "$label missing compose MainActivity",
            )
        }
        if ("ui-xml" in optionIds) {
            assertTrue(
                "app/src/main/res/layout/activity_main.xml" in files,
                "$label missing xml activity layout",
            )
            assertTrue(
                "core/designsystem/src/main/res/values/themes.xml" in files,
                "$label missing xml themes.xml",
            )
        }
        if ("di-hilt" in optionIds) {
            assertTrue("alias(libs.plugins.hilt)" in appBuild, "$label missing hilt plugin")
            assertTrue("kapt(libs.hilt.compiler)" in appBuild, "$label missing hilt compiler")
        }
        if ("di-koin" in optionIds) {
            assertTrue("implementation(libs.koin.android)" in appBuild, "$label missing koin dependency")
            assertTrue("startKoin" in files.getValue("app/src/main/kotlin/com/example/demo/GeneratedApplication.kt").decodeToString())
        }
        if ("di-dagger2" in optionIds) {
            assertTrue("kapt(libs.dagger.compiler)" in appBuild, "$label missing dagger compiler")
        }
        if ("library-room" in optionIds) {
            assertTrue("alias(libs.plugins.ksp)" in databaseBuild, "$label missing room ksp plugin")
            assertTrue("ksp(libs.room.compiler)" in databaseBuild, "$label missing room compiler")
            assertTrue(
                "core/database/src/main/kotlin/com/example/demo/core/database/AppDatabase.kt" in files,
                "$label missing AppDatabase",
            )
        }
        if ("library-retrofit" in optionIds) {
            assertTrue("api(libs.retrofit)" in networkBuild, "$label missing retrofit dependency")
            assertTrue(
                "core/network/src/main/kotlin/com/example/demo/core/network/ApiService.kt" in files,
                "$label missing ApiService",
            )
        }
        if ("library-coil" in optionIds) {
            if ("ui-compose" in optionIds) {
                assertTrue(
                    "implementation(libs.coil.compose)" in designSystemBuild,
                    "$label missing coil compose dependency",
                )
                assertTrue(
                    "core/designsystem/src/main/kotlin/com/example/demo/core/designsystem/components/NetworkImage.kt" in files,
                    "$label missing NetworkImage",
                )
            } else {
                assertTrue("implementation(libs.coil)" in designSystemBuild, "$label missing coil dependency")
                assertTrue(
                    "core/designsystem/src/main/kotlin/com/example/demo/core/designsystem/components/NetworkImageView.kt" in files,
                    "$label missing NetworkImageView",
                )
            }
        }
        if ("library-timber" in optionIds) {
            assertTrue("implementation(libs.timber)" in appBuild, "$label missing timber dependency")
            assertTrue("logging/DebugTree.kt" in files.keys.joinToString("\n"), "$label missing DebugTree")
        }
        if ("quality-detekt" in optionIds) {
            assertTrue("alias(libs.plugins.detekt)" in rootBuild, "$label missing detekt plugin")
            assertTrue("config/detekt/detekt.yml" in files, "$label missing detekt config")
        }
        if ("quality-ktlint" in optionIds) {
            assertTrue("alias(libs.plugins.ktlint)" in rootBuild, "$label missing ktlint plugin")
            assertTrue(".editorconfig" in files, "$label missing editorconfig")
        }
        if ("ci-github-actions" in optionIds) {
            assertTrue(".github/workflows/android.yml" in files, "$label missing GitHub workflow")
        }
        if ("ci-gitlab" in optionIds) {
            assertTrue(".gitlab-ci.yml" in files, "$label missing GitLab pipeline")
        }
        if (selection.architecture?.mode == ArchitectureModeV1.CUSTOM) {
            assertTrue(
                "feature/home/presentation/src/main/kotlin/com/example/demo/feature/home/presentation/HomeCoordinator.kt" in files,
                "$label missing custom architecture component",
            )
        }
        assertTrue("\"featureNames\": [" in configuration, "$label missing featureNames in configuration")
    }

    private fun <T> powerSet(values: List<T>): List<List<T>> {
        val result = mutableListOf<List<T>>()
        val upperBound = 1 shl values.size
        for (mask in 0 until upperBound) {
            val subset = mutableListOf<T>()
            values.indices.forEach { index ->
                if ((mask and (1 shl index)) != 0) {
                    subset += values[index]
                }
            }
            result += subset
        }
        return result
    }

    private fun generatedProjectSmokeEnabled(): Boolean =
        System.getenv("WIZARD_SMOKE_GENERATED_ANDROID") == "true"

    private fun generatedProjectFullSmokeEnabled(): Boolean =
        System.getenv("WIZARD_SMOKE_GENERATED_ANDROID_FULL") == "true"

    private fun localWizardApiService(): WizardApiService =
        WizardApiService(
            MergedCatalogProvider(
                localProvider = ClasspathCatalogProvider(localCatalogPaths),
                remoteProvider = EmptyCatalogProvider,
            ),
        )

    private fun assembleExportedProject(
        service: WizardApiService,
        selection: WizardSelectionV1,
        tempPrefix: String,
    ) {
        val resolved = service.resolve(
            ResolveRequestV1(
                selection = selection,
                strictMode = true,
            ),
        )
        val payload = service.export(
            ExportRequestV1(
                selection = selection,
                lockfile = resolved.lockfile,
                strictMode = true,
            ),
        )

        val tempDir = Files.createTempDirectory("wizard-$tempPrefix-")
        unzipToDirectory(Base64.getDecoder().decode(payload.artifact.archiveBase64), tempDir)
        tempDir.resolve("gradlew").toFile().setExecutable(true)

        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "${System.getProperty("user.home")}/Library/Android/sdk"

        val process = ProcessBuilder("./gradlew", ":app:assembleDebug", "--no-daemon")
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .apply {
                environment()["JAVA_HOME"] = System.getProperty("java.home")
                environment()["ANDROID_HOME"] = androidHome
                environment()["ANDROID_SDK_ROOT"] = androidHome
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, output)
    }

    private fun unzip(archiveBase64: String): Map<String, ByteArray> {
        val bytes = Base64.getDecoder().decode(archiveBase64)
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    files[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return files
    }

    private fun unzipToDirectory(bytes: ByteArray, targetDir: Path) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outputPath = targetDir.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(outputPath)
                } else {
                    Files.createDirectories(outputPath.parent)
                    Files.write(outputPath, zip.readBytes())
                }
                zip.closeEntry()
            }
        }
    }
}
