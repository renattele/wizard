package ru.renattele.wizard.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.fetch.RequestInit
import ru.renattele.wizard.contracts.v1.CatalogResponseV1
import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.WizardSelectionV1

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun main() {
    window.onload = {
        loadCatalog()
        bindResolveAction()
    }
}

private fun loadCatalog() {
    val catalogContainer = document.getElementById("catalog") as HTMLDivElement
    catalogContainer.innerHTML = "Loading catalog..."

    window.fetch("/api/v1/catalog")
        .then { response -> response.text() }
        .then { body ->
            val catalog = json.decodeFromString<CatalogResponseV1>(body)
            val html = catalog.options.joinToString(separator = "") { option ->
                """
                <div class=\"option-card\">
                    <label>
                        <input type=\"checkbox\" value=\"${option.id}\" /> ${option.displayName}
                    </label>
                    <div class=\"option-meta\">${option.category} · ${option.type}</div>
                    <div class=\"option-meta\">${option.description}</div>
                </div>
                """.trimIndent()
            }
            catalogContainer.innerHTML = html
        }
        .catch { error ->
            catalogContainer.innerHTML = "Failed to load catalog: $error"
        }
}

private fun bindResolveAction() {
    val button = document.getElementById("resolveBtn") as HTMLButtonElement
    val output = document.getElementById("result") as HTMLElement

    button.onclick = {
        val template = (document.getElementById("template") as HTMLInputElement).value
        val nodes = document.querySelectorAll("#catalog input[type='checkbox']")
        val selectedOptionIds = mutableListOf<String>()
        for (index in 0 until nodes.length) {
            val input = nodes.item(index) as? HTMLInputElement ?: continue
            if (input.checked) {
                selectedOptionIds += input.value
            }
        }
        selectedOptionIds.sort()

        val payload = ResolveRequestV1(
            selection = WizardSelectionV1(
                templateId = template,
                selectedOptionIds = selectedOptionIds,
            ),
            strictMode = true,
        )

        window.fetch(
            input = "/api/v1/resolve",
            init = RequestInit(
                method = "POST",
                headers = js("({ 'Content-Type': 'application/json' })"),
                body = json.encodeToString(payload),
            ),
        )
            .then { response -> response.text() }
            .then { body -> output.textContent = body }
            .catch { error -> output.textContent = "Resolve failed: $error" }
    }
}
