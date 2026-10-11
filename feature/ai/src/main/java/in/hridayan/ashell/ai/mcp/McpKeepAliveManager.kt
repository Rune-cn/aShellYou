package `in`.hridayan.ashell.ai.mcp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort keep-alive for the local MCP server.
 *
 * Two independent mechanisms, both optional:
 *  - a partial [PowerManager.WakeLock] so the CPU is not suspended while the server runs;
 *  - a 1×1 transparent overlay window, which raises the process priority on many OEM ROMs.
 *
 * Everything here is opportunistic: if the user has not granted a permission, the
 * corresponding mechanism is silently skipped and the server keeps working.
 */
@Singleton
class McpKeepAliveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayView: View? = null

    // ── permission state ──────────────────────────────────────────────────────────────────

    fun isNotificationGranted(): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return enabled
        return enabled && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    // ── lifecycle ─────────────────────────────────────────────────────────────────────────

    fun start() {
        acquireWakeLock()
        showOverlay()
    }

    fun stop() {
        releaseWakeLock()
        hideOverlay()
    }

    // ── wake lock ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$KEEP_ALIVE_TAG:mcp-server"
        ).apply { setReferenceCounted(false) }

        runCatching { lock.acquire() }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        wakeLock = null
    }

    // ── overlay ───────────────────────────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null || !canDrawOverlays()) return

        mainHandler.post {
            if (overlayView != null) return@post
            runCatching {
                val windowManager =
                    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = View(context)
                val params = WindowManager.LayoutParams(
                    OVERLAY_SIZE,
                    OVERLAY_SIZE,
                    overlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }
                windowManager.addView(view, params)
                overlayView = view
            }
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        mainHandler.post {
            runCatching {
                val windowManager =
                    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
            }
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private companion object {
        const val KEEP_ALIVE_TAG = "aShellYou"
        const val OVERLAY_SIZE = 1
    }
}
