package `in`.hridayan.ashell.settings.presentation.page.search.graph.entries

import androidx.navigation.NavController
import `in`.hridayan.ashell.core.common.settings.SettingsKeys
import `in`.hridayan.ashell.core.navigation.NavRoutes
import `in`.hridayan.ashell.core.resources.R
import `in`.hridayan.ashell.settings.presentation.page.search.graph.constants.SettingsGraphId
import `in`.hridayan.settingsgraph.search.SearchScreenScope

internal fun SearchScreenScope.mcpServerGraph(navController: NavController) {
    screen(
        id = SettingsGraphId.MCP_SERVER,
        title = R.string.mcp_server,
        navigate = {
            navController.navigate(NavRoutes.McpServerScreen) { launchSingleTop = true }
        },
    ) {
        entry(key = SettingsKeys.McpServerEnabled) {
            title(R.string.mcp_server_enabled)
            description(R.string.des_mcp_server_enabled)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpServerStreamableHttpEnabled) {
            title(R.string.mcp_streamable_http)
            description(R.string.des_mcp_streamable_http)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpServerSseEnabled) {
            title(R.string.mcp_sse)
            description(R.string.des_mcp_sse)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpServerPort) {
            title(R.string.mcp_port)
            description(R.string.des_mcp_port)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpServerAuthToken) {
            title(R.string.mcp_auth_token)
            description(R.string.des_mcp_auth_token)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpServerAllowLanAccess) {
            title(R.string.mcp_allow_lan)
            description(R.string.des_mcp_allow_lan)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpServerAutoApproveCommands) {
            title(R.string.mcp_auto_approve)
            description(R.string.des_mcp_auto_approve)
            icon(R.drawable.ic_mcp_server)
        }

        entry(key = SettingsKeys.McpKeepAlive) {
            title(R.string.mcp_keep_alive)
            description(R.string.des_mcp_keep_alive)
            icon(R.drawable.ic_mcp_server)
        }

        mcpKeepAliveGraph(navController)
    }
}

internal fun SearchScreenScope.mcpKeepAliveGraph(navController: NavController) {
    screen(
        id = SettingsGraphId.MCP_KEEP_ALIVE,
        title = R.string.mcp_keep_alive,
        navigate = {
            navController.navigate(NavRoutes.McpKeepAliveScreen) { launchSingleTop = true }
        },
    ) {
        entry(key = SettingsKeys.McpServerKeepAlive) {
            title(R.string.mcp_keep_alive_enabled)
            description(R.string.des_mcp_keep_alive_enabled)
            icon(R.drawable.ic_mcp_server)
        }
    }
}
