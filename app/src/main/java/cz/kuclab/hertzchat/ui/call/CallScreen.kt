package cz.kuclab.hertzchat.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.p2p.CallEndReason
import cz.kuclab.hertzchat.p2p.CallState
import cz.kuclab.hertzchat.ui.common.GlassAmbientBackground
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.Strands
import cz.kuclab.hertzchat.ui.theme.HertzIcons
import java.io.File
import kotlinx.coroutines.delay

/**
 * The fullscreen call surface: peer photo and name, live state, mic strands
 * while talking, and the three glass controls (mute, hang up, speaker).
 * An incoming call shows accept/decline instead. A terminal state lingers just
 * long enough to read why the call ended, then leaves on its own.
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
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = HertzGlass.contentOnGlass(),
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                callStatusText(state, nowMs),
                style = MaterialTheme.typography.bodyLarge,
                color = HertzGlass.contentOnGlass().copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Strands(
                level = if (state is CallState.Active && !muted) micLevel else 0f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 24.dp, bottom = 8.dp),
            )
            when (state) {
                is CallState.Incoming -> Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    GlassCircleButton(
                        icon = HertzIcons.CallEnd,
                        contentDescription = "Odmítnout",
                        onClick = { viewModel.calls.reject(); onDone() },
                        size = 64.dp,
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
                        size = 64.dp,
                        accent = true,
                    )
                }
                is CallState.Outgoing, is CallState.Active -> Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    GlassCircleButton(
                        icon = if (muted) HertzIcons.MicOff else HertzIcons.Mic,
                        contentDescription = if (muted) "Zapnout mikrofon" else "Ztlumit mikrofon",
                        onClick = viewModel.calls::toggleMute,
                        size = 56.dp,
                    )
                    GlassCircleButton(
                        icon = HertzIcons.CallEnd,
                        contentDescription = "Ukončit hovor",
                        onClick = { viewModel.calls.hangup() },
                        size = 64.dp,
                        danger = true,
                    )
                    GlassCircleButton(
                        icon = HertzIcons.Speaker,
                        contentDescription = if (speaker) "Vypnout reproduktor" else "Zapnout reproduktor",
                        onClick = viewModel.calls::toggleSpeaker,
                        size = 56.dp,
                        accent = speaker,
                    )
                }
                else -> Unit
            }
            Text(
                "Šifrováno end-to-end (Signal)",
                style = MaterialTheme.typography.labelSmall,
                color = HertzGlass.contentOnGlass().copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 24.dp),
            )
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
