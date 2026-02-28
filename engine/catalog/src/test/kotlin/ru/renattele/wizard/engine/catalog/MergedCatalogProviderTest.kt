package ru.renattele.wizard.engine.catalog

import ru.renattele.wizard.contracts.v1.CatalogPackSourceV1
import ru.renattele.wizard.manifest.PluginPackManifest
import kotlin.test.Test
import kotlin.test.assertEquals

class MergedCatalogProviderTest {
    @Test
    fun `local pack overrides remote pack on id collision`() {
        val local = StubCatalogProvider(
            CatalogBundle(
                packs = listOf(
                    CatalogPackDescriptor(
                        id = "pack-core",
                        version = "1.0.0",
                        source = CatalogPackSourceV1.LOCAL,
                        precedence = LOCAL_PRECEDENCE,
                        pack = PluginPackManifest(id = "pack-core", version = "1.0.0"),
                    ),
                ),
            ),
        )
        val remote = StubCatalogProvider(
            CatalogBundle(
                packs = listOf(
                    CatalogPackDescriptor(
                        id = "pack-core",
                        version = "1.0.1",
                        source = CatalogPackSourceV1.REMOTE,
                        precedence = REMOTE_PRECEDENCE,
                        pack = PluginPackManifest(id = "pack-core", version = "1.0.1"),
                    ),
                ),
            ),
        )

        val merged = MergedCatalogProvider(local, remote).loadCatalog()

        assertEquals(1, merged.packs.size)
        assertEquals(CatalogPackSourceV1.LOCAL, merged.packs.single().source)
        assertEquals("1.0.0", merged.packs.single().version)
    }
}

private class StubCatalogProvider(private val bundle: CatalogBundle) : CatalogProvider {
    override fun loadCatalog(request: CatalogRequest): CatalogBundle = bundle
}
