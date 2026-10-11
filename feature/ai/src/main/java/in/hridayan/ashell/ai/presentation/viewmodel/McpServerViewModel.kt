package `in`.hridayan.ashell.ai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.hridayan.ashell.ai.mcp.McpServerController
import `in`.hridayan.ashell.ai.mcp.McpServerState
import `in`.hridayan.ashell.ai.mcp.McpToolBridge
import `in`.hridayan.ashell.core.common.domain.repository.SettingsRepository
import `in`.hridayan.ashell.core.common.settings.SettingsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class McpServerViewModel @Inject constructor(
    private val controller: McpServerController,
    private val toolBridge: McpToolBridge,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val serverState: StateFlow<McpServerState> = controller.state

    private val _exposedTools = MutableStateFlow<List<String>>(emptyList())
    val exposedTools: StateFlow<List<String>> = _exposedTools.asStateFlow()

    init {
        refreshTools()
    }

    fun refreshTools() {
        viewModelScope.launch {
            _exposedTools.value = toolBridge.listTools().map { it.name }
        }
    }

    fun startServer() {
        runCatching { controller.start() }
    }

    fun stopServer() {
        runCatching { controller.stop() }
    }

    fun setPort(port: Int) {
        viewModelScope.launch {
            settingsRepository.setInt(SettingsKeys.McpServerPort, port)
        }
    }

    fun setAuthToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setString(SettingsKeys.McpServerAuthToken, token)
        }
    }
}
