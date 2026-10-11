package `in`.hridayan.ashell.ai.mcp

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.hridayan.ashell.ai.mcp.service.McpServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point used by the UI to control the local MCP server and observe its state.
 *
 * The actual server runs inside [McpServerService] so Android keeps the process alive
 * while the app is in the background.
 */
@Singleton
class McpServerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    val state: StateFlow<McpServerState> = _state.asStateFlow()

    fun start() {
        val intent = Intent(context, McpServerService::class.java)
            .setAction(McpServerService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        context.stopService(Intent(context, McpServerService::class.java))
    }

    internal fun updateState(newState: McpServerState) {
        _state.value = newState
    }
}
