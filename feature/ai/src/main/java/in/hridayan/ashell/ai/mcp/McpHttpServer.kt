package `in`.hridayan.ashell.ai.mcp

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import java.util.UUID

private const val SESSION_HEADER = "Mcp-Session-Id"
private const val NO_BUFFERING_HEADER = "X-Accel-Buffering"

/**
 * Embedded Ktor (CIO) server exposing the MCP engine over HTTP.
 *
 * - **Streamable HTTP** (`/mcp`, POST) is the standard transport. Per the `2026-07-28`
 *   revision it is the only endpoint needed: requests are stateless, sessions and the GET
 *   stream are gone.
 * - **HTTP + SSE** (`/sse` + `/messages`) is the deprecated `2024-11-05` transport, kept for
 *   older clients.
 */
class McpHttpServer(
    private val config: McpConfig,
    private val mcpEngine: McpServerEngine,
    private val sessions: McpSseSessionStore
) {
    fun build(): EmbeddedServer<*, *> =
        embeddedServer(CIO, port = config.port, host = config.bindHost) {
            mcpModule()
        }

    private fun Application.mcpModule() {
        install(SSE)

        // Optional bearer-token gate for every MCP endpoint.
        intercept(ApplicationCallPipeline.Plugins) {
            val call = context
            val path = call.request.path()
            val isMcpPath =
                path == "/mcp" || path.startsWith("/sse") || path.startsWith("/messages")
            if (!isMcpPath) return@intercept
            if (call.request.httpMethod == HttpMethod.Options) return@intercept
            if (config.authToken.isBlank()) return@intercept

            val provided = call.request.headers[HttpHeaders.Authorization]
            if (provided != "Bearer ${config.authToken}") {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                finish()
            }
        }

        routing {
            // CORS is a route-scoped plugin, so it is installed on the routing node.
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Accept)
                allowHeader(HttpHeaders.Origin)
                allowHeader(McpProtocol.HEADER_PROTOCOL_VERSION)
                allowHeader(McpProtocol.HEADER_METHOD)
                allowHeader(McpProtocol.HEADER_NAME)
                allowHeader(SESSION_HEADER)
                exposeHeader(SESSION_HEADER)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
            }

            get("/") {
                call.respondText(
                    "aShellYou MCP server. MCP endpoint: POST /mcp. " +
                        "Deprecated HTTP+SSE transport: /sse + /messages.",
                    ContentType.Text.Plain
                )
            }

            if (config.streamableHttpEnabled) {
                post("/mcp") { call.handleMcpPost() }

                // The 2026-07-28 revision removed the GET stream endpoint.
                get("/mcp") {
                    call.respond(
                        HttpStatusCode.MethodNotAllowed,
                        "This MCP endpoint only accepts POST"
                    )
                }
                delete("/mcp") { call.respond(HttpStatusCode.NoContent) }
            }

            if (config.sseEnabled) {
                sse("/sse") {
                    val sessionId = UUID.randomUUID().toString()
                    val channel = sessions.create(sessionId)
                    send(event = "endpoint", data = "/messages?sessionId=$sessionId")
                    try {
                        for (message in channel) {
                            send(event = "message", data = message)
                        }
                    } catch (_: Throwable) {
                        // Client disconnected.
                    } finally {
                        sessions.remove(sessionId)
                    }
                }

                post("/messages") {
                    val sessionId = call.request.queryParameters["sessionId"]
                    if (sessionId.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Missing sessionId")
                        return@post
                    }
                    val channel = sessions.get(sessionId)
                    if (channel == null) {
                        call.respond(HttpStatusCode.NotFound, "Unknown session")
                        return@post
                    }

                    val incoming = McpIncoming(
                        body = call.receiveText(),
                        protocolVersionHeader =
                            call.request.headers[McpProtocol.HEADER_PROTOCOL_VERSION]
                    )
                    call.respond(HttpStatusCode.Accepted)
                    (mcpEngine.handle(incoming) as? McpResponse.Reply)
                        ?.let { channel.trySend(it.body) }
                }
            }
        }
    }

    private suspend fun ApplicationCall.handleMcpPost() {
        // DNS-rebinding protection: reject browser calls from foreign origins unless the user
        // explicitly exposed the server to the local network.
        val origin = request.headers[HttpHeaders.Origin]
        if (origin != null && !config.allowLanAccess && !isLocalOrigin(origin)) {
            respondText("Forbidden origin", ContentType.Text.Plain, HttpStatusCode.Forbidden)
            return
        }

        val accept = request.headers[HttpHeaders.Accept].orEmpty()
        val incoming = McpIncoming(
            body = receiveText(),
            protocolVersionHeader = request.headers[McpProtocol.HEADER_PROTOCOL_VERSION]
        )

        when (val result = mcpEngine.handle(incoming)) {
            is McpResponse.Accepted -> respond(HttpStatusCode.Accepted)

            is McpResponse.Reply -> {
                val status = HttpStatusCode.fromValue(result.status)
                val wantsSse = status == HttpStatusCode.OK &&
                    accept.contains(ContentType.Text.EventStream.contentType) &&
                    !accept.contains(ContentType.Application.Json.contentType)

                if (wantsSse) {
                    // Ask reverse proxies not to buffer, so events reach the client promptly.
                    response.headers.append(NO_BUFFERING_HEADER, "no")
                    respondText(
                        text = "event: message\ndata: ${result.body}\n\n",
                        contentType = ContentType.Text.EventStream,
                        status = status
                    )
                } else {
                    respondText(
                        text = result.body,
                        contentType = ContentType.Application.Json,
                        status = status
                    )
                }
            }
        }
    }

    private fun isLocalOrigin(origin: String): Boolean {
        val host = runCatching { Url(origin).host }.getOrNull() ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
    }
}
