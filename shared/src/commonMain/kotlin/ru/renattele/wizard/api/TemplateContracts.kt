package ru.renattele.wizard.api

import kotlinx.serialization.Serializable

@Serializable
data class TemplateCatalogResponse(
    val templates: List<TemplateDescriptor> = emptyList(),
    val options: List<TemplateOptionDescriptor> = emptyList(),
)

@Serializable
data class TemplateDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    val version: String,
    val tags: List<String> = emptyList(),
    val baseOptionIds: List<String> = emptyList(),
)

@Serializable
data class TemplateOptionDescriptor(
    val id: String,
    val type: TemplateOptionType,
    val category: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean = false,
    val dependency: TemplateDependencyContract = TemplateDependencyContract(),
    val version: OptionVersionContract? = null,
)

@Serializable
enum class TemplateOptionType {
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
data class TemplateDependencyContract(
    val requiresOptionIds: List<String> = emptyList(),
    val requiresCapabilities: List<String> = emptyList(),
    val providesCapabilities: List<String> = emptyList(),
    val conflictsHard: List<String> = emptyList(),
    val versionRange: String? = null,
)

@Serializable
data class OptionVersionContract(
    val recommended: String,
    val supported: List<String> = emptyList(),
)

@Serializable
data class TemplateResolutionRequest(
    val templateId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val optionVersions: Map<String, String> = emptyMap(),
)

@Serializable
data class TemplateResolutionResponse(
    val templateId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val resolvedOptionIds: List<String> = emptyList(),
    val applyOrder: List<String> = emptyList(),
    val autoAdded: List<AutoAddedTemplateOption> = emptyList(),
    val issues: List<TemplateResolutionIssue> = emptyList(),
)

@Serializable
data class AutoAddedTemplateOption(
    val optionId: String,
    val requiredBy: String,
    val chain: List<String>,
)

@Serializable
data class TemplateResolutionIssue(
    val code: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
)
