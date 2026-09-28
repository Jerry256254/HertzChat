package cz.kuclab.hertzchat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzIcons

/**
 * The circular send button inside the input pill: the same frosted glass as
 * the mic and attach circles (not a blue dot), with the rocket glyph. Same
 * 40dp everywhere, so the three circles line up exactly.
 */
@Composable
fun ChatInputSendButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    GlassCircleButton(
        icon = HertzIcons.Send,
        contentDescription = contentDescription ?: "Odeslat",
        onClick = onClick,
        size = 40.dp,
        modifier = modifier,
    )
}
