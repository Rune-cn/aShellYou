package `in`.hridayan.ashell.ai.mcp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import `in`.hridayan.ashell.ai.domain.tool.CommandExecutionManager
import `in`.hridayan.ashell.ai.mcp.McpConfig
import `in`.hridayan.ashell.ai.mcp.McpHttpServer
import `in`.hridayan.ashell.ai.mcp.McpServerController
import `in`.hridayan.ashell.ai.mcp.McpServerEngine
import `in`.hridayan.ashell.ai.mcp.McpServerState
import `in`.hridayan.ashell.ai.mcp.McpSseSessionStore
import `in`.hridayan.ashell.core.common.domain.repository.SettingsRepository
import `in`.hridayan.ashell.core.common.settings.SettingsKeys
import `in`.hridayan.ashell.core.resources.R
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the local MCP server alive in the background.
 *
 * It reads the configuration from the settings, starts the embedded Ktor server and
 * mirrors the lifecycle state into [McpServerController] so the UI can observe it.
 */
@AndroidEntryPoint
class McpServerService : Service() {

    @Inject lateinit var controller: McpServerController
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var commandExecutionManager: CommandExecutionManager
    @Inject lateinit var engine: McpServerEngine
    @Inject lateinit var sessions: McpSseSessionStore
    @Inject lateinit var keepAliveManager: McpKeepAliveManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<*, *>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(port = null))
        scope.launch { startServer() }
        return START_STICKY
    }

    private suspend fun startServer() {
        if (server != null) return

        val enabled = settingsRepository.getBoolean(SettingsKeys.McpServerEnabled).firstOrNull()
            ?: SettingsKeys.McpServerEnabled.default
        if (!enabled) {
            stopSelf()
            return
        }

        val config = loadConfig()
        commandExecutionManager.autoApproveAll = config.autoApproveCommands

        try {
            val built = McpHttpServer(config, engine, sessions).build()
            built.start(wait = false)
            server = built

            if (config.keepAlive) keepAliveManager.start()

            notify(buildNotification(port = config.port))
            controller.updateState(
                McpServerState.Running(config, engine.exposedToolCount())
            )
        } catch (e: Exception) {
            commandExecutionManager.autoApproveAll = false
            controller.updateState(McpServerState.Failed(e.message ?: "Unknown error"))
            stopSelf()
        }
    }

    private suspend fun loadConfig(): McpConfig = McpConfig(
        port = settingsRepository.getInt(SettingsKeys.McpServerPort).firstOrNull()
            ?: SettingsKeys.McpServerPort.default,
        authToken = settingsRepository.getString(SettingsKeys.McpServerAuthToken).firstOrNull()
            .orEmpty(),
        allowLanAccess = settingsRepository.getBoolean(SettingsKeys.McpServerAllowLanAccess)
            .firstOrNull() ?: false,
        streamableHttpEnabled = settingsRepository
            .getBoolean(SettingsKeys.McpServerStreamableHttpEnabled).firstOrNull() ?: true,
        sseEnabled = settingsRepository.getBoolean(SettingsKeys.McpServerSseEnabled)
            .firstOrNull() ?: true,
        autoApproveCommands = settingsRepository
            .getBoolean(SettingsKeys.McpServerAutoApproveCommands).firstOrNull() ?: false,
        keepAlive = settingsRepository.getBoolean(SettingsKeys.McpServerKeepAlive)
            .firstOrNull() ?: false
    )

    override fun onDestroy() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 500)
        server = null
        sessions.clear()
        keepAliveManager.stop()
        commandExecutionManager.autoApproveAll = false
        controller.updateState(McpServerState.Stopped)
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.mcp_running_notification_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.des_mcp_server_enabled)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int?): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mcp_server)
            .setContentTitle(getString(R.string.mcp_running_notification_title))
            .apply {
                if (port != null) {
                    setContentText(getString(R.string.mcp_running_notification_text, port))
                }
            }
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "in.hridayan.ashell.ai.mcp.action.START"
        private const val CHANNEL_ID = "mcp_server"
        private const val NOTIFICATION_ID = 0x4D4350
    }
}
