@file:OptIn(ExperimentalMaterial3Api::class)

package `in`.hridayan.ashell.ai.presentation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.hridayan.ashell.ai.presentation.components.McpEndpointCard
import `in`.hridayan.ashell.ai.presentation.components.McpStatusCard
import `in`.hridayan.ashell.ai.presentation.viewmodel.McpServerViewModel
import `in`.hridayan.ashell.core.common.settings.LocalSettings
import `in`.hridayan.ashell.core.common.settings.SettingsKeys
import `in`.hridayan.ashell.core.navigation.LocalNavController
import `in`.hridayan.ashell.core.navigation.NavRoutes
import `in`.hridayan.ashell.core.navigation.navigateBack
import `in`.hridayan.ashell.core.presentation.components.scaffold.AppScaffold
import `in`.hridayan.ashell.core.resources.R
import `in`.hridayan.settingsgraph.ui.SettingsColumn

@Composable
fun McpServerScreen(
    modifier: Modifier = Modifier,
    viewModel: McpServerViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current

    val hapticsEnabled = settings[SettingsKeys.HapticsAndVibration]
    val enabled = settings[SettingsKeys.McpServerEnabled]
    val streamableHttpEnabled = settings[SettingsKeys.McpServerStreamableHttpEnabled]
    val sseEnabled = settings[SettingsKeys.McpServerSseEnabled]
    val allowLanAccess = settings[SettingsKeys.McpServerAllowLanAccess]
    val autoApprove = settings[SettingsKeys.McpServerAutoApproveCommands]
    val port = settings[SettingsKeys.McpServerPort]
    val authToken = settings[SettingsKeys.McpServerAuthToken]

    val serverState by viewModel.serverState.collectAsState()
    val exposedTools by viewModel.exposedTools.collectAsState()

    // Keep the foreground service in sync with the persisted switch.
    LaunchedEffect(enabled) {
        if (enabled) viewModel.startServer() else viewModel.stopServer()
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()

    val baseUrl = "http://127.0.0.1:$port"

    AppScaffold(
        onNavigateBack = { navController.navigateBack() },
        modifier = modifier,
        listState = listState,
        topAppBarState = topAppBarState,
        topBarTitle = stringResource(R.string.mcp_server),
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
                group(R.string.mcp_server) {
                    switchItem(SettingsKeys.McpServerEnabled) {
                        title(R.string.mcp_server_enabled)
                        description(R.string.des_mcp_server_enabled)
                        icon(R.drawable.ic_mcp_server)
                    }

                    switchItem(SettingsKeys.McpServerStreamableHttpEnabled) {
                        title(R.string.mcp_streamable_http)
                        description(R.string.des_mcp_streamable_http)
                        icon(Icons.Rounded.Link)
                        visible { enabled }
                    }

                    switchItem(SettingsKeys.McpServerSseEnabled) {
                        title(R.string.mcp_sse)
                        description(R.string.des_mcp_sse)
                        icon(Icons.Rounded.Sync)
                        visible { enabled }
                    }

                    clickableItem(SettingsKeys.McpKeepAlive) {
                        title(R.string.mcp_keep_alive)
                        description(R.string.des_mcp_keep_alive)
                        icon(Icons.Rounded.PowerSettingsNew)
                        onClick { navController.navigate(NavRoutes.McpKeepAliveScreen) }
                    }
                }

                if (enabled) {
                    item(key = "mcp_status") {
                        McpStatusCard(
                            state = serverState,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                        )
                    }

                    item(key = "mcp_port") {
                        var portText by remember(port) { mutableStateOf(port.toString()) }
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { newValue ->
                                if (newValue.length <= 5 && newValue.all { it.isDigit() }) {
                                    portText = newValue
                                    newValue.toIntOrNull()
                                        ?.takeIf { it in 1..65535 }
                                        ?.let(viewModel::setPort)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 4.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(R.string.mcp_port)) },
                            supportingText = { Text(stringResource(R.string.des_mcp_port)) },
                            singleLine = true,
                        )
                    }

                    item(key = "mcp_token") {
                        var tokenText by remember { mutableStateOf(authToken) }
                        OutlinedTextField(
                            value = tokenText,
                            onValueChange = {
                                tokenText = it
                                viewModel.setAuthToken(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 4.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(R.string.mcp_auth_token)) },
                            supportingText = {
                                Text(stringResource(R.string.des_mcp_auth_token))
                            },
                            singleLine = true,
                        )
                    }

                    item(key = "mcp_connection_header") {
                        SectionHeader(stringResource(R.string.mcp_connection))
                    }

                    if (streamableHttpEnabled) {
                        item(key = "mcp_endpoint_streamable") {
                            McpEndpointCard(
                                label = stringResource(R.string.mcp_endpoint_streamable_http),
                                value = "$baseUrl/mcp",
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (sseEnabled) {
                        item(key = "mcp_endpoint_sse") {
                            McpEndpointCard(
                                label = stringResource(R.string.mcp_endpoint_sse),
                                value = "$baseUrl/sse",
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                            )
                        }

                        item(key = "mcp_endpoint_messages") {
                            McpEndpointCard(
                                label = stringResource(R.string.mcp_endpoint_messages),
                                value = "$baseUrl/messages",
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                            )
                        }
                    }

                    item(key = "mcp_client_hint") {
                        HintText(stringResource(R.string.mcp_client_hint))
                    }

                    group(R.string.mcp_security) {
                        switchItem(SettingsKeys.McpServerAllowLanAccess) {
                            title(R.string.mcp_allow_lan)
                            description(R.string.des_mcp_allow_lan)
                            icon(R.drawable.ic_wifi_settings)
                        }

                        switchItem(SettingsKeys.McpServerAutoApproveCommands) {
                            title(R.string.mcp_auto_approve)
                            description(R.string.des_mcp_auto_approve)
                            icon(Icons.Rounded.Terminal)
                        }
                    }

                    if (allowLanAccess) {
                        item(key = "mcp_lan_hint") {
                            HintText(stringResource(R.string.mcp_lan_hint))
                        }
                    }

                    item(key = "mcp_tools_header") {
                        SectionHeader(stringResource(R.string.mcp_exposed_tools))
                    }

                    item(key = "mcp_tools") {
                        Text(
                            text = if (exposedTools.isEmpty()) {
                                stringResource(R.string.mcp_no_tools)
                            } else {
                                exposedTools.joinToString(separator = "\n")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp),
                        )
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp),
    )
}
