package cz.kuclab.hertzchat.ui.chat

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageBubble(
    message: MessageEntity,
    threadMedia: List<MessageEntity> = listOf(message),
    mediaIndex: Int = 0,
    onDownload: (MessageEntity) -> Unit = {},
) {
    var showViewer by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(HertzShapes.Media)
            .clickable { showViewer = true },
    ) {
        AsyncImage(
            model = message.mediaPath,
            contentDescription = "Obrázek",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        DownloadBadge(onClick = { onDownload(message) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
    if (showViewer) {
        MediaViewerDialog(media = threadMedia, startIndex = mediaIndex, onDownload = onDownload, onDismiss = { showViewer = false })
    }
}

@Composable
fun VideoBubble(
    message: MessageEntity,
    threadMedia: List<MessageEntity> = listOf(message),
    mediaIndex: Int = 0,
    onDownload: (MessageEntity) -> Unit = {},
) {
    var showPlayer by remember { mutableStateOf(false) }
    val frame = rememberVideoFrame(message.mediaPath)
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(HertzShapes.Media)
            .background(Color.Black)
            .clickable { showPlayer = true },
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) {
            androidx.compose.foundation.Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        Icon(Icons.Filled.PlayCircle, contentDescription = "Přehrát video", tint = Color.White, modifier = Modifier.size(56.dp))
        message.mediaDurationMs?.let { duration ->
            Text(
                formatVoiceDuration(duration),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), HertzShapes.Small)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        DownloadBadge(onClick = { onDownload(message) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
    if (showPlayer) {
        MediaViewerDialog(media = threadMedia, startIndex = mediaIndex, onDownload = onDownload, onDismiss = { showPlayer = false })
    }
}

/** Small round download button overlaid on media thumbnails. */
@Composable
private fun DownloadBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.padding(4.dp).size(36.dp).background(Color.Black.copy(alpha = 0.55f), HertzShapes.Pill),
    ) {
        Icon(Icons.Filled.Download, contentDescription = "Stáhnout", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun VoiceBubble(message: MessageEntity, onSurface: Color, accent: Color, onDownload: (MessageEntity) -> Unit = {}) {
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }

    DisposableEffect(message.messageId) {
        onDispose { player.release() }
    }

    // A plain clickable Box rather than IconButton: IconButton forces a 48dp touch
    // target with its own internal padding, which inside a bubble shows up as a wide
    // gap on the left that the right side has no equivalent of - the content ends up
    // visibly off-centre. The row keeps the same padding as FileBubble so every
    // attachment bubble is inset identically.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clickable {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        message.mediaPath?.let { path ->
                            player.reset()
                            player.setDataSource(path)
                            player.setOnCompletionListener { isPlaying = false }
                            player.prepare()
                            player.start()
                            isPlaying = true
                        }
                    }
                },
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pozastavit" else "Přehrát",
                tint = accent,
                modifier = Modifier.size(28.dp),
            )
        }
        val seconds = ((message.mediaDurationMs ?: 0L) / 1000).toInt()
        Text(
            "Hlasová zpráva · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            color = onSurface,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        IconButton(onClick = { onDownload(message) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Download, contentDescription = "Stáhnout hlasovku", tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The fullscreen gallery: swipe horizontally through every photo/video of the thread,
 * pinch or double-tap to zoom photos, videos play inline with controls, and anything
 * can be saved to the device from the top bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewerDialog(
    media: List<MessageEntity>,
    startIndex: Int,
    onDownload: (MessageEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(media.indices), pageCount = { media.size })
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val item = media[page]
                when (item.type) {
                    cz.kuclab.hertzchat.data.db.MessageType.VIDEO -> ViewerVideoPage(
                        path = item.mediaPath,
                        active = pagerState.currentPage == page,
                    )
                    else -> ViewerImagePage(path = item.mediaPath)
                }
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Zavřít", tint = Color.White)
                }
                if (media.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / ${media.size}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(onClick = { media.getOrNull(pagerState.currentPage)?.let(onDownload) }) {
                    Icon(Icons.Filled.Download, contentDescription = "Stáhnout", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ViewerImagePage(path: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    AsyncImage(
        model = path,
        contentDescription = "Obrázek na celou obrazovku",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = max(1f, min(scale * zoom, 5f))
                    if (scale == 1f) {
                        offsetX = 0f; offsetY = 0f
                    } else {
                        offsetX += pan.x; offsetY += pan.y
                    }
                }
            },
    )
}

@Composable
private fun ViewerVideoPage(path: String?, active: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            path?.let { setMediaItem(MediaItem.fromUri(File(it).toURI().toString())) }
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    androidx.compose.runtime.LaunchedEffect(active) {
        exoPlayer.playWhenReady = active
        if (!active) exoPlayer.pause()
    }
    AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxSize(),
    )
}

/** First frame of a local video file, decoded once per bubble for the thumbnail. */
@Composable
private fun rememberVideoFrame(path: String?): ImageBitmap? {
    return remember(path) {
        if (path == null) return@remember null
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                retriever.getFrameAtTime(0)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

/**
 * A received/sent arbitrary file: name, size, tap to preview in the built-in viewer,
 * and an explicit download button. Files the viewer can't render still offer "open in
 * another app" from the viewer screen itself.
 */
@Composable
fun FileBubble(
    message: MessageEntity,
    onSurface: Color,
    onOpenFile: (MessageEntity) -> Unit = {},
    onDownload: (MessageEntity) -> Unit = {},
) {
    val file = message.mediaPath?.let { File(it) }
    val name = message.mediaFileName ?: file?.name ?: "Soubor"

    Row(
        modifier = Modifier
            .clickable { onOpenFile(message) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = onSurface,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(name, color = onSurface, maxLines = 1)
            file?.takeIf { it.exists() }?.let {
                Text(
                    formatFileSize(it.length()),
                    color = onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        IconButton(onClick = { onDownload(message) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Download, contentDescription = "Stáhnout soubor", tint = onSurface, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format("%.0f kB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatVoiceDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
