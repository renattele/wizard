package ${Package}.core.network

interface ApiService {
    suspend fun ping(): String
}
