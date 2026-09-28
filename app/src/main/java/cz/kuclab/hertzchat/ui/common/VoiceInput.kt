package cz.kuclab.hertzchat.ui.common

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzIcons
import java.io.File
import kotlinx.coroutines.delay

/**
 * The mic button inside the input pill: press-and-hold to record, release to stop.
 * Glass at rest, solid red the moment the press lands. Nothing is ever sent
 * automatically - the finished clip lands in the pill's preview, where it can be
 * played back, deleted, or sent.
 */
@Composable
fun HoldToRecordButton(
    onPressStart: () -> Boolean,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val fill = if (held) MaterialTheme.colorScheme.error else HertzGlass.fill()
    val iconTint = if (held) MaterialTheme.colorScheme.onError else HertzGlass.contentOnGlass()
    // A plain Box on purpose: nesting detectTapGestures around IconButton's own
    // clickable lets the inner clickable swallow the press, so holding the mic
    // silently did nothing. The single pointerInput here is the only consumer.
    // The held state is color-only - the button is never scaled or deformed.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(fill)
            .glassEdge(CircleShape)
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
        Icon(HertzIcons.Mic, contentDescription = "Podrž pro nahrání hlasovky", tint = iconTint)
    }
}

/**
 * The pill's center while the mic is held: audio strands streaming with the
 * live mic level, plus elapsed time and a hint that releasing stops (not
 * sends). Surface-less - the pill behind it is the surface.
 */
@Composable
fun VoiceRecordingContent(elapsedMs: Long, level: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Strands(
            level = level,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().height(30.dp),
        )
        Text(
            "${formatVoiceDuration(elapsedMs)} · pusť pro náhled",
            style = MaterialTheme.typography.labelSmall,
            color = HertzGlass.contentOnGlass().copy(alpha = 0.7f),
        )
    }
}

/**
 * The recorded-but-unsent clip as the pill's center: play/pause with scrub, the
 * duration, and delete. Surface-less - the pill behind it is the surface, and
 * sending stays on the pill's send button beside this preview.
 */
@Composable
fun VoicePreviewContent(
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

    Row(
        modifier = modifier.fillMaxWidth().padding(end = 2.dp),
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
            modifier = Modifier.size(38.dp),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Smazat nahrávku", tint = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = ::toggle, modifier = Modifier.size(38.dp)) {
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
            color = HertzGlass.contentOnGlass().copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

fun formatVoiceDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
