package `in`.hridayan.ashell.ai.mcp

import `in`.hridayan.ashell.ai.domain.tool.ToolRegistry
import `in`.hridayan.ashell.core.common.domain.model.ai.AiTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the app's [ToolRegistry] to the MCP server.
 *
 * Only tools that belong to skills the user enabled in the AI settings are exposed, so the
 * MCP surface always mirrors the in-app assistant surface.
 */
@Singleton
class McpToolBridge @Inject constructor(
    private val toolRegistry: ToolRegistry
) {
    suspend fun listTools(): List<AiTool> = toolRegistry.getEnabledTools()

    suspend fun findTool(name: String): AiTool? = listTools().firstOrNull { it.name == name }
}
