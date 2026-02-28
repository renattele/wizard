package ru.renattele.wizard.api

import kotlinx.serialization.Serializable
import ru.renattele.wizard.config.ArchitectureLayer
import ru.renattele.wizard.config.WizardConfiguration

@Serializable
data class CreateConfigurationRequest(
    val configuration: WizardConfiguration = WizardConfiguration(),
)

@Serializable
data class UpdateStepRequest(
    val step: WizardStep,
    val configuration: WizardConfiguration,
)

@Serializable
data class ConfigurationResponse(
    val id: String,
    val configuration: WizardConfiguration,
    val updatedAt: String,
)

@Serializable
data class ValidationResult(
    val valid: Boolean,
    val issues: List<ValidationIssue> = emptyList(),
)

@Serializable
data class ValidationIssue(
    val field: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
)

@Serializable
enum class ValidationSeverity {
    INFO,
    WARNING,
    ERROR,
}

@Serializable
data class ProjectPreview(
    val modules: List<PreviewModule> = emptyList(),
    val files: List<PreviewFile> = emptyList(),
    val patchReport: PatchReport? = null,
)

@Serializable
data class PreviewModule(
    val name: String,
    val path: String,
    val dependencies: List<String> = emptyList(),
)

@Serializable
data class PreviewFile(
    val path: String,
    val description: String,
)

@Serializable
data class DependencyCatalogItem(
    val id: String,
    val category: String,
    val displayName: String,
    val description: String,
    val groupId: String,
    val artifactId: String,
    val recommendedVersion: String,
    val supportedVersions: List<String> = emptyList(),
    val conflictingLibraries: List<String> = emptyList(),
    val options: Map<String, String> = emptyMap(),
    val requires: List<String> = emptyList(),
    val conflictsHard: List<String> = emptyList(),
    val versionRange: String? = null,
    val capabilitiesProvided: List<String> = emptyList(),
    val capabilitiesRequired: List<String> = emptyList(),
    val optionType: DependencyOptionType = DependencyOptionType.LIBRARY,
)

@Serializable
enum class DependencyOptionType {
    LIBRARY,
    BUILD_SYSTEM,
    UI_FRAMEWORK,
    ARCHITECTURE,
    QUALITY,
    CI,
    BASE,
}

@Serializable
data class PresetPattern(
    val id: String,
    val displayName: String,
    val description: String,
    val layers: List<ArchitectureLayer>,
)

@Serializable
data class CatalogDependenciesResponse(
    val items: List<DependencyCatalogItem>,
)

@Serializable
data class CatalogPresetsResponse(
    val items: List<PresetPattern>,
)

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

@Serializable
data class ExportResponse(
    val fileName: String,
    val sizeBytes: Long,
)

@Serializable
enum class WizardStep {
    PROJECT,
    ARCHITECTURE,
    DEPENDENCIES,
    UI,
    QUALITY_CI,
    PREVIEW,
}

@Serializable
data class ConfigurationResolutionResponse(
    val configurationId: String,
    val selectedOptionIds: List<String>,
    val resolvedOptionIds: List<String>,
    val orderedOptionIds: List<String>,
    val autoAdded: List<AutoAddedOption> = emptyList(),
    val issues: List<ResolutionIssue> = emptyList(),
    val engineVersion: String,
)

@Serializable
data class AutoAddedOption(
    val optionId: String,
    val requiredBy: String,
    val chain: List<String>,
)

@Serializable
data class ResolutionIssue(
    val code: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
)

@Serializable
data class PatchReport(
    val appliedOptionIds: List<String> = emptyList(),
    val appliedFiles: List<String> = emptyList(),
    val skippedPatches: List<SkippedPatch> = emptyList(),
)

@Serializable
data class SkippedPatch(
    val optionId: String,
    val targetPath: String,
    val reason: String,
)
