package ru.renattele.wizard.engine.configuration.application

import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.configuration.domain.AutoAddedOption
import ru.renattele.wizard.engine.configuration.domain.ConfigurationCatalog
import ru.renattele.wizard.engine.configuration.domain.GenerationPlan
import ru.renattele.wizard.engine.configuration.domain.LockState
import ru.renattele.wizard.engine.configuration.domain.LockedOption
import ru.renattele.wizard.engine.configuration.domain.PreparedGeneration
import ru.renattele.wizard.engine.configuration.domain.Problem
import ru.renattele.wizard.engine.configuration.domain.ProblemCode
import ru.renattele.wizard.engine.configuration.domain.ProblemSeverity
import ru.renattele.wizard.engine.configuration.domain.ResolvedConfiguration
import ru.renattele.wizard.engine.configuration.domain.ResolvedOption
import ru.renattele.wizard.engine.configuration.domain.VerifiedConfiguration
import ru.renattele.wizard.engine.configuration.domain.VersionRangeMatcher
import java.security.MessageDigest

class LoadCatalogUseCase(
    private val catalogProvider: CatalogProvider,
    private val modelFactory: ConfigurationModelFactory = ConfigurationModelFactory(),
) {
    operator fun invoke(): ConfigurationCatalog = modelFactory.create(catalogProvider.loadCatalog())
}

class ResolveConfigurationUseCase(
    private val loadCatalog: LoadCatalogUseCase,
) {
    operator fun invoke(
        templateId: String,
        selectedOptionIds: List<String>,
        optionVersions: Map<String, String>,
        strictMode: Boolean,
    ): ResolvedConfiguration {
        val catalog = loadCatalog()
        return resolve(
            catalog = catalog,
            templateId = templateId,
            selectedOptionIds = selectedOptionIds,
            optionVersions = optionVersions,
            strictMode = strictMode,
        )
    }

    internal fun resolve(
        catalog: ConfigurationCatalog,
        templateId: String,
        selectedOptionIds: List<String>,
        optionVersions: Map<String, String>,
        strictMode: Boolean,
    ): ResolvedConfiguration {
        val problems = catalog.problems.toMutableList()
        val template = catalog.templates[templateId]
        if (template == null) {
            val finalProblems = problems + Problem(
                code = ProblemCode.UNKNOWN_TEMPLATE,
                message = "Unknown template '$templateId'",
                source = templateId,
            )
            return ResolvedConfiguration(
                templateId = templateId,
                resolvedOptions = emptyList(),
                applyOrder = emptyList(),
                autoAdded = emptyList(),
                problems = finalProblems,
                lockState = LockState(
                    templateId = templateId,
                    strictMode = strictMode,
                    options = emptyList(),
                    applyOrder = emptyList(),
                    catalogFingerprint = catalog.catalogFingerprint,
                    resolutionHash = hashLock(templateId, catalog.catalogFingerprint, emptyList(), emptyList()),
                ),
            )
        }

        val selected = linkedSetOf<String>()
        selected += template.baseOptionIds
        selected += selectedOptionIds.sorted()
        val knownSelected = selected.filter(catalog.options::containsKey).toSet()
        selected.filterNot(catalog.options::containsKey)
            .forEach { unknownId ->
                problems += Problem(
                    code = ProblemCode.UNKNOWN_OPTION,
                    message = "Unknown option '$unknownId'",
                    source = unknownId,
                )
            }

        selected.clear()
        selected += knownSelected.sorted()

        val autoAdded = mutableListOf<AutoAddedOption>()
        val queue = ArrayDeque(selected.toList())

        while (queue.isNotEmpty()) {
            val optionId = queue.removeFirst()
            val option = catalog.options[optionId] ?: continue

            option.dependency.requiresOptionIds.forEach { requiredId ->
                when {
                    !catalog.options.containsKey(requiredId) -> problems += Problem(
                        code = ProblemCode.UNKNOWN_REQUIRED_OPTION,
                        message = "Option '$optionId' requires unknown option '$requiredId'",
                        source = optionId,
                    )

                    selected.add(requiredId) -> {
                        queue += requiredId
                        autoAdded += AutoAddedOption(
                            optionId = requiredId,
                            requiredBy = optionId,
                            chain = listOf(optionId, requiredId),
                        )
                    }
                }
            }

            option.dependency.requiresCapabilities.forEach { capability ->
                val selectedProvider = selected.firstOrNull { selectedId ->
                    catalog.options[selectedId]
                        ?.dependency
                        ?.providesCapabilities
                        ?.contains(capability) == true
                }
                if (selectedProvider != null) return@forEach

                val providers = catalog.capabilityProviders[capability].orEmpty()
                when {
                    providers.isEmpty() -> problems += Problem(
                        code = ProblemCode.MISSING_CAPABILITY,
                        message = "Option '$optionId' requires capability '$capability'",
                        source = optionId,
                    )

                    providers.size > 1 -> problems += Problem(
                        code = ProblemCode.AMBIGUOUS_CAPABILITY_PROVIDER,
                        message = "Capability '$capability' required by '$optionId' has ambiguous providers: ${providers.joinToString()}",
                        severity = ProblemSeverity.WARNING,
                        source = capability,
                    )

                    selected.add(providers.single()) -> {
                        val candidate = providers.single()
                        queue += candidate
                        autoAdded += AutoAddedOption(
                            optionId = candidate,
                            requiredBy = optionId,
                            chain = listOf(optionId, candidate),
                        )
                    }
                }
            }
        }

        val resolved = selected.mapNotNull(catalog.options::get)

        resolved.forEach { option ->
            option.dependency.conflictsHard
                .filter(selected::contains)
                .forEach { conflictId ->
                    problems += Problem(
                        code = ProblemCode.HARD_CONFLICT,
                        message = "Option '${option.id}' conflicts with '$conflictId'",
                        source = option.id,
                    )
                }
        }

        val chosenVersions = resolved.associate { option ->
            option.id to (optionVersions[option.id] ?: option.versionPolicy.recommended)
        }

        resolved.forEach { option ->
            val selectedVersion = chosenVersions.getValue(option.id)
            if (!VersionRangeMatcher.matches(option.versionPolicy.range, selectedVersion)) {
                problems += Problem(
                    code = ProblemCode.VERSION_OUT_OF_RANGE,
                    message = "Version '$selectedVersion' is outside range '${option.versionPolicy.range}' for option '${option.id}'",
                    source = option.id,
                )
            }
        }

        val applyOrder = sortByDependencies(resolved, catalog, problems)
        val orderedOptions = applyOrder.mapNotNull { optionId ->
            val option = catalog.options[optionId] ?: return@mapNotNull null
            ResolvedOption(
                id = option.id,
                type = option.type,
                category = option.category,
                displayName = option.displayName,
                version = chosenVersions.getValue(option.id),
                sourcePackId = option.sourcePackId,
                artifactCoordinates = option.artifactCoordinates,
                artifactChecksum = option.artifactChecksum,
                providedCapabilities = option.dependency.providesCapabilities,
                parameters = option.parameters,
                patches = option.patches,
            )
        }

        val lockedOptions = orderedOptions.map { option ->
            LockedOption(
                optionId = option.id,
                version = option.version,
                sourcePackId = option.sourcePackId,
                artifactCoordinates = option.artifactCoordinates,
                artifactChecksum = option.artifactChecksum,
            )
        }

        val lockState = LockState(
            templateId = templateId,
            strictMode = strictMode,
            options = lockedOptions,
            applyOrder = applyOrder,
            catalogFingerprint = catalog.catalogFingerprint,
            resolutionHash = hashLock(templateId, catalog.catalogFingerprint, applyOrder, lockedOptions),
        )

        return ResolvedConfiguration(
            templateId = templateId,
            resolvedOptions = orderedOptions,
            applyOrder = applyOrder,
            autoAdded = autoAdded.sortedBy(AutoAddedOption::optionId),
            problems = problems.distinctBy { Triple(it.code, it.source, it.message) },
            lockState = lockState,
        )
    }

    private fun sortByDependencies(
        resolvedOptions: List<ru.renattele.wizard.engine.configuration.domain.OptionSpec>,
        catalog: ConfigurationCatalog,
        problems: MutableList<Problem>,
    ): List<String> {
        val ids = resolvedOptions.map { it.id }.toSet()
        val indegree = ids.associateWith { 0 }.toMutableMap()
        val outgoing = ids.associateWith { linkedSetOf<String>() }.toMutableMap()

        resolvedOptions.forEach { option ->
            option.dependency.requiresOptionIds
                .filter(ids::contains)
                .forEach { dependencyId ->
                    outgoing.getValue(dependencyId).add(option.id)
                    indegree[option.id] = indegree.getValue(option.id) + 1
                }
        }

        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys.sorted())
        val ordered = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            ordered += current
            outgoing[current].orEmpty().sorted().forEach { dependent ->
                indegree[dependent] = indegree.getValue(dependent) - 1
                if (indegree.getValue(dependent) == 0) {
                    queue += dependent
                }
            }
        }

        if (ordered.size != ids.size) {
            problems += Problem(
                code = ProblemCode.CYCLE_DETECTED,
                message = "Dependency cycle detected; fallback deterministic ordering is used",
                severity = ProblemSeverity.WARNING,
            )
            return ids.sorted()
        }

        return ordered
    }

    private fun hashLock(
        templateId: String,
        catalogFingerprint: String,
        applyOrder: List<String>,
        options: List<LockedOption>,
    ): String {
        val payload = buildString {
            append(templateId)
            append('|')
            append(catalogFingerprint)
            append('|')
            append(applyOrder.joinToString(","))
            options.forEach { option ->
                append('|')
                append(option.optionId)
                append('@')
                append(option.version)
                append('#')
                append(option.sourcePackId)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

class VerifyLockUseCase(
    private val resolveConfiguration: ResolveConfigurationUseCase,
    private val loadCatalog: LoadCatalogUseCase,
) {
    operator fun invoke(
        templateId: String,
        selectedOptionIds: List<String>,
        optionVersions: Map<String, String>,
        strictMode: Boolean,
        providedLock: LockState?,
    ): VerifiedConfiguration {
        val catalog = loadCatalog()
        val resolution = resolveConfiguration.resolve(
            catalog = catalog,
            templateId = templateId,
            selectedOptionIds = selectedOptionIds,
            optionVersions = optionVersions,
            strictMode = strictMode,
        )

        val lockVerified = providedLock != null && providedLock == resolution.lockState
        if (strictMode && providedLock == null) {
            throw IllegalArgumentException("Strict mode requires lockfile")
        }
        if (strictMode && !lockVerified) {
            throw IllegalArgumentException("Lockfile is stale and must be regenerated")
        }

        return VerifiedConfiguration(
            resolution = resolution,
            lockVerified = lockVerified,
        )
    }
}

class PrepareGenerationUseCase(
    private val verifyLock: VerifyLockUseCase,
) {
    operator fun invoke(
        templateId: String,
        selectedOptionIds: List<String>,
        optionVersions: Map<String, String>,
        strictMode: Boolean,
        providedLock: LockState?,
    ): PreparedGeneration {
        val verified = verifyLock(
            templateId = templateId,
            selectedOptionIds = selectedOptionIds,
            optionVersions = optionVersions,
            strictMode = strictMode,
            providedLock = providedLock,
        )

        return PreparedGeneration(
            plan = GenerationPlan(
                templateId = templateId,
                resolvedOptions = verified.resolution.resolvedOptions,
                applyOrder = verified.resolution.applyOrder,
                strictMode = strictMode,
                lockState = verified.resolution.lockState,
                problems = verified.resolution.problems,
            ),
            lockVerified = verified.lockVerified,
        )
    }
}
