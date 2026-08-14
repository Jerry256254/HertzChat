package cz.kuclab.hertzchat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.media.VoiceRecorder
import cz.kuclab.hertzchat.ui.common.AttachmentMenu
import cz.kuclab.hertzchat.ui.common.ChatInputAccentButton
import cz.kuclab.hertzchat.ui.common.ChatInputBar
import cz.kuclab.hertzchat.ui.common.ChatInputPillIcon
import cz.kuclab.hertzchat.ui.common.MarkdownText
import cz.kuclab.hertzchat.ui.theme.HertzGreen
import java.io.File

private val BubbleShapeMine = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
private val BubbleShapeTheirs = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)

@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val nickname by viewModel.contactNickname.collectAsState()
    val avatarPath by viewModel.contactAvatarPath.collectAsState()
    val context = LocalContext.current

    var attachMenuOpen by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    val voiceRecorder = remember { VoiceRecorder(context) }

    var editingImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        editingImageUri = uri
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::sendVideo)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::sendFile)
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { detailsOpen = true },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarPath != null) {
                                AsyncImage(
                                    model = File(avatarPath!!),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().size(38.dp).clip(CircleShape),
                                )
                            } else {
                                Text(nickname.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Text(
                            if (viewModel.isSelf) "$nickname (Ty)" else nickname,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                value = draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = "Zpráva",
                leading = {
                    Box {
                        ChatInputPillIcon(
                            onClick = { attachMenuOpen = true },
                            icon = Icons.Filled.AttachFile,
                            contentDescription = "Přiložit",
                        )
                        AttachmentMenu(
                            expanded = attachMenuOpen,
                            onDismissRequest = { attachMenuOpen = false },
                            onPickImage = { pickImage.launch("image/*") },
                            onPickVideo = { pickVideo.launch("video/*") },
                            onPickFile = { pickFile.launch("*/*") },
                        )
                    }
                },
                trailingButton = {
                    ChatInputAccentButton(
                        onClick = {
                            if (isRecording) {
                                isRecording = false
                                voiceRecorder.stop()?.let { (file, durationMs) ->
                                    if (durationMs > 400) viewModel.sendVoice(file, durationMs) else file.delete()
                                }
                            } else if (draft.isNotBlank()) {
                                viewModel.send()
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    isRecording = true
                                    voiceRecorder.start()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        icon = when {
                            isRecording -> Icons.Filled.Stop
                            draft.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Filled.Mic
                        },
                        contentDescription = if (isRecording) "Zastavit nahrávání" else if (draft.isNotBlank()) "Odeslat" else "Nahrát hlasovku",
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                },
            )
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

    if (detailsOpen) {
        ContactDetailsSheet(
            nickname = if (viewModel.isSelf) "$nickname (Ty)" else nickname,
            avatarPath = avatarPath,
            hertzId = viewModel.contactId,
            isSelf = viewModel.isSelf,
            onDismiss = { detailsOpen = false },
            onBlock = {
                detailsOpen = false
                viewModel.blockContact()
                onBack()
            },
        )
    }
}

@Composable
private fun ContactDetailsSheet(
    nickname: String,
    avatarPath: String?,
    hertzId: String,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarPath != null) {
                    AsyncImage(
                        model = File(avatarPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().size(80.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        nickname.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            Text(
                nickname,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                hertzId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(hertzId)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Zkopírovat Hertz ID")
            }
            if (!isSelf) {
                OutlinedButton(
                    onClick = onBlock,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Blokovat kontakt")
                }
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isAssistant = message.fromAssistant
    val bubbleColor = when {
        isAssistant -> MaterialTheme.colorScheme.tertiaryContainer
        message.fromMe -> HertzGreen
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isAssistant -> MaterialTheme.colorScheme.onTertiaryContainer
        message.fromMe -> androidx.compose.ui.graphics.Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignedRight = message.fromMe && !isAssistant
    val alignment = if (alignedRight) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (alignedRight) BubbleShapeMine else BubbleShapeTheirs

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (alignedRight) Alignment.End else Alignment.Start) {
        if (isAssistant) {
            Text(
                "Mistral AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            when (message.type) {
                MessageType.TEXT -> Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    MarkdownText(message.text.orEmpty(), color = textColor)
                }
                MessageType.IMAGE -> ImageBubble(message)
                MessageType.VIDEO -> VideoBubble(message)
                MessageType.VOICE -> Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor),
                ) {
                    VoiceBubble(message, onSurface = textColor, accent = textColor)
                }
                MessageType.FILE -> Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor),
                ) {
                    FileBubble(message, onSurface = textColor)
                }
            }
        }
        if (message.fromMe) {
            Text(
                text = deliveryStateLabel(message.deliveryState),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
