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
import ru.renattele.wizard.engine.configuration.domain.LockState
import ru.renattele.wizard.engine.configuration.domain.LockedOption
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.PreparedGeneration
import ru.renattele.wizard.engine.configuration.domain.Problem
import ru.renattele.wizard.engine.configuration.domain.ProblemCode
import ru.renattele.wizard.engine.configuration.domain.ProblemSeverity
import ru.renattele.wizard.engine.generator.DeterministicPatchPipeline
import ru.renattele.wizard.engine.generator.GenerationRequest
import ru.renattele.wizard.engine.generator.PatchPipeline

class WizardApiService(
    catalogProvider: CatalogProvider,
    private val pipeline: PatchPipeline = DeterministicPatchPipeline(),
) {
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
        val generated = pipeline.generate(GenerationRequest(plan = prepared.plan))
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

        return prepared.copy(
            plan = prepared.plan.copy(
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
        )
    }

    private fun buildGenerationCustomization(selection: WizardSelectionV1): GenerationCustomization =
        GenerationCustomization(
            sharedVariables = templateVariables(selection),
            optionParameters = selection.optionParameters,
        )

    private fun buildAdditionalPatchBatches(selection: WizardSelectionV1): List<AdditionalPatchBatch> {
        val templateVars = templateVariables(selection)
        val batches = mutableListOf<AdditionalPatchBatch>()

        val architecture = selection.architecture
        if (architecture?.mode == ArchitectureModeV1.CUSTOM) {
            architecture.customComponentTypes.forEach { component ->
                val componentVars = templateVars + mapOf(
                    "ComponentId" to component.id,
                    "ComponentName" to component.displayName,
                    "Layer" to component.layer,
                    "AllowedDependencies" to component.allowedDependencyTypeIds.joinToString(", "),
                )
                val rawPath = renderTemplate(component.fileNameTemplate, componentVars)
                val normalizedPath = normalizePath(
                    if (rawPath.contains('/')) rawPath else "custom/${component.layer.lowercase()}/$rawPath",
                )
                val renderedSource = renderTemplate(component.sourceTemplate, componentVars)
                batches += AdditionalPatchBatch(
                    sourceId = "custom-component:${component.id}",
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
        val vars = linkedMapOf<String, String>()
        vars += selection.contextVars.toSortedMap()
        vars["TemplateId"] = selection.templateId
        vars["ProjectName"] = projectConfig?.projectName?.takeIf(String::isNotBlank) ?: "Wizard Project"
        val packageId = projectConfig?.packageId?.takeIf(String::isNotBlank) ?: "wizard.generated"
        vars["Package"] = packageId
        vars["PackagePath"] = packageId.replace('.', '/')
        projectConfig?.uiFramework?.takeIf(String::isNotBlank)?.let { vars["UiFramework"] = it }
        projectConfig?.designSystemPrefix?.takeIf(String::isNotBlank)?.let { vars["DesignSystemPrefix"] = it }
        projectConfig?.primaryColor?.takeIf(String::isNotBlank)?.let { vars["PrimaryColor"] = it }
        projectConfig?.secondaryColor?.takeIf(String::isNotBlank)?.let { vars["SecondaryColor"] = it }
        projectConfig?.ciTemplate?.takeIf(String::isNotBlank)?.let { vars["CiTemplate"] = it }
        selection.architecture?.presetPatternId?.takeIf(String::isNotBlank)?.let { vars["PresetPattern"] = it }
        if ("Feature" !in vars) {
            vars["Feature"] = "SampleFeature"
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
        val architecture = selection.architecture ?: return
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
