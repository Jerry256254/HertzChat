package cz.kuclab.hertzchat.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.p2p.CallEndReason
import cz.kuclab.hertzchat.p2p.CallState
import cz.kuclab.hertzchat.ui.common.GlassAmbientBackground
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.GlassSurface
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.Strands
import cz.kuclab.hertzchat.ui.theme.HertzIcons
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.io.File
import kotlinx.coroutines.delay

/**
 * The fullscreen call surface in the app's glass spirit: the peer's photo
 * blurred into a full-bleed backdrop, one frosted card with who and how long,
 * and the controls floating in their own frosted pill. An incoming call shows
 * accept/decline instead. A terminal state lingers just long enough to read
 * why the call ended, then leaves on its own.
 */
@Composable
fun CallScreen(contactId: String, onDone: () -> Unit, viewModel: CallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val muted by viewModel.muted.collectAsState()
    val speaker by viewModel.speaker.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val nickname by viewModel.peerNickname.collectAsState()
    val avatarPath by viewModel.peerAvatarPath.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    var micAsked by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            onDone()
            return@rememberLauncherForActivityResult
        }
        // The same grant gate serves both entries: ringing out, or picking up.
        if (viewModel.calls.state.value is CallState.Incoming) {
            viewModel.calls.accept()
        } else {
            viewModel.calls.startCall(contactId)
        }
    }

    // Entering this screen with no live call means "call this contact".
    LaunchedEffect(contactId) {
        if (viewModel.calls.state.value == CallState.Idle && !micAsked) {
            micAsked = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.calls.startCall(contactId)
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // A finished call shows its reason, then dismisses itself.
    LaunchedEffect(state) {
        if (state is CallState.Ended) {
            delay(1800)
            viewModel.calls.clearEnded()
            onDone()
        }
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state is CallState.Active) {
        while (state is CallState.Active) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassAmbientBackground()
        // The peer's photo as the room: blurred full-bleed under a scrim, so
        // the frosted card and controls float over them, not over flat paint.
        // Below Android 12 the blur modifier is a no-op and the scrim alone
        // carries the look - still on-palette, just less frosty.
        if (avatarPath != null) {
            AsyncImage(
                model = File(avatarPath!!),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(80.dp),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f),
            ),
        )

        // Short screens (small phones, landscape-ish heights, big fonts) used to
        // overflow: fixed 104dp photo + paddings + pill simply didn't fit and the
        // controls clipped off-screen. Everything below scales off the available
        // height instead, so the whole surface always fits.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val topPad = if (compact) 12.dp else 32.dp
            val bottomPad = if (compact) 12.dp else 24.dp
            val cardVPadding = if (compact) 14.dp else 28.dp
            val photoSize = if (compact) 72.dp else 104.dp
            val nameTopPad = if (compact) 8.dp else 14.dp
            val strandsHeight = if (compact) 36.dp else 56.dp
            val primaryButton = if (compact) 52.dp else 60.dp
            val secondaryButton = if (compact) 46.dp else 52.dp
            val nameStyle = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall

            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(top = topPad, bottom = bottomPad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassSurface(
                    shape = HertzShapes.Dialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = cardVPadding, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // While ringing, the photo breathes gently - alive, not static.
                        val ringing = state is CallState.Outgoing || state is CallState.Incoming
                        val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
                            initialValue = 1f,
                            targetValue = 1.07f,
                            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                            label = "pulse",
                        )
                        Box(
                            modifier = Modifier
                                .size(photoSize)
                                .graphicsLayer {
                                    val s = if (ringing) pulse else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarPath != null) {
                                AsyncImage(
                                    model = File(avatarPath!!),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    nickname.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Text(
                            nickname.ifEmpty { "Neznámý" },
                            style = nameStyle,
                            fontWeight = FontWeight.SemiBold,
                            color = HertzGlass.contentOnGlass(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = nameTopPad),
                        )
                        Text(
                            callStatusText(state, nowMs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = HertzGlass.contentOnGlass().copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Strands(
                            level = if (state is CallState.Active && !muted) micLevel else 0f,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(strandsHeight).padding(top = 12.dp),
                        )
                        Text(
                            "Šifrováno end-to-end (Signal)",
                            style = MaterialTheme.typography.labelSmall,
                            color = HertzGlass.contentOnGlass().copy(alpha = 0.5f),
                            maxLines = 1,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

                when (state) {
                    is CallState.Incoming -> GlassSurface(
                        shape = HertzShapes.Pill,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassCircleButton(
                                icon = HertzIcons.CallEnd,
                                contentDescription = "Odmítnout",
                                onClick = { viewModel.calls.reject(); onDone() },
                                size = primaryButton,
                                danger = true,
                            )
                            GlassCircleButton(
                                icon = HertzIcons.Call,
                                contentDescription = "Přijmout",
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.calls.accept()
                                    } else {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                size = primaryButton,
                                accent = true,
                            )
                        }
                    }
                    is CallState.Outgoing, is CallState.Active -> GlassSurface(
                        shape = HertzShapes.Pill,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassCircleButton(
                                icon = if (muted) HertzIcons.MicOff else HertzIcons.Mic,
                                contentDescription = if (muted) "Zapnout mikrofon" else "Ztlumit mikrofon",
                                onClick = viewModel.calls::toggleMute,
                                size = secondaryButton,
                            )
                            GlassCircleButton(
                                icon = HertzIcons.CallEnd,
                                contentDescription = "Ukončit hovor",
                                onClick = { viewModel.calls.hangup() },
                                size = primaryButton,
                                danger = true,
                            )
                            GlassCircleButton(
                                icon = HertzIcons.Speaker,
                                contentDescription = if (speaker) "Vypnout reproduktor" else "Zapnout reproduktor",
                                onClick = viewModel.calls::toggleSpeaker,
                                size = secondaryButton,
                                accent = speaker,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun callStatusText(state: CallState, nowMs: Long): String = when (state) {
    CallState.Idle -> ""
    is CallState.Outgoing -> "Vytáčení…"
    is CallState.Incoming -> "Příchozí hovor"
    is CallState.Active -> formatCallDuration(nowMs - state.startedAtMs)
    is CallState.Ended -> when (state.reason) {
        CallEndReason.DECLINED -> "Hovor odmítnut"
        CallEndReason.MISSED -> "Zmeškaný hovor"
        CallEndReason.NO_ANSWER -> "Nezvedá to"
        CallEndReason.HANGUP -> "Hovor ukončen"
        CallEndReason.FAILED -> "Spojení se nezdařilo"
        CallEndReason.BUSY -> "Obsazeno"
    }
}

private fun formatCallDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
