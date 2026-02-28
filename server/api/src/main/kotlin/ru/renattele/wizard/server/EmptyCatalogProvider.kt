package ru.renattele.wizard.server

import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.catalog.CatalogRequest

object EmptyCatalogProvider : CatalogProvider {
    override fun loadCatalog(request: CatalogRequest): CatalogBundle = CatalogBundle(emptyList())
}
