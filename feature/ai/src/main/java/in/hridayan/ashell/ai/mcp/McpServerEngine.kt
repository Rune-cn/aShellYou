package `in`.hridayan.ashell.ai.mcp

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** A single inbound JSON-RPC message plus the transport metadata that travels beside it. */
data class McpIncoming(
    val body: String,
    val protocolVersionHeader: String? = null
)

/** Outcome of handling one inbound message. */
sealed interface McpResponse {
    /** Notification or client-sent response: HTTP 202 with no body. */
    data object Accepted : McpResponse

    /** JSON-RPC reply. [status] is the HTTP status the transport should use. */
    data class Reply(val body: String, val status: Int = 200) : McpResponse
}

/**
 * Transport-agnostic MCP server logic.
 *
 * It is **dual-era**: a request carrying modern per-request `_meta` is served statelessly per
 * `2026-07-28`, while an `initialize` request selects the legacy handshake semantics. Both can
 * be served on the same endpoint.
 */
@Singleton
class McpServerEngine @Inject constructor(
    private val toolBridge: McpToolBridge
) {
    suspend fun exposedToolCount(): Int = toolBridge.listTools().size

    suspend fun handle(incoming: McpIncoming): McpResponse {
        val request = McpProtocol.parse(incoming.body)
            ?: return badRequest(
                McpProtocol.error(null, McpProtocol.PARSE_ERROR, "Invalid JSON payload")
            )

        val method = request["method"]?.jsonPrimitive?.contentOrNull
        val id = request["id"]

        // Clients must not send JSON-RPC responses over this transport; nothing to answer.
        if (method == null) return McpResponse.Accepted

        // Notifications never receive a response body.
        if (id == null || id is JsonNull) return McpResponse.Accepted

        val meta = McpProtocol.requestMeta(request)
        val bodyVersion =
            meta?.get(McpProtocol.META_PROTOCOL_VERSION)?.jsonPrimitive?.contentOrNull

        return if (bodyVersion != null) {
            handleModern(id, method, request, meta, bodyVersion, incoming.protocolVersionHeader)
        } else {
            handleLegacy(id, method, request)
        }
    }

    // ── modern era: stateless, per-request `_meta` ────────────────────────────────────────

    private suspend fun handleModern(
        id: JsonElement,
        method: String,
        request: JsonObject,
        meta: JsonObject,
        bodyVersion: String,
        headerVersion: String?
    ): McpResponse {
        if (headerVersion == null || headerVersion != bodyVersion) {
            return badRequest(
                McpProtocol.error(
                    id,
                    McpProtocol.HEADER_MISMATCH,
                    "${McpProtocol.HEADER_PROTOCOL_VERSION} must be present and match " +
                        "_meta.${McpProtocol.META_PROTOCOL_VERSION}"
                )
            )
        }

        if (bodyVersion !in McpProtocol.SUPPORTED_PROTOCOL_VERSIONS) {
            return badRequest(
                McpProtocol.error(
                    id,
                    McpProtocol.UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version: $bodyVersion",
                    buildJsonObject {
                        putJsonArray("supported") {
                            McpProtocol.SUPPORTED_PROTOCOL_VERSIONS.forEach { add(it) }
                        }
                    }
                )
            )
        }

        if (meta[McpProtocol.META_CLIENT_CAPABILITIES] !is JsonObject) {
            return badRequest(
                McpProtocol.error(
                    id,
                    McpProtocol.INVALID_PARAMS,
                    "Missing required _meta field: ${McpProtocol.META_CLIENT_CAPABILITIES}"
                )
            )
        }

        return try {
            when (method) {
                "server/discover" -> modern(id, discoverResult())
                "tools/list" -> modern(id, toolsListResult(modern = true))
                "tools/call" -> toolsCall(id, request, modern = true)
                "resources/list" -> modern(id, listResult("resources", modern = true))
                "resources/templates/list" -> modern(id, listResult("resourceTemplates", modern = true))
                "prompts/list" -> modern(id, listResult("prompts", modern = true))
                else -> McpResponse.Reply(
                    McpProtocol.encode(
                        McpProtocol.error(
                            id,
                            McpProtocol.METHOD_NOT_FOUND,
                            "Method not found: $method"
                        )
                    ),
                    status = 404
                )
            }
        } catch (e: Exception) {
            McpResponse.Reply(
                McpProtocol.encode(
                    McpProtocol.error(id, McpProtocol.INTERNAL_ERROR, e.message ?: "Internal error")
                )
            )
        }
    }

    private fun discoverResult(): JsonObject = buildJsonObject {
        putJsonArray("supportedVersions") {
            McpProtocol.SUPPORTED_PROTOCOL_VERSIONS.forEach { add(it) }
        }
        putJsonObject("capabilities") {
            putJsonObject("tools") { put("listChanged", false) }
        }
        put("instructions", INSTRUCTIONS)
    }

    // ── legacy era: `initialize` handshake ────────────────────────────────────────────────

    private suspend fun handleLegacy(
        id: JsonElement,
        method: String,
        request: JsonObject
    ): McpResponse {
        val result = try {
            when (method) {
                "initialize" -> McpProtocol.result(id, initializeResult(request), modern = false)
                "ping" -> McpProtocol.result(id, buildJsonObject { }, modern = false)
                "tools/list" -> McpProtocol.result(id, toolsListResult(modern = false), modern = false)
                "tools/call" -> toolsCall(id, request, modern = false)
                "resources/list" ->
                    McpProtocol.result(id, listResult("resources", modern = false), modern = false)

                "resources/templates/list" ->
                    McpProtocol.result(id, listResult("resourceTemplates", modern = false), modern = false)

                "prompts/list" -> McpProtocol.result(id, listResult("prompts", modern = false), modern = false)
                "logging/setLevel" -> McpProtocol.result(id, buildJsonObject { }, modern = false)
                else -> McpProtocol.error(
                    id,
                    McpProtocol.METHOD_NOT_FOUND,
                    "Method not found: $method"
                )
            }
        } catch (e: Exception) {
            McpProtocol.error(id, McpProtocol.INTERNAL_ERROR, e.message ?: "Internal error")
        }
        return McpResponse.Reply(McpProtocol.encode(result))
    }

    private fun initializeResult(request: JsonObject): JsonObject {
        val requested = (request["params"] as? JsonObject)
            ?.get("protocolVersion")
            ?.jsonPrimitive
            ?.contentOrNull
        val negotiated =
            if (requested != null && requested in McpProtocol.LEGACY_PROTOCOL_VERSIONS) {
                requested
            } else {
                McpProtocol.LEGACY_PROTOCOL_VERSIONS.first()
            }

        return buildJsonObject {
            put("protocolVersion", negotiated)
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", false) }
            }
            putJsonObject("serverInfo") {
                put("name", McpProtocol.SERVER_NAME)
                put("version", McpProtocol.SERVER_VERSION)
            }
            put("instructions", INSTRUCTIONS)
        }
    }

    // ── shared operations ─────────────────────────────────────────────────────────────────

    private fun modern(id: JsonElement, result: JsonObject): McpResponse =
        McpResponse.Reply(McpProtocol.encode(McpProtocol.result(id, result, modern = true)))

    private fun badRequest(envelope: JsonObject): McpResponse =
        McpResponse.Reply(McpProtocol.encode(envelope), status = 400)

    private suspend fun toolsListResult(modern: Boolean): JsonObject {
        // Read the tools before entering the non-inline builder lambda: `putJsonArray`
        // takes a plain lambda, in which suspend calls are not allowed.
        val tools = toolBridge.listTools()
        return buildJsonObject {
            putJsonArray("tools") {
                tools.forEach { add(McpProtocol.toolDescriptor(it)) }
            }
            if (modern) {
                // CacheableResult: required for list results in the modern revision.
                put("ttlMs", McpProtocol.LIST_CACHE_TTL_MS)
                put("cacheScope", "private")
            }
        }
    }

    private suspend fun toolsCall(
        id: JsonElement,
        request: JsonObject,
        modern: Boolean
    ): McpResponse {
        val params = request["params"] as? JsonObject
            ?: return encoded(
                McpProtocol.error(id, McpProtocol.INVALID_PARAMS, "Missing params")
            )
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return encoded(
                McpProtocol.error(id, McpProtocol.INVALID_PARAMS, "Missing tool name")
            )
        val arguments = params["arguments"] as? JsonObject

        val tool = toolBridge.findTool(name)
            ?: return encoded(
                McpProtocol.result(id, toolResult("Unknown tool: $name", isError = true), modern)
            )

        val output = withTimeoutOrNull(TOOL_TIMEOUT_MILLIS) {
            runCatching { tool.execute(arguments) }
                .getOrElse { e -> """{"error":"${e.message.orEmpty().escape()}"}""" }
        }

        if (output == null) {
            return encoded(
                McpProtocol.result(
                    id,
                    toolResult(
                        "Tool '$name' timed out. If it needs approval, enable " +
                            "\"Auto-approve shell commands\" in the MCP server settings.",
                        isError = true
                    ),
                    modern
                )
            )
        }

        val isError = McpProtocol.parse(output)?.containsKey("error") == true
        return encoded(McpProtocol.result(id, toolResult(output, isError), modern))
    }

    /** A JSON-RPC envelope that is not a protocol-level failure: answered with HTTP 200. */
    private fun encoded(envelope: JsonObject): McpResponse =
        McpResponse.Reply(McpProtocol.encode(envelope))

    private fun toolResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            )
        }
        put("isError", isError)
    }

    private fun listResult(key: String, modern: Boolean): JsonObject = buildJsonObject {
        putJsonArray(key) { }
        if (modern) {
            put("ttlMs", McpProtocol.LIST_CACHE_TTL_MS)
            put("cacheScope", "private")
        }
    }

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private companion object {
        const val TOOL_TIMEOUT_MILLIS = 60_000L

        const val INSTRUCTIONS =
            "Runs shell and ADB commands on this Android device through aShellYou. " +
                "Tools mirror the agent skills the user enabled in the app."
    }
}
