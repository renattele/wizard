package ru.renattele.wizard.engine.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import ru.renattele.wizard.contracts.v1.ExportFormatV1
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.engine.configuration.domain.AdditionalPatchBatch
import ru.renattele.wizard.engine.configuration.domain.DependencyRule
import ru.renattele.wizard.engine.configuration.domain.GenerationPlan
import ru.renattele.wizard.engine.configuration.domain.LockState
import ru.renattele.wizard.engine.configuration.domain.LockedOption
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.ResolvedOption
import kotlin.test.assertTrue

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

        assertEquals("{\"projectName\":\"Demo\"}", result.files.getValue(".wizard/configuration.json"))
        assertTrue(result.files.getValue("feature/catalog/Coordinator.kt").contains("Coordinator"))
        assertTrue(result.files.getValue("README.md").contains("Custom patch"))
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
