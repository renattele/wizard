package ru.renattele.wizard.contracts.v1

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

const val WIZARD_API_VERSION_V1: String = "1.0.0"

@Serializable
data class WizardSessionRequestV1(
    val sessionId: String? = null,
    val selection: WizardSelectionV1,
    val strictMode: Boolean = true,
)

@Serializable
data class WizardSelectionV1(
    val templateId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val optionVersions: Map<String, String> = emptyMap(),
    val contextVars: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogResponseV1(
    val schemaVersion: String,
    val packs: List<CatalogPackV1> = emptyList(),
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
data class CatalogOptionV1(
    val id: String,
    val type: OptionTypeV1,
    val category: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean = false,
    val selectedVersion: String? = null,
)

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
    val sessionId: String? = null,
    val selection: WizardSelectionV1,
    val strictMode: Boolean = true,
)

@Serializable
data class ResolveResponseV1(
    val sessionId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val resolvedOptionIds: List<String> = emptyList(),
    val orderedOptionIds: List<String> = emptyList(),
    val autoAdded: List<AutoAddedOptionV1> = emptyList(),
    val issues: List<ResolutionIssueV1> = emptyList(),
    val compatibilityReport: CompatibilityReportV1,
    val lockfile: WizardLockfile,
)

@Serializable
data class AutoAddedOptionV1(
    val optionId: String,
    val requiredBy: String,
    val chain: List<String> = emptyList(),
)

@Serializable
data class ResolutionIssueV1(
    val code: ResolutionCodeV1,
    val message: String,
    val severity: SeverityV1 = SeverityV1.ERROR,
)

@Serializable
enum class ResolutionCodeV1 {
    UNKNOWN_OPTION,
    MISSING_REQUIRED_OPTION,
    MISSING_CAPABILITY,
    HARD_CONFLICT,
    VERSION_OUT_OF_RANGE,
    COMPATIBILITY_VIOLATION,
    CYCLE_DETECTED,
}

@Serializable
enum class SeverityV1 {
    INFO,
    WARNING,
    ERROR,
}

@Serializable
data class CompatibilityReportV1(
    val compatible: Boolean,
    val engineVersion: String,
    val apiVersion: String = WIZARD_API_VERSION_V1,
    val issues: List<CompatibilityIssueV1> = emptyList(),
)

@Serializable
data class CompatibilityIssueV1(
    val code: CompatibilityIssueCodeV1,
    val message: String,
    val severity: SeverityV1 = SeverityV1.ERROR,
)

@Serializable
enum class CompatibilityIssueCodeV1 {
    ENGINE_RANGE,
    API_RANGE,
    KOTLIN_RANGE,
    GRADLE_RANGE,
}

@Serializable
data class WizardLockfile(
    val schemaVersion: String = "1",
    val templateId: String,
    val generatedAt: Instant = Instant.parse("1970-01-01T00:00:00Z"),
    val strictMode: Boolean = true,
    val options: List<LockedOptionV1> = emptyList(),
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
    val patchReport: PatchReportV1,
    val compatibilityReport: CompatibilityReportV1,
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
    val fileName: String,
    val sizeBytes: Long,
    val patchReport: PatchReportV1,
    val compatibilityReport: CompatibilityReportV1,
)

@Serializable
data class PatchReportV1(
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
