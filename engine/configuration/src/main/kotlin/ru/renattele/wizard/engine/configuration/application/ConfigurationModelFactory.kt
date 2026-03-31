package ru.renattele.wizard.engine.configuration.application

import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogPackDescriptor
import ru.renattele.wizard.engine.configuration.domain.ConfigurationCatalog
import ru.renattele.wizard.engine.configuration.domain.DependencyRule
import ru.renattele.wizard.engine.configuration.domain.OptionSpec
import ru.renattele.wizard.engine.configuration.domain.OptionParameterAllowedValue
import ru.renattele.wizard.engine.configuration.domain.OptionParameterSpec
import ru.renattele.wizard.engine.configuration.domain.OptionParameterType
import ru.renattele.wizard.engine.configuration.domain.PackSpec
import ru.renattele.wizard.engine.configuration.domain.PatchActivation
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.Problem
import ru.renattele.wizard.engine.configuration.domain.ProblemCode
import ru.renattele.wizard.engine.configuration.domain.ProblemSeverity
import ru.renattele.wizard.engine.configuration.domain.TemplateSpec
import ru.renattele.wizard.engine.configuration.domain.VersionPolicy
import ru.renattele.wizard.engine.configuration.domain.VersionRangeMatcher
import ru.renattele.wizard.manifest.ConflictStrategy
import ru.renattele.wizard.manifest.OptionParameterType as ManifestOptionParameterType
import ru.renattele.wizard.manifest.PatchOperationType
import java.security.MessageDigest

class ConfigurationModelFactory {
    fun create(bundle: CatalogBundle): ConfigurationCatalog {
        val problems = mutableListOf<Problem>()
        val sortedPacks = bundle.packs
            .sortedWith(compareBy<CatalogPackDescriptor>({ it.precedence }, { it.id }, { it.version }))

        val templates = linkedMapOf<String, TemplateSpec>()
        val options = linkedMapOf<String, OptionSpec>()

        sortedPacks.forEach { descriptor ->
            descriptor.pack.templates.forEach { template ->
                if (templates.containsKey(template.id)) {
                    problems += Problem(
                        code = ProblemCode.INVALID_CATALOG,
                        message = "Template '${template.id}' is defined by multiple packs",
                        source = descriptor.id,
                    )
                    return@forEach
                }

                templates[template.id] = TemplateSpec(
                    id = template.id,
                    displayName = template.displayName,
                    description = template.description,
                    version = template.version,
                    tags = template.tags.sorted(),
                    baseOptionIds = template.baseOptionIds.distinct().sorted(),
                    sourcePackId = descriptor.id,
                )
            }

            descriptor.pack.options.forEach { option ->
                if (options.containsKey(option.id)) {
                    problems += Problem(
                        code = ProblemCode.INVALID_CATALOG,
                        message = "Option '${option.id}' is defined by multiple packs",
                        source = descriptor.id,
                    )
                    return@forEach
                }

                options[option.id] = OptionSpec(
                    id = option.id,
                    type = option.type,
                    category = option.category,
                    displayName = option.displayName,
                    description = option.description,
                    defaultEnabled = option.defaultEnabled,
                    dependency = DependencyRule(
                        requiresOptionIds = option.dependency.requiresOptionIds.distinct().sorted(),
                        requiresCapabilities = option.dependency.requiresCapabilities.distinct().sorted(),
                        providesCapabilities = option.dependency.providesCapabilities.distinct().sorted(),
                        conflictsHard = option.dependency.conflictsHard.distinct().sorted(),
                    ),
                    versionPolicy = VersionPolicy(
                        recommended = option.version.recommended,
                        supported = option.version.supported.distinct().sorted(),
                        range = option.version.range,
                    ),
                    parameters = option.parameters.map { parameter ->
                        OptionParameterSpec(
                            id = parameter.id,
                            displayName = parameter.displayName,
                            description = parameter.description,
                            type = parameter.type.toDomainType(),
                            required = parameter.required,
                            defaultValue = parameter.defaultValue,
                            allowedValues = parameter.allowedValues.map { allowedValue ->
                                OptionParameterAllowedValue(
                                    value = allowedValue.value,
                                    displayName = allowedValue.displayName,
                                )
                            },
                        )
                    },
                    patches = option.patches.map { patch ->
                        PatchSpec(
                            operation = patch.type.toDomainOperation(),
                            targetPath = patch.targetPath,
                            content = patch.content,
                            find = patch.find,
                            replace = patch.replace,
                            activation = PatchActivation(
                                requiresOptionIds = patch.activation.requiresOptionIds.distinct().sorted(),
                                requiresCapabilities = patch.activation.requiresCapabilities.distinct().sorted(),
                            ),
                            conflictStrategy = patch.conflictStrategy.toDomainStrategy(),
                        )
                    },
                    sourcePackId = descriptor.id,
                    artifactCoordinates = option.artifact?.url,
                    artifactChecksum = option.artifact?.sha256,
                )
            }
        }

        templates.values.forEach { template ->
            template.baseOptionIds
                .filterNot(options::containsKey)
                .forEach { missingId ->
                    problems += Problem(
                        code = ProblemCode.UNKNOWN_BASE_OPTION,
                        message = "Template '${template.id}' references unknown base option '$missingId'",
                        source = template.id,
                    )
                }
        }

        options.values.forEach { option ->
            option.dependency.requiresOptionIds
                .filterNot(options::containsKey)
                .forEach { missingId ->
                    problems += Problem(
                        code = ProblemCode.UNKNOWN_REQUIRED_OPTION,
                        message = "Option '${option.id}' requires unknown option '$missingId'",
                        source = option.id,
                    )
                }

            option.dependency.conflictsHard
                .filterNot(options::containsKey)
                .forEach { missingId ->
                    problems += Problem(
                        code = ProblemCode.UNKNOWN_CONFLICT_OPTION,
                        message = "Option '${option.id}' conflicts with unknown option '$missingId'",
                        source = option.id,
                    )
                }

            val range = option.versionPolicy.range
            if (range != null && !VersionRangeMatcher.looksLikeSupportedRange(range)) {
                problems += Problem(
                    code = ProblemCode.INVALID_VERSION_RANGE,
                    message = "Option '${option.id}' has unsupported range syntax '$range'",
                    source = option.id,
                )
            }
        }

        val capabilityProviders = options.values
            .flatMap { option -> option.dependency.providesCapabilities.map { capability -> capability to option.id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, providerIds) -> providerIds.distinct().sorted() }

        val cycle = detectDependencyCycle(options)
        if (cycle.isNotEmpty()) {
            problems += Problem(
                code = ProblemCode.CYCLE_DETECTED,
                message = "Dependency cycle detected: ${cycle.joinToString(" -> ")}",
                severity = ProblemSeverity.WARNING,
                source = cycle.first(),
            )
        }

        return ConfigurationCatalog(
            packs = sortedPacks.map { descriptor ->
                PackSpec(
                    id = descriptor.id,
                    version = descriptor.version,
                    source = descriptor.source.name,
                )
            },
            templates = templates.toMap(),
            options = options.toMap(),
            capabilityProviders = capabilityProviders,
            catalogFingerprint = hashCatalog(sortedPacks),
            problems = problems.sortedWith(compareBy<Problem>({ it.source }, { it.code.name }, { it.message })),
        )
    }

    private fun detectDependencyCycle(options: Map<String, OptionSpec>): List<String> {
        val visiting = linkedSetOf<String>()
        val visited = mutableSetOf<String>()

        fun dfs(optionId: String): List<String> {
            if (optionId in visiting) {
                return visiting.dropWhile { it != optionId } + optionId
            }
            if (!visited.add(optionId)) return emptyList()

            visiting += optionId
            val cycle = options[optionId]
                ?.dependency
                ?.requiresOptionIds
                ?.sorted()
                ?.firstNotNullOfOrNull { dependencyId -> dfs(dependencyId) }
                .orEmpty()
            visiting.remove(optionId)

            return cycle
        }

        return options.keys.sorted().firstNotNullOfOrNull(::dfs).orEmpty()
    }

    private fun hashCatalog(packs: List<CatalogPackDescriptor>): String {
        val payload = buildString {
            packs.forEach { descriptor ->
                append(descriptor.id)
                append('@')
                append(descriptor.version)
                append('#')
                append(descriptor.source.name)
                append('|')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun PatchOperationType.toDomainOperation(): PatchOperation = when (this) {
        PatchOperationType.ADD_FILE -> PatchOperation.ADD_FILE
        PatchOperationType.REPLACE_IN_FILE -> PatchOperation.REPLACE_IN_FILE
        PatchOperationType.APPEND_FILE -> PatchOperation.APPEND_FILE
        PatchOperationType.REMOVE_FILE -> PatchOperation.REMOVE_FILE
    }

    private fun ConflictStrategy.toDomainStrategy(): PatchConflictStrategy = when (this) {
        ConflictStrategy.FAIL -> PatchConflictStrategy.FAIL
        ConflictStrategy.SKIP -> PatchConflictStrategy.SKIP
        ConflictStrategy.MERGE_WITH_RULE -> PatchConflictStrategy.MERGE_WITH_RULE
    }

    private fun ManifestOptionParameterType.toDomainType(): OptionParameterType = when (this) {
        ManifestOptionParameterType.STRING -> OptionParameterType.STRING
        ManifestOptionParameterType.BOOLEAN -> OptionParameterType.BOOLEAN
        ManifestOptionParameterType.ENUM -> OptionParameterType.ENUM
        ManifestOptionParameterType.MULTILINE -> OptionParameterType.MULTILINE
    }
}
