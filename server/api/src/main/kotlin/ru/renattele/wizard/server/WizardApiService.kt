package ru.renattele.wizard.server

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.renattele.wizard.contracts.v1.ArchitectureModeV1
import ru.renattele.wizard.contracts.v1.AutoAddedOptionV1
import ru.renattele.wizard.contracts.v1.CatalogOptionDependencyV1
import ru.renattele.wizard.contracts.v1.CatalogOptionV1
import ru.renattele.wizard.contracts.v1.CatalogOptionVersionV1
import ru.renattele.wizard.contracts.v1.CatalogPackSourceV1
import ru.renattele.wizard.contracts.v1.CatalogPackV1
import ru.renattele.wizard.contracts.v1.CatalogPatchSummaryV1
import ru.renattele.wizard.contracts.v1.CatalogResponseV1
import ru.renattele.wizard.contracts.v1.ConflictStrategyV1
import ru.renattele.wizard.contracts.v1.ExportRequestV1
import ru.renattele.wizard.contracts.v1.ExportResponseV1
import ru.renattele.wizard.contracts.v1.GeneratedFilePreviewV1
import ru.renattele.wizard.contracts.v1.OptionParameterAllowedValueV1
import ru.renattele.wizard.contracts.v1.OptionParameterTypeV1
import ru.renattele.wizard.contracts.v1.OptionParameterV1
import ru.renattele.wizard.contracts.v1.PatchOperationV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.PreviewResponseV1
import ru.renattele.wizard.contracts.v1.ProblemCodeV1
import ru.renattele.wizard.contracts.v1.ProblemV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.ResolvedOptionV1
import ru.renattele.wizard.contracts.v1.SeverityV1
import ru.renattele.wizard.contracts.v1.TemplateDescriptorV1
import ru.renattele.wizard.contracts.v1.UserPatchV1
import ru.renattele.wizard.contracts.v1.WizardLockfile
import ru.renattele.wizard.contracts.v1.WizardSelectionV1
import ru.renattele.wizard.contracts.v1.LockedOptionV1
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.configuration.application.ApplyOptionCustomizationsUseCase
import ru.renattele.wizard.engine.configuration.application.GenerationCustomization
import ru.renattele.wizard.engine.configuration.application.LoadCatalogUseCase
import ru.renattele.wizard.engine.configuration.application.PrepareGenerationUseCase
import ru.renattele.wizard.engine.configuration.application.ResolveConfigurationUseCase
import ru.renattele.wizard.engine.configuration.application.VerifyLockUseCase
import ru.renattele.wizard.engine.configuration.domain.AdditionalPatchBatch
import ru.renattele.wizard.engine.configuration.domain.ConfigurationCatalog
import ru.renattele.wizard.engine.configuration.domain.LockState
import ru.renattele.wizard.engine.configuration.domain.LockedOption
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.PreparedGeneration
import ru.renattele.wizard.engine.configuration.domain.Problem
import ru.renattele.wizard.engine.configuration.domain.ProblemCode
import ru.renattele.wizard.engine.configuration.domain.ProblemSeverity
import ru.renattele.wizard.engine.generator.ClasspathTemplateResourceLoader
import ru.renattele.wizard.engine.generator.DeterministicPatchPipeline
import ru.renattele.wizard.engine.generator.GenerationRequest
import ru.renattele.wizard.engine.generator.PatchPipeline
import ru.renattele.wizard.engine.generator.TemplateResourceLoader

class WizardApiService(
    catalogProvider: CatalogProvider,
    private val pipeline: PatchPipeline = DeterministicPatchPipeline(),
    private val resourceLoader: TemplateResourceLoader = ClasspathTemplateResourceLoader(),
) {
    private data class FeatureSpec(
        val rawName: String,
        val packageName: String,
        val className: String,
        val route: String,
    )

    private data class TemplateModules(
        val includeNetwork: Boolean,
        val includeDatabase: Boolean,
        val includeTesting: Boolean,
        val includeAnalytics: Boolean,
        val includeObservability: Boolean,
        val includeSync: Boolean,
    )

    private val loadCatalog = LoadCatalogUseCase(catalogProvider)
    private val resolveConfiguration = ResolveConfigurationUseCase(loadCatalog)
    private val verifyLock = VerifyLockUseCase(resolveConfiguration, loadCatalog)
    private val prepareGeneration = PrepareGenerationUseCase(verifyLock)
    private val applyOptionCustomizations = ApplyOptionCustomizationsUseCase()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun catalog(): CatalogResponseV1 {
        val catalog = loadCatalog()
        return CatalogResponseV1(
            schemaVersion = "2.1.0",
            packs = catalog.packs.map { pack ->
                CatalogPackV1(
                    id = pack.id,
                    version = pack.version,
                    source = enumValueOf<CatalogPackSourceV1>(pack.source),
                )
            },
            templates = catalog.templates.values
                .sortedBy { it.id }
                .map { template ->
                    TemplateDescriptorV1(
                        id = template.id,
                        displayName = template.displayName,
                        description = template.description,
                        version = template.version,
                        tags = template.tags,
                        baseOptionIds = template.baseOptionIds,
                    )
                },
            options = catalog.options.values
                .sortedBy { it.id }
                .map { option ->
                    CatalogOptionV1(
                        id = option.id,
                        type = option.type,
                        category = option.category,
                        displayName = option.displayName,
                        description = option.description,
                        defaultEnabled = option.defaultEnabled,
                        dependency = CatalogOptionDependencyV1(
                            requiresOptionIds = option.dependency.requiresOptionIds,
                            requiresCapabilities = option.dependency.requiresCapabilities,
                            providesCapabilities = option.dependency.providesCapabilities,
                            conflictsHard = option.dependency.conflictsHard,
                        ),
                        version = CatalogOptionVersionV1(
                            recommended = option.versionPolicy.recommended,
                            supported = option.versionPolicy.supported,
                            range = option.versionPolicy.range,
                        ),
                        parameters = option.parameters.map { parameter ->
                            OptionParameterV1(
                                id = parameter.id,
                                displayName = parameter.displayName,
                                description = parameter.description,
                                type = parameter.type.toApiType(),
                                required = parameter.required,
                                defaultValue = parameter.defaultValue,
                                allowedValues = parameter.allowedValues.map { allowedValue ->
                                    OptionParameterAllowedValueV1(
                                        value = allowedValue.value,
                                        displayName = allowedValue.displayName,
                                    )
                                },
                            )
                        },
                        patches = option.patches.map { patch ->
                            CatalogPatchSummaryV1(
                                operation = patch.operation.toApiOperation(),
                                targetPath = patch.targetPath,
                                conflictStrategy = patch.conflictStrategy.toApiStrategy(),
                                resourcePath = patch.resourcePath,
                            )
                        },
                    )
                },
        )
    }

    fun resolve(request: ResolveRequestV1): ResolveResponseV1 {
        val selection = normalizedSelection(request.selection)
        validateCustomization(selection)
        val resolution = resolveConfiguration(
            templateId = selection.templateId,
            selectedOptionIds = selection.selectedOptionIds,
            optionVersions = selection.optionVersions,
            strictMode = request.strictMode,
        )
        applyOptionCustomizations(
            resolvedOptions = resolution.resolvedOptions,
            customization = buildGenerationCustomization(selection),
        )
        val configurationHash = configurationHashFor(selection)
        return resolution.toApiResponse(configurationHash)
    }

    fun preview(request: PreviewRequestV1): PreviewResponseV1 {
        val prepared = prepare(request.selection, request.strictMode, request.lockfile)
        val generated = pipeline.generate(
            GenerationRequest(
                plan = prepared.plan,
                resourceLoader = resourceLoader,
            ),
        )
        return PreviewResponseV1(
            files = generated.files.entries.map { (path, content) ->
                GeneratedFilePreviewV1(path = path, content = content)
            },
            generationReport = generated.generationReport,
            problems = prepared.plan.problems.map { problem -> problem.toApiProblem() },
            lockVerified = prepared.lockVerified,
        )
    }

    fun export(request: ExportRequestV1): ExportResponseV1 {
        val prepared = prepare(request.selection, request.strictMode, request.lockfile)
        val generated = pipeline.generate(
            GenerationRequest(
                plan = prepared.plan,
                exportFormat = request.format,
                resourceLoader = resourceLoader,
            ),
        )

        return ExportResponseV1(
            artifact = requireNotNull(generated.artifact) { "Export artifact was not produced" },
            generationReport = generated.generationReport,
            problems = prepared.plan.problems.map { problem -> problem.toApiProblem() },
            lockVerified = prepared.lockVerified,
        )
    }

    private fun prepare(
        selection: WizardSelectionV1,
        strictMode: Boolean,
        lockfile: WizardLockfile?,
    ): PreparedGeneration {
        val normalizedSelection = normalizedSelection(selection)
        validateCustomization(normalizedSelection)
        val configurationHash = configurationHashFor(normalizedSelection)
        if (strictMode && lockfile?.configurationHash != configurationHash) {
            throw IllegalArgumentException("Lockfile is stale and must be regenerated")
        }

        val prepared = prepareGeneration(
            templateId = normalizedSelection.templateId,
            selectedOptionIds = normalizedSelection.selectedOptionIds,
            optionVersions = normalizedSelection.optionVersions,
            strictMode = strictMode,
            providedLock = lockfile?.toDomainLockState(),
        )
        val catalog = loadCatalog()

        return prepared.copy(
            plan = prepared.plan.copy(
                templatePatchBatches = buildTemplatePatchBatches(catalog, normalizedSelection),
                resolvedOptions = applyOptionCustomizations(
                    resolvedOptions = prepared.plan.resolvedOptions,
                    customization = buildGenerationCustomization(normalizedSelection),
                ),
                seedFiles = mapOf(".wizard/configuration.json" to json.encodeToString(normalizedSelection)),
                additionalPatchBatches = buildAdditionalPatchBatches(normalizedSelection),
            ),
            lockVerified = prepared.lockVerified && (lockfile == null || lockfile.configurationHash == configurationHash),
        )
    }

    private fun normalizedSelection(selection: WizardSelectionV1): WizardSelectionV1 {
        val presetArchitectureId = selection.architecture
            ?.takeIf { it.mode == ArchitectureModeV1.PRESET }
            ?.presetPatternId
            ?.takeIf(String::isNotBlank)
        val projectConfig = selection.projectConfig

        val normalizedOptionIds = (selection.selectedOptionIds + listOfNotNull(presetArchitectureId))
            .distinct()
            .sorted()

        return selection.copy(
            selectedOptionIds = normalizedOptionIds,
            optionVersions = selection.optionVersions.toSortedMap(),
            optionParameters = selection.optionParameters
                .toSortedMap()
                .mapValues { (_, values) -> values.toSortedMap() },
            contextVars = selection.contextVars.toSortedMap(),
            projectConfig = projectConfig?.copy(
                featureNames = projectConfig.featureNames
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
                releaseArtifactTypes = projectConfig.releaseArtifactTypes
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
            ),
        )
    }

    private fun buildGenerationCustomization(selection: WizardSelectionV1): GenerationCustomization =
        GenerationCustomization(
            sharedVariables = templateVariables(selection),
            optionParameters = selection.optionParameters,
        )

    private fun buildTemplatePatchBatches(
        catalog: ConfigurationCatalog,
        selection: WizardSelectionV1,
    ): List<AdditionalPatchBatch> {
        val template = catalog.templates[selection.templateId]
            ?: throw IllegalArgumentException("Unknown template '${selection.templateId}'")
        val templateVars = templateVariables(selection)
        val batches = mutableListOf<AdditionalPatchBatch>()

        if (template.patches.isNotEmpty()) {
            batches += AdditionalPatchBatch(
                sourceId = "template:${template.id}",
                patches = template.patches.map { patch -> patch.renderWith(templateVars) },
            )
        }

        if (selection.templateId == "android-app-lite") {
            val modules = templateModules(selection)
            if (modules.includeNetwork) {
                batches += sharedModuleBatch(
                    sourceId = "template:${template.id}:core-network",
                    targetPath = "core/network",
                    resourcePath = "packs/templates/android-base/core/network",
                    templateVariables = templateVars,
                )
            }
            if (modules.includeDatabase) {
                batches += sharedModuleBatch(
                    sourceId = "template:${template.id}:core-database",
                    targetPath = "core/database",
                    resourcePath = "packs/templates/android-base/core/database",
                    templateVariables = templateVars,
                )
            }
            if (modules.includeTesting) {
                batches += sharedModuleBatch(
                    sourceId = "template:${template.id}:core-testing",
                    targetPath = "core/testing",
                    resourcePath = "packs/templates/android-base/core/testing",
                    templateVariables = templateVars,
                )
            }
        }

        return batches
    }

    private fun sharedModuleBatch(
        sourceId: String,
        targetPath: String,
        resourcePath: String,
        templateVariables: Map<String, String>,
    ): AdditionalPatchBatch =
        AdditionalPatchBatch(
            sourceId = sourceId,
            patches = listOf(
                PatchSpec(
                    operation = PatchOperation.ADD_TEMPLATE_DIRECTORY,
                    targetPath = targetPath,
                    content = null,
                    resourcePath = resourcePath,
                    find = null,
                    replace = null,
                    conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                    templateVariables = templateVariables,
                ),
            ),
        )

    private fun buildAdditionalPatchBatches(selection: WizardSelectionV1): List<AdditionalPatchBatch> {
        val templateVars = templateVariables(selection)
        val batches = mutableListOf<AdditionalPatchBatch>()
        val features = featureSpecs(selection)
        val uiMode = selectedUiMode(selection)
        val selectedArchitecture = selectedArchitectureId(selection)
        val architectureTemplateId = selectedArchitecture?.removePrefix("arch-")

        features.forEach { feature ->
            val featureVars = templateVars + featureVariables(feature)
            val featureRoot = "feature/${feature.packageName}"
            val presentationRoot = "$featureRoot/presentation"

            batches += AdditionalPatchBatch(
                sourceId = "feature:${feature.packageName}:domain",
                patches = listOf(
                    PatchSpec(
                        operation = PatchOperation.ADD_TEMPLATE_DIRECTORY,
                        targetPath = "$featureRoot/domain",
                        content = null,
                        resourcePath = "packs/templates/feature/domain",
                        find = null,
                        replace = null,
                        conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                        templateVariables = featureVars,
                    ),
                ),
            )
            batches += AdditionalPatchBatch(
                sourceId = "feature:${feature.packageName}:data",
                patches = listOf(
                    PatchSpec(
                        operation = PatchOperation.ADD_TEMPLATE_DIRECTORY,
                        targetPath = "$featureRoot/data",
                        content = null,
                        resourcePath = "packs/templates/feature/data",
                        find = null,
                        replace = null,
                        conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                        templateVariables = featureVars,
                    ),
                ),
            )
            batches += AdditionalPatchBatch(
                sourceId = "feature:${feature.packageName}:presentation-base",
                patches = listOf(
                    PatchSpec(
                        operation = PatchOperation.ADD_TEMPLATE_DIRECTORY,
                        targetPath = presentationRoot,
                        content = null,
                        resourcePath = when (uiMode) {
                            "compose" -> "packs/templates/feature/compose/presentation"
                            else -> "packs/templates/feature/xml/presentation"
                        },
                        find = null,
                        replace = null,
                        conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                        templateVariables = featureVars,
                    ),
                ),
            )

            when {
                selectedArchitecture != null -> {
                    val architectureResourcePath = "packs/templates/architecture/$architectureTemplateId/$uiMode"
                    val targetDir = when (uiMode) {
                        "compose" -> "$presentationRoot/src/main/kotlin/${templateVars.getValue("PackagePath")}/feature/${feature.packageName}/presentation"
                        else -> "$presentationRoot/src/main/kotlin/${templateVars.getValue("PackagePath")}/feature/${feature.packageName}/presentation"
                    }
                    batches += AdditionalPatchBatch(
                        sourceId = "architecture:$selectedArchitecture:${feature.packageName}",
                        patches = listOf(
                            PatchSpec(
                                operation = PatchOperation.ADD_TEMPLATE_DIRECTORY,
                                targetPath = targetDir,
                                content = null,
                                resourcePath = architectureResourcePath,
                                find = null,
                                replace = null,
                                conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                                templateVariables = featureVars,
                            ),
                        ),
                    )
                }

                selection.architecture?.mode == ArchitectureModeV1.CUSTOM -> {
                    val targetDir = "$presentationRoot/src/main/kotlin/${templateVars.getValue("PackagePath")}/feature/${feature.packageName}/presentation"
                    val placeholderContent = if (uiMode == "compose") {
                        "package ${templateVars.getValue("Package")}.feature.${feature.packageName}.presentation\n\nimport androidx.compose.material3.Text\nimport androidx.compose.runtime.Composable\n\n@Composable\nfun ${feature.className}Screen() {\n    Text(text = \"${feature.className}\")\n}\n"
                    } else {
                        "package ${templateVars.getValue("Package")}.feature.${feature.packageName}.presentation\n\nimport androidx.fragment.app.Fragment\n\nclass ${feature.className}Fragment : Fragment(R.layout.fragment_${feature.packageName})\n"
                    }
                    batches += AdditionalPatchBatch(
                        sourceId = "custom-placeholder:${feature.packageName}",
                        patches = listOf(
                            PatchSpec(
                                operation = PatchOperation.ADD_FILE,
                                targetPath = "$targetDir/${if (uiMode == "compose") "${feature.className}Screen.kt" else "${feature.className}Fragment.kt"}",
                                content = placeholderContent,
                                find = null,
                                replace = null,
                                conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                            ),
                        ),
                    )
                }
            }
        }

        val architecture = selection.architecture
        if (architecture?.mode == ArchitectureModeV1.CUSTOM) {
            features.forEach { feature ->
                architecture.customComponentTypes.forEach { component ->
                    val moduleName = when (component.layer.lowercase()) {
                        "presentation" -> "presentation"
                        "domain" -> "domain"
                        "data" -> "data"
                        else -> throw IllegalArgumentException("Unsupported custom component layer '${component.layer}'")
                    }
                    val componentVars = templateVars + featureVariables(feature) + mapOf(
                        "ComponentId" to component.id,
                        "ComponentName" to component.displayName,
                        "Layer" to component.layer,
                        "AllowedDependencies" to component.allowedDependencyTypeIds.joinToString(", "),
                    )
                    val rawPath = renderTemplate(component.fileNameTemplate, componentVars)
                    val defaultSourceDir = "feature/${feature.packageName}/$moduleName/src/main/kotlin/${templateVars.getValue("PackagePath")}/feature/${feature.packageName}/${component.layer.lowercase()}"
                    val normalizedPath = normalizePath(
                        if (rawPath.contains('/')) {
                            "feature/${feature.packageName}/$moduleName/$rawPath"
                        } else {
                            "$defaultSourceDir/$rawPath"
                        },
                    )
                    val renderedSource = renderTemplate(component.sourceTemplate, componentVars)
                    batches += AdditionalPatchBatch(
                        sourceId = "custom-component:${feature.packageName}:${component.id}",
                        patches = listOf(
                            PatchSpec(
                                operation = PatchOperation.ADD_FILE,
                                targetPath = normalizedPath,
                                content = renderedSource,
                                find = null,
                                replace = null,
                                conflictStrategy = PatchConflictStrategy.MERGE_WITH_RULE,
                            ),
                        ),
                    )
                }
            }
        }

        selection.customPatches.forEachIndexed { index, patch ->
            val sourceId = patch.sourceId?.takeIf(String::isNotBlank) ?: "user-patch-${index + 1}"
            val renderedTargetPath = normalizePath(renderTemplate(patch.targetPath, templateVars))
            batches += AdditionalPatchBatch(
                sourceId = sourceId,
                patches = listOf(
                    PatchSpec(
                        operation = patch.operation.toDomainOperation(),
                        targetPath = renderedTargetPath,
                        content = patch.content?.let { renderTemplate(it, templateVars) },
                        find = patch.find?.let { renderTemplate(it, templateVars) },
                        replace = patch.replace?.let { renderTemplate(it, templateVars) },
                        conflictStrategy = patch.conflictStrategy.toDomainStrategy(),
                    ),
                ),
            )
        }

        return batches
    }

    private fun templateVariables(selection: WizardSelectionV1): Map<String, String> {
        val projectConfig = selection.projectConfig
        val features = featureSpecs(selection)
        val startFeature = features.first()
        val selectedArchitecture = selectedArchitectureId(selection)
        val templateModules = templateModules(selection)
        val vars = linkedMapOf<String, String>()
        vars += selection.contextVars.toSortedMap()
        vars["TemplateId"] = selection.templateId
        vars["ProjectName"] = projectConfig?.projectName?.takeIf(String::isNotBlank) ?: "Wizard Project"
        val packageId = projectConfig?.packageId?.takeIf(String::isNotBlank) ?: "wizard.generated"
        vars["Package"] = packageId
        vars["PackagePath"] = packageId.replace('.', '/')
        vars["ModulePreset"] = projectConfig?.modulePreset ?: "android-clean"
        vars["MinSdk"] = (projectConfig?.minSdk ?: 24).toString()
        vars["TargetSdk"] = (projectConfig?.targetSdk ?: 35).toString()
        vars["ReleaseTarget"] = projectConfig?.releaseTarget ?: "git-release-assets"
        vars["ReleaseArtifactTypesCsv"] = projectConfig?.releaseArtifactTypes?.joinToString(", ").orEmpty()
        vars["UiFramework"] = selectedUiMode(selection)
        vars["TemplateFlavor"] = when (selection.templateId) {
            "android-app-lite" -> "lite"
            "android-app-scalable" -> "scalable"
            else -> "balanced"
        }
        vars["ArchitectureViewModelSuffix"] = architectureViewModelSuffix(selection)
        vars["ArchitectureStoreSuffix"] = architectureStoreSuffix(selection)
        vars["ArchitecturePresenterSuffix"] = architecturePresenterSuffix(selection)
        vars["ArchitectureContractSuffix"] = architectureContractSuffix(selection)
        vars["ArchitectureRouterSuffix"] = architectureRouterSuffix(selection)
        vars["ArchitectureInteractorSuffix"] = architectureInteractorSuffix(selection)
        vars["ArchitectureStateSuffix"] = architectureStateSuffix(selection)
        vars["ArchitectureEventSuffix"] = architectureEventSuffix(selection)
        vars["ArchitectureEffectSuffix"] = architectureEffectSuffix(selection)
        vars["ArchitectureIntentSuffix"] = architectureIntentSuffix(selection)
        vars["ArchitectureUseCaseSuffix"] = architectureUseCaseSuffix(selection)
        vars["ArchitectureNavigatorOwner"] = architectureNavigatorOwner(selection)
        vars["DesignSystemPrefix"] = projectConfig?.designSystemPrefix?.takeIf(String::isNotBlank) ?: "T"
        vars["PrimaryColor"] = projectConfig?.primaryColor?.takeIf(String::isNotBlank) ?: "#6750A4"
        vars["SecondaryColor"] = projectConfig?.secondaryColor?.takeIf(String::isNotBlank) ?: "#625B71"
        vars["PrimaryColorHex"] = toComposeColor(projectConfig?.primaryColor, "#6750A4")
        vars["SecondaryColorHex"] = toComposeColor(projectConfig?.secondaryColor, "#625B71")
        vars["PrimaryColorXml"] = toXmlColor(projectConfig?.primaryColor, "#6750A4")
        vars["SecondaryColorXml"] = toXmlColor(projectConfig?.secondaryColor, "#625B71")
        projectConfig?.ciTemplate?.takeIf(String::isNotBlank)?.let { vars["CiTemplate"] = it }
        selection.architecture?.presetPatternId?.takeIf(String::isNotBlank)?.let { vars["PresetPattern"] = it }
        vars["FeatureIncludes"] = features.joinToString(separator = "\n") { feature ->
            listOf(
                "include(\":feature:${feature.packageName}:presentation\")",
                "include(\":feature:${feature.packageName}:domain\")",
                "include(\":feature:${feature.packageName}:data\")",
            ).joinToString(separator = "\n")
        }
        vars["SharedModuleIncludes"] = buildList {
            if (templateModules.includeNetwork) add("include(\":core:network\")")
            if (templateModules.includeDatabase) add("include(\":core:database\")")
            if (templateModules.includeTesting) add("include(\":core:testing\")")
            if (templateModules.includeAnalytics) add("include(\":core:analytics\")")
            if (templateModules.includeObservability) add("include(\":core:observability\")")
            if (templateModules.includeSync) add("include(\":core:sync\")")
        }.joinToString(separator = "\n")
        vars["FeatureReadmeList"] = features.joinToString(separator = "\n") { feature ->
            "- `feature:${feature.packageName}:presentation`, `feature:${feature.packageName}:domain`, `feature:${feature.packageName}:data`"
        }
        vars["SharedModuleReadmeList"] = buildList {
            if (templateModules.includeNetwork) add("- `core:network`")
            if (templateModules.includeDatabase) add("- `core:database`")
            if (templateModules.includeTesting) add("- `core:testing`")
            if (templateModules.includeAnalytics) add("- `core:analytics`")
            if (templateModules.includeObservability) add("- `core:observability`")
            if (templateModules.includeSync) add("- `core:sync`")
        }.joinToString(separator = "\n")
        vars["AppFeaturePresentationDependencies"] = features.joinToString(separator = "\n") { feature ->
            "    implementation(project(\":feature:${feature.packageName}:presentation\"))"
        }
        vars["AppFeatureDataDependencies"] = features.joinToString(separator = "\n") { feature ->
            "    implementation(project(\":feature:${feature.packageName}:data\"))"
        }
        vars["AppSharedModuleDependencies"] = buildList {
            if (templateModules.includeAnalytics) add("    implementation(project(\":core:analytics\"))")
            if (templateModules.includeObservability) add("    implementation(project(\":core:observability\"))")
            if (templateModules.includeSync) add("    implementation(project(\":core:sync\"))")
        }.joinToString(separator = "\n")
        vars["FeatureDataInfraDependencies"] = buildList {
            if (templateModules.includeNetwork) add("    implementation(project(\":core:network\"))")
            if (templateModules.includeDatabase) add("    implementation(project(\":core:database\"))")
        }.joinToString(separator = "\n")
        vars["FeatureRepositoryImports"] = features.joinToString(separator = "\n") { feature ->
            "import $packageId.feature.${feature.packageName}.data.${feature.className}RepositoryImpl\n" +
                "import $packageId.feature.${feature.packageName}.domain.${feature.className}Repository"
        }
        vars["FeatureHiltProviderMethods"] = features.joinToString(separator = "\n\n") { feature ->
            "    @Provides\n" +
                "    @Singleton\n" +
                "    fun provide${feature.className}Repository(): ${feature.className}Repository = ${feature.className}RepositoryImpl()"
        }
        vars["FeatureDaggerProviderMethods"] = features.joinToString(separator = "\n\n") { feature ->
            "    @Provides\n" +
                "    @Singleton\n" +
                "    fun provide${feature.className}Repository(): ${feature.className}Repository = ${feature.className}RepositoryImpl()"
        }
        vars["FeatureKoinBindings"] = features.joinToString(separator = "\n") { feature ->
            "        single<${feature.className}Repository> { ${feature.className}RepositoryImpl() }"
        }
        vars["StartFeatureRoute"] = startFeature.route
        vars["StartFeaturePackage"] = startFeature.packageName
        vars["StartFeatureClass"] = startFeature.className
        vars["FeatureXmlDestinations"] = features.joinToString(separator = ",\n") { feature ->
            "    FeatureDestination(route = \"${feature.route}\", title = \"${feature.className}\")"
        }
        vars["FeatureComposableImports"] = features
            .map { feature ->
                composeNavigationImports(packageId, feature, selectedArchitecture, selection)
            }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(separator = "\n")
        vars["FeatureComposableDestinations"] = features.joinToString(separator = "\n") { feature ->
            composeDestinationBlock(feature, selectedArchitecture, selection)
        }
        if ("Feature" !in vars) {
            vars["Feature"] = startFeature.className
        }
        return vars
    }

    private fun renderTemplate(template: String, variables: Map<String, String>): String {
        var rendered = template
        variables.forEach { (key, value) ->
            rendered = rendered.replace("\${$key}", value)
        }
        return rendered
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/')
            .replace(Regex("/+"), "/")
            .removePrefix("/")

    private fun configurationHashFor(selection: WizardSelectionV1): String {
        val normalized = normalizedSelection(selection)
        val payload = json.encodeToString(normalized)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun validateCustomization(selection: WizardSelectionV1) {
        val projectConfig = requireNotNull(selection.projectConfig) {
            "Project configuration is required"
        }
        require(projectConfig.projectName?.isNotBlank() == true) { "projectName must not be blank" }
        require(projectConfig.packageId?.isNotBlank() == true) { "packageId must not be blank" }
        require(projectConfig.featureNames.isNotEmpty()) { "featureNames must contain at least one feature" }

        val architecture = selection.architecture
        require(
            architecture?.mode == ArchitectureModeV1.CUSTOM ||
                selection.selectedOptionIds.any { it.startsWith("arch-") },
        ) { "One architecture preset or custom architecture is required" }

        if (architecture == null) return
        if (architecture.mode == ArchitectureModeV1.CUSTOM && architecture.customComponentTypes.isEmpty()) {
            throw IllegalArgumentException("Custom architecture mode requires at least one component type")
        }

        val componentIds = linkedSetOf<String>()
        architecture.customComponentTypes.forEach { component ->
            require(component.id.isNotBlank()) { "Custom component type id must not be blank" }
            require(component.displayName.isNotBlank()) { "Custom component displayName must not be blank" }
            require(component.layer.isNotBlank()) { "Custom component layer must not be blank" }
            require(component.fileNameTemplate.isNotBlank()) { "Custom component fileNameTemplate must not be blank" }
            require(component.sourceTemplate.isNotBlank()) { "Custom component sourceTemplate must not be blank" }
            require(componentIds.add(component.id)) { "Duplicate custom component type '${component.id}'" }
        }

        selection.customPatches.forEach { patch ->
            require(patch.targetPath.isNotBlank()) { "Custom patch targetPath must not be blank" }
            if (patch.operation == PatchOperationV1.REPLACE_IN_FILE) {
                require(!patch.replace.isNullOrBlank()) { "Replace patch must define replacement content" }
            }
        }
    }

    private fun selectedUiMode(selection: WizardSelectionV1): String = when {
        "ui-compose" in selection.selectedOptionIds -> "compose"
        "ui-xml" in selection.selectedOptionIds -> "xml"
        selection.projectConfig?.uiFramework?.equals("xml", ignoreCase = true) == true -> "xml"
        else -> "compose"
    }

    private fun selectedArchitectureId(selection: WizardSelectionV1): String? =
        selection.selectedOptionIds.firstOrNull { it in setOf("arch-mvvm", "arch-mvi", "arch-mvp", "arch-udf", "arch-viper-lite") }

    private fun templateModules(selection: WizardSelectionV1): TemplateModules {
        val selected = selection.selectedOptionIds.toSet()
        val needsNetwork = selected.any { it in setOf("library-retrofit", "library-ktor-client", "library-moshi", "library-kotlinx-serialization", "library-chucker") }
        val needsDatabase = selected.any { it in setOf("library-room", "library-sqldelight") }
        val needsTesting = selected.any { it in setOf("library-mockk", "library-turbine") }

        return when (selection.templateId) {
            "android-app-lite" -> TemplateModules(
                includeNetwork = needsNetwork,
                includeDatabase = needsDatabase,
                includeTesting = needsTesting,
                includeAnalytics = false,
                includeObservability = false,
                includeSync = false,
            )

            "android-app-scalable" -> TemplateModules(
                includeNetwork = true,
                includeDatabase = true,
                includeTesting = true,
                includeAnalytics = true,
                includeObservability = true,
                includeSync = true,
            )

            else -> TemplateModules(
                includeNetwork = true,
                includeDatabase = true,
                includeTesting = true,
                includeAnalytics = false,
                includeObservability = false,
                includeSync = false,
            )
        }
    }

    private fun featureSpecs(selection: WizardSelectionV1): List<FeatureSpec> =
        selection.projectConfig?.featureNames
            .orEmpty()
            .map { rawName ->
                val tokens = rawName
                    .trim()
                    .split(Regex("[^A-Za-z0-9]+"))
                    .filter(String::isNotBlank)
                require(tokens.isNotEmpty()) { "Feature name '$rawName' must contain letters or digits" }
                val packageName = tokens.joinToString(separator = "") { it.lowercase() }
                val className = tokens.joinToString(separator = "") { token ->
                    token.lowercase().replaceFirstChar { char -> char.uppercase() }
                }
                FeatureSpec(
                    rawName = rawName,
                    packageName = packageName,
                    className = className,
                    route = packageName,
                )
            }

    private fun featureVariables(feature: FeatureSpec): Map<String, String> = mapOf(
        "Feature" to feature.className,
        "FeatureClass" to feature.className,
        "FeaturePackage" to feature.packageName,
        "FeatureRoute" to feature.route,
        "FeatureName" to feature.rawName,
    )

    private fun architectureParameterValue(
        selection: WizardSelectionV1,
        parameterId: String,
        defaultValue: String,
    ): String =
        selectedArchitectureId(selection)
            ?.let { architectureId -> selection.optionParameters[architectureId]?.get(parameterId) }
            ?.takeIf(String::isNotBlank)
            ?: defaultValue

    private fun architectureViewModelSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "viewModelSuffix", "ViewModel")

    private fun architectureStoreSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "storeSuffix", "Store")

    private fun architecturePresenterSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "presenterSuffix", "Presenter")

    private fun architectureContractSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "contractSuffix", "Contract")

    private fun architectureRouterSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "routerSuffix", "Router")

    private fun architectureInteractorSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "interactorSuffix", "Interactor")

    private fun architectureStateSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "stateSuffix", "State")

    private fun architectureEventSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "eventSuffix", "Event")

    private fun architectureEffectSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "effectSuffix", "Effect")

    private fun architectureIntentSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "intentSuffix", "Intent")

    private fun architectureUseCaseSuffix(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "useCaseSuffix", "UseCase")

    private fun architectureNavigatorOwner(selection: WizardSelectionV1): String =
        architectureParameterValue(selection, "navigatorOwner", "screen")

    private fun composeNavigationImports(
        packageId: String,
        feature: FeatureSpec,
        architectureId: String?,
        selection: WizardSelectionV1,
    ): String = when (architectureId) {
        "arch-mvvm" ->
            "import $packageId.feature.${feature.packageName}.presentation.${feature.className}${architectureViewModelSuffix(selection)}\n" +
                "import $packageId.feature.${feature.packageName}.presentation.${feature.className}Screen"
        "arch-udf" ->
            "import $packageId.feature.${feature.packageName}.presentation.${feature.className}${architectureStoreSuffix(selection)}\n" +
                "import $packageId.feature.${feature.packageName}.presentation.${feature.className}Screen"
        "arch-mvi" ->
            "import $packageId.feature.${feature.packageName}.presentation.${feature.className}Screen\n" +
                "import $packageId.feature.${feature.packageName}.presentation.${feature.className}${architectureStoreSuffix(selection)}"
        "arch-mvp" -> "import $packageId.feature.${feature.packageName}.presentation.${feature.className}Screen"
        "arch-viper-lite" -> "import $packageId.feature.${feature.packageName}.presentation.${feature.className}Screen"
        else -> "import androidx.compose.material3.Text"
    }

    private fun composeDestinationBlock(
        feature: FeatureSpec,
        architectureId: String?,
        selection: WizardSelectionV1,
    ): String = when (architectureId) {
        "arch-mvvm" ->
            "        composable(route = \"${feature.route}\") {\n" +
                "            val viewModel = ${feature.className}${architectureViewModelSuffix(selection)}()\n" +
                "            ${feature.className}Screen(state = viewModel.state)\n" +
                "        }"
        "arch-udf" ->
            "        composable(route = \"${feature.route}\") {\n" +
                "            val store = ${feature.className}${architectureStoreSuffix(selection)}()\n" +
                "            ${feature.className}Screen(state = store.state, dispatch = store::dispatch)\n" +
                "        }"
        "arch-mvi" ->
            "        composable(route = \"${feature.route}\") {\n" +
                "            val store = ${feature.className}${architectureStoreSuffix(selection)}()\n" +
                "            ${feature.className}Screen(state = store.state)\n" +
                "        }"
        "arch-mvp" ->
            "        composable(route = \"${feature.route}\") {\n" +
                "            ${feature.className}Screen()\n" +
                "        }"
        "arch-viper-lite" ->
            "        composable(route = \"${feature.route}\") {\n" +
                "            ${feature.className}Screen()\n" +
                "        }"
        else ->
            "        composable(route = \"${feature.route}\") {\n" +
                "            Text(text = \"${feature.className}\")\n" +
                "        }"
    }

    private fun PatchSpec.renderWith(variables: Map<String, String>): PatchSpec =
        copy(
            targetPath = renderTemplate(targetPath, variables),
            content = content?.let { renderTemplate(it, variables) },
            resourcePath = resourcePath?.let { renderTemplate(it, variables) },
            find = find?.let { renderTemplate(it, variables) },
            replace = replace?.let { renderTemplate(it, variables) },
            templateVariables = variables,
        )

    private fun toComposeColor(color: String?, fallback: String): String {
        val normalized = toXmlColor(color, fallback).removePrefix("#")
        return "0xFF$normalized"
    }

    private fun toXmlColor(color: String?, fallback: String): String {
        val normalized = color?.trim().orEmpty()
        return if (normalized.matches(Regex("^#[0-9A-Fa-f]{6}$"))) normalized else fallback
    }

    private fun ru.renattele.wizard.engine.configuration.domain.ResolvedConfiguration.toApiResponse(
        configurationHash: String,
    ): ResolveResponseV1 =
        ResolveResponseV1(
            resolvedOptions = resolvedOptions.map { option ->
                ResolvedOptionV1(
                    id = option.id,
                    type = option.type,
                    category = option.category,
                    displayName = option.displayName,
                    version = option.version,
                    sourcePackId = option.sourcePackId,
                )
            },
            applyOrder = applyOrder,
            autoAdded = autoAdded.map { autoAdded ->
                AutoAddedOptionV1(
                    optionId = autoAdded.optionId,
                    requiredBy = autoAdded.requiredBy,
                    chain = autoAdded.chain,
                )
            },
            problems = problems.map { problem -> problem.toApiProblem() },
            lockfile = lockState.toApiLockfile(configurationHash),
        )

    private fun LockState.toApiLockfile(configurationHash: String): WizardLockfile =
        WizardLockfile(
            templateId = templateId,
            strictMode = strictMode,
            options = options.map { option ->
                LockedOptionV1(
                    optionId = option.optionId,
                    version = option.version,
                    sourcePackId = option.sourcePackId,
                    artifactCoordinates = option.artifactCoordinates,
                    artifactChecksum = option.artifactChecksum,
                )
            },
            applyOrder = applyOrder,
            catalogFingerprint = catalogFingerprint,
            configurationHash = configurationHash,
            resolutionHash = resolutionHash,
        )

    private fun WizardLockfile.toDomainLockState(): LockState =
        LockState(
            templateId = templateId,
            strictMode = strictMode,
            options = options.map { option ->
                LockedOption(
                    optionId = option.optionId,
                    version = option.version,
                    sourcePackId = option.sourcePackId,
                    artifactCoordinates = option.artifactCoordinates,
                    artifactChecksum = option.artifactChecksum,
                )
            },
            applyOrder = applyOrder,
            catalogFingerprint = catalogFingerprint,
            resolutionHash = resolutionHash,
        )

    private fun Problem.toApiProblem(): ProblemV1 =
        ProblemV1(
            code = when (code) {
                ProblemCode.INVALID_CATALOG -> ProblemCodeV1.INVALID_CATALOG
                ProblemCode.UNKNOWN_TEMPLATE -> ProblemCodeV1.UNKNOWN_TEMPLATE
                ProblemCode.UNKNOWN_OPTION -> ProblemCodeV1.UNKNOWN_OPTION
                ProblemCode.UNKNOWN_BASE_OPTION -> ProblemCodeV1.UNKNOWN_BASE_OPTION
                ProblemCode.UNKNOWN_REQUIRED_OPTION -> ProblemCodeV1.UNKNOWN_REQUIRED_OPTION
                ProblemCode.UNKNOWN_CONFLICT_OPTION -> ProblemCodeV1.UNKNOWN_CONFLICT_OPTION
                ProblemCode.INVALID_VERSION_RANGE -> ProblemCodeV1.INVALID_VERSION_RANGE
                ProblemCode.AMBIGUOUS_CAPABILITY_PROVIDER -> ProblemCodeV1.AMBIGUOUS_CAPABILITY_PROVIDER
                ProblemCode.MISSING_REQUIRED_OPTION -> ProblemCodeV1.MISSING_REQUIRED_OPTION
                ProblemCode.MISSING_CAPABILITY -> ProblemCodeV1.MISSING_CAPABILITY
                ProblemCode.HARD_CONFLICT -> ProblemCodeV1.HARD_CONFLICT
                ProblemCode.VERSION_OUT_OF_RANGE -> ProblemCodeV1.VERSION_OUT_OF_RANGE
                ProblemCode.CYCLE_DETECTED -> ProblemCodeV1.CYCLE_DETECTED
                ProblemCode.LOCK_REQUIRED -> ProblemCodeV1.LOCK_REQUIRED
                ProblemCode.LOCK_STALE -> ProblemCodeV1.LOCK_STALE
            },
            message = message,
            severity = when (severity) {
                ProblemSeverity.INFO -> SeverityV1.INFO
                ProblemSeverity.WARNING -> SeverityV1.WARNING
                ProblemSeverity.ERROR -> SeverityV1.ERROR
            },
            source = source,
        )

    private fun PatchOperation.toApiOperation(): PatchOperationV1 = when (this) {
        PatchOperation.ADD_FILE -> PatchOperationV1.ADD_FILE
        PatchOperation.ADD_TEMPLATE_FILE -> PatchOperationV1.ADD_TEMPLATE_FILE
        PatchOperation.ADD_TEMPLATE_DIRECTORY -> PatchOperationV1.ADD_TEMPLATE_DIRECTORY
        PatchOperation.REPLACE_IN_FILE -> PatchOperationV1.REPLACE_IN_FILE
        PatchOperation.APPEND_FILE -> PatchOperationV1.APPEND_FILE
        PatchOperation.REMOVE_FILE -> PatchOperationV1.REMOVE_FILE
    }

    private fun PatchConflictStrategy.toApiStrategy(): ConflictStrategyV1 = when (this) {
        PatchConflictStrategy.FAIL -> ConflictStrategyV1.FAIL
        PatchConflictStrategy.SKIP -> ConflictStrategyV1.SKIP
        PatchConflictStrategy.MERGE_WITH_RULE -> ConflictStrategyV1.MERGE_WITH_RULE
    }

    private fun ru.renattele.wizard.engine.configuration.domain.OptionParameterType.toApiType(): OptionParameterTypeV1 = when (this) {
        ru.renattele.wizard.engine.configuration.domain.OptionParameterType.STRING -> OptionParameterTypeV1.STRING
        ru.renattele.wizard.engine.configuration.domain.OptionParameterType.BOOLEAN -> OptionParameterTypeV1.BOOLEAN
        ru.renattele.wizard.engine.configuration.domain.OptionParameterType.ENUM -> OptionParameterTypeV1.ENUM
        ru.renattele.wizard.engine.configuration.domain.OptionParameterType.MULTILINE -> OptionParameterTypeV1.MULTILINE
    }

    private fun PatchOperationV1.toDomainOperation(): PatchOperation = when (this) {
        PatchOperationV1.ADD_FILE -> PatchOperation.ADD_FILE
        PatchOperationV1.ADD_TEMPLATE_FILE -> PatchOperation.ADD_TEMPLATE_FILE
        PatchOperationV1.ADD_TEMPLATE_DIRECTORY -> PatchOperation.ADD_TEMPLATE_DIRECTORY
        PatchOperationV1.REPLACE_IN_FILE -> PatchOperation.REPLACE_IN_FILE
        PatchOperationV1.APPEND_FILE -> PatchOperation.APPEND_FILE
        PatchOperationV1.REMOVE_FILE -> PatchOperation.REMOVE_FILE
    }

    private fun ConflictStrategyV1.toDomainStrategy(): PatchConflictStrategy = when (this) {
        ConflictStrategyV1.FAIL -> PatchConflictStrategy.FAIL
        ConflictStrategyV1.SKIP -> PatchConflictStrategy.SKIP
        ConflictStrategyV1.MERGE_WITH_RULE -> PatchConflictStrategy.MERGE_WITH_RULE
    }
}
