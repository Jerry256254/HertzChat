package cz.kuclab.hertzchat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.media.VoiceRecorder
import java.io.File

@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val nickname by viewModel.contactNickname.collectAsState()
    val avatarPath by viewModel.contactAvatarPath.collectAsState()
    val context = LocalContext.current

    var attachMenuOpen by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val voiceRecorder = remember { VoiceRecorder(context) }

    var editingImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        editingImageUri = uri
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::sendVideo)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isRecording = true
            voiceRecorder.start()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarPath != null) {
                                AsyncImage(
                                    model = File(avatarPath!!),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().size(36.dp).clip(CircleShape),
                                )
                            } else {
                                Text(nickname.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Text(nickname, modifier = Modifier.padding(start = 12.dp))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { attachMenuOpen = true }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Přiložit")
                    }
                    DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Obrázek") },
                            onClick = { attachMenuOpen = false; pickImage.launch("image/*") },
                        )
                        DropdownMenuItem(
                            text = { Text("Video") },
                            leadingIcon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                            onClick = { attachMenuOpen = false; pickVideo.launch("video/*") },
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = viewModel::onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Zpráva") },
                )
                IconButton(onClick = {
                    if (isRecording) {
                        isRecording = false
                        voiceRecorder.stop()?.let { (file, durationMs) ->
                            if (durationMs > 400) viewModel.sendVoice(file, durationMs) else file.delete()
                        }
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            isRecording = true
                            voiceRecorder.start()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) {
                    Icon(
                        if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Zastavit nahrávání" else "Nahrát hlasovku",
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = viewModel::send) {
                    Icon(Icons.Filled.Send, contentDescription = "Odeslat")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.messages, key = { it.messageId }) { message ->
                MessageBubble(message)
            }
        }
    }

    editingImageUri?.let { uri ->
        val quality by viewModel.imageJpegQuality.collectAsState()
        ImageEditorDialog(
            uri = uri,
            jpegQuality = quality,
            onCancel = { editingImageUri = null },
            onConfirm = { bytes ->
                viewModel.sendImageBytes(bytes)
                editingImageUri = null
            },
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val bubbleColor = if (message.fromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.fromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            when (message.type) {
                MessageType.TEXT -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(message.text.orEmpty(), color = textColor)
                }
                MessageType.IMAGE -> ImageBubble(message)
                MessageType.VIDEO -> VideoBubble(message)
                MessageType.VOICE -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bubbleColor),
                ) {
                    VoiceBubble(message, onSurface = textColor, accent = textColor)
                }
                MessageType.FILE -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Soubor", color = textColor)
                }
            }
        }
        if (message.fromMe) {
            Text(
                text = deliveryStateLabel(message.deliveryState),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun deliveryStateLabel(state: cz.kuclab.hertzchat.data.db.DeliveryState): String = when (state) {
    cz.kuclab.hertzchat.data.db.DeliveryState.PENDING -> "Čeká se, až bude příjemce online..."
    cz.kuclab.hertzchat.data.db.DeliveryState.SENDING -> "Odesílá se..."
    cz.kuclab.hertzchat.data.db.DeliveryState.SENT -> "Odesláno"
    cz.kuclab.hertzchat.data.db.DeliveryState.DELIVERED -> "Doručeno"
    cz.kuclab.hertzchat.data.db.DeliveryState.READ -> "Přečteno"
    cz.kuclab.hertzchat.data.db.DeliveryState.FAILED -> "Nepodařilo se odeslat"
}
