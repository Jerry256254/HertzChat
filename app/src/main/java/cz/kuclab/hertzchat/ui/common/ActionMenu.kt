package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzGreen

/**
 * The shared popup-menu container: anchored to the calling control (Material3
 * positions it next to the trigger and flips it on-screen when there is no
 * room), with the app's translucent menu fill over a blurred backdrop and the
 * shared radius. One component, so all menus share the exact same surface -
 * by construction, not by review.
 *
 * A menu's container is drawn by a `Surface` inside `DropdownMenu` whose shape
 * comes from `shapes.extraSmall` and whose color is `surfaceContainer` - this
 * M3 takes neither as a parameter, so both go in through the local theme.
 */
@Composable
fun GlassPopupMenu(
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
            modifier = modifier,
        ) {
            // Inside the popup window, so this blurs the screen behind the menu.
            WindowBlurBehind()
            content()
        }
    }
}

/**
 * Short action lists (chat-row long-press, per-screen overflow menu): an icon
 * chip plus label per row, left-aligned like chat apps do - centered rows scan
 * badly once labels differ in length.
 */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 220.dp).padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
fun ActionMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val chipContainer = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Row(
        modifier = Modifier
            .widthIn(min = 220.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(chipContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The attachment picker: a popup anchored at the attach button with a row of
 * icon-chip buttons (own tinted color per type, label underneath). Opens above
 * the input pill - there is no room below the button, so the popup flips up.
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
    GlassPopupMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (onTakePhoto != null) {
                AttachmentOption(
                    icon = Icons.Filled.PhotoCamera,
                    label = "Vyfotit",
                    color = Color(0xFF03A9F4),
                    onClick = { onDismissRequest(); onTakePhoto() },
                )
            }
            AttachmentOption(
                icon = Icons.Filled.Image,
                label = "Obrázek",
                color = HertzGreen,
                onClick = { onDismissRequest(); onPickImage() },
            )
            AttachmentOption(
                icon = Icons.Filled.VideoLibrary,
                label = "Video",
                color = Color(0xFF7C4DFF),
                onClick = { onDismissRequest(); onPickVideo() },
            )
            AttachmentOption(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                label = "Soubor",
                color = Color(0xFFFF9800),
                onClick = { onDismissRequest(); onPickFile() },
            )
        }
    }
}

@Composable
private fun AttachmentOption(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    // 68dp fits the longest label ("Obrázek") on one line; four options plus
    // spacing stay under 360dp wide screens. Single-line with ellipsis as a
    // backstop - a wrapped label ("Sou/bor") is what used to break this grid.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.padding(top = 3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
