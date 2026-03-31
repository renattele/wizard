package ru.renattele.wizard.engine.resolver
import ru.renattele.wizard.contracts.v1.AutoAddedOptionV1
import ru.renattele.wizard.contracts.v1.LockedOptionV1
import ru.renattele.wizard.contracts.v1.ProblemCodeV1
import ru.renattele.wizard.contracts.v1.ProblemV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.ResolvedOptionV1
import ru.renattele.wizard.contracts.v1.SeverityV1
import ru.renattele.wizard.contracts.v1.WizardLockfile
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.configuration.application.LoadCatalogUseCase
import ru.renattele.wizard.engine.configuration.application.ResolveConfigurationUseCase
import ru.renattele.wizard.engine.configuration.domain.Problem
import ru.renattele.wizard.engine.configuration.domain.ProblemCode
import ru.renattele.wizard.engine.configuration.domain.ProblemSeverity

class DeterministicResolutionEngine : ResolutionEngine {
    override fun resolve(request: ResolveRequestV1, catalog: CatalogBundle): ResolveResponseV1 {
        val provider = object : CatalogProvider {
            override fun loadCatalog(request: ru.renattele.wizard.engine.catalog.CatalogRequest): CatalogBundle = catalog
        }
        val useCase = ResolveConfigurationUseCase(LoadCatalogUseCase(provider))
        val resolution = useCase(
            templateId = request.selection.templateId,
            selectedOptionIds = request.selection.selectedOptionIds,
            optionVersions = request.selection.optionVersions,
            strictMode = request.strictMode,
        )

        return ResolveResponseV1(
            resolvedOptions = resolution.resolvedOptions.map { option ->
                ResolvedOptionV1(
                    id = option.id,
                    type = option.type,
                    category = option.category,
                    displayName = option.displayName,
                    version = option.version,
                    sourcePackId = option.sourcePackId,
                )
            },
            applyOrder = resolution.applyOrder,
            autoAdded = resolution.autoAdded.map { autoAdded ->
                AutoAddedOptionV1(
                    optionId = autoAdded.optionId,
                    requiredBy = autoAdded.requiredBy,
                    chain = autoAdded.chain,
                )
            },
            problems = resolution.problems.map(Problem::toApiProblem),
            lockfile = WizardLockfile(
                templateId = resolution.lockState.templateId,
                strictMode = resolution.lockState.strictMode,
                options = resolution.lockState.options.map { option ->
                    LockedOptionV1(
                        optionId = option.optionId,
                        version = option.version,
                        sourcePackId = option.sourcePackId,
                        artifactCoordinates = option.artifactCoordinates,
                        artifactChecksum = option.artifactChecksum,
                    )
                },
                applyOrder = resolution.lockState.applyOrder,
                catalogFingerprint = resolution.lockState.catalogFingerprint,
                resolutionHash = resolution.lockState.resolutionHash,
            ),
        )
    }
}

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
