package ru.renattele.wizard.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.fetch.RequestInit
import ru.renattele.wizard.contracts.v1.ArchitectureModeV1
import ru.renattele.wizard.contracts.v1.ArchitectureModelV1
import ru.renattele.wizard.contracts.v1.CatalogOptionV1
import ru.renattele.wizard.contracts.v1.CatalogResponseV1
import ru.renattele.wizard.contracts.v1.OptionParameterTypeV1
import ru.renattele.wizard.contracts.v1.OptionParameterV1
import ru.renattele.wizard.contracts.v1.OptionTypeV1
import ru.renattele.wizard.contracts.v1.PreviewRequestV1
import ru.renattele.wizard.contracts.v1.ProjectConfigV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.UserPatchV1
import ru.renattele.wizard.contracts.v1.WizardSelectionV1

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private var cachedCatalog: CatalogResponseV1? = null

@Serializable
private data class AdvancedConfigurationDraft(
    val optionParameters: Map<String, Map<String, String>> = emptyMap(),
    val contextVars: Map<String, String> = emptyMap(),
    val projectConfig: ProjectConfigV1? = null,
    val architecture: ArchitectureModelV1? = null,
    val customPatches: List<UserPatchV1> = emptyList(),
)

fun main() {
    window.onload = {
        loadCatalog()
        bindResolveAction()
        bindPreviewAction()
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
    val catalogContainer = document.getElementById("catalog") as HTMLDivElement
    val templateSelect = document.getElementById("template") as HTMLSelectElement
    val architectureSelect = document.getElementById("architecturePreset") as HTMLSelectElement
    catalogContainer.innerHTML = "Loading catalog..."

    window.fetch(apiUrl("/api/v1/catalog"))
        .then { response -> response.text() }
        .then { body ->
            val catalog = json.decodeFromString<CatalogResponseV1>(body)
            cachedCatalog = catalog
            templateSelect.innerHTML = catalog.templates.joinToString(separator = "") { template ->
                """
                <option value="${escapeHtml(template.id)}">${escapeHtml(template.displayName)}</option>
                """.trimIndent()
            }

            val architectureOptions = catalog.options.filter(::isArchitectureOption)
            architectureSelect.innerHTML =
                """
                <option value="">None</option>
                """.trimIndent() + architectureOptions.joinToString(separator = "") { option ->
                    """
                    <option value="${escapeHtml(option.id)}">${escapeHtml(option.displayName)}</option>
                    """.trimIndent()
                }

            val html = catalog.options
                .filterNot(::isArchitectureOption)
                .joinToString(separator = "") { option ->
                    """
                    <div class=\"option-card\">
                        <label>
                            <input type=\"checkbox\" value=\"${escapeHtml(option.id)}\" ${if (option.defaultEnabled) "checked" else ""}/> ${escapeHtml(option.displayName)}
                        </label>
                        <div class=\"option-meta\">${escapeHtml(option.category)} · ${escapeHtml(option.type.name)}</div>
                        <div class=\"option-meta\">${escapeHtml(option.description)}</div>
                    </div>
                    """.trimIndent()
                }
            catalogContainer.innerHTML = html
            bindCatalogInteraction()
            renderParameterEditors()
        }
        .catch { error ->
            catalogContainer.innerHTML = "Failed to load catalog: $error"
        }
}

private fun bindCatalogInteraction() {
    val architectureSelect = document.getElementById("architecturePreset") as HTMLSelectElement
    architectureSelect.onchange = { renderParameterEditors() }

    val nodes = document.querySelectorAll("#catalog input[type='checkbox']")
    for (index in 0 until nodes.length) {
        val input = nodes.item(index) as? HTMLInputElement ?: continue
        input.onchange = { renderParameterEditors() }
    }
}

private fun bindResolveAction() {
    val button = document.getElementById("resolveBtn") as HTMLButtonElement
    val output = document.getElementById("result") as HTMLElement

    button.onclick = {
        try {
            val selection = buildSelection()
            val payload = ResolveRequestV1(
                selection = selection,
                strictMode = false,
            )
            postJson("/api/v1/resolve", json.encodeToString(payload), output)
        } catch (error: Throwable) {
            output.textContent = "Resolve failed: ${error.message ?: error.toString()}"
        }
    }
}

private fun bindPreviewAction() {
    val button = document.getElementById("previewBtn") as HTMLButtonElement
    val output = document.getElementById("result") as HTMLElement

    button.onclick = {
        try {
            val selection = buildSelection()
            val payload = PreviewRequestV1(
                selection = selection,
                strictMode = false,
            )
            postJson("/api/v1/preview", json.encodeToString(payload), output)
        } catch (error: Throwable) {
            output.textContent = "Preview failed: ${error.message ?: error.toString()}"
        }
    }
}

private fun postJson(endpoint: String, body: String, output: HTMLElement) {
    output.textContent = "Loading..."
    window.fetch(
        input = apiUrl(endpoint),
        init = RequestInit(
            method = "POST",
            headers = js("({ 'Content-Type': 'application/json' })"),
            body = body,
        ),
    )
        .then { response -> response.text() }
        .then { responseBody ->
            output.textContent = prettyBody(responseBody)
        }
        .catch { error -> output.textContent = "Request failed: $error" }
}

private fun prettyBody(body: String): String =
    runCatching {
        val parsed = json.parseToJsonElement(body)
        json.encodeToString(parsed)
    }.getOrElse { body }

private fun buildSelection(): WizardSelectionV1 {
    val template = (document.getElementById("template") as HTMLSelectElement).value
    val architecturePreset = (document.getElementById("architecturePreset") as HTMLSelectElement).value.trim()
    val selectedOptionIds = selectedOptionIdsFromForm(includeArchitecturePreset = false).toMutableList()
    val advancedConfig = parseAdvancedConfig()
    val architecture = when {
        advancedConfig.architecture?.mode == ArchitectureModeV1.CUSTOM -> advancedConfig.architecture
        architecturePreset.isNotBlank() -> ArchitectureModelV1(
            mode = ArchitectureModeV1.PRESET,
            presetPatternId = architecturePreset,
        )
        !advancedConfig.architecture?.presetPatternId.isNullOrBlank() -> advancedConfig.architecture
        else -> null
    }
    val presetOptionId = architecture?.takeIf { it.mode == ArchitectureModeV1.PRESET }?.presetPatternId
    if (!presetOptionId.isNullOrBlank()) {
        selectedOptionIds += presetOptionId
    }

    val normalizedOptionIds = selectedOptionIds.distinct().sorted()
    val uiOptionParameters = collectOptionParametersFromForm()
    val mergedOptionParameters = advancedConfig.optionParameters.toMutableMap()
    uiOptionParameters.forEach { (optionId, params) ->
        mergedOptionParameters[optionId] = (mergedOptionParameters[optionId].orEmpty() + params)
            .filterValues { value -> value.isNotBlank() }
    }

    return WizardSelectionV1(
        templateId = template,
        selectedOptionIds = normalizedOptionIds,
        optionParameters = mergedOptionParameters
            .toList()
            .sortedBy { (optionId, _) -> optionId }
            .associate { (optionId, values) ->
                optionId to values.toList().sortedBy { (parameterId, _) -> parameterId }.toMap()
            },
        contextVars = advancedConfig.contextVars,
        projectConfig = advancedConfig.projectConfig,
        architecture = architecture,
        customPatches = advancedConfig.customPatches,
    )
}

private fun renderParameterEditors() {
    val container = document.getElementById("optionParameters") as HTMLDivElement
    val catalog = cachedCatalog ?: run {
        container.innerHTML = ""
        return
    }

    val currentValues = collectOptionParametersFromForm()
    val selectedOptionIds = selectedOptionIdsFromForm(includeArchitecturePreset = true)
    val selectedOptions = selectedOptionIds.mapNotNull { optionId ->
        catalog.options.firstOrNull { option -> option.id == optionId }
    }.filter { option -> option.parameters.isNotEmpty() }

    if (selectedOptions.isEmpty()) {
        container.innerHTML = "<div class=\"subtitle\">Selected preset options do not expose parameters yet.</div>"
        return
    }

    container.innerHTML = selectedOptions.joinToString(separator = "") { option ->
        val parametersHtml = option.parameters.joinToString(separator = "") { parameter ->
            val currentValue = currentValues[option.id]?.get(parameter.id) ?: parameter.defaultValue.orEmpty()
            buildParameterFieldHtml(option.id, parameter, currentValue)
        }
        """
        <div class="parameter-card">
            <h3>${escapeHtml(option.displayName)}</h3>
            <div class="subtitle">${escapeHtml(option.description)}</div>
            <div class="parameter-grid">$parametersHtml</div>
        </div>
        """.trimIndent()
    }
}

private fun buildParameterFieldHtml(
    optionId: String,
    parameter: OptionParameterV1,
    currentValue: String,
): String {
    val inputId = parameterFieldId(optionId, parameter.id)
    val description = parameter.description.takeIf { it.isNotBlank() }?.let {
        """<div class="parameter-hint">${escapeHtml(it)}</div>"""
    }.orEmpty()

    return when (parameter.type) {
        OptionParameterTypeV1.BOOLEAN -> """
            <div class="parameter-field parameter-boolean">
                <input type="checkbox" id="${escapeHtml(inputId)}" data-option-id="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}" ${if (currentValue == "true") "checked" else ""}/>
                <label for="${escapeHtml(inputId)}">${escapeHtml(parameter.displayName)}</label>
                $description
            </div>
        """.trimIndent()

        OptionParameterTypeV1.ENUM -> {
            val optionsHtml = parameter.allowedValues.joinToString(separator = "") { allowedValue ->
                """
                <option value="${escapeHtml(allowedValue.value)}" ${if (allowedValue.value == currentValue) "selected" else ""}>${escapeHtml(allowedValue.displayName)}</option>
                """.trimIndent()
            }
            """
            <div class="parameter-field">
                <label for="${escapeHtml(inputId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <select id="${escapeHtml(inputId)}" data-option-id="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}">$optionsHtml</select>
            </div>
            """.trimIndent()
        }

        OptionParameterTypeV1.MULTILINE -> """
            <div class="parameter-field">
                <label for="${escapeHtml(inputId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <textarea id="${escapeHtml(inputId)}" data-option-id="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}">${escapeHtml(currentValue)}</textarea>
            </div>
        """.trimIndent()

        OptionParameterTypeV1.STRING -> """
            <div class="parameter-field">
                <label for="${escapeHtml(inputId)}">${escapeHtml(parameter.displayName)}</label>
                $description
                <input id="${escapeHtml(inputId)}" type="text" value="${escapeHtml(currentValue)}" data-option-id="${escapeHtml(optionId)}" data-parameter-id="${escapeHtml(parameter.id)}"/>
            </div>
        """.trimIndent()
    }
}

private fun collectOptionParametersFromForm(): Map<String, Map<String, String>> {
    val catalog = cachedCatalog ?: return emptyMap()
    val selectedOptions = selectedOptionIdsFromForm(includeArchitecturePreset = true).toSet()
    val result = linkedMapOf<String, MutableMap<String, String>>()

    catalog.options
        .filter { option -> option.id in selectedOptions && option.parameters.isNotEmpty() }
        .forEach { option ->
            option.parameters.forEach { parameter ->
                val field = document.getElementById(parameterFieldId(option.id, parameter.id)) ?: return@forEach
                val value = when (parameter.type) {
                    OptionParameterTypeV1.BOOLEAN -> ((field as HTMLInputElement).checked).toString()
                    OptionParameterTypeV1.MULTILINE -> (field as HTMLTextAreaElement).value.trim()
                    OptionParameterTypeV1.ENUM -> (field as HTMLSelectElement).value.trim()
                    OptionParameterTypeV1.STRING -> (field as HTMLInputElement).value.trim()
                }
                if (value.isNotBlank() || parameter.type == OptionParameterTypeV1.BOOLEAN) {
                    result.getOrPut(option.id) { linkedMapOf() }[parameter.id] = value
                }
            }
        }

    return result
}

private fun selectedOptionIdsFromForm(includeArchitecturePreset: Boolean): List<String> {
    val selectedOptionIds = mutableListOf<String>()
    val nodes = document.querySelectorAll("#catalog input[type='checkbox']")
    for (index in 0 until nodes.length) {
        val input = nodes.item(index) as? HTMLInputElement ?: continue
        if (input.checked) {
            selectedOptionIds += input.value
        }
    }
    if (includeArchitecturePreset) {
        val architecturePreset = (document.getElementById("architecturePreset") as HTMLSelectElement).value.trim()
        if (architecturePreset.isNotBlank()) {
            selectedOptionIds += architecturePreset
        }
    }
    return selectedOptionIds.distinct().sorted()
}

private fun parameterFieldId(optionId: String, parameterId: String): String =
    "parameter-${sanitizeHtmlId(optionId)}-${sanitizeHtmlId(parameterId)}"

private fun sanitizeHtmlId(value: String): String =
    value.replace(Regex("[^A-Za-z0-9_-]"), "-")

private fun isArchitectureOption(option: CatalogOptionV1): Boolean =
    option.type == OptionTypeV1.ARCHITECTURE || option.category == "architecture"

private fun parseAdvancedConfig(): AdvancedConfigurationDraft {
    val raw = (document.getElementById("advancedConfig") as HTMLTextAreaElement).value.trim()
    if (raw.isBlank()) {
        return AdvancedConfigurationDraft()
    }
    return json.decodeFromString(raw)
}

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
