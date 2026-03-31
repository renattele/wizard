package ru.renattele.wizard.engine.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.contracts.v1.ProblemCodeV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.WizardSelectionV1
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogPackDescriptor
import ru.renattele.wizard.manifest.OptionDependencyContract
import ru.renattele.wizard.manifest.OptionManifest
import ru.renattele.wizard.manifest.OptionVersionManifest
import ru.renattele.wizard.manifest.PluginPackManifest
import ru.renattele.wizard.manifest.TemplateManifest

class DeterministicResolutionEngineTest {
    private val resolver = DeterministicResolutionEngine()

    @Test
    fun `resolution is deterministic for same input`() {
        val catalog = testCatalog()
        val request = ResolveRequestV1(
            selection = WizardSelectionV1(
                templateId = "android-app",
                selectedOptionIds = listOf("feature-analytics"),
            ),
        )

        val first = resolver.resolve(request, catalog)
        val second = resolver.resolve(request, catalog)

        assertEquals(first.applyOrder, second.applyOrder)
        assertEquals(first.lockfile.resolutionHash, second.lockfile.resolutionHash)
    }

    @Test
    fun `hard conflict is detected`() {
        val catalog = testCatalog(
            extraOptions = listOf(
                OptionManifest(
                    id = "ui-xml",
                    type = OptionTypeV1.UI_FRAMEWORK,
                    category = "ui",
                    displayName = "XML",
                    description = "XML UI",
                    dependency = OptionDependencyContract(conflictsHard = listOf("ui-compose")),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
            ),
        )
        val request = ResolveRequestV1(
            selection = WizardSelectionV1(
                templateId = "android-app",
                selectedOptionIds = listOf("ui-compose", "ui-xml"),
            ),
        )

        val response = resolver.resolve(request, catalog)

        assertTrue(response.problems.any { it.code == ProblemCodeV1.HARD_CONFLICT })
    }

    @Test
    fun `version range violation is reported`() {
        val catalog = testCatalog()
        val request = ResolveRequestV1(
            selection = WizardSelectionV1(
                templateId = "android-app",
                selectedOptionIds = listOf("ui-compose"),
                optionVersions = mapOf("ui-compose" to "3.0.0"),
            ),
        )

        val response = resolver.resolve(request, catalog)

        assertTrue(response.problems.any { it.code == ProblemCodeV1.VERSION_OUT_OF_RANGE })
    }

    private fun testCatalog(extraOptions: List<OptionManifest> = emptyList()): CatalogBundle {
        val basePack = PluginPackManifest(
            id = "pack-core",
            version = "1.0.0",
            templates = listOf(
                TemplateManifest(
                    id = "android-app",
                    displayName = "Android App",
                    description = "App template",
                    version = "1.0.0",
                    baseOptionIds = listOf("base-kotlin"),
                ),
            ),
            options = listOf(
                OptionManifest(
                    id = "base-kotlin",
                    type = OptionTypeV1.BASE,
                    category = "base",
                    displayName = "Kotlin Base",
                    description = "Base stack",
                    dependency = OptionDependencyContract(),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
                OptionManifest(
                    id = "ui-compose",
                    type = OptionTypeV1.UI_FRAMEWORK,
                    category = "ui",
                    displayName = "Compose",
                    description = "Compose UI",
                    dependency = OptionDependencyContract(),
                    version = OptionVersionManifest(
                        recommended = "1.0.0",
                        range = ">=1.0.0 <2.0.0",
                    ),
                ),
                OptionManifest(
                    id = "feature-analytics",
                    type = OptionTypeV1.FEATURE_PLUGIN,
                    category = "feature",
                    displayName = "Analytics",
                    description = "Analytics feature",
                    dependency = OptionDependencyContract(requiresOptionIds = listOf("ui-compose")),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
            ) + extraOptions,
        )

        return CatalogBundle(
            packs = listOf(
                CatalogPackDescriptor(
                    id = basePack.id,
                    version = basePack.version,
                    source = ru.renattele.wizard.contracts.v1.CatalogPackSourceV1.LOCAL,
                    precedence = 0,
                    pack = basePack,
                ),
            ),
        )
    }
}
