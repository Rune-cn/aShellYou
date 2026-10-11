package `in`.hridayan.ashell.ai.mcp

/** Lifecycle state of the local MCP server, exposed to the UI. */
sealed interface McpServerState {
    data object Stopped : McpServerState

    data class Running(
        val config: McpConfig,
        val exposedToolCount: Int
    ) : McpServerState

    data class Failed(val message: String) : McpServerState
}
