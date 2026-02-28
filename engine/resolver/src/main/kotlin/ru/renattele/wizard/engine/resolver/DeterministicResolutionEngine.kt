package ru.renattele.wizard.engine.resolver

import ru.renattele.wizard.contracts.v1.AutoAddedOptionV1
import ru.renattele.wizard.contracts.v1.CompatibilityIssueCodeV1
import ru.renattele.wizard.contracts.v1.CompatibilityIssueV1
import ru.renattele.wizard.contracts.v1.CompatibilityReportV1
import ru.renattele.wizard.contracts.v1.LockedOptionV1
import ru.renattele.wizard.contracts.v1.ResolutionCodeV1
import ru.renattele.wizard.contracts.v1.ResolutionIssueV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.SeverityV1
import ru.renattele.wizard.contracts.v1.WizardLockfile
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogPackDescriptor
import ru.renattele.wizard.manifest.OptionManifest
import java.security.MessageDigest
import java.util.UUID

class DeterministicResolutionEngine(
    private val runtimeKotlinVersion: String = "2.3.0",
    private val runtimeGradleVersion: String = "8.14.3",
) : ResolutionEngine {
    override fun resolve(request: ResolveRequestV1, catalog: CatalogBundle): ResolveResponseV1 {
        val template = catalog.findTemplate(request.selection.templateId)
        val issues = mutableListOf<ResolutionIssueV1>()
        val autoAdded = mutableListOf<AutoAddedOptionV1>()

        if (template == null) {
            issues += ResolutionIssueV1(
                code = ResolutionCodeV1.UNKNOWN_OPTION,
                message = "Unknown template '${request.selection.templateId}'",
            )
            val emptyCompatibility = CompatibilityReportV1(
                compatible = false,
                engineVersion = ENGINE_VERSION,
                issues = listOf(
                    CompatibilityIssueV1(
                        code = CompatibilityIssueCodeV1.API_RANGE,
                        message = "Cannot resolve unknown template",
                    ),
                ),
            )
            return ResolveResponseV1(
                sessionId = request.sessionId ?: UUID.randomUUID().toString(),
                selectedOptionIds = request.selection.selectedOptionIds,
                issues = issues,
                compatibilityReport = emptyCompatibility,
                lockfile = WizardLockfile(
                    templateId = request.selection.templateId,
                    strictMode = request.strictMode,
                    resolutionHash = hashLock(request.selection.templateId, emptyList()),
                ),
            )
        }

        val optionToPack = buildOptionToPackMap(catalog.packs)
        val optionMap = catalog.options.associateBy { it.id }
        val selected = linkedSetOf<String>()
        selected += template.baseOptionIds
        selected += request.selection.selectedOptionIds

        val knownSelected = selected.filter { optionMap.containsKey(it) }.toSet()
        selected.filterNot { optionMap.containsKey(it) }
            .forEach { unknown ->
                issues += ResolutionIssueV1(
                    code = ResolutionCodeV1.UNKNOWN_OPTION,
                    message = "Unknown option '$unknown'",
                )
            }

        val queue = ArrayDeque(knownSelected.sorted())
        val capabilityProviders = catalog.options
            .flatMap { option -> option.dependency.providesCapabilities.map { capability -> capability to option.id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, providers) -> providers.distinct().sorted() }

        while (queue.isNotEmpty()) {
            val optionId = queue.removeFirst()
            val option = optionMap[optionId] ?: continue

            option.dependency.requiresOptionIds.sorted().forEach { requiredId ->
                if (!selected.contains(requiredId)) {
                    if (!optionMap.containsKey(requiredId)) {
                        issues += ResolutionIssueV1(
                            code = ResolutionCodeV1.MISSING_REQUIRED_OPTION,
                            message = "Option '$optionId' requires unknown option '$requiredId'",
                        )
                    } else {
                        selected += requiredId
                        queue += requiredId
                        autoAdded += AutoAddedOptionV1(
                            optionId = requiredId,
                            requiredBy = optionId,
                            chain = listOf(optionId, requiredId),
                        )
                    }
                }
            }

            option.dependency.requiresCapabilities.sorted().forEach { capability ->
                val selectedProvidesCapability = selected.any { selectedId ->
                    optionMap[selectedId]?.dependency?.providesCapabilities?.contains(capability) == true
                }
                if (selectedProvidesCapability) return@forEach

                val candidate = capabilityProviders[capability]?.firstOrNull()
                if (candidate == null) {
                    issues += ResolutionIssueV1(
                        code = ResolutionCodeV1.MISSING_CAPABILITY,
                        message = "Option '$optionId' requires capability '$capability'",
                    )
                } else if (!selected.contains(candidate)) {
                    selected += candidate
                    queue += candidate
                    autoAdded += AutoAddedOptionV1(
                        optionId = candidate,
                        requiredBy = optionId,
                        chain = listOf(optionId, candidate),
                    )
                }
            }
        }

        val resolvedOptions = selected
            .mapNotNull { optionMap[it] }
            .sortedBy { it.id }

        resolvedOptions.forEach { option ->
            option.dependency.conflictsHard.sorted().forEach { conflictId ->
                if (selected.contains(conflictId)) {
                    issues += ResolutionIssueV1(
                        code = ResolutionCodeV1.HARD_CONFLICT,
                        message = "Option '${option.id}' conflicts with '$conflictId'",
                    )
                }
            }
        }

        val selectedVersions = resolvedOptions.associate { option ->
            val explicit = request.selection.optionVersions[option.id]
            option.id to (explicit ?: option.version.recommended)
        }

        resolvedOptions.forEach { option ->
            val selectedVersion = selectedVersions.getValue(option.id)
            if (!VersionRangeMatcher.matches(option.version.range, selectedVersion)) {
                issues += ResolutionIssueV1(
                    code = ResolutionCodeV1.VERSION_OUT_OF_RANGE,
                    message = "Version '$selectedVersion' is outside range '${option.version.range}' for option '${option.id}'",
                )
            }
        }

        val orderedIds = sortByDependencies(resolvedOptions, optionMap, issues)

        val compatibilityIssues = buildCompatibilityIssues(
            packs = catalog.packs,
            selectedOptionIds = selected,
            strictMode = request.strictMode,
        )

        val lockfileOptions = orderedIds.map { optionId ->
            val option = optionMap.getValue(optionId)
            val pack = optionToPack[optionId]
            LockedOptionV1(
                optionId = optionId,
                version = selectedVersions.getValue(optionId),
                sourcePackId = pack?.id ?: "unknown",
                artifactCoordinates = option.artifact?.url,
                artifactChecksum = option.artifact?.sha256,
            )
        }

        val lockfile = WizardLockfile(
            templateId = request.selection.templateId,
            strictMode = request.strictMode,
            options = lockfileOptions,
            resolutionHash = hashLock(request.selection.templateId, lockfileOptions),
        )

        val compatibilityReport = CompatibilityReportV1(
            compatible = compatibilityIssues.none { it.severity == SeverityV1.ERROR },
            engineVersion = ENGINE_VERSION,
            issues = compatibilityIssues,
        )

        return ResolveResponseV1(
            sessionId = request.sessionId ?: UUID.randomUUID().toString(),
            selectedOptionIds = request.selection.selectedOptionIds.sorted(),
            resolvedOptionIds = resolvedOptions.map { it.id },
            orderedOptionIds = orderedIds,
            autoAdded = autoAdded.sortedBy { it.optionId },
            issues = issues,
            compatibilityReport = compatibilityReport,
            lockfile = lockfile,
        )
    }

    private fun sortByDependencies(
        resolvedOptions: List<OptionManifest>,
        optionMap: Map<String, OptionManifest>,
        issues: MutableList<ResolutionIssueV1>,
    ): List<String> {
        val ids = resolvedOptions.map { it.id }.toSet()
        val indegree = ids.associateWith { 0 }.toMutableMap()
        val outgoing = ids.associateWith { mutableSetOf<String>() }.toMutableMap()

        resolvedOptions.forEach { option ->
            option.dependency.requiresOptionIds
                .filter { ids.contains(it) }
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
            issues += ResolutionIssueV1(
                code = ResolutionCodeV1.CYCLE_DETECTED,
                message = "Dependency cycle detected; fallback deterministic ordering is used",
                severity = SeverityV1.WARNING,
            )
            return ids.sorted()
        }

        return ordered
    }

    private fun buildCompatibilityIssues(
        packs: List<CatalogPackDescriptor>,
        selectedOptionIds: Set<String>,
        strictMode: Boolean,
    ): List<CompatibilityIssueV1> {
        val relevantPacks = packs.filter { descriptor ->
            descriptor.pack.options.any { selectedOptionIds.contains(it.id) }
        }

        val severity = if (strictMode) SeverityV1.ERROR else SeverityV1.WARNING
        val issues = mutableListOf<CompatibilityIssueV1>()

        relevantPacks.forEach { descriptor ->
            val compatibility = descriptor.pack.compatibility

            if (!VersionRangeMatcher.matches(compatibility.engineRange, ENGINE_VERSION)) {
                issues += CompatibilityIssueV1(
                    code = CompatibilityIssueCodeV1.ENGINE_RANGE,
                    message = "Pack '${descriptor.id}' is not compatible with engine version '$ENGINE_VERSION'",
                    severity = severity,
                )
            }
            if (!VersionRangeMatcher.matches(compatibility.apiRange, "1.0.0")) {
                issues += CompatibilityIssueV1(
                    code = CompatibilityIssueCodeV1.API_RANGE,
                    message = "Pack '${descriptor.id}' is not compatible with API version '2.0.0'",
                    severity = severity,
                )
            }
            if (!VersionRangeMatcher.matches(compatibility.kotlinRange, runtimeKotlinVersion)) {
                issues += CompatibilityIssueV1(
                    code = CompatibilityIssueCodeV1.KOTLIN_RANGE,
                    message = "Pack '${descriptor.id}' is not compatible with Kotlin '$runtimeKotlinVersion'",
                    severity = severity,
                )
            }
            if (!VersionRangeMatcher.matches(compatibility.gradleRange, runtimeGradleVersion)) {
                issues += CompatibilityIssueV1(
                    code = CompatibilityIssueCodeV1.GRADLE_RANGE,
                    message = "Pack '${descriptor.id}' is not compatible with Gradle '$runtimeGradleVersion'",
                    severity = severity,
                )
            }
        }

        return issues
    }

    private fun buildOptionToPackMap(packs: List<CatalogPackDescriptor>): Map<String, CatalogPackDescriptor> {
        val map = mutableMapOf<String, CatalogPackDescriptor>()
        packs.forEach { descriptor ->
            descriptor.pack.options.forEach { option ->
                map.putIfAbsent(option.id, descriptor)
            }
        }
        return map
    }

    private fun hashLock(templateId: String, options: List<LockedOptionV1>): String {
        val payload = buildString {
            append(templateId)
            options.forEach {
                append('|')
                append(it.optionId)
                append('@')
                append(it.version)
                append('#')
                append(it.sourcePackId)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
