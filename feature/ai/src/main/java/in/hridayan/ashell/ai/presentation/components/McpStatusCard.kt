package `in`.hridayan.ashell.ai.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.hridayan.ashell.ai.mcp.McpServerState
import `in`.hridayan.ashell.core.resources.R

/**
 * Status banner for the local MCP server: a tonal card whose leading icon matches the
 * lifecycle state, plus the listening port, the exposed tool count and whether keep-alive
 * is on.
 */
@Composable
fun McpStatusCard(
    state: McpServerState,
    exposedTools: List<String>,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val appearance = when (state) {
        is McpServerState.Running -> StatusAppearance(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            accent = scheme.primary,
            icon = Icons.Rounded.CheckCircle,
            title = stringResource(R.string.mcp_status_running),
            subtitle = exposedTools.joinToString(", ")
                .ifEmpty { stringResource(R.string.mcp_no_tools_short) }
        )

        is McpServerState.Stopped -> StatusAppearance(
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
            accent = scheme.outline,
            icon = Icons.Rounded.RadioButtonUnchecked,
            title = stringResource(R.string.mcp_status_stopped),
            subtitle = null
        )

        is McpServerState.Failed -> StatusAppearance(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            accent = scheme.error,
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.mcp_status_error, state.message),
            subtitle = null
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = appearance.container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(26.dp),
                imageVector = appearance.icon,
                contentDescription = null,
                tint = appearance.accent
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appearance.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = appearance.content
                )
                appearance.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = appearance.content.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private data class StatusAppearance(
    val container: Color,
    val content: Color,
    val accent: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String?
)
