package `in`.hridayan.ashell.ai.mcp

/**
 * Immutable snapshot of the local MCP server configuration.
 *
 * A new instance is built from the app settings every time the server is (re)started, so
 * changing a setting requires restarting the server to take effect.
 */
data class McpConfig(
    val port: Int,
    val authToken: String,
    val allowLanAccess: Boolean,
    val streamableHttpEnabled: Boolean,
    val sseEnabled: Boolean,
    val autoApproveCommands: Boolean
) {
    /**
     * Address the embedded server binds to. Binding to `127.0.0.1` keeps the server
     * reachable only from this device; `0.0.0.0` exposes it to the local network.
     */
    val bindHost: String get() = if (allowLanAccess) "0.0.0.0" else "127.0.0.1"
}
