package cz.kuclab.hertzchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.db.MessageEntity
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageBubble(message: MessageEntity) {
    var showViewer by remember { mutableStateOf(false) }
    AsyncImage(
        model = message.mediaPath,
        contentDescription = "Obrázek",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { showViewer = true },
    )
    if (showViewer) {
        FullScreenImageViewer(path = message.mediaPath, onDismiss = { showViewer = false })
    }
}

@Composable
fun VideoBubble(message: MessageEntity) {
    var showPlayer by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .clickable { showPlayer = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayCircle, contentDescription = "Přehrát video", tint = Color.White, modifier = Modifier.size(56.dp))
    }
    if (showPlayer) {
        FullScreenVideoPlayer(path = message.mediaPath, onDismiss = { showPlayer = false })
    }
}

@Composable
fun VoiceBubble(message: MessageEntity, onSurface: Color, accent: Color) {
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }

    DisposableEffect(message.messageId) {
        onDispose { player.release() }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = {
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
        }) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pozastavit" else "Přehrát",
                tint = accent,
            )
        }
        val seconds = ((message.mediaDurationMs ?: 0L) / 1000).toInt()
        Text("Hlasová zpráva · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = onSurface)
    }
}

@Composable
private fun FullScreenImageViewer(path: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = max(1f, min(scale * zoom, 5f))
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Zavřít", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FullScreenVideoPlayer(path: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                path?.let { setMediaItem(MediaItem.fromUri(File(it).toURI().toString())) }
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer } },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Zavřít", tint = Color.White)
            }
        }
    }
}
