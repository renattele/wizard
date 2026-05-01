package ru.renattele.wizard.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.fetch.RequestInit
import ru.renattele.wizard.contracts.v1.ArchitectureModeV1
import ru.renattele.wizard.contracts.v1.ArchitectureModelV1
import ru.renattele.wizard.contracts.v1.CatalogOptionV1
import ru.renattele.wizard.contracts.v1.CatalogResponseV1
import ru.renattele.wizard.contracts.v1.CustomComponentTypeV1
import ru.renattele.wizard.contracts.v1.ExportRequestV1
import ru.renattele.wizard.contracts.v1.ExportResponseV1
import ru.renattele.wizard.contracts.v1.GeneratedFilePreviewV1
import ru.renattele.wizard.contracts.v1.OptionParameterTypeV1
import ru.renattele.wizard.contracts.v1.OptionParameterV1
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.PreviewResponseV1
import ru.renattele.wizard.contracts.v1.ProjectConfigV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.contracts.v1.WizardLockfile
import ru.renattele.wizard.contracts.v1.WizardSelectionV1

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private const val releaseTarget = "git-release-assets"
private val releaseArtifactTypes = listOf("apk", "aab")

private val stepTitles = listOf(
    "Project",
    "Architecture",
    "Dependencies",
    "UI",
    "Quality & CI",
    "Preview & Export",
)

private data class UiState(
    var selection: WizardSelectionV1 = WizardSelectionV1(templateId = ""),
    var stepIndex: Int = 0,
    var search: String = "",
    var dependencyCategory: String = "all",
    var status: String = "",
    var lockfile: WizardLockfile? = null,
    var resolveResponse: ResolveResponseV1? = null,
    var previewResponse: PreviewResponseV1? = null,
    var exportResponse: ExportResponseV1? = null,
    var selectedFilePath: String? = null,
    var editorDraft: CustomComponentDraft? = null,
    var editorIndex: Int = -1,
)

@Serializable
private data class CustomComponentDraft(
    val id: String = "",
    val displayName: String = "",
    val layer: String = "presentation",
    val fileNameTemplate: String = "",
    val sourceTemplate: String = "",
    val allowedDependencyIds: String = "",
)

@Serializable
private data class ApiErrorPayload(
    val code: String = "UNKNOWN_ERROR",
    val message: String = "Unexpected error",
)

private var cachedCatalog: CatalogResponseV1? = null
private var state = UiState()

fun main() {
    window.onload = {
        renderLoading("Loading catalog...")
        loadCatalog()
    }
}

private fun apiUrl(path: String): String {
    val location = window.location
    val isLocalHost = location.hostname == "localhost" || location.hostname == "127.0.0.1"
    val isWebpackDevServer = isLocalHost && location.port != "8080"
    val baseUrl = if (isWebpackDevServer) {
        "${location.protocol}//${location.hostname}:8080"
    } else {
        location.origin
    }
    return "$baseUrl$path"
}

private fun loadCatalog() {
    window.fetch(apiUrl("/api/v1/catalog"))
        .then { response -> response.text() }
        .then { body ->
            cachedCatalog = json.decodeFromString(CatalogResponseV1.serializer(), body)
            state.selection = decodeSelectionFromHash() ?: defaultSelection()
            ensureDefaults()
            render()
        }
        .catch { error ->
            renderLoading("Catalog load failed: $error")
        }
}

private fun defaultSelection(): WizardSelectionV1 {
    val catalog = requireNotNull(cachedCatalog)
    val templateId = catalog.templates.firstOrNull()?.id.orEmpty()
    val defaultIds = catalog.options
        .filter { it.defaultEnabled }
        .map { it.id }
        .toMutableList()
    if ("arch-mvvm" !in defaultIds) {
        defaultIds += "arch-mvvm"
    }
    return WizardSelectionV1(
        templateId = templateId,
        selectedOptionIds = defaultIds.distinct().sorted(),
        projectConfig = ProjectConfigV1(
            projectName = "Demo App",
            packageId = "com.example.demo",
            modulePreset = "android-clean",
            featureNames = listOf("home"),
            minSdk = 24,
            targetSdk = 35,
            uiFramework = if ("ui-xml" in defaultIds) "xml" else "compose",
            designSystemPrefix = "T",
            primaryColor = "#6750A4",
            secondaryColor = "#625B71",
            ciTemplate = if ("ci-gitlab" in defaultIds) "gitlab" else "github-actions",
            releaseTarget = releaseTarget,
            releaseArtifactTypes = releaseArtifactTypes,
            qualityTools = defaultIds.filter { it.startsWith("quality-") },
        ),
        architecture = ArchitectureModelV1(
            mode = ArchitectureModeV1.PRESET,
            presetPatternId = "arch-mvvm",
        ),
        contextVars = mapOf(
            "releaseBranch" to "main",
            "signingKeystoreSecret" to "SIGNING_KEYSTORE_BASE64",
            "signingKeyAliasSecret" to "SIGNING_KEY_ALIAS",
        ),
    )
}

private fun ensureDefaults() {
    val selection = state.selection
    if (selection.templateId.isBlank()) {
        state.selection = defaultSelection()
        return
    }

    val config = selection.projectConfig ?: defaultSelection().projectConfig!!
    state.selection = selection.copy(
        selectedOptionIds = selection.selectedOptionIds.distinct().sorted(),
        projectConfig = config.copy(
            modulePreset = "android-clean",
            featureNames = config.featureNames.map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("home") },
            uiFramework = when {
                "ui-xml" in selection.selectedOptionIds -> "xml"
                else -> "compose"
            },
            ciTemplate = if ("ci-gitlab" in selection.selectedOptionIds) "gitlab" else "github-actions",
            releaseTarget = releaseTarget,
            releaseArtifactTypes = releaseArtifactTypes,
            qualityTools = selection.selectedOptionIds.filter { it.startsWith("quality-") },
        ),
    )
}

private fun renderLoading(message: String) {
    val app = document.getElementById("app") as HTMLDivElement
    app.innerHTML = """<div class="main"><div class="panel">$message</div></div>"""
}

private fun render() {
    val catalog = cachedCatalog ?: return
    ensureDefaults()
    val app = document.getElementById("app") as HTMLDivElement
    app.innerHTML = buildShellHtml(catalog)
    bindCommonActions(catalog)
    when (state.stepIndex) {
        0 -> bindProjectStep()
        1 -> bindArchitectureStep(catalog)
        2 -> bindDependenciesStep(catalog)
        3 -> bindUiStep(catalog)
        4 -> bindQualityStep(catalog)
        5 -> bindPreviewStep()
    }
    bindParameterEditors(catalog)
    bindEditorModal()
}

private fun buildShellHtml(catalog: CatalogResponseV1): String {
    val selection = state.selection
    val config = selection.projectConfig ?: return ""
    return buildString {
        append("""<div class="shell">""")
        append("""<aside class="sidebar">""")
        append(
            """
            <div class="brand">
                <h1>Wizard v1</h1>
                <p>Android project generator with curated packs, architecture presets, and export.</p>
            </div>
            """.trimIndent(),
        )
        append("""<div class="step-list">""")
        stepTitles.forEachIndexed { index, title ->
            append(
                """
                <button class="step-item ${if (state.stepIndex == index) "active" else ""}" data-step="$index">
                    <strong>${index + 1}. ${escapeHtml(title)}</strong><br />
                    <span class="muted">${stepHint(index)}</span>
                </button>
                """.trimIndent(),
            )
        }
        append("</div>")
        append("</aside>")
        append("""<main class="main">""")
        append(
            """
            <div class="toolbar">
                <div>
                    <h2 class="section-title">${escapeHtml(stepTitles[state.stepIndex])}</h2>
                    <div class="muted">${escapeHtml(config.projectName ?: "Generated project")}</div>
                </div>
                <div class="chips">
                    <span class="chip">${escapeHtml(config.packageId ?: "")}</span>
                    <span class="chip">${escapeHtml(config.featureNames.joinToString())}</span>
                </div>
            </div>
            """.trimIndent(),
        )
        append(stepContent(catalog))
        append(
            """
            <div class="footer-actions" style="margin-top:16px;">
                <button class="ghost-button" id="prevStepBtn" ${if (state.stepIndex == 0) "disabled" else ""}>Previous</button>
                <button class="button" id="nextStepBtn" ${if (state.stepIndex == stepTitles.lastIndex) "disabled" else ""}>Next</button>
            </div>
            """.trimIndent(),
        )
        append("</main>")
        append(if (state.editorDraft != null) editorModalHtml() else "")
        append("</div>")
    }
}

private fun stepHint(index: Int): String = when (index) {
    0 -> "Template, package, SDK, features"
    1 -> "Preset or custom component graph"
    2 -> "DI, network, database, logging"
    3 -> "Compose or XML, theme tokens"
    4 -> "Linters, CI, release placeholders"
    else -> "Resolve, inspect files, export ZIP"
}

private fun stepContent(catalog: CatalogResponseV1): String = when (state.stepIndex) {
    0 -> projectStepHtml(catalog)
    1 -> architectureStepHtml(catalog)
    2 -> dependenciesStepHtml(catalog)
    3 -> uiStepHtml(catalog)
    4 -> qualityStepHtml(catalog)
    else -> previewStepHtml()
}

private fun projectStepHtml(catalog: CatalogResponseV1): String {
    val selection = state.selection
    val config = selection.projectConfig!!
    val templateOptions = catalog.templates.joinToString(separator = "") { template ->
        """<option value="${escapeHtml(template.id)}" ${if (template.id == selection.templateId) "selected" else ""}>${escapeHtml(template.displayName)}</option>"""
    }
    val features = config.featureNames.joinToString(separator = "") { feature ->
        """
        <div class="feature-row">
            <div>
                <strong>${escapeHtml(feature)}</strong><br />
                <span class="muted">feature:${escapeHtml(feature.lowercase())}:presentation/domain/data</span>
            </div>
            <button class="ghost-button danger" data-remove-feature="${escapeHtml(feature)}">Remove</button>
        </div>
        """.trimIndent()
    }
    return """
        <div class="panel">
            <div class="grid two">
                <div class="field">
                    <label for="templateId">Template</label>
                    <select id="templateId">$templateOptions</select>
                </div>
                <div class="field">
                    <label for="projectName">Project name</label>
                    <input id="projectName" value="${escapeHtml(config.projectName.orEmpty())}" />
                </div>
                <div class="field">
                    <label for="packageId">Package</label>
                    <input id="packageId" value="${escapeHtml(config.packageId.orEmpty())}" />
                </div>
                <div class="field">
                    <label for="modulePreset">Module preset</label>
                    <input id="modulePreset" value="${escapeHtml(config.modulePreset)}" disabled />
                </div>
                <div class="field">
                    <label for="minSdk">Min SDK</label>
                    <input id="minSdk" type="number" value="${config.minSdk ?: 24}" />
                </div>
                <div class="field">
                    <label for="targetSdk">Target SDK</label>
                    <input id="targetSdk" type="number" value="${config.targetSdk ?: 35}" />
                </div>
            </div>
        </div>
        <div class="panel">
            <h3 class="section-title">Features</h3>
            <p class="section-text">Each feature generates `presentation`, `domain`, and `data` modules.</p>
            <div class="inline-actions">
                <input id="featureInput" placeholder="catalog" />
                <button class="button" id="addFeatureBtn">Add feature</button>
            </div>
            <div class="feature-list" style="margin-top:12px;">
                $features
            </div>
        </div>
    """.trimIndent()
}

private fun architectureStepHtml(catalog: CatalogResponseV1): String {
    val selection = state.selection
    val architecture = selection.architecture
    val presetMode = architecture?.mode != ArchitectureModeV1.CUSTOM
    val presetId = if (presetMode) selectedArchitectureId() ?: "arch-mvvm" else ""
    val options = catalog.options.filter { it.type == OptionTypeV1.ARCHITECTURE }
    val optionCards = options.joinToString(separator = "") { option ->
        """
        <button class="option-card ${if (presetId == option.id) "active" else ""}" data-arch-option="${escapeHtml(option.id)}">
            <h3>${escapeHtml(option.displayName)}</h3>
            <div class="meta">${escapeHtml(option.description)}</div>
        </button>
        """.trimIndent()
    }
    val components = architecture?.customComponentTypes.orEmpty().joinToString(separator = "") { component ->
        """
        <div class="component-row">
            <div>
                <strong>${escapeHtml(component.displayName)}</strong><br />
                <span class="muted">${escapeHtml(component.layer)} · ${escapeHtml(component.fileNameTemplate)}</span>
            </div>
            <div class="inline-actions">
                <button class="ghost-button" data-edit-component="${escapeHtml(component.id)}">Edit</button>
                <button class="ghost-button danger" data-delete-component="${escapeHtml(component.id)}">Delete</button>
            </div>
        </div>
        """.trimIndent()
    }
    return """
        <div class="panel">
            <div class="segmented">
                <button id="presetModeBtn" class="${if (presetMode) "active" else ""}">Preset patterns</button>
                <button id="customModeBtn" class="${if (!presetMode) "active" else ""}">Custom</button>
            </div>
        </div>
        <div class="panel">
            ${if (presetMode) {
                """
                <h3 class="section-title">Preset architectures</h3>
                <div class="option-grid">$optionCards</div>
                ${parameterEditorsHtml(catalog, options.filter { it.id == presetId })}
                """
            } else {
                """
                <h3 class="section-title">Custom component types</h3>
                <p class="section-text">Define reusable presentation/domain/data component templates.</p>
                <div class="inline-actions">
                    <button class="button" id="addComponentBtn">Add component type</button>
                </div>
                <div class="component-list" style="margin-top:12px;">$components</div>
                """
            }}
        </div>
    """.trimIndent()
}

private fun dependenciesStepHtml(catalog: CatalogResponseV1): String {
    val dependencyOptions = dependencyOptions(catalog)
    val categories = listOf("all") + dependencyOptions.map { it.category }.distinct().sorted()
    val categoryOptions = categories.joinToString(separator = "") { category ->
        """<option value="${escapeHtml(category)}" ${if (category == state.dependencyCategory) "selected" else ""}>${escapeHtml(category)}</option>"""
    }
    val visible = dependencyOptions
        .filter { state.dependencyCategory == "all" || it.category == state.dependencyCategory }
        .filter { option ->
            state.search.isBlank() ||
                option.displayName.contains(state.search, ignoreCase = true) ||
                option.description.contains(state.search, ignoreCase = true)
        }
    val cards = visible.joinToString(separator = "") { option -> optionCardHtml(option) }
    val selected = dependencyOptions.filter { it.id in state.selection.selectedOptionIds }
    return """
        <div class="panel">
            <div class="grid two">
                <div class="field">
                    <label for="dependencySearch">Search</label>
                    <input id="dependencySearch" value="${escapeHtml(state.search)}" placeholder="Retrofit, Hilt, Room..." />
                </div>
                <div class="field">
                    <label for="dependencyCategory">Category</label>
                    <select id="dependencyCategory">$categoryOptions</select>
                </div>
            </div>
        </div>
        <div class="panel">
            <h3 class="section-title">Selected stack</h3>
            <div class="chips">
                ${selected.joinToString(separator = "") { """<span class="chip">${escapeHtml(it.displayName)}</span>""" }}
            </div>
        </div>
        <div class="panel">
            <div class="option-grid">$cards</div>
            ${parameterEditorsHtml(catalog, selected)}
        </div>
    """.trimIndent()
}

private fun uiStepHtml(catalog: CatalogResponseV1): String {
    val config = state.selection.projectConfig!!
    val options = catalog.options.filter { it.type == OptionTypeV1.UI_FRAMEWORK }
    val selected = options.filter { it.id in state.selection.selectedOptionIds }
    return """
        <div class="panel">
                <h3 class="section-title">UI framework</h3>
            <div class="option-grid">
                ${options.joinToString(separator = "") { option -> optionCardHtml(option) }}
            </div>
        </div>
        <div class="panel">
            <div class="grid three">
                <div class="field">
                    <label for="designPrefix">Design system prefix</label>
                    <input id="designPrefix" value="${escapeHtml(config.designSystemPrefix.orEmpty())}" />
                </div>
                <div class="field">
                    <label for="primaryColor">Primary color</label>
                    <input id="primaryColor" type="color" value="${escapeHtml(normalizeColor(config.primaryColor ?: "#6750A4"))}" />
                </div>
                <div class="field">
                    <label for="secondaryColor">Secondary color</label>
                    <input id="secondaryColor" type="color" value="${escapeHtml(normalizeColor(config.secondaryColor ?: "#625B71"))}" />
                </div>
            </div>
        </div>
        <div class="panel">
            <h3 class="section-title">Token preview</h3>
            <div class="grid three">
                <div class="summary-card">
                    <h3>${escapeHtml(config.designSystemPrefix.orEmpty())}Button</h3>
                    <div class="meta">Primary action component</div>
                </div>
                <div class="summary-card">
                    <h3>${escapeHtml(config.designSystemPrefix.orEmpty())}TextField</h3>
                    <div class="meta">Input component</div>
                </div>
                <div class="summary-card">
                    <h3>${escapeHtml(config.designSystemPrefix.orEmpty())}Card</h3>
                    <div class="meta">Container component</div>
                </div>
            </div>
            <div class="inline-actions" style="margin-top:12px;">
                <span class="chip" style="background:${escapeHtml(normalizeColor(config.primaryColor ?: "#6750A4"))}; color:#fff;">Primary</span>
                <span class="chip" style="background:${escapeHtml(normalizeColor(config.secondaryColor ?: "#625B71"))}; color:#fff;">Secondary</span>
            </div>
            ${parameterEditorsHtml(catalog, selected)}
        </div>
    """.trimIndent()
}

private fun qualityStepHtml(catalog: CatalogResponseV1): String {
    val config = state.selection.projectConfig!!
    val qualityOptions = catalog.options.filter { it.type == OptionTypeV1.QUALITY }
    val ciOptions = catalog.options.filter { it.type == OptionTypeV1.CI }
    return """
        <div class="panel">
            <h3 class="section-title">Quality gates</h3>
            <div class="option-grid">
                ${qualityOptions.joinToString(separator = "") { optionCardHtml(it) }}
            </div>
        </div>
        <div class="panel">
            <h3 class="section-title">CI provider</h3>
            <div class="option-grid">
                ${ciOptions.joinToString(separator = "") { optionCardHtml(it) }}
            </div>
        </div>
        <div class="panel">
            <h3 class="section-title">Release placeholders</h3>
            <div class="grid three">
                <div class="field">
                    <label for="releaseBranch">Release branch</label>
                    <input id="releaseBranch" value="${escapeHtml(state.selection.contextVars["releaseBranch"].orEmpty())}" />
                </div>
                <div class="field">
                    <label for="keystoreSecret">Keystore secret</label>
                    <input id="keystoreSecret" value="${escapeHtml(state.selection.contextVars["signingKeystoreSecret"].orEmpty())}" />
                </div>
                <div class="field">
                    <label for="aliasSecret">Key alias secret</label>
                    <input id="aliasSecret" value="${escapeHtml(state.selection.contextVars["signingKeyAliasSecret"].orEmpty())}" />
                </div>
            </div>
            <div class="chips" style="margin-top:12px;">
                <span class="chip">${escapeHtml(config.ciTemplate.orEmpty())}</span>
                <span class="chip">${escapeHtml(config.releaseTarget)}</span>
                <span class="chip">${escapeHtml(config.releaseArtifactTypes.joinToString())}</span>
            </div>
        </div>
    """.trimIndent()
}

private fun previewStepHtml(): String {
    val preview = state.previewResponse
    val files = preview?.files.orEmpty()
    val selectedFile = files.firstOrNull { it.path == state.selectedFilePath } ?: files.firstOrNull()
    val modules = files.mapNotNull(::moduleOf).distinct().sorted()
    return """
        <div class="panel">
            <div class="inline-actions">
                <button class="button" id="resolveBtn">Resolve</button>
                <button class="button" id="previewBtn">Preview</button>
                <button class="ghost-button" id="downloadZipBtn">Download ZIP</button>
                <button class="ghost-button" id="configJsonBtn">Export config</button>
                <button class="ghost-button" id="shareLinkBtn">Copy share link</button>
            </div>
        </div>
        <div class="panel">
            <div class="grid three">
                <div class="summary-card">
                    <h3>Modules</h3>
                    <div class="meta">${modules.joinToString("<br />")}</div>
                </div>
                <div class="summary-card">
                    <h3>Selected options</h3>
                    <div class="meta">${state.selection.selectedOptionIds.joinToString("<br />")}</div>
                </div>
                <div class="summary-card">
                    <h3>Status</h3>
                    <div class="meta">${escapeHtml(state.status.ifBlank { "Ready" })}</div>
                </div>
            </div>
        </div>
        <div class="panel">
            <h3 class="section-title">Diagnostics</h3>
            ${diagnosticsHtml()}
        </div>
        <div class="panel">
            <h3 class="section-title">Generated files</h3>
            <div class="result-grid">
                <div class="file-list">
                    ${files.joinToString(separator = "") { file ->
                        """<button class="${if (file.path == selectedFile?.path) "active" else ""}" data-file-path="${escapeHtml(file.path)}">${escapeHtml(file.path)}</button>"""
                    }}
                </div>
                <div class="file-preview">${escapeHtml(selectedFile?.content ?: "Run Preview to inspect files.")}</div>
            </div>
        </div>
    """.trimIndent()
}

private fun optionCardHtml(option: CatalogOptionV1): String = """
    <button class="option-card ${if (option.id in state.selection.selectedOptionIds) "active" else ""}" data-toggle-option="${escapeHtml(option.id)}">
        <h3>${escapeHtml(option.displayName)}</h3>
        <div class="meta">${escapeHtml(option.category)} · ${escapeHtml(option.type.name)}</div>
        <div class="meta">${escapeHtml(option.description)}</div>
    </button>
""".trimIndent()

private fun parameterEditorsHtml(
    catalog: CatalogResponseV1,
    options: List<CatalogOptionV1>,
): String {
    val selection = state.selection
    val rendered = options
        .filter { it.parameters.isNotEmpty() }
        .joinToString(separator = "") { option ->
            val fields = option.parameters.joinToString(separator = "") { parameter ->
                val value = selection.optionParameters[option.id]?.get(parameter.id) ?: parameter.defaultValue.orEmpty()
                parameterFieldHtml(option.id, parameter, value)
            }
            """
            <div class="panel">
                <h3 class="section-title">${escapeHtml(option.displayName)} parameters</h3>
                <div class="grid two">$fields</div>
            </div>
            """.trimIndent()
        }
    return rendered
}

private fun parameterFieldHtml(
    optionId: String,
    parameter: OptionParameterV1,
    currentValue: String,
): String {
    val fieldId = parameterFieldId(optionId, parameter.id)
    val description = parameter.description.takeIf { it.isNotBlank() }?.let {
        """<div class="muted">${escapeHtml(it)}</div>"""
    }.orEmpty()
    return when (parameter.type) {
        OptionParameterTypeV1.BOOLEAN -> """
            <div class="field">
                <label for="${escapeHtml(fieldId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <select id="${escapeHtml(fieldId)}" data-parameter-option="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}">
                    <option value="true" ${if (currentValue == "true") "selected" else ""}>true</option>
                    <option value="false" ${if (currentValue == "false") "selected" else ""}>false</option>
                </select>
            </div>
        """.trimIndent()

        OptionParameterTypeV1.ENUM -> """
            <div class="field">
                <label for="${escapeHtml(fieldId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <select id="${escapeHtml(fieldId)}" data-parameter-option="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}">
                    ${parameter.allowedValues.joinToString(separator = "") { allowed ->
                        """<option value="${escapeHtml(allowed.value)}" ${if (allowed.value == currentValue) "selected" else ""}>${escapeHtml(allowed.displayName)}</option>"""
                    }}
                </select>
            </div>
        """.trimIndent()

        OptionParameterTypeV1.MULTILINE -> """
            <div class="field">
                <label for="${escapeHtml(fieldId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <textarea id="${escapeHtml(fieldId)}" data-parameter-option="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}">${escapeHtml(currentValue)}</textarea>
            </div>
        """.trimIndent()

        OptionParameterTypeV1.STRING -> """
            <div class="field">
                <label for="${escapeHtml(fieldId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <input id="${escapeHtml(fieldId)}" value="${escapeHtml(currentValue)}" data-parameter-option="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}" />
            </div>
        """.trimIndent()
    }
}

private fun editorModalHtml(): String {
    val draft = state.editorDraft ?: return ""
    return """
        <div class="modal-backdrop">
            <div class="modal">
                <h3 class="section-title">${if (state.editorIndex >= 0) "Edit component" else "Add component"}</h3>
                <div class="grid two">
                    <div class="field">
                        <label for="componentId">Id</label>
                        <input id="componentId" value="${escapeHtml(draft.id)}" />
                    </div>
                    <div class="field">
                        <label for="componentDisplayName">Display name</label>
                        <input id="componentDisplayName" value="${escapeHtml(draft.displayName)}" />
                    </div>
                    <div class="field">
                        <label for="componentLayer">Layer</label>
                        <select id="componentLayer">
                            ${listOf("presentation", "domain", "data").joinToString(separator = "") { layer ->
                                """<option value="$layer" ${if (draft.layer == layer) "selected" else ""}>$layer</option>"""
                            }}
                        </select>
                    </div>
                    <div class="field">
                        <label for="componentDeps">Allowed dependency ids</label>
                        <input id="componentDeps" value="${escapeHtml(draft.allowedDependencyIds)}" placeholder="interactor, router" />
                    </div>
                </div>
                <div class="field" style="margin-top:12px;">
                    <label for="componentFileName">File name template</label>
                    <input id="componentFileName" value="${escapeHtml(draft.fileNameTemplate)}" placeholder="${escapeHtml("src/main/kotlin/\${PackagePath}/feature/\${FeaturePackage}/presentation/\${FeatureClass}Coordinator.kt")}" />
                </div>
                <div class="field" style="margin-top:12px;">
                    <label for="componentSource">Source template</label>
                    <textarea id="componentSource">${escapeHtml(draft.sourceTemplate)}</textarea>
                </div>
                <div class="footer-actions" style="margin-top:16px;">
                    <button class="ghost-button" id="cancelComponentBtn">Cancel</button>
                    <button class="button" id="saveComponentBtn">Save component</button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun bindCommonActions(catalog: CatalogResponseV1) {
    bindButtons("[data-step]") { button ->
        state.stepIndex = dataAttr(button, "step")?.toIntOrNull() ?: 0
        render()
    }
    (document.getElementById("prevStepBtn") as? HTMLButtonElement)?.onclick = {
        state.stepIndex = (state.stepIndex - 1).coerceAtLeast(0)
        render()
        null
    }
    (document.getElementById("nextStepBtn") as? HTMLButtonElement)?.onclick = {
        state.stepIndex = (state.stepIndex + 1).coerceAtMost(stepTitles.lastIndex)
        render()
        null
    }
    bindButtons("[data-toggle-option]") { button ->
        toggleOption(dataAttr(button, "toggle-option").orEmpty(), catalog)
    }
}

private fun bindProjectStep() {
    bindInputValue("templateId") { mutateSelection { copy(templateId = it) } }
    bindInputValue("projectName") { updateConfig { copy(projectName = it) } }
    bindInputValue("packageId") { updateConfig { copy(packageId = it) } }
    bindInputValue("minSdk") { updateConfig { copy(minSdk = it.toIntOrNull() ?: 24) } }
    bindInputValue("targetSdk") { updateConfig { copy(targetSdk = it.toIntOrNull() ?: 35) } }
    (document.getElementById("addFeatureBtn") as? HTMLButtonElement)?.onclick = {
        val input = document.getElementById("featureInput") as? HTMLInputElement
        if (input != null) {
            val value = input.value.trim()
            if (value.isNotBlank()) {
                updateConfig {
                    copy(featureNames = (featureNames + value).distinct())
                }
            }
            input.value = ""
        }
        null
    }
    bindButtons("[data-remove-feature]") { button ->
        val feature = dataAttr(button, "remove-feature").orEmpty()
        updateConfig {
            copy(featureNames = featureNames.filterNot { it == feature }.ifEmpty { listOf("home") })
        }
    }
}

private fun bindArchitectureStep(catalog: CatalogResponseV1) {
    (document.getElementById("presetModeBtn") as? HTMLButtonElement)?.onclick = {
        val preset = selectedArchitectureId() ?: "arch-mvvm"
        mutateSelection {
            copy(
                selectedOptionIds = (selectedOptionIds.filterNot { it.startsWith("arch-") } + preset).distinct().sorted(),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.PRESET,
                    presetPatternId = preset,
                ),
            )
        }
        render()
        null
    }
    (document.getElementById("customModeBtn") as? HTMLButtonElement)?.onclick = {
        mutateSelection {
            copy(
                selectedOptionIds = selectedOptionIds.filterNot { it.startsWith("arch-") },
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.CUSTOM,
                    customComponentTypes = architecture?.customComponentTypes.orEmpty(),
                ),
            )
        }
        render()
        null
    }
    bindButtons("[data-arch-option]") { button ->
        val optionId = dataAttr(button, "arch-option").orEmpty()
        mutateSelection {
            copy(
                selectedOptionIds = (selectedOptionIds.filterNot { it.startsWith("arch-") } + optionId).distinct().sorted(),
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.PRESET,
                    presetPatternId = optionId,
                ),
            )
        }
    }
    (document.getElementById("addComponentBtn") as? HTMLButtonElement)?.onclick = {
        state.editorIndex = -1
        state.editorDraft = CustomComponentDraft(
            fileNameTemplate = "src/main/kotlin/\${PackagePath}/feature/\${FeaturePackage}/presentation/\${FeatureClass}Coordinator.kt",
            sourceTemplate = "package \${Package}.feature.\${FeaturePackage}.presentation\n\nclass \${FeatureClass}Coordinator",
        )
        render()
        null
    }
    bindButtons("[data-edit-component]") { button ->
        val componentId = dataAttr(button, "edit-component").orEmpty()
        val component = state.selection.architecture?.customComponentTypes?.firstOrNull { it.id == componentId }
        if (component != null) {
            state.editorIndex = state.selection.architecture?.customComponentTypes?.indexOf(component) ?: -1
            state.editorDraft = CustomComponentDraft(
                id = component.id,
                displayName = component.displayName,
                layer = component.layer,
                fileNameTemplate = component.fileNameTemplate,
                sourceTemplate = component.sourceTemplate,
                allowedDependencyIds = component.allowedDependencyTypeIds.joinToString(", "),
            )
            render()
        }
    }
    bindButtons("[data-delete-component]") { button ->
        val componentId = dataAttr(button, "delete-component").orEmpty()
        mutateSelection {
            val next = architecture?.customComponentTypes.orEmpty().filterNot { it.id == componentId }
            copy(
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.CUSTOM,
                    customComponentTypes = next,
                ),
            )
        }
    }
}

private fun bindDependenciesStep(catalog: CatalogResponseV1) {
    bindInputValue("dependencySearch") {
        state.search = it
        render()
    }
    bindInputValue("dependencyCategory") {
        state.dependencyCategory = it
        render()
    }
}

private fun bindUiStep(catalog: CatalogResponseV1) {
    bindInputValue("designPrefix") { updateConfig { copy(designSystemPrefix = it) } }
    bindInputValue("primaryColor") { updateConfig { copy(primaryColor = normalizeColor(it)) } }
    bindInputValue("secondaryColor") { updateConfig { copy(secondaryColor = normalizeColor(it)) } }
}

private fun bindQualityStep(catalog: CatalogResponseV1) {
    bindInputValue("releaseBranch") { updateContextVar("releaseBranch", it) }
    bindInputValue("keystoreSecret") { updateContextVar("signingKeystoreSecret", it) }
    bindInputValue("aliasSecret") { updateContextVar("signingKeyAliasSecret", it) }
}

private fun bindPreviewStep() {
    (document.getElementById("resolveBtn") as? HTMLButtonElement)?.onclick = {
        resolveSelection()
        null
    }
    (document.getElementById("previewBtn") as? HTMLButtonElement)?.onclick = {
        previewSelection()
        null
    }
    (document.getElementById("downloadZipBtn") as? HTMLButtonElement)?.onclick = {
        downloadArchive()
        null
    }
    (document.getElementById("configJsonBtn") as? HTMLButtonElement)?.onclick = {
        downloadText(
            fileName = "wizard-selection.json",
            contentType = "application/json",
            content = json.encodeToString(WizardSelectionV1.serializer(), state.selection),
        )
        null
    }
    (document.getElementById("shareLinkBtn") as? HTMLButtonElement)?.onclick = {
        val hash = encodeSelectionToHash(state.selection)
        window.location.hash = hash
        val url = "${window.location.origin}${window.location.pathname}#$hash"
        state.status = "Share link updated"
        val clipboard = js("navigator.clipboard")
        if (clipboard != null) {
            clipboard.writeText(url)
        }
        render()
        null
    }
    bindButtons("[data-file-path]") { button ->
        state.selectedFilePath = dataAttr(button, "file-path")
        render()
    }
}

private fun bindEditorModal() {
    (document.getElementById("cancelComponentBtn") as? HTMLButtonElement)?.onclick = {
        state.editorDraft = null
        render()
        null
    }
    (document.getElementById("saveComponentBtn") as? HTMLButtonElement)?.onclick = {
        val draft = CustomComponentDraft(
            id = valueOf("componentId"),
            displayName = valueOf("componentDisplayName"),
            layer = valueOf("componentLayer"),
            fileNameTemplate = valueOf("componentFileName"),
            sourceTemplate = textareaValueOf("componentSource"),
            allowedDependencyIds = valueOf("componentDeps"),
        )
        val component = CustomComponentTypeV1(
            id = draft.id.trim(),
            displayName = draft.displayName.trim(),
            layer = draft.layer.trim(),
            fileNameTemplate = draft.fileNameTemplate.trim(),
            sourceTemplate = draft.sourceTemplate,
            allowedDependencyTypeIds = draft.allowedDependencyIds.split(',').map(String::trim).filter(String::isNotBlank),
        )
        mutateSelection {
            val current = architecture?.customComponentTypes.orEmpty().toMutableList()
            if (state.editorIndex >= 0) {
                current[state.editorIndex] = component
            } else {
                current += component
            }
            copy(
                architecture = ArchitectureModelV1(
                    mode = ArchitectureModeV1.CUSTOM,
                    customComponentTypes = current,
                ),
                selectedOptionIds = selectedOptionIds.filterNot { it.startsWith("arch-") },
            )
        }
        state.editorDraft = null
        render()
        null
    }
}

private fun bindParameterEditors(catalog: CatalogResponseV1) {
    val nodes = document.querySelectorAll("[data-parameter-option]")
    for (index in 0 until nodes.length) {
        val element = nodes.item(index)
        when (element) {
            is HTMLInputElement -> element.onchange = {
                updateParameter(
                    optionId = dataAttr(element, "parameter-option").orEmpty(),
                    parameterId = dataAttr(element, "parameter-id").orEmpty(),
                    value = element.value,
                )
                null
            }

            is HTMLSelectElement -> element.onchange = {
                updateParameter(
                    optionId = dataAttr(element, "parameter-option").orEmpty(),
                    parameterId = dataAttr(element, "parameter-id").orEmpty(),
                    value = element.value,
                )
                null
            }

            is HTMLTextAreaElement -> element.onchange = {
                updateParameter(
                    optionId = dataAttr(element, "parameter-option").orEmpty(),
                    parameterId = dataAttr(element, "parameter-id").orEmpty(),
                    value = element.value,
                )
                null
            }
        }
    }
}

private fun resolveSelection(after: (() -> Unit)? = null) {
    state.status = "Resolving..."
    render()
    val body = json.encodeToString(
        ResolveRequestV1.serializer(),
        ResolveRequestV1(
            selection = state.selection,
            strictMode = true,
        ),
    )
    postJson(apiUrl("/api/v1/resolve"), body)
        .then { payload ->
            val response = json.decodeFromString(ResolveResponseV1.serializer(), payload)
            state.resolveResponse = response
            state.lockfile = response.lockfile
            state.status = "Resolved ${response.resolvedOptions.size} options"
            render()
            after?.invoke()
        }
        .catch { error ->
            state.status = "Resolve failed: $error"
            render()
        }
}

private fun previewSelection() {
    resolveSelection {
        val lockfile = state.lockfile ?: return@resolveSelection
        val body = json.encodeToString(
            PreviewRequestV1.serializer(),
            PreviewRequestV1(
                selection = state.selection,
                lockfile = lockfile,
                strictMode = true,
            ),
        )
        state.status = "Previewing..."
        render()
        postJson(apiUrl("/api/v1/preview"), body)
            .then { payload ->
                val response = json.decodeFromString(PreviewResponseV1.serializer(), payload)
                state.previewResponse = response
                state.selectedFilePath = response.files.firstOrNull()?.path
                state.status = "Preview ready"
                render()
            }
            .catch { error ->
                state.status = "Preview failed: $error"
                render()
            }
    }
}

private fun downloadArchive() {
    resolveSelection {
        val lockfile = state.lockfile ?: return@resolveSelection
        val body = json.encodeToString(
            ExportRequestV1.serializer(),
            ExportRequestV1(
                selection = state.selection,
                lockfile = lockfile,
                strictMode = true,
            ),
        )
        state.status = "Preparing archive..."
        render()
        window.fetch(
            input = apiUrl("/api/v1/export/download"),
            init = RequestInit(
                method = "POST",
                headers = js("({ 'Content-Type': 'application/json' })"),
                body = body,
            ),
        )
            .then { response ->
                if (response.ok) {
                    val fileName = response.headers.get("Content-Disposition")
                        ?.substringAfter("filename=\"")
                        ?.substringBefore("\"")
                        ?: "generated-project.zip"
                    response.blob().then { blob ->
                        saveBlob(fileName, blob)
                        state.status = "Archive downloaded"
                        render()
                    }
                } else {
                    response.text().then { body ->
                        throw RuntimeException(parseApiError(body, response.status.toString()))
                    }
                }
            }
            .catch { error ->
                state.status = "Download failed: $error"
                render()
            }
    }
}

private fun postJson(
    url: String,
    body: String,
) = window.fetch(
    input = url,
    init = RequestInit(
        method = "POST",
        headers = js("({ 'Content-Type': 'application/json' })"),
        body = body,
    ),
).then { response ->
    response.text().then { payload ->
        if (response.ok) {
            payload
        } else {
            throw RuntimeException(parseApiError(payload, response.status.toString()))
        }
    }
}

private fun parseApiError(payload: String, status: String): String {
    val decoded = runCatching {
        json.decodeFromString(ApiErrorPayload.serializer(), payload)
    }.getOrNull()
    return decoded?.message?.takeIf(String::isNotBlank)
        ?: "HTTP $status: ${payload.ifBlank { "Unknown error" }}"
}

private fun toggleOption(optionId: String, catalog: CatalogResponseV1) {
    val option = catalog.options.firstOrNull { it.id == optionId } ?: return
    val group = when {
        option.type == OptionTypeV1.UI_FRAMEWORK -> "ui"
        option.type == OptionTypeV1.CI -> "ci"
        option.id.startsWith("di-") -> "di"
        option.type == OptionTypeV1.ARCHITECTURE -> "architecture"
        else -> null
    }
    mutateSelection {
        val ids = selectedOptionIds.toMutableList()
        if (optionId in ids) {
            ids.remove(optionId)
        } else {
            if (group != null) {
                ids.removeAll { existingId ->
                    val existing = catalog.options.firstOrNull { it.id == existingId } ?: return@removeAll false
                    when (group) {
                        "ui" -> existing.type == OptionTypeV1.UI_FRAMEWORK
                        "ci" -> existing.type == OptionTypeV1.CI
                        "di" -> existing.id.startsWith("di-")
                        "architecture" -> existing.type == OptionTypeV1.ARCHITECTURE
                        else -> false
                    }
                }
            }
            ids += optionId
        }
        copy(
            selectedOptionIds = ids.distinct().sorted(),
            architecture = when {
                option.type == OptionTypeV1.ARCHITECTURE && optionId in ids ->
                    ArchitectureModelV1(mode = ArchitectureModeV1.PRESET, presetPatternId = optionId)
                architecture?.mode == ArchitectureModeV1.CUSTOM -> architecture
                else -> architecture
            },
        )
    }
}

private fun updateParameter(optionId: String, parameterId: String, value: String) {
    mutateSelection {
        val next = optionParameters.toMutableMap()
        val optionValues = next[optionId].orEmpty().toMutableMap()
        optionValues[parameterId] = value
        next[optionId] = optionValues
        copy(optionParameters = next)
    }
}

private fun mutateSelection(update: WizardSelectionV1.() -> WizardSelectionV1) {
    state.selection = state.selection.update()
    state.lockfile = null
    state.resolveResponse = null
    state.previewResponse = null
    state.exportResponse = null
    state.selectedFilePath = null
    ensureDefaults()
    render()
}

private fun updateConfig(update: ProjectConfigV1.() -> ProjectConfigV1) {
    mutateSelection {
        copy(projectConfig = requireNotNull(projectConfig).update())
    }
}

private fun updateContextVar(key: String, value: String) {
    mutateSelection {
        copy(contextVars = contextVars + mapOf(key to value))
    }
}

private fun bindInputValue(id: String, onChange: (String) -> Unit) {
    when (val element = document.getElementById(id)) {
        is HTMLInputElement -> element.onchange = {
            onChange(element.value)
            null
        }

        is HTMLSelectElement -> element.onchange = {
            onChange(element.value)
            null
        }
    }
}

private fun bindButtons(selector: String, block: (HTMLButtonElement) -> Unit) {
    val nodes = document.querySelectorAll(selector)
    for (index in 0 until nodes.length) {
        val button = nodes.item(index) as? HTMLButtonElement ?: continue
        button.onclick = {
            block(button)
            null
        }
    }
}

private fun dataAttr(element: org.w3c.dom.Element, name: String): String? =
    element.getAttribute("data-$name")

private fun dependencyOptions(catalog: CatalogResponseV1): List<CatalogOptionV1> =
    catalog.options.filter { option ->
        option.type == OptionTypeV1.LIBRARY &&
            option.category !in setOf("ui", "architecture", "quality", "ci", "base", "build", "target")
    }

private fun selectedArchitectureId(): String? =
    state.selection.selectedOptionIds.firstOrNull { it.startsWith("arch-") }
        ?: state.selection.architecture?.presetPatternId

private fun diagnosticsHtml(): String {
    val preview = state.previewResponse
    if (preview != null) {
        val report = preview.generationReport
        return """
            <div class="grid two">
                <div class="summary-card">
                    <h3>Generation</h3>
                    <div class="meta">Files: ${preview.files.size}</div>
                    <div class="meta">Applied options: ${report.appliedOptionIds.size}</div>
                    <div class="meta">Applied files: ${report.appliedFiles.size}</div>
                    <div class="meta">Skipped patches: ${report.skippedPatches.size}</div>
                </div>
                <div class="summary-card">
                    <h3>Resolution</h3>
                    <div class="meta">Lock verified: ${if (preview.lockVerified) "yes" else "no"}</div>
                    <div class="meta">Problems: ${preview.problems.size}</div>
                    <div class="meta">Status: ${escapeHtml(state.status.ifBlank { "Ready" })}</div>
                </div>
            </div>
            ${problemsHtml(preview.problems)}
        """.trimIndent()
    }
    val resolve = state.resolveResponse
    if (resolve != null) {
        return """
            <div class="grid two">
                <div class="summary-card">
                    <h3>Resolve</h3>
                    <div class="meta">Resolved options: ${resolve.resolvedOptions.size}</div>
                    <div class="meta">Apply order: ${resolve.applyOrder.size}</div>
                    <div class="meta">Auto-added: ${resolve.autoAdded.size}</div>
                </div>
                <div class="summary-card">
                    <h3>Lockfile</h3>
                    <div class="meta">Strict mode: ${if (resolve.lockfile.strictMode) "yes" else "no"}</div>
                    <div class="meta">Problems: ${resolve.problems.size}</div>
                    <div class="meta">Generated at: ${escapeHtml(resolve.lockfile.generatedAt.toString())}</div>
                </div>
            </div>
            ${problemsHtml(resolve.problems)}
        """.trimIndent()
    }
    val export = state.exportResponse
    if (export != null) {
        val report = export.generationReport
        return """
            <div class="grid two">
                <div class="summary-card">
                    <h3>Artifact</h3>
                    <div class="meta">File: ${escapeHtml(export.artifact.fileName)}</div>
                    <div class="meta">Type: ${escapeHtml(export.artifact.mediaType)}</div>
                    <div class="meta">Size: ${export.artifact.sizeBytes} bytes</div>
                </div>
                <div class="summary-card">
                    <h3>Generation</h3>
                    <div class="meta">Applied options: ${report.appliedOptionIds.size}</div>
                    <div class="meta">Applied files: ${report.appliedFiles.size}</div>
                    <div class="meta">Lock verified: ${if (export.lockVerified) "yes" else "no"}</div>
                </div>
            </div>
            ${problemsHtml(export.problems)}
        """.trimIndent()
    }
    return """<div class="status-box">${escapeHtml(state.status.ifBlank { "Resolve or preview to inspect generation output." })}</div>"""
}

private fun problemsHtml(problems: List<ru.renattele.wizard.contracts.v1.ProblemV1>): String {
    if (problems.isEmpty()) {
        return """<div class="status-box">No problems reported.</div>"""
    }
    val items = problems.joinToString(separator = "") { problem ->
        """
        <div class="problem-row">
            <strong>${escapeHtml(problem.severity.name)}</strong>
            <span>${escapeHtml(problem.code.name)}</span>
            <span>${escapeHtml(problem.message)}</span>
        </div>
        """.trimIndent()
    }
    return """<div class="status-box">$items</div>"""
}

private fun moduleOf(file: GeneratedFilePreviewV1): String? {
    val path = file.path
    return when {
        path.startsWith("feature/") -> path.split('/').take(3).joinToString(":").replace("feature", "feature")
        path.startsWith("core/") -> path.split('/').take(2).joinToString(":").replace("core", "core")
        path.startsWith("app/") -> "app"
        else -> null
    }
}

private fun parameterFieldId(optionId: String, parameterId: String): String =
    "parameter-${optionId.replace(':', '-')}-${parameterId.replace(':', '-')}"

private fun saveBlob(fileName: String, blob: dynamic) {
    val urlApi = js("window.URL || window.webkitURL")
    val url = urlApi.createObjectURL(blob) as String
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = fileName
    document.body?.appendChild(anchor)
    anchor.click()
    anchor.remove()
    urlApi.revokeObjectURL(url)
}

private fun downloadText(fileName: String, contentType: String, content: String) {
    val blob = Blob(arrayOf(content), BlobPropertyBag(type = contentType))
    saveBlob(fileName, blob)
}

private fun normalizeColor(value: String): String =
    if (value.matches(Regex("^#[0-9A-Fa-f]{6}$"))) value else "#6750A4"

private fun valueOf(id: String): String = (document.getElementById(id) as? HTMLInputElement)?.value.orEmpty()

private fun textareaValueOf(id: String): String = (document.getElementById(id) as? HTMLTextAreaElement)?.value.orEmpty()

private fun encodeSelectionToHash(selection: WizardSelectionV1): String =
    base64UrlEncode(json.encodeToString(WizardSelectionV1.serializer(), selection))

private fun decodeSelectionFromHash(): WizardSelectionV1? {
    val hash = window.location.hash.removePrefix("#").trim()
    if (hash.isBlank()) return null
    return runCatching {
        json.decodeFromString(
            WizardSelectionV1.serializer(),
            base64UrlDecode(hash),
        )
    }.getOrNull()
}

internal fun base64UrlEncode(value: String): String =
    window.btoa(js("unescape(encodeURIComponent(value))") as String)
        .replace("+", "-")
        .replace("/", "_")
        .replace("=", "")

internal fun base64UrlDecode(value: String): String {
    val padded = buildString {
        append(value.replace("-", "+").replace("_", "/"))
        while (length % 4 != 0) append('=')
    }
    return js("decodeURIComponent(escape(window.atob(padded)))") as String
}

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
