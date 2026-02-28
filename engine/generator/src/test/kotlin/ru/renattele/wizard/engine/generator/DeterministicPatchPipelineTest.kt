package ru.renattele.wizard.engine.generator

import ru.renattele.wizard.contracts.v1.CatalogPackSourceV1
import ru.renattele.wizard.contracts.v1.LockedOptionV1
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.contracts.v1.WizardLockfile
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogPackDescriptor
import ru.renattele.wizard.manifest.OptionManifest
import ru.renattele.wizard.manifest.OptionVersionManifest
import ru.renattele.wizard.manifest.PatchOperationManifest
import ru.renattele.wizard.manifest.PatchOperationType
import ru.renattele.wizard.manifest.PluginPackManifest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicPatchPipelineTest {
    private val pipeline = DeterministicPatchPipeline()

    @Test
    fun `pipeline is idempotent for same input`() {
        val catalog = testCatalog()
        val request = GenerationRequest(
            templateId = "android-app",
            orderedOptionIds = listOf("ui-compose"),
            catalog = catalog,
            files = mapOf("README.md" to "# App\n"),
            strictMode = true,
            lockfile = WizardLockfile(
                templateId = "android-app",
                resolutionHash = "hash",
                options = listOf(
                    LockedOptionV1(
                        optionId = "ui-compose",
                        version = "1.0.0",
                        sourcePackId = "pack-compose",
                    ),
                ),
            ),
        )

        val first = pipeline.generate(request)
        val second = pipeline.generate(request)

        assertEquals(first.files, second.files)
        assertEquals(first.patchReport.appliedFiles, second.patchReport.appliedFiles)
    }

    private fun testCatalog(): CatalogBundle {
        val pack = PluginPackManifest(
            id = "pack-compose",
            version = "1.0.0",
            options = listOf(
                OptionManifest(
                    id = "ui-compose",
                    type = OptionTypeV1.UI_FRAMEWORK,
                    category = "ui",
                    displayName = "Compose",
                    description = "Compose UI",
                    version = OptionVersionManifest(recommended = "1.0.0"),
                    patches = listOf(
                        PatchOperationManifest(
                            type = PatchOperationType.APPEND_FILE,
                            targetPath = "README.md",
                            content = "Compose enabled\n",
                        ),
                    ),
                ),
            ),
        )

        return CatalogBundle(
            packs = listOf(
                CatalogPackDescriptor(
                    id = pack.id,
                    version = pack.version,
                    source = CatalogPackSourceV1.LOCAL,
                    precedence = 0,
                    pack = pack,
                ),
            ),
        )
    }
}
