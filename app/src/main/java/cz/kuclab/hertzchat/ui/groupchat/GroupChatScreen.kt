package cz.kuclab.hertzchat.ui.groupchat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.media.VoiceRecorder
import cz.kuclab.hertzchat.ui.chat.FileBubble
import cz.kuclab.hertzchat.ui.chat.ImageBubble
import cz.kuclab.hertzchat.ui.chat.ImageEditorDialog
import cz.kuclab.hertzchat.ui.chat.VideoBubble
import cz.kuclab.hertzchat.ui.chat.VoiceBubble
import cz.kuclab.hertzchat.ui.common.ActionMenu
import cz.kuclab.hertzchat.ui.common.ActionMenuItem
import cz.kuclab.hertzchat.ui.common.AttachmentMenu
import cz.kuclab.hertzchat.ui.common.ChatInputAccentButton
import cz.kuclab.hertzchat.ui.common.ChatInputBar
import cz.kuclab.hertzchat.ui.common.ChatInputPillIcon
import cz.kuclab.hertzchat.ui.common.MarkdownText

@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit, onLeft: () -> Unit, viewModel: GroupChatViewModel = hiltViewModel()) {
    val groupName by viewModel.groupName.collectAsState()
    val members by viewModel.members.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val mentionQuery by viewModel.mentionQuery.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var membersDialogOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    val context = LocalContext.current
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

    val nicknamesById = remember(members) { members.associate { it.contactId to it.nickname } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(groupName.ifBlank { "Skupina" })
                        Text("${members.size + 1} členů", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } },
                actions = {
                    IconButton(onClick = { membersDialogOpen = true }) { Icon(Icons.Filled.Groups, contentDescription = "Členové") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Možnosti") }
                    ActionMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        ActionMenuItem(
                            text = "Opustit skupinu",
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            destructive = true,
                            onClick = { menuOpen = false; confirmLeave = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (mentionQuery != null) {
                    val suggestions = viewModel.mentionSuggestions()
                    if (suggestions.isNotEmpty()) {
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            Column {
                                suggestions.forEach { suggestion ->
                                    Text(
                                        "@" + suggestion.label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectMention(suggestion) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                ChatInputBar(
                    value = draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = "Zpráva, nebo @jméno / @Mistral N dotaz",
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
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.messageId }) { message ->
                GroupMessageBubble(message, senderNickname = message.senderContactId?.let { nicknamesById[it] })
            }
        }
    }

    if (membersDialogOpen) {
        AlertDialog(
            onDismissRequest = { membersDialogOpen = false },
            title = { Text("Členové skupiny") },
            text = {
                Column {
                    Text("Já", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                    members.forEach { Text(it.nickname, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { membersDialogOpen = false }) { Text("Zavřít") } },
        )
    }

    editingImageUri?.let { uri ->
        ImageEditorDialog(
            uri = uri,
            jpegQuality = 85,
            onCancel = { editingImageUri = null },
            onConfirm = { bytes ->
                viewModel.sendImageBytes(bytes)
                editingImageUri = null
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Opustit skupinu?") },
            text = { Text("Místní historie zpráv této skupiny se smaže. Ostatní členové o tom nebudou automaticky informováni.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leaveGroup(); onLeft() }) { Text("Opustit") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Zrušit") } },
        )
    }
}

@Composable
private fun GroupMessageBubble(message: MessageEntity, senderNickname: String?) {
    val isAssistant = message.fromAssistant
    val bubbleColor = when {
        isAssistant -> MaterialTheme.colorScheme.tertiaryContainer
        message.fromMe -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isAssistant -> MaterialTheme.colorScheme.onTertiaryContainer
        message.fromMe -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (message.fromMe && !isAssistant) Alignment.CenterEnd else Alignment.CenterStart

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromMe && !isAssistant) Alignment.End else Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (isAssistant) {
                    Image(
                        painter = painterResource(R.drawable.mistral_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(20.dp).clip(CircleShape).padding(end = 4.dp),
                    )
                }
                Column {
                    val label = if (isAssistant) "Mistral AI" else if (!message.fromMe) senderNickname else null
                    label?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    }
                    when (message.type) {
                        MessageType.IMAGE -> ImageBubble(message)
                        MessageType.VIDEO -> VideoBubble(message)
                        MessageType.VOICE -> Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(bubbleColor),
                        ) {
                            VoiceBubble(message, onSurface = textColor, accent = textColor)
                        }
                        MessageType.FILE -> Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(bubbleColor),
                        ) {
                            FileBubble(message, onSurface = textColor)
                        }
                        else -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bubbleColor)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            MarkdownText(message.text.orEmpty(), color = textColor)
                        }
                    }
                }
            }
        }
    }
}
