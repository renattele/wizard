package ru.renattele.wizard.contracts.v1

import kotlin.time.Instant
import kotlinx.serialization.Serializable

const val WIZARD_API_VERSION_V1: String = "1.0.0"

@Serializable
data class WizardSelectionV1(
    val templateId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val optionVersions: Map<String, String> = emptyMap(),
    val optionParameters: Map<String, Map<String, String>> = emptyMap(),
    val contextVars: Map<String, String> = emptyMap(),
    val projectConfig: ProjectConfigV1? = null,
    val architecture: ArchitectureModelV1? = null,
    val customPatches: List<UserPatchV1> = emptyList(),
)

@Serializable
data class ProjectConfigV1(
    val projectName: String? = null,
    val packageId: String? = null,
    val targetPlatforms: List<String> = emptyList(),
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val uiFramework: String? = null,
    val designSystemPrefix: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val ciTemplate: String? = null,
    val qualityTools: List<String> = emptyList(),
)

@Serializable
data class ArchitectureModelV1(
    val mode: ArchitectureModeV1 = ArchitectureModeV1.PRESET,
    val presetPatternId: String? = null,
    val customComponentTypes: List<CustomComponentTypeV1> = emptyList(),
)

@Serializable
enum class ArchitectureModeV1 {
    PRESET,
    CUSTOM,
}

@Serializable
data class CustomComponentTypeV1(
    val id: String,
    val displayName: String,
    val layer: String,
    val fileNameTemplate: String,
    val sourceTemplate: String,
    val allowedDependencyTypeIds: List<String> = emptyList(),
)

@Serializable
data class UserPatchV1(
    val sourceId: String? = null,
    val operation: PatchOperationV1,
    val targetPath: String,
    val content: String? = null,
    val find: String? = null,
    val replace: String? = null,
    val conflictStrategy: ConflictStrategyV1 = ConflictStrategyV1.MERGE_WITH_RULE,
)

@Serializable
data class CatalogResponseV1(
    val schemaVersion: String,
    val packs: List<CatalogPackV1> = emptyList(),
    val templates: List<TemplateDescriptorV1> = emptyList(),
    val options: List<CatalogOptionV1> = emptyList(),
    val generatedAt: Instant = Instant.parse("1970-01-01T00:00:00Z"),
)

@Serializable
data class CatalogPackV1(
    val id: String,
    val version: String,
    val source: CatalogPackSourceV1,
)

@Serializable
enum class CatalogPackSourceV1 {
    LOCAL,
    REMOTE,
}

@Serializable
data class TemplateDescriptorV1(
    val id: String,
    val displayName: String,
    val description: String,
    val version: String,
    val tags: List<String> = emptyList(),
    val baseOptionIds: List<String> = emptyList(),
)

@Serializable
data class CatalogOptionV1(
    val id: String,
    val type: OptionTypeV1,
    val category: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean = false,
    val dependency: CatalogOptionDependencyV1 = CatalogOptionDependencyV1(),
    val version: CatalogOptionVersionV1 = CatalogOptionVersionV1(),
    val parameters: List<OptionParameterV1> = emptyList(),
    val patches: List<CatalogPatchSummaryV1> = emptyList(),
)

@Serializable
data class OptionParameterV1(
    val id: String,
    val displayName: String,
    val description: String = "",
    val type: OptionParameterTypeV1 = OptionParameterTypeV1.STRING,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val allowedValues: List<OptionParameterAllowedValueV1> = emptyList(),
)

@Serializable
data class OptionParameterAllowedValueV1(
    val value: String,
    val displayName: String,
)

@Serializable
enum class OptionParameterTypeV1 {
    STRING,
    BOOLEAN,
    ENUM,
    MULTILINE,
}

@Serializable
data class CatalogOptionDependencyV1(
    val requiresOptionIds: List<String> = emptyList(),
    val requiresCapabilities: List<String> = emptyList(),
    val providesCapabilities: List<String> = emptyList(),
    val conflictsHard: List<String> = emptyList(),
)

@Serializable
data class CatalogOptionVersionV1(
    val recommended: String = "latest",
    val supported: List<String> = emptyList(),
    val range: String? = null,
)

@Serializable
data class CatalogPatchSummaryV1(
    val operation: PatchOperationV1,
    val targetPath: String,
    val conflictStrategy: ConflictStrategyV1,
)

@Serializable
enum class PatchOperationV1 {
    ADD_FILE,
    REPLACE_IN_FILE,
    APPEND_FILE,
    REMOVE_FILE,
}

@Serializable
enum class ConflictStrategyV1 {
    FAIL,
    SKIP,
    MERGE_WITH_RULE,
}

@Serializable
enum class OptionTypeV1 {
    LIBRARY,
    BUILD_SYSTEM,
    UI_FRAMEWORK,
    ARCHITECTURE,
    QUALITY,
    CI,
    BASE,
    FEATURE_PLUGIN,
}

@Serializable
data class ResolveRequestV1(
    val selection: WizardSelectionV1,
    val strictMode: Boolean = true,
)

@Serializable
data class ResolveResponseV1(
    val resolvedOptions: List<ResolvedOptionV1> = emptyList(),
    val applyOrder: List<String> = emptyList(),
    val autoAdded: List<AutoAddedOptionV1> = emptyList(),
    val problems: List<ProblemV1> = emptyList(),
    val lockfile: WizardLockfile,
)

@Serializable
data class ResolvedOptionV1(
    val id: String,
    val type: OptionTypeV1,
    val category: String,
    val displayName: String,
    val version: String,
    val sourcePackId: String,
)

@Serializable
data class AutoAddedOptionV1(
    val optionId: String,
    val requiredBy: String,
    val chain: List<String> = emptyList(),
)

@Serializable
data class ProblemV1(
    val code: ProblemCodeV1,
    val message: String,
    val severity: SeverityV1 = SeverityV1.ERROR,
    val source: String? = null,
)

@Serializable
enum class ProblemCodeV1 {
    INVALID_CATALOG,
    INVALID_CUSTOM_CONFIGURATION,
    INVALID_CUSTOM_PATCH,
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

@Serializable
enum class SeverityV1 {
    INFO,
    WARNING,
    ERROR,
}

@Serializable
data class WizardLockfile(
    val schemaVersion: String = "2",
    val templateId: String,
    val generatedAt: Instant = Instant.parse("1970-01-01T00:00:00Z"),
    val strictMode: Boolean = true,
    val options: List<LockedOptionV1> = emptyList(),
    val applyOrder: List<String> = emptyList(),
    val catalogFingerprint: String = "",
    val configurationHash: String = "",
    val resolutionHash: String,
)

@Serializable
data class LockedOptionV1(
    val optionId: String,
    val version: String,
    val sourcePackId: String,
    val artifactCoordinates: String? = null,
    val artifactChecksum: String? = null,
)

@Serializable
data class PreviewRequestV1(
    val selection: WizardSelectionV1,
    val lockfile: WizardLockfile? = null,
    val strictMode: Boolean = true,
)

@Serializable
data class PreviewResponseV1(
    val files: List<GeneratedFilePreviewV1> = emptyList(),
    val generationReport: GenerationReportV1,
    val problems: List<ProblemV1> = emptyList(),
    val lockVerified: Boolean,
)

@Serializable
data class GeneratedFilePreviewV1(
    val path: String,
    val content: String,
)

@Serializable
data class ExportRequestV1(
    val selection: WizardSelectionV1,
    val lockfile: WizardLockfile? = null,
    val strictMode: Boolean = true,
    val format: ExportFormatV1 = ExportFormatV1.ZIP,
)

@Serializable
enum class ExportFormatV1 {
    ZIP,
    TAR_GZ,
}

@Serializable
data class ExportResponseV1(
    val artifact: GeneratedArtifactV1,
    val generationReport: GenerationReportV1,
    val problems: List<ProblemV1> = emptyList(),
    val lockVerified: Boolean,
)

@Serializable
data class GeneratedArtifactV1(
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val archiveBase64: String,
)

@Serializable
data class GenerationReportV1(
    val appliedOptionIds: List<String> = emptyList(),
    val appliedFiles: List<String> = emptyList(),
    val skippedPatches: List<SkippedPatchV1> = emptyList(),
)

@Serializable
data class SkippedPatchV1(
    val optionId: String,
    val targetPath: String,
    val reason: String,
)

@Serializable
data class HealthResponseV1(
    val status: String,
    val apiVersion: String = WIZARD_API_VERSION_V1,
    val engineVersion: String,
)
