package ru.renattele.wizard.server

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface SessionRepository {
    fun save(sessionId: String, response: ResolveResponseV1)
    fun find(sessionId: String): ResolveResponseV1?
}

class InMemorySessionRepository : SessionRepository {
    private val data = ConcurrentHashMap<String, ResolveResponseV1>()

    override fun save(sessionId: String, response: ResolveResponseV1) {
        data[sessionId] = response
    }

    override fun find(sessionId: String): ResolveResponseV1? = data[sessionId]
}

@Serializable
data class AuditEvent(
    val sessionId: String,
    val event: String,
    val details: String,
    val createdAt: Instant = Instant.parse("1970-01-01T00:00:00Z"),
)

interface AuditRepository {
    fun append(event: AuditEvent)
    fun list(): List<AuditEvent>
}

class InMemoryAuditRepository : AuditRepository {
    private val events = CopyOnWriteArrayList<AuditEvent>()

    override fun append(event: AuditEvent) {
        events += event
    }

    override fun list(): List<AuditEvent> = events.toList()
}
