package `in`.hridayan.ashell.ai.mcp

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.CORS
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private const val SESSION_HEADER = "Mcp-Session-Id"

/**
 * Embedded Ktor (CIO) server exposing the MCP engine over HTTP.
 *
 * Two transports can be enabled independently:
 *  - **Streamable HTTP** (`/mcp`): the current transport, a single endpoint that answers a
 *    POST with either JSON or an SSE stream.
 *  - **HTTP + SSE** (`/sse` + `/messages`): the legacy 2024-11-05 transport.
 */
class McpHttpServer(
    private val config: McpConfig,
    private val engine: McpServerEngine,
    private val sessions: McpSseSessionStore
) {
    fun build(): EmbeddedServer<*, *> =
        embeddedServer(CIO, port = config.port, host = config.bindHost) {
            mcpModule()
        }

    private fun Application.mcpModule() {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Accept)
            allowHeader(SESSION_HEADER)
            allowHeader("MCP-Protocol-Version")
            exposeHeader(SESSION_HEADER)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
        }

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
            get("/") {
                call.respondText(
                    "aShellYou MCP server. Endpoints: /mcp (Streamable HTTP), " +
                        "/sse + /messages (HTTP+SSE).",
                    ContentType.Text.Plain
                )
            }

            if (config.streamableHttpEnabled) {
                post("/mcp") { call.handleStreamableHttpPost() }
                get("/mcp") {
                    call.respond(
                        HttpStatusCode.MethodNotAllowed,
                        "Use POST for the Streamable HTTP transport"
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
                    val payload = engine.handleMessage(call.receiveText())
                    call.respond(HttpStatusCode.Accepted)
                    if (payload != null) channel.trySend(payload)
                }
            }
        }
    }

    private suspend fun ApplicationCall.handleStreamableHttpPost() {
        val body = receiveText()
        val payload = engine.handleMessage(body)

        if (payload == null) {
            // Notification or response: acknowledge without a body.
            respond(HttpStatusCode.Accepted)
            return
        }

        if (isInitializeRequest(body)) {
            response.headers.append(SESSION_HEADER, UUID.randomUUID().toString())
        }

        val accept = request.headers[HttpHeaders.Accept].orEmpty()
        val wantsSse = accept.contains(ContentType.Text.EventStream.contentType) &&
            !accept.contains(ContentType.Application.Json.contentType)

        if (wantsSse) {
            respondText(
                text = "event: message\ndata: $payload\n\n",
                contentType = ContentType.Text.EventStream
            )
        } else {
            respondText(text = payload, contentType = ContentType.Application.Json)
        }
    }

    private fun isInitializeRequest(body: String): Boolean =
        McpProtocol.parse(body)?.get("method")?.jsonPrimitive?.contentOrNull == "initialize"
}
