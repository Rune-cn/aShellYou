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
    /**
     * The MCP surface is deliberately limited to the terminal tool. `execute_command` already
     * covers everything the diagnostics helpers do (an agent can just run `dumpsys`, `df`,
     * `logcat`, …), and a one-tool list keeps the model context — and therefore the prompt
     * cache — stable.
     */
    suspend fun listTools(): List<AiTool> =
        toolRegistry.getAllSkillsWithTools()
            .values
            .flatten()
            .filter { it.name == TERMINAL_TOOL_NAME }

    suspend fun findTool(name: String): AiTool? = listTools().firstOrNull { it.name == name }

    private companion object {
        const val TERMINAL_TOOL_NAME = "execute_command"
    }
}
