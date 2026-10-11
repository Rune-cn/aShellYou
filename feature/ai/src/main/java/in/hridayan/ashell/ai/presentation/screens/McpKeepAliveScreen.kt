@file:OptIn(ExperimentalMaterial3Api::class)

package `in`.hridayan.ashell.ai.presentation.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.hridayan.ashell.ai.presentation.viewmodel.McpKeepAliveViewModel
import `in`.hridayan.ashell.core.common.settings.LocalSettings
import `in`.hridayan.ashell.core.common.settings.SettingsKeys
import `in`.hridayan.ashell.core.navigation.LocalNavController
import `in`.hridayan.ashell.core.navigation.navigateBack
import `in`.hridayan.ashell.core.presentation.components.scaffold.AppScaffold
import `in`.hridayan.ashell.core.resources.R
import `in`.hridayan.settingsgraph.ui.SettingsColumn

private const val KEY_PERM_NOTIFICATIONS = "mcp_perm_notifications"
private const val KEY_PERM_BATTERY = "mcp_perm_battery"
private const val KEY_PERM_OVERLAY = "mcp_perm_overlay"

/**
 * Keep-alive settings for the local MCP server, together with the optional runtime
 * permissions each mechanism needs.
 */
@Composable
fun McpKeepAliveScreen(
    modifier: Modifier = Modifier,
    viewModel: McpKeepAliveViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current

    val hapticsEnabled = settings[SettingsKeys.HapticsAndVibration]
    val keepAlive = settings[SettingsKeys.McpServerKeepAlive]
    val permissions by viewModel.permissions.collectAsState()

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    // Persist and apply the flag to the running server.
    LaunchedEffect(keepAlive) { viewModel.applyKeepAlive(keepAlive) }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()

    AppScaffold(
        onNavigateBack = { navController.navigateBack() },
        modifier = modifier,
        listState = listState,
        topAppBarState = topAppBarState,
        topBarTitle = stringResource(R.string.mcp_keep_alive),
        content = { innerPadding, topBarScrollBehavior ->

            SettingsColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
                listState = listState,
                contentPadding = innerPadding,
                topAppBarState = topAppBarState,
                hapticsEnabled = hapticsEnabled,
            ) {
                group(R.string.mcp_keep_alive) {
                    switchItem(SettingsKeys.McpServerKeepAlive) {
                        title(R.string.mcp_keep_alive_enabled)
                        description(R.string.des_mcp_keep_alive_enabled)
                        icon(R.drawable.ic_mcp_server)
                    }
                }

                item(key = "mcp_keep_alive_hint") {
                    Text(
                        text = stringResource(R.string.mcp_keep_alive_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }

                group(R.string.mcp_keep_alive_permissions) {
                    clickableItem(KEY_PERM_NOTIFICATIONS) {
                        title(R.string.mcp_perm_notifications)
                        description {
                            permissionLabel(permissions.notifications)
                        }
                        icon(R.drawable.ic_mcp_server)
                        onClick {
                            if (permissions.notifications) {
                                settingsLauncher.launch(viewModel.notificationSettingsIntent())
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                settingsLauncher.launch(viewModel.notificationSettingsIntent())
                            }
                        }
                    }

                    clickableItem(KEY_PERM_BATTERY) {
                        title(R.string.mcp_perm_battery)
                        description {
                            permissionLabel(permissions.batteryOptimizations)
                        }
                        icon(R.drawable.ic_mcp_server)
                        onClick {
                            if (!permissions.batteryOptimizations) {
                                settingsLauncher.launch(viewModel.batteryOptimizationIntent())
                            }
                        }
                    }

                    clickableItem(KEY_PERM_OVERLAY) {
                        title(R.string.mcp_perm_overlay)
                        description {
                            permissionLabel(permissions.overlay)
                        }
                        icon(R.drawable.ic_mcp_server)
                        onClick {
                            if (!permissions.overlay) {
                                settingsLauncher.launch(viewModel.overlayPermissionIntent())
                            }
                        }
                    }
                }

                item(key = "spacer_bottom") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(25.dp)
                    )
                }
            }
        },
    )
}

@Composable
private fun permissionLabel(granted: Boolean): String =
    stringResource(
        if (granted) R.string.mcp_perm_granted else R.string.mcp_perm_not_granted
    )
