package `in`.hridayan.ashell.ai.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.hridayan.ashell.ai.mcp.McpKeepAliveManager
import `in`.hridayan.ashell.ai.mcp.McpServerController
import `in`.hridayan.ashell.ai.mcp.McpServerState
import `in`.hridayan.ashell.core.common.domain.repository.SettingsRepository
import `in`.hridayan.ashell.core.common.settings.SettingsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether each optional keep-alive permission has been granted. */
data class McpPermissionState(
    val notifications: Boolean = false,
    val batteryOptimizations: Boolean = false,
    val overlay: Boolean = false
)

@HiltViewModel
class McpKeepAliveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keepAliveManager: McpKeepAliveManager,
    private val controller: McpServerController,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _permissions = MutableStateFlow(McpPermissionState())
    val permissions: StateFlow<McpPermissionState> = _permissions.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = McpPermissionState(
            notifications = keepAliveManager.isNotificationGranted(),
            batteryOptimizations = keepAliveManager.isBatteryOptimizationIgnored(),
            overlay = keepAliveManager.canDrawOverlays()
        )
    }

    /**
     * Persists the keep-alive flag and applies it to the running server. When the server is
     * not running the value is simply stored and picked up the next time it starts.
     */
    fun applyKeepAlive(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBoolean(SettingsKeys.McpServerKeepAlive, enabled)
            if (controller.state.value is McpServerState.Running) {
                if (enabled) keepAliveManager.start() else keepAliveManager.stop()
            }
        }
    }

    // The screen launches these through an ActivityResultLauncher so it can refresh the
    // permission state when the user comes back from the system screen.

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri())

    fun overlayPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(packageUri())

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)
}
