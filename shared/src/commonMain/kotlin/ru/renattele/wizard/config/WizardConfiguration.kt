package ru.renattele.wizard.config

import kotlinx.serialization.Serializable

@Serializable
data class WizardConfiguration(
    val templateSystem: TemplateSystemConfig = TemplateSystemConfig(),
    val projectMetadata: ProjectMetadataConfig = ProjectMetadataConfig(),
    val architecture: ArchitectureConfig = ArchitectureConfig(),
    val dependencies: DependencySelectionConfig = DependencySelectionConfig(),
    val ui: UiConfig = UiConfig(),
    val qualityCi: QualityCiConfig = QualityCiConfig(),
)

@Serializable
data class TemplateSystemConfig(
    val templateId: String = "android-app",
    val selectedOptionIds: List<String> = emptyList(),
    val optionVersions: Map<String, String> = emptyMap(),
    val contextVars: Map<String, String> = emptyMap(),
)

@Serializable
data class ProjectMetadataConfig(
    val name: String = "",
    val packageName: String = "",
    val targets: List<ProjectTarget> = listOf(ProjectTarget.ANDROID),
    val minSdk: Int = 24,
    val targetSdk: Int = 35,
    val modularityLevel: ModularityLevel = ModularityLevel.MODULAR,
)

@Serializable
enum class ProjectTarget {
    ANDROID,
    IOS,
    DESKTOP,
    WEB,
}

@Serializable
enum class ModularityLevel {
    MONOLITH,
    MODULAR,
    ADVANCED,
}

@Serializable
data class ArchitectureConfig(
    val mode: ArchitectureMode = ArchitectureMode.PRESET,
    val presetId: String? = "MVVM",
    val customComponents: List<CustomComponentTypeConfig> = emptyList(),
)

@Serializable
enum class ArchitectureMode {
    PRESET,
    CUSTOM,
}

@Serializable
data class CustomComponentTypeConfig(
    val id: String,
    val name: String,
    val layer: ArchitectureLayer,
    val fileNameTemplate: String,
    val codeTemplate: String,
    val allowedDependencyTypeIds: List<String> = emptyList(),
)

@Serializable
enum class ArchitectureLayer {
    PRESENTATION,
    DOMAIN,
    DATA,
    CORE,
}

@Serializable
data class DependencySelectionConfig(
    val selectedLibraries: List<LibrarySelection> = emptyList(),
)

@Serializable
data class LibrarySelection(
    val libraryId: String,
    val version: String? = null,
    val enabled: Boolean = true,
    val options: Map<String, String> = emptyMap(),
)

@Serializable
data class UiConfig(
    val framework: UiFramework = UiFramework.COMPOSE,
    val themeName: String = "Default",
    val primaryColor: String = "#1F6FEB",
    val secondaryColor: String = "#2EA043",
    val designSystemPrefix: String = "W",
)

@Serializable
enum class UiFramework {
    COMPOSE,
    XML,
}

@Serializable
data class QualityCiConfig(
    val qualityTools: List<QualityTool> = listOf(QualityTool.DETEKT, QualityTool.KTLINT),
    val enabledTests: List<TestType> = listOf(TestType.UNIT),
    val ciTemplate: CiTemplate = CiTemplate.GITHUB_ACTIONS,
    val triggerBranches: List<String> = listOf("main"),
)

@Serializable
enum class QualityTool {
    DETEKT,
    KTLINT,
}

@Serializable
enum class TestType {
    UNIT,
    INTEGRATION,
    UI,
}

@Serializable
enum class CiTemplate {
    GITHUB_ACTIONS,
    GITLAB_CI,
    NONE,
}
