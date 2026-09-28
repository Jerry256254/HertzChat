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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzGreen
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Short action lists (chat-row long-press, per-screen overflow menu): a glass
 * card centered on screen, every row an icon chip plus label. Same material
 * and entrance as every other menu in the app - see [CenteredGlassMenu].
 */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CenteredGlassMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
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
    GlassMenuItem(text = text, icon = icon, onClick = onClick, destructive = destructive)
}

/**
 * The attachment picker: a centered glass card with a row of icon-chip buttons
 * (own tinted color per type, label underneath) - a compact grid that reads at
 * a glance rather than a list you have to scan line by line.
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
    CenteredGlassMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Rounded-rect ripple bounds: a circle clip on this tall column would eat
        // the label's corners (oval clip on a non-square box).
        modifier = Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
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
            color = HertzGlass.contentOnGlass(),
        )
    }
}
