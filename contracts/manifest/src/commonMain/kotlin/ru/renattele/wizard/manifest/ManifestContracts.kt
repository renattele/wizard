package ru.renattele.wizard.manifest

import kotlinx.serialization.Serializable
import ru.renattele.wizard.contracts.v1.OptionTypeV1

const val WIZARD_MANIFEST_SCHEMA_VERSION: String = "1.0.0"

@Serializable
data class PluginPackManifest(
    val id: String,
    val version: String,
    val schemaVersion: String = WIZARD_MANIFEST_SCHEMA_VERSION,
    val source: ManifestPackSource = ManifestPackSource.LOCAL,
    val compatibility: CompatibilityContract = CompatibilityContract(),
    val templates: List<TemplateManifest> = emptyList(),
    val options: List<OptionManifest> = emptyList(),
)

@Serializable
enum class ManifestPackSource {
    LOCAL,
    REMOTE,
}

@Serializable
data class TemplateManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val version: String,
    val tags: List<String> = emptyList(),
    val baseOptionIds: List<String> = emptyList(),
)

@Serializable
data class OptionManifest(
    val id: String,
    val type: OptionTypeV1,
    val category: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean = false,
    val dependency: OptionDependencyContract = OptionDependencyContract(),
    val version: OptionVersionManifest = OptionVersionManifest(),
    val artifact: RemoteArtifactDescriptor? = null,
    val patches: List<PatchOperationManifest> = emptyList(),
)

@Serializable
data class OptionDependencyContract(
    val requiresOptionIds: List<String> = emptyList(),
    val requiresCapabilities: List<String> = emptyList(),
    val providesCapabilities: List<String> = emptyList(),
    val conflictsHard: List<String> = emptyList(),
)

@Serializable
data class OptionVersionManifest(
    val recommended: String = "latest",
    val supported: List<String> = emptyList(),
    val range: String? = null,
)

@Serializable
data class CompatibilityContract(
    val apiRange: String = "1.x",
    val engineRange: String = "1.x",
    val kotlinRange: String = "2.3.x",
    val gradleRange: String = "8.x",
)

@Serializable
data class RemoteArtifactDescriptor(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
data class PatchOperationManifest(
    val type: PatchOperationType,
    val targetPath: String,
    val content: String? = null,
    val find: String? = null,
    val replace: String? = null,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.FAIL,
)

@Serializable
enum class PatchOperationType {
    ADD_FILE,
    REPLACE_IN_FILE,
    APPEND_FILE,
    REMOVE_FILE,
}

@Serializable
enum class ConflictStrategy {
    FAIL,
    SKIP,
    MERGE_WITH_RULE,
}

@Serializable
data class RemotePackIndex(
    val schemaVersion: String = WIZARD_MANIFEST_SCHEMA_VERSION,
    val entries: List<RemotePackIndexEntry> = emptyList(),
)

@Serializable
data class RemotePackIndexEntry(
    val packId: String,
    val version: String,
    val artifact: RemoteArtifactDescriptor,
)

@Serializable
data class ManifestValidationResult(
    val valid: Boolean,
    val issues: List<ManifestValidationIssue> = emptyList(),
)

@Serializable
data class ManifestValidationIssue(
    val code: ManifestValidationCode,
    val message: String,
)

@Serializable
enum class ManifestValidationCode {
    DUPLICATE_OPTION,
    DUPLICATE_TEMPLATE,
    UNKNOWN_REQUIRED_OPTION,
    UNKNOWN_CONFLICT_OPTION,
    INVALID_VERSION_RANGE,
}
