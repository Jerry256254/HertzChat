package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The message composer shared by every chat surface (1:1, group, assistant): a
 * pill-shaped field with optional icons docked inside it on the left (attach) and
 * a separate circular accent button outside it on the right (send/mic/stop).
 *
 * Built on [BasicTextField] rather than Material's `TextField`, which reserves
 * vertical space for a floating label even when no label is set - inside a pill
 * that shows up as text sitting visibly below center with no way to correct it
 * from the outside.
 *
 * `imePadding()` here (not just `windowSoftInputMode="adjustResize"` in the
 * manifest) is required because the app runs edge-to-edge - edge-to-edge opts out
 * of the system's automatic resize, so Compose has to consume the IME inset itself
 * or this bar stays pinned behind the keyboard instead of rising above it.
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailingButton: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                } else {
                    Box(modifier = Modifier.size(10.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Start,
                                )
                            }
                            innerTextField()
                        },
                    )
                }
                Box(modifier = Modifier.size(6.dp))
            }
        }
        Box(modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) {
            trailingButton()
        }
    }
}

/** An icon docked inside [ChatInputBar]'s pill (attach, emoji, ...) - sized to sit flush in the pill. */
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

/** The circular accent button beside [ChatInputBar]'s pill (send/mic/stop). */
@Composable
fun ChatInputAccentButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onPrimary)
    }
}
