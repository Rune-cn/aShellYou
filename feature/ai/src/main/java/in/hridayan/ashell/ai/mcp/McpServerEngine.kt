package `in`.hridayan.ashell.ai.mcp

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transport-agnostic implementation of the MCP server logic: it turns a raw JSON-RPC
 * message into a raw JSON-RPC response (or `null` for notifications).
 *
 * Both the Streamable HTTP and the HTTP+SSE transports share this engine.
 */
@Singleton
class McpServerEngine @Inject constructor(
    private val toolBridge: McpToolBridge
) {
    suspend fun exposedToolCount(): Int = toolBridge.listTools().size

    /**
     * @return the serialized response, or `null` when [raw] is a notification (or a
     *   response to a server-initiated request) and therefore must not be answered.
     */
    suspend fun handleMessage(raw: String): String? {
        val request = McpProtocol.parse(raw)
            ?: return McpProtocol.encode(
                McpProtocol.error(null, McpProtocol.PARSE_ERROR, "Invalid JSON payload")
            )

        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return null
        val id = request["id"]

        // Notifications (no id) must never receive a response.
        if (id == null || id is JsonNull) return null

        val response = try {
            when (method) {
                "initialize" -> McpProtocol.result(id, initializeResult(request))
                "ping" -> McpProtocol.result(id, buildJsonObject { })
                "tools/list" -> McpProtocol.result(id, toolsListResult())
                "tools/call" -> toolsCall(id, request)
                "resources/list" -> McpProtocol.result(id, emptyListResult("resources"))
                "resources/templates/list" ->
                    McpProtocol.result(id, emptyListResult("resourceTemplates"))

                "prompts/list" -> McpProtocol.result(id, emptyListResult("prompts"))
                "logging/setLevel" -> McpProtocol.result(id, buildJsonObject { })
                else -> McpProtocol.error(
                    id,
                    McpProtocol.METHOD_NOT_FOUND,
                    "Method not found: $method"
                )
            }
        } catch (e: Exception) {
            McpProtocol.error(id, McpProtocol.INTERNAL_ERROR, e.message ?: "Internal error")
        }
        return McpProtocol.encode(response)
    }

    private fun initializeResult(request: JsonObject): JsonObject {
        val requested =
            (request["params"] as? JsonObject)?.get("protocolVersion")?.jsonPrimitive?.contentOrNull
        val negotiated = if (requested != null && requested in McpProtocol.SUPPORTED_PROTOCOL_VERSIONS) {
            requested
        } else {
            McpProtocol.LATEST_PROTOCOL_VERSION
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
            put(
                "instructions",
                "Runs shell and ADB commands on this Android device through aShellYou."
            )
        }
    }

    private suspend fun toolsListResult(): JsonObject {
        // Read the tools before entering the non-inline builder lambda: `putJsonArray`
        // takes a plain lambda, in which suspend calls are not allowed.
        val tools = toolBridge.listTools()
        return buildJsonObject {
            putJsonArray("tools") {
                tools.forEach { add(McpProtocol.toolDescriptor(it)) }
            }
        }
    }

    private suspend fun toolsCall(id: JsonElement, request: JsonObject): JsonObject {
        val params = request["params"] as? JsonObject
            ?: return McpProtocol.error(id, McpProtocol.INVALID_PARAMS, "Missing params")
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return McpProtocol.error(id, McpProtocol.INVALID_PARAMS, "Missing tool name")
        val arguments = params["arguments"] as? JsonObject

        val tool = toolBridge.findTool(name)
            ?: return McpProtocol.result(id, toolResult("Unknown tool: $name", isError = true))

        val output = withTimeoutOrNull(TOOL_TIMEOUT_MILLIS) {
            runCatching { tool.execute(arguments) }
                .getOrElse { e -> """{"error":"${e.message.orEmpty().escape()}"}""" }
        }

        if (output == null) {
            return McpProtocol.result(
                id,
                toolResult(
                    "Tool '$name' timed out. If it needs approval, enable " +
                        "\"Auto-approve shell commands\" in the MCP server settings.",
                    isError = true
                )
            )
        }

        val isError = McpProtocol.parse(output)?.containsKey("error") == true
        return McpProtocol.result(id, toolResult(output, isError))
    }

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

    private fun emptyListResult(key: String): JsonObject = buildJsonObject {
        putJsonArray(key) { }
    }

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private companion object {
        const val TOOL_TIMEOUT_MILLIS = 60_000L
    }
}
