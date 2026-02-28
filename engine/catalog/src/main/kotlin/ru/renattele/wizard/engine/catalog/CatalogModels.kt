package ru.renattele.wizard.engine.catalog

import ru.renattele.wizard.contracts.v1.CatalogPackSourceV1
import ru.renattele.wizard.manifest.PluginPackManifest

const val LOCAL_PRECEDENCE: Int = 0
const val REMOTE_PRECEDENCE: Int = 1

data class CatalogRequest(
    val includeRemote: Boolean = true,
)

data class CatalogBundle(
    val packs: List<CatalogPackDescriptor>,
) {
    val options = packs.flatMap { it.pack.options }
    val templates = packs.flatMap { it.pack.templates }

    fun findTemplate(templateId: String) = templates.firstOrNull { it.id == templateId }
}

data class CatalogPackDescriptor(
    val id: String,
    val version: String,
    val source: CatalogPackSourceV1,
    val precedence: Int,
    val pack: PluginPackManifest,
)

interface CatalogProvider {
    fun loadCatalog(request: CatalogRequest = CatalogRequest()): CatalogBundle
}
