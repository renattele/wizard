package ${Package}.core.network

import kotlinx.serialization.json.Json

object NetworkJson {
    val default: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
}
