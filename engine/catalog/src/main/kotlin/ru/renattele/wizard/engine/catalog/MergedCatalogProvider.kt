package ru.renattele.wizard.engine.catalog

class MergedCatalogProvider(
    private val localProvider: CatalogProvider,
    private val remoteProvider: CatalogProvider,
) : CatalogProvider {
    override fun loadCatalog(request: CatalogRequest): CatalogBundle {
        val local = localProvider.loadCatalog(request)
        val remote = remoteProvider.loadCatalog(request)

        val sorted = (local.packs + remote.packs)
            .sortedWith(compareBy<CatalogPackDescriptor>({ it.precedence }, { it.id }, { it.version }))

        val merged = linkedMapOf<String, CatalogPackDescriptor>()
        sorted.forEach { descriptor ->
            merged.putIfAbsent(descriptor.id, descriptor)
        }

        return CatalogBundle(merged.values.toList())
    }
}
