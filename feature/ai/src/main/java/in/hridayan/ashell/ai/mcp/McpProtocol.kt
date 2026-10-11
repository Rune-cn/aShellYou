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
 * Small helpers around the Model Context Protocol wire format (JSON-RPC 2.0).
 *
 * Everything is modelled with [JsonElement] instead of generated DTOs so the server stays
 * forward compatible with newer protocol revisions.
 */
object McpProtocol {
    const val LATEST_PROTOCOL_VERSION = "2025-06-18"

    val SUPPORTED_PROTOCOL_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")

    const val SERVER_NAME = "aShellYou"
    const val SERVER_VERSION = "1.0.0"

    // JSON-RPC 2.0 error codes
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes a JSON-RPC envelope. [JsonElement.toString] already emits valid JSON. */
    fun encode(element: JsonElement): String = element.toString()

    fun parse(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    fun result(id: JsonElement?, result: JsonElement): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result)
    }

    fun error(id: JsonElement?, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
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
