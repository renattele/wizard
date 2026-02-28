package ru.renattele.wizard.engine.catalog

import kotlinx.serialization.json.Json
import ru.renattele.wizard.contracts.v1.CatalogPackSourceV1
import ru.renattele.wizard.manifest.PluginPackManifest

class ClasspathCatalogProvider(
    private val resourcePaths: List<String>,
    private val classLoader: ClassLoader = ClasspathCatalogProvider::class.java.classLoader,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CatalogProvider {
    override fun loadCatalog(request: CatalogRequest): CatalogBundle {
        val packs = resourcePaths.mapNotNull { path ->
            val stream = classLoader.getResourceAsStream(path) ?: return@mapNotNull null
            val bytes = stream.use { it.readBytes() }
            val pack = json.decodeFromString<PluginPackManifest>(bytes.decodeToString())
            CatalogPackDescriptor(
                id = pack.id,
                version = pack.version,
                source = CatalogPackSourceV1.LOCAL,
                precedence = LOCAL_PRECEDENCE,
                pack = pack,
            )
        }

        return CatalogBundle(packs = packs)
    }
}
