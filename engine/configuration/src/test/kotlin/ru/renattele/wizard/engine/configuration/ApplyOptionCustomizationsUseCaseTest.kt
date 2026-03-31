package ru.renattele.wizard.engine.configuration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.engine.configuration.application.ApplyOptionCustomizationsUseCase
import ru.renattele.wizard.engine.configuration.application.GenerationCustomization
import ru.renattele.wizard.engine.configuration.domain.OptionParameterSpec
import ru.renattele.wizard.engine.configuration.domain.OptionParameterType
import ru.renattele.wizard.engine.configuration.domain.PatchActivation
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.ResolvedOption

class ApplyOptionCustomizationsUseCaseTest {
    private val useCase = ApplyOptionCustomizationsUseCase()

    @Test
    fun `renders option parameter defaults and shared variables into patches`() {
        val option = resolvedOption()

        val customized = useCase(
            resolvedOptions = listOf(option),
            customization = GenerationCustomization(
                sharedVariables = mapOf(
                    "Package" to "com.example.demo",
                    "PackagePath" to "com/example/demo",
                ),
                optionParameters = mapOf(
                    "arch-mvvm" to mapOf("featureName" to "Orders"),
                ),
            ),
        )

        val renderedPatches = customized.single().patches
        assertEquals("src/commonMain/kotlin/com/example/demo/presentation/OrdersViewModel.kt", renderedPatches.first().targetPath)
        assertEquals("package com.example.demo.presentation\n\nclass OrdersViewModel\n", renderedPatches.first().content)
    }

    @Test
    fun `rejects unknown option parameter ids`() {
        assertFailsWith<IllegalArgumentException> {
            useCase(
                resolvedOptions = listOf(resolvedOption()),
                customization = GenerationCustomization(
                    optionParameters = mapOf(
                        "arch-mvvm" to mapOf("missing" to "value"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `applies optional patches only when required capability is selected`() {
        val customized = useCase(
            resolvedOptions = listOf(
                resolvedOption(),
                ResolvedOption(
                    id = "ui-compose",
                    type = OptionTypeV1.UI_FRAMEWORK,
                    category = "ui",
                    displayName = "Compose",
                    version = "1.0.0",
                    sourcePackId = "pack-compose",
                    artifactCoordinates = null,
                    artifactChecksum = null,
                    providedCapabilities = listOf("ui:framework", "ui:compose"),
                    patches = emptyList(),
                ),
            ),
            customization = GenerationCustomization(
                sharedVariables = mapOf(
                    "Package" to "com.example.demo",
                    "PackagePath" to "com/example/demo",
                ),
            ),
        )

        val renderedPatches = customized.first { it.id == "arch-mvvm" }.patches
        assertEquals(2, renderedPatches.size)
        assertEquals(
            "src/commonMain/kotlin/com/example/demo/presentation/CatalogScreen.kt",
            renderedPatches.last().targetPath,
        )
    }

    @Test
    fun `skips optional patches when required capability is absent`() {
        val customized = useCase(
            resolvedOptions = listOf(resolvedOption()),
            customization = GenerationCustomization(
                sharedVariables = mapOf(
                    "Package" to "com.example.demo",
                    "PackagePath" to "com/example/demo",
                ),
            ),
        )

        val renderedPatches = customized.single().patches
        assertEquals(1, renderedPatches.size)
    }

    private fun resolvedOption(): ResolvedOption =
        ResolvedOption(
            id = "arch-mvvm",
            type = OptionTypeV1.ARCHITECTURE,
            category = "architecture",
            displayName = "MVVM",
            version = "1.0.0",
            sourcePackId = "pack-arch",
            artifactCoordinates = null,
            artifactChecksum = null,
            providedCapabilities = listOf("arch:mvvm"),
            parameters = listOf(
                OptionParameterSpec(
                    id = "featureName",
                    displayName = "Feature Name",
                    description = "",
                    type = OptionParameterType.STRING,
                    required = true,
                    defaultValue = "Catalog",
                ),
                OptionParameterSpec(
                    id = "viewModelName",
                    displayName = "ViewModel Name",
                    description = "",
                    type = OptionParameterType.STRING,
                    required = true,
                    defaultValue = "\${Param.featureName}ViewModel",
                ),
            ),
            patches = listOf(
                PatchSpec(
                    operation = PatchOperation.ADD_FILE,
                    targetPath = "src/commonMain/kotlin/\${PackagePath}/presentation/\${Param.viewModelName}.kt",
                    content = "package \${Package}.presentation\n\nclass \${Param.viewModelName}\n",
                    find = null,
                    replace = null,
                    activation = PatchActivation(),
                    conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                ),
                PatchSpec(
                    operation = PatchOperation.ADD_FILE,
                    targetPath = "src/commonMain/kotlin/\${PackagePath}/presentation/\${Param.featureName}Screen.kt",
                    content = "package \${Package}.presentation\n\nfun \${Param.featureName}Screen() = Unit\n",
                    find = null,
                    replace = null,
                    activation = PatchActivation(requiresCapabilities = listOf("ui:compose")),
                    conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                ),
            ),
        )
}
