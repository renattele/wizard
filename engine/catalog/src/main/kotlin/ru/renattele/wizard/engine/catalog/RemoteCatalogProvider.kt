package ru.renattele.wizard.engine.catalog

import kotlinx.serialization.json.Json
import ru.renattele.wizard.contracts.v1.CatalogPackSourceV1
import ru.renattele.wizard.engine.security.ChecksumVerifier
import ru.renattele.wizard.manifest.PluginPackManifest
import ru.renattele.wizard.manifest.RemotePackIndex
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

class RemoteCatalogProvider(
    private val indexUris: List<URI>,
    private val fetcher: PackFetcher,
    private val checksumVerifier: ChecksumVerifier,
    private val ttl: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CatalogProvider {
    private val cache = mutableMapOf<URI, CachedRemoteIndex>()

    override fun loadCatalog(request: CatalogRequest): CatalogBundle {
        if (!request.includeRemote) return CatalogBundle(emptyList())

        val now = Instant.now(clock)
        val packs = indexUris.flatMap { indexUri ->
            val cached = cache[indexUri]
            val index = if (cached == null || cached.expiresAt.isBefore(now)) {
                val payload = fetcher.fetch(indexUri)
                val decoded = json.decodeFromString<RemotePackIndex>(payload.decodeToString())
                cache[indexUri] = CachedRemoteIndex(decoded, now.plus(ttl))
                decoded
            } else {
                cached.index
            }

            index.entries.mapNotNull { entry ->
                val manifestBytes = fetcher.fetch(URI.create(entry.artifact.url))
                if (!checksumVerifier.verifySha256(manifestBytes, entry.artifact.sha256)) {
                    return@mapNotNull null
                }

                val pack = json.decodeFromString<PluginPackManifest>(manifestBytes.decodeToString())
                CatalogPackDescriptor(
                    id = pack.id,
                    version = pack.version,
                    source = CatalogPackSourceV1.REMOTE,
                    precedence = REMOTE_PRECEDENCE,
                    pack = pack,
                )
            }
        }

        return CatalogBundle(packs)
    }
}

data class CachedRemoteIndex(
    val index: RemotePackIndex,
    val expiresAt: Instant,
)
