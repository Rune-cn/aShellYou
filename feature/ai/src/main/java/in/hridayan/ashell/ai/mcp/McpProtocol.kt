package `in`.hridayan.ashell.ai.mcp

import `in`.hridayan.ashell.core.common.domain.model.ai.AiTool
import `in`.hridayan.ashell.core.common.domain.model.ai.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Helpers around the Model Context Protocol wire format (JSON-RPC 2.0).
 *
 * The protocol has two eras:
 *  - **modern** (`2026-07-28` and later): stateless, no `initialize` handshake, every request
 *    carries its protocol version and client capabilities in `_meta`.
 *  - **legacy** (`2025-11-25` and earlier): a connection-scoped session opened with
 *    `initialize`.
 *
 * This server implements both, so it can serve current clients and fall back for older ones.
 */
object McpProtocol {
    /** Latest revision implemented here (modern, stateless, per-request `_meta`). */
    const val LATEST_PROTOCOL_VERSION = "2026-07-28"

    /** Revisions that convey version/capabilities per request, without a handshake. */
    val MODERN_PROTOCOL_VERSIONS = listOf("2026-07-28")

    /** Revisions that establish a session with an `initialize` handshake. */
    val LEGACY_PROTOCOL_VERSIONS =
        listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")

    val SUPPORTED_PROTOCOL_VERSIONS = MODERN_PROTOCOL_VERSIONS + LEGACY_PROTOCOL_VERSIONS

    const val SERVER_NAME = "aShellYou"
    const val SERVER_VERSION = "1.1.0"

    // JSON-RPC 2.0 error codes
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    // MCP reserved error codes (error-code allocation policy of 2026-07-28)
    const val HEADER_MISMATCH = -32020
    const val MISSING_REQUIRED_CLIENT_CAPABILITY = -32021
    const val UNSUPPORTED_PROTOCOL_VERSION = -32022

    // Reserved `_meta` keys
    const val META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion"
    const val META_CLIENT_INFO = "io.modelcontextprotocol/clientInfo"
    const val META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
    const val META_SERVER_INFO = "io.modelcontextprotocol/serverInfo"

    // Streamable HTTP request metadata headers
    const val HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"
    const val HEADER_METHOD = "Mcp-Method"
    const val HEADER_NAME = "Mcp-Name"

    /** Tool lists are cheap to recompute and depend on the user's enabled skills. */
    const val LIST_CACHE_TTL_MS = 0L

    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes a JSON-RPC envelope. [JsonElement.toString] already emits valid JSON. */
    fun encode(element: JsonElement): String = element.toString()

    fun parse(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    /**
     * Locates the per-request `_meta` object. It normally lives at `params._meta`, but a
     * top-level `_meta` is accepted as well.
     */
    fun requestMeta(request: JsonObject): JsonObject? {
        val params = request["params"] as? JsonObject
        return (params?.get("_meta") as? JsonObject) ?: (request["_meta"] as? JsonObject)
    }

    fun serverInfoMeta(): JsonObject = buildJsonObject {
        putJsonObject(META_SERVER_INFO) {
            put("name", SERVER_NAME)
            put("version", SERVER_VERSION)
        }
    }

    /**
     * Builds a JSON-RPC success envelope. Modern revisions require every result to carry
     * `resultType` and a `_meta` block identifying the server.
     */
    fun result(id: JsonElement?, result: JsonObject, modern: Boolean): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("result", if (modern) decorate(result) else result)
        }

    private fun decorate(result: JsonObject): JsonObject = buildJsonObject {
        result.forEach { (key, value) -> put(key, value) }
        put("resultType", "complete")
        put("_meta", serverInfoMeta())
    }

    fun error(
        id: JsonElement?,
        code: Int,
        message: String,
        data: JsonElement? = null
    ): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
            if (data != null) put("data", data)
        }
    }

    /** Converts an [AiTool] into the descriptor returned by `tools/list`. */
    fun toolDescriptor(tool: AiTool): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("inputSchema", inputSchema(tool.parametersSchema))
    }

    private fun inputSchema(schema: ToolSchema?): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            schema?.properties?.forEach { (name, property) ->
                putJsonObject(name) {
                    put("type", property.type.lowercase())
                    property.description?.let { put("description", it) }
                }
            }
        }
        putJsonArray("required") {
            schema?.required?.forEach { add(it) }
        }
    }
}
