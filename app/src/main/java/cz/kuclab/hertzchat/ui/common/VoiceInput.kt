package cz.kuclab.hertzchat.ui.common

import android.media.MediaPlayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.delay

/**
 * The mic button beside the input pill: press-and-hold to record, release to stop.
 * Nothing is ever sent automatically - the finished clip lands in [VoicePreviewBar],
 * where it can be played back, deleted, or sent.
 */
@Composable
fun HoldToRecordButton(
    onPressStart: () -> Boolean,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (held) 1.18f else 1f, label = "micHeldScale")
    // A plain Box on purpose: nesting detectTapGestures around IconButton's own
    // clickable lets the inner clickable swallow the press, so holding the mic
    // silently did nothing. The single pointerInput here is the only consumer.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (held) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (onPressStart()) {
                            held = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                tryAwaitRelease()
                            } finally {
                                // The press scope can be cancelled without a release
                                // (window focus lost, navigation) - the recorder must
                                // still stop, or it records forever in a stuck UI.
                                held = false
                                onPressEnd()
                            }
                        }
                    },
                )
            },
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Podrž pro nahrání hlasovky", tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Replaces the input pill while the mic button is held: elapsed time plus a hint that releasing stops (not sends). */
@Composable
fun VoiceRecordingIndicator(elapsedMs: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = cz.kuclab.hertzchat.ui.theme.HertzShapes.Pill,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Text(
                "Nahrávám… ${formatVoiceDuration(elapsedMs)} - pusť pro náhled",
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The recorded-but-unsent clip: play/pause with scrub, the duration, and delete.
 * Sending stays on the input bar's send button, next to this preview.
 */
@Composable
fun VoicePreviewBar(
    file: File,
    durationMs: Long,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = remember(file) { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var prepared by remember { mutableStateOf(false) }

    DisposableEffect(file) {
        onDispose {
            runCatching { player.release() }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
            delay(200)
        }
    }

    fun toggle() {
        if (isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            runCatching {
                if (!prepared) {
                    player.reset()
                    player.setDataSource(file.absolutePath)
                    player.setOnCompletionListener { isPlaying = false; positionMs = 0L }
                    player.prepare()
                    prepared = true
                }
                player.seekTo(positionMs.toInt().coerceAtMost(durationMs.toInt()))
                player.start()
                isPlaying = true
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = cz.kuclab.hertzchat.ui.theme.HertzShapes.Pill,
        color = cz.kuclab.hertzchat.ui.theme.HertzMatte.input(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    }
                    onDelete()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Smazat nahrávku", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = ::toggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pozastavit" else "Přehrát",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = {
                    positionMs = it.toLong()
                    if (prepared) runCatching { player.seekTo(it.toInt()) }
                },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f),
            )
            Text(
                formatVoiceDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

fun formatVoiceDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
