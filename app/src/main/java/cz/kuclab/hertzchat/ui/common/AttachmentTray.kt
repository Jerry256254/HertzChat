package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.io.File

/** One staged attachment: picked (or photographed) but not sent yet - it only goes out with the send button. */
data class PendingAttachment(
    /** Staged bytes live in a temp file rather than in memory - a photo burst must not blow the heap. */
    val file: File,
    val mimeType: String,
    val kind: PayloadKind,
    val fileName: String?,
)

/**
 * The horizontal strip of staged attachments above the input: thumbnails with a
 * remove button each. Sending transmits every staged item plus the typed message.
 */
@Composable
fun PendingAttachmentsTray(
    attachments: List<PendingAttachment>,
    onRemove: (PendingAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(attachments.size, key = { attachments[it].file.absolutePath }) { index ->
            val attachment = attachments[index]
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(HertzShapes.Media)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                when (attachment.kind) {
                    PayloadKind.IMAGE -> AsyncImage(
                        model = attachment.file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(HertzShapes.Media),
                    )
                    PayloadKind.VIDEO -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (attachment.kind == PayloadKind.VIDEO) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (attachment.kind != PayloadKind.IMAGE) {
                    Text(
                        attachment.fileName?.take(10) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = { onRemove(attachment) },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                ) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Odebrat přílohu", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
