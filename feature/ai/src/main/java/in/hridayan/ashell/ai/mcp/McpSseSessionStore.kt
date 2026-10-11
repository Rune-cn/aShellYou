package `in`.hridayan.ashell.ai.mcp

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks open legacy HTTP+SSE connections.
 *
 * Each session owns a [Channel] that the `/sse` response coroutine drains while the
 * `/messages` endpoint writes responses into it.
 */
@Singleton
class McpSseSessionStore @Inject constructor() {

    private val sessions = ConcurrentHashMap<String, Channel<String>>()

    fun create(sessionId: String): Channel<String> {
        val channel = Channel<String>(Channel.BUFFERED)
        sessions[sessionId] = channel
        return channel
    }

    fun get(sessionId: String): Channel<String>? = sessions[sessionId]

    fun remove(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    fun clear() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}
