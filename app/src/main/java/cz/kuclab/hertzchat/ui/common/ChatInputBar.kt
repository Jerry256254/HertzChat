package cz.kuclab.hertzchat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The circular send button inside the input pill: solid accent (the primary
 * action sits on color, not on glass), white glyph, glass edge. Same 48dp as
 * the mic and attach circles flanking the pill's text.
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
