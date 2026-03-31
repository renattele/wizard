package ru.renattele.wizard.engine.configuration

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogPackDescriptor
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.catalog.CatalogRequest
import ru.renattele.wizard.engine.configuration.application.LoadCatalogUseCase
import ru.renattele.wizard.engine.configuration.application.ResolveConfigurationUseCase
import ru.renattele.wizard.engine.configuration.application.VerifyLockUseCase
import ru.renattele.wizard.engine.configuration.domain.ProblemCode
import ru.renattele.wizard.manifest.OptionDependencyContract
import ru.renattele.wizard.manifest.OptionManifest
import ru.renattele.wizard.manifest.OptionVersionManifest
import ru.renattele.wizard.manifest.PluginPackManifest
import ru.renattele.wizard.manifest.TemplateManifest

class ConfigurationUseCasesTest {
    @Test
    fun `catalog validation reports unknown base option`() {
        val loadCatalog = LoadCatalogUseCase(staticProvider(invalidCatalog()))
        val catalog = loadCatalog()

        assertTrue(catalog.problems.any { it.code == ProblemCode.UNKNOWN_BASE_OPTION })
    }

    @Test
    fun `resolve reports ambiguous capability only when a consumer requires it`() {
        val loadCatalog = LoadCatalogUseCase(staticProvider(invalidCatalog()))
        val resolve = ResolveConfigurationUseCase(loadCatalog)

        val resolution = resolve(
            templateId = "broken-app",
            selectedOptionIds = listOf("consumer"),
            optionVersions = emptyMap(),
            strictMode = false,
        )

        assertTrue(resolution.problems.any { it.code == ProblemCode.AMBIGUOUS_CAPABILITY_PROVIDER })
    }

    @Test
    fun `verify lock rejects stale fingerprint`() {
        val loadCatalog = LoadCatalogUseCase(staticProvider(validCatalog()))
        val resolve = ResolveConfigurationUseCase(loadCatalog)
        val verify = VerifyLockUseCase(resolve, loadCatalog)
        val resolved = resolve(
            templateId = "android-app",
            selectedOptionIds = listOf("ui-compose"),
            optionVersions = emptyMap(),
            strictMode = true,
        )

        val staleLock = resolved.lockState.copy(catalogFingerprint = "stale")

        assertFailsWith<IllegalArgumentException> {
            verify(
                templateId = "android-app",
                selectedOptionIds = listOf("ui-compose"),
                optionVersions = emptyMap(),
                strictMode = true,
                providedLock = staleLock,
            )
        }
    }

    private fun staticProvider(bundle: CatalogBundle): CatalogProvider = object : CatalogProvider {
        override fun loadCatalog(request: CatalogRequest): CatalogBundle = bundle
    }

    private fun validCatalog(): CatalogBundle {
        val pack = PluginPackManifest(
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
                    displayName = "Base Kotlin",
                    description = "Base",
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
                OptionManifest(
                    id = "ui-compose",
                    type = OptionTypeV1.UI_FRAMEWORK,
                    category = "ui",
                    displayName = "Compose",
                    description = "UI",
                    dependency = OptionDependencyContract(requiresOptionIds = listOf("base-kotlin")),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
            ),
        )

        return CatalogBundle(
            packs = listOf(
                CatalogPackDescriptor(
                    id = pack.id,
                    version = pack.version,
                    source = ru.renattele.wizard.contracts.v1.CatalogPackSourceV1.LOCAL,
                    precedence = 0,
                    pack = pack,
                ),
            ),
        )
    }

    private fun invalidCatalog(): CatalogBundle {
        val pack = PluginPackManifest(
            id = "pack-invalid",
            version = "1.0.0",
            templates = listOf(
                TemplateManifest(
                    id = "broken-app",
                    displayName = "Broken App",
                    description = "Broken template",
                    version = "1.0.0",
                    baseOptionIds = listOf("missing-base"),
                ),
            ),
            options = listOf(
                OptionManifest(
                    id = "feature-a",
                    type = OptionTypeV1.FEATURE_PLUGIN,
                    category = "feature",
                    displayName = "Feature A",
                    description = "Provides same capability",
                    dependency = OptionDependencyContract(providesCapabilities = listOf("cap:shared")),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
                OptionManifest(
                    id = "feature-b",
                    type = OptionTypeV1.FEATURE_PLUGIN,
                    category = "feature",
                    displayName = "Feature B",
                    description = "Provides same capability",
                    dependency = OptionDependencyContract(providesCapabilities = listOf("cap:shared")),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
                OptionManifest(
                    id = "consumer",
                    type = OptionTypeV1.FEATURE_PLUGIN,
                    category = "feature",
                    displayName = "Consumer",
                    description = "Needs shared capability",
                    dependency = OptionDependencyContract(requiresCapabilities = listOf("cap:shared")),
                    version = OptionVersionManifest(recommended = "1.0.0"),
                ),
            ),
        )

        return CatalogBundle(
            packs = listOf(
                CatalogPackDescriptor(
                    id = pack.id,
                    version = pack.version,
                    source = ru.renattele.wizard.contracts.v1.CatalogPackSourceV1.LOCAL,
                    precedence = 0,
                    pack = pack,
                ),
            ),
        )
    }
}
