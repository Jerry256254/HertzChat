package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The one popup menu in the whole app - overflow (three dots), chat-row
 * long-press, attachment picker, language and quality pickers, everything.
 * Anchored to the calling control (Material3 positions it next to the trigger
 * and flips it on-screen when there is no room), with the shared translucent
 * fill over a blurred backdrop and the shared radius. One component, so all
 * menus look identical - by construction, not by review.
 *
 * A menu's container is drawn by a `Surface` inside `DropdownMenu` whose shape
 * comes from `shapes.extraSmall` and whose color is `surfaceContainer` - this
 * M3 takes neither as a parameter, so both go in through the local theme.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(surfaceContainer = HertzGlass.menuFill()),
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp)),
        typography = MaterialTheme.typography,
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = modifier.widthIn(min = 200.dp).padding(vertical = 8.dp),
        ) {
            // Inside the popup window, so this blurs the screen behind the menu.
            WindowBlurBehind()
            content()
        }
    }
}

/**
 * The one menu row in the whole app: plain text in a modern list, no icons.
 * Icons in every row turned menus into sticker sheets; a clean text list
 * scans faster and looks the same in all seven languages.
 */
@Composable
fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    val press = glassPressAlpha(interactionSource)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f * press))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color,
        )
    }
}

/**
 * The attachment picker: a popup anchored at the attach button with one text
 * row per type. Opens above the input pill - there is no room below the
 * button, so the popup flips up.
 */
@Composable
fun AttachmentMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: (() -> Unit)? = null,
) {
    GlassMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        if (onTakePhoto != null) {
            GlassMenuItem(text = "Vyfotit", onClick = { onDismissRequest(); onTakePhoto() })
        }
        GlassMenuItem(text = "Obrázek", onClick = { onDismissRequest(); onPickImage() })
        GlassMenuItem(text = "Video", onClick = { onDismissRequest(); onPickVideo() })
        GlassMenuItem(text = "Soubor", onClick = { onDismissRequest(); onPickFile() })
    }
}
