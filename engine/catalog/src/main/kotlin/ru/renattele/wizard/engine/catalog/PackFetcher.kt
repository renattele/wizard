package ru.renattele.wizard.engine.catalog

import java.net.URI

interface PackFetcher {
    fun fetch(uri: URI): ByteArray
}

class JavaNetPackFetcher : PackFetcher {
    override fun fetch(uri: URI): ByteArray =
        uri.toURL().openStream().use { input -> input.readBytes() }
}
