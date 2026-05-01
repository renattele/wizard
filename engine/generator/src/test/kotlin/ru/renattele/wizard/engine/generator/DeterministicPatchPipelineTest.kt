package ru.renattele.wizard.engine.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.renattele.wizard.contracts.v1.ExportFormatV1
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.engine.configuration.domain.AdditionalPatchBatch
import ru.renattele.wizard.engine.configuration.domain.GenerationPlan
import ru.renattele.wizard.engine.configuration.domain.LockState
import ru.renattele.wizard.engine.configuration.domain.LockedOption
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.ResolvedOption

class DeterministicPatchPipelineTest {
    private val pipeline = DeterministicPatchPipeline()

    @Test
    fun `pipeline is idempotent for same input`() {
        val request = GenerationRequest(
            plan = testPlan(),
            files = mapOf("README.md" to "# App\n"),
        )

        val first = pipeline.generate(request)
        val second = pipeline.generate(request)

        assertEquals(first.files, second.files)
        assertEquals(first.generationReport.appliedFiles, second.generationReport.appliedFiles)
    }

    @Test
    fun `export produces archive`() {
        val result = pipeline.generate(
            GenerationRequest(
                plan = testPlan(),
                files = mapOf("README.md" to "# App\n"),
                exportFormat = ExportFormatV1.ZIP,
            ),
        )

        assertNotNull(result.artifact)
        assertEquals("application/zip", result.artifact.mediaType)
    }

    @Test
    fun `pipeline applies user patch batches and seed files`() {
        val result = pipeline.generate(
            GenerationRequest(
                plan = testPlan().copy(
                    seedFiles = mapOf(".wizard/configuration.json" to "{\"projectName\":\"Demo\"}"),
                    additionalPatchBatches = listOf(
                        AdditionalPatchBatch(
                            sourceId = "custom-component:coordinator",
                            patches = listOf(
                                PatchSpec(
                                    operation = PatchOperation.ADD_FILE,
                                    targetPath = "feature/catalog/Coordinator.kt",
                                    content = "class Coordinator\n",
                                    find = null,
                                    replace = null,
                                    conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                                ),
                            ),
                        ),
                        AdditionalPatchBatch(
                            sourceId = "user-patch-1",
                            patches = listOf(
                                PatchSpec(
                                    operation = PatchOperation.APPEND_FILE,
                                    targetPath = "README.md",
                                    content = "Custom patch\n",
                                    find = null,
                                    replace = null,
                                    conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                                ),
                            ),
                        ),
                    ),
                ),
                files = mapOf("README.md" to "# App\n"),
            ),
        )

        assertEquals("{\"projectName\":\"Demo\"}\n", result.files.getValue(".wizard/configuration.json"))
        assertTrue(result.files.getValue("feature/catalog/Coordinator.kt").contains("Coordinator"))
        assertTrue(result.files.getValue("README.md").contains("Custom patch"))
    }

    @Test
    fun `pipeline renders template files and directories from resource loader`() {
        val plan = testPlan().copy(
            additionalPatchBatches = listOf(
                AdditionalPatchBatch(
                    sourceId = "template-file",
                    patches = listOf(
                        PatchSpec(
                            operation = PatchOperation.ADD_TEMPLATE_FILE,
                            targetPath = "README.md",
                            content = null,
                            resourcePath = "templates/readme.md",
                            find = null,
                            replace = null,
                            conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                            templateVariables = mapOf("ProjectName" to "Demo"),
                        ),
                    ),
                ),
                AdditionalPatchBatch(
                    sourceId = "template-dir",
                    patches = listOf(
                        PatchSpec(
                            operation = PatchOperation.ADD_TEMPLATE_DIRECTORY,
                            targetPath = "feature/home/presentation",
                            content = null,
                            resourcePath = "templates/feature",
                            find = null,
                            replace = null,
                            conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                            templateVariables = mapOf(
                                "PackagePath" to "com/example/demo",
                                "FeatureClass" to "Home",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = pipeline.generate(
            GenerationRequest(
                plan = plan,
                resourceLoader = StubTemplateResourceLoader(
                    files = mapOf("templates/readme.md" to "# \${ProjectName}\n"),
                    directories = mapOf(
                        "templates/feature" to mapOf(
                            "src/main/kotlin/\${PackagePath}/feature/\${FeatureClass}Screen.kt" to "class \${FeatureClass}Screen\n",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Compose enabled\n# Demo\n", result.files.getValue("README.md"))
        assertEquals(
            "class HomeScreen\n",
            result.files.getValue("feature/home/presentation/src/main/kotlin/com/example/demo/feature/HomeScreen.kt"),
        )
    }

    @Test
    fun `pipeline removes unresolved template markers from generated files`() {
        val result = pipeline.generate(
            GenerationRequest(
                plan = testPlan().copy(
                    additionalPatchBatches = listOf(
                        AdditionalPatchBatch(
                            sourceId = "markers",
                            patches = listOf(
                                PatchSpec(
                                    operation = PatchOperation.ADD_FILE,
                                    targetPath = "build.gradle.kts",
                                    content = """
                                        plugins {
                                            id("demo")
                                            // __PLUGIN_MARKER__
                                        }

                                        // __CONFIG_MARKER__
                                    """.trimIndent(),
                                    find = null,
                                    replace = null,
                                    conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            """
            plugins {
                id("demo")
            }
            """.trimIndent() + "\n",
            result.files.getValue("build.gradle.kts"),
        )
    }

    private fun testPlan(): GenerationPlan =
        GenerationPlan(
            templateId = "android-app",
            resolvedOptions = listOf(
                ResolvedOption(
                    id = "ui-compose",
                    type = OptionTypeV1.UI_FRAMEWORK,
                    category = "ui",
                    displayName = "Compose",
                    version = "1.0.0",
                    sourcePackId = "pack-compose",
                    artifactCoordinates = null,
                    artifactChecksum = null,
                    patches = listOf(
                        PatchSpec(
                            operation = PatchOperation.APPEND_FILE,
                            targetPath = "README.md",
                            content = "Compose enabled\n",
                            find = null,
                            replace = null,
                            conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                        ),
                    ),
                ),
            ),
            applyOrder = listOf("ui-compose"),
            strictMode = true,
            lockState = LockState(
                templateId = "android-app",
                strictMode = true,
                options = listOf(
                    LockedOption(
                        optionId = "ui-compose",
                        version = "1.0.0",
                        sourcePackId = "pack-compose",
                        artifactCoordinates = null,
                        artifactChecksum = null,
                    ),
                ),
                applyOrder = listOf("ui-compose"),
                catalogFingerprint = "catalog",
                resolutionHash = "hash",
            ),
            problems = emptyList(),
        )
}

private class StubTemplateResourceLoader(
    private val files: Map<String, String> = emptyMap(),
    private val directories: Map<String, Map<String, String>> = emptyMap(),
) : TemplateResourceLoader {
    override fun readText(path: String): String = files.getValue(path)

    override fun readDirectory(path: String): Map<String, String> = directories.getValue(path)
}
