package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** An icon docked inside the input pill (attach, emoji, ...) - sized to sit flush in the pill. */
@Composable
fun ChatInputPillIcon(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The circular send button inside the input pill: solid accent (the primary
 * action sits on color, not on glass), white glyph, glass edge and press squash.
 */
@Composable
fun ChatInputAccentButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    GlassCircleButton(
        icon = icon,
        contentDescription = contentDescription ?: "Odeslat",
        onClick = onClick,
        size = 48.dp,
        accent = true,
        modifier = modifier,
    )
}
