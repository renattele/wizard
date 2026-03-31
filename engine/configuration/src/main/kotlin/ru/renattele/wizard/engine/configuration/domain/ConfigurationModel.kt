package ru.renattele.wizard.engine.configuration.domain

import ru.renattele.wizard.contracts.v1.OptionTypeV1

data class ConfigurationCatalog(
    val packs: List<PackSpec>,
    val templates: Map<String, TemplateSpec>,
    val options: Map<String, OptionSpec>,
    val capabilityProviders: Map<String, List<String>>,
    val catalogFingerprint: String,
    val problems: List<Problem>,
)

data class PackSpec(
    val id: String,
    val version: String,
    val source: String,
)

data class TemplateSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val version: String,
    val tags: List<String>,
    val baseOptionIds: List<String>,
    val sourcePackId: String,
)

data class OptionSpec(
    val id: String,
    val type: OptionTypeV1,
    val category: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean,
    val dependency: DependencyRule,
    val versionPolicy: VersionPolicy,
    val parameters: List<OptionParameterSpec> = emptyList(),
    val patches: List<PatchSpec>,
    val sourcePackId: String,
    val artifactCoordinates: String?,
    val artifactChecksum: String?,
)

data class OptionParameterSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val type: OptionParameterType,
    val required: Boolean,
    val defaultValue: String?,
    val allowedValues: List<OptionParameterAllowedValue> = emptyList(),
)

data class OptionParameterAllowedValue(
    val value: String,
    val displayName: String,
)

enum class OptionParameterType {
    STRING,
    BOOLEAN,
    ENUM,
    MULTILINE,
}

data class DependencyRule(
    val requiresOptionIds: List<String>,
    val requiresCapabilities: List<String>,
    val providesCapabilities: List<String>,
    val conflictsHard: List<String>,
)

data class VersionPolicy(
    val recommended: String,
    val supported: List<String>,
    val range: String?,
)

data class PatchSpec(
    val operation: PatchOperation,
    val targetPath: String,
    val content: String?,
    val find: String?,
    val replace: String?,
    val activation: PatchActivation = PatchActivation(),
    val conflictStrategy: PatchConflictStrategy,
)

data class PatchActivation(
    val requiresOptionIds: List<String> = emptyList(),
    val requiresCapabilities: List<String> = emptyList(),
)

enum class PatchOperation {
    ADD_FILE,
    REPLACE_IN_FILE,
    APPEND_FILE,
    REMOVE_FILE,
}

enum class PatchConflictStrategy {
    FAIL,
    SKIP,
    MERGE_WITH_RULE,
}

data class Problem(
    val code: ProblemCode,
    val message: String,
    val severity: ProblemSeverity = ProblemSeverity.ERROR,
    val source: String? = null,
)

enum class ProblemCode {
    INVALID_CATALOG,
    UNKNOWN_TEMPLATE,
    UNKNOWN_OPTION,
    UNKNOWN_BASE_OPTION,
    UNKNOWN_REQUIRED_OPTION,
    UNKNOWN_CONFLICT_OPTION,
    INVALID_VERSION_RANGE,
    AMBIGUOUS_CAPABILITY_PROVIDER,
    MISSING_REQUIRED_OPTION,
    MISSING_CAPABILITY,
    HARD_CONFLICT,
    VERSION_OUT_OF_RANGE,
    CYCLE_DETECTED,
    LOCK_REQUIRED,
    LOCK_STALE,
}

enum class ProblemSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class ResolvedConfiguration(
    val templateId: String,
    val resolvedOptions: List<ResolvedOption>,
    val applyOrder: List<String>,
    val autoAdded: List<AutoAddedOption>,
    val problems: List<Problem>,
    val lockState: LockState,
)

data class ResolvedOption(
    val id: String,
    val type: OptionTypeV1,
    val category: String,
    val displayName: String,
    val version: String,
    val sourcePackId: String,
    val artifactCoordinates: String?,
    val artifactChecksum: String?,
    val providedCapabilities: List<String> = emptyList(),
    val parameters: List<OptionParameterSpec> = emptyList(),
    val patches: List<PatchSpec>,
)

data class AutoAddedOption(
    val optionId: String,
    val requiredBy: String,
    val chain: List<String>,
)

data class LockState(
    val templateId: String,
    val strictMode: Boolean,
    val options: List<LockedOption>,
    val applyOrder: List<String>,
    val catalogFingerprint: String,
    val resolutionHash: String,
)

data class LockedOption(
    val optionId: String,
    val version: String,
    val sourcePackId: String,
    val artifactCoordinates: String?,
    val artifactChecksum: String?,
)

data class VerifiedConfiguration(
    val resolution: ResolvedConfiguration,
    val lockVerified: Boolean,
)

data class GenerationPlan(
    val templateId: String,
    val resolvedOptions: List<ResolvedOption>,
    val applyOrder: List<String>,
    val strictMode: Boolean,
    val lockState: LockState,
    val problems: List<Problem>,
    val seedFiles: Map<String, String> = emptyMap(),
    val additionalPatchBatches: List<AdditionalPatchBatch> = emptyList(),
)

data class PreparedGeneration(
    val plan: GenerationPlan,
    val lockVerified: Boolean,
)

data class AdditionalPatchBatch(
    val sourceId: String,
    val patches: List<PatchSpec>,
)
