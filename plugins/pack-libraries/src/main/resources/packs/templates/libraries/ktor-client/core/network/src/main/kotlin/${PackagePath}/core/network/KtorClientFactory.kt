package ${Package}.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation

object KtorClientFactory {
    fun create(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation)
    }
}
