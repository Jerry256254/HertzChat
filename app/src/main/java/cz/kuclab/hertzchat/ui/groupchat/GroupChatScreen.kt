package cz.kuclab.hertzchat.ui.groupchat

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.media.VoiceRecorder
import cz.kuclab.hertzchat.ui.chat.FileBubble
import cz.kuclab.hertzchat.ui.chat.ImageBubble
import cz.kuclab.hertzchat.ui.chat.PhotoEditorDialog
import cz.kuclab.hertzchat.ui.chat.PhotoSource
import cz.kuclab.hertzchat.ui.chat.VideoBubble
import cz.kuclab.hertzchat.ui.chat.VoiceBubble
import cz.kuclab.hertzchat.ui.common.ActionMenu
import cz.kuclab.hertzchat.ui.common.ActionMenuItem
import cz.kuclab.hertzchat.ui.common.AttachmentMenu
import cz.kuclab.hertzchat.ui.common.ChatInputPillIcon
import cz.kuclab.hertzchat.ui.common.ChatSearchBar
import cz.kuclab.hertzchat.ui.common.HoldToRecordButton
import cz.kuclab.hertzchat.ui.common.MarkdownText
import cz.kuclab.hertzchat.ui.common.ThreadInputBar
import cz.kuclab.hertzchat.ui.common.highlightQuery
import cz.kuclab.hertzchat.ui.theme.HertzMatte
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit, onLeft: () -> Unit, onOpenFile: (String) -> Unit, viewModel: GroupChatViewModel = hiltViewModel()) {
    val groupName by viewModel.groupName.collectAsState()
    val members by viewModel.members.collectAsState()
    val membersUi by viewModel.membersUi.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    val addableContacts by viewModel.addableContacts.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val mentionQuery by viewModel.mentionQuery.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var membersDialogOpen by remember { mutableStateOf(false) }
    var addMembersOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var matchPos by remember { mutableStateOf(0) }
    var attachMenuOpen by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var recordStartedAt by remember { mutableLongStateOf(0L) }
    var recordElapsed by remember { mutableLongStateOf(0L) }
    var pendingVoice by remember { mutableStateOf<Pair<File, Long>?>(null) }

    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    val listState = rememberLazyListState()
    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isRecording) {
        while (isRecording) {
            recordElapsed = System.currentTimeMillis() - recordStartedAt
            delay(250)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userNotice.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(searchQuery, searchOpen) {
        if (!searchOpen || searchQuery.isBlank()) {
            matchIds = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        matchIds = viewModel.searchInChat(searchQuery).map { it.messageId }
        matchPos = 0
    }

    LaunchedEffect(matchIds, matchPos) {
        val id = matchIds.getOrNull(matchPos) ?: return@LaunchedEffect
        val ascendingIndex = messages.indexOfFirst { it.messageId == id }
        if (ascendingIndex != -1) listState.animateScrollToItem(messages.size - 1 - ascendingIndex)
    }

    var editingImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraOutputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraOutputUri?.let { editingImageUri = it }
        } else {
            cameraOutputUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
        }
        cameraOutputUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        editingImageUri = uri
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.stageAttachmentUri(it, PayloadKind.VIDEO) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.stageAttachmentUri(it, PayloadKind.FILE) }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbar.showSnackbar("Hlasové zprávy potřebují oprávnění k mikrofonu.") }
    }

    val nicknamesById = remember(members) { members.associate { it.contactId to it.nickname } }
    val avatarsById = remember(membersUi) { membersUi.associate { it.contactId to it.avatarPath } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = HertzMatte.topBarColors(),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(groupName.ifBlank { "Skupina" })
                        Text("${members.size + 1} členů", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } },
                actions = {
                    IconButton(onClick = { membersDialogOpen = true }) { Icon(Icons.Filled.Groups, contentDescription = "Členové") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Možnosti") }
                        ActionMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ActionMenuItem(
                                text = "Hledat v konverzaci",
                                icon = Icons.Filled.Search,
                                onClick = { menuOpen = false; searchOpen = true },
                            )
                            ActionMenuItem(
                                text = "Vyčistit konverzaci",
                                icon = Icons.Filled.DeleteSweep,
                                destructive = true,
                                onClick = { menuOpen = false; confirmClear = true },
                            )
                            ActionMenuItem(
                                text = "Opustit skupinu",
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                destructive = true,
                                onClick = { menuOpen = false; confirmLeave = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (mentionQuery != null && !isRecording && pendingVoice == null) {
                    val suggestions = viewModel.mentionSuggestions()
                    if (suggestions.isNotEmpty()) {
                        androidx.compose.material3.Surface(
                            shape = HertzShapes.Card,
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
                ThreadInputBar(
                    draft = draft,
                    onDraftChange = viewModel::onDraftChange,
                    placeholder = "Zpráva, nebo @jméno",
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
                                onTakePhoto = {
                                    val uri = cz.kuclab.hertzchat.media.newCameraPhotoUri(context)
                                    cameraOutputUri = uri
                                    takePhoto.launch(uri)
                                },
                            )
                        }
                    },
                    attachments = pending,
                    onRemoveAttachment = viewModel::removePending,
                    isRecording = isRecording,
                    recordElapsedMs = recordElapsed,
                    pendingVoice = pendingVoice,
                    onDeleteVoice = {
                        pendingVoice?.first?.delete()
                        pendingVoice = null
                    },
                    onSend = {
                        pendingVoice?.let { (file, duration) ->
                            viewModel.sendVoice(file, duration)
                            pendingVoice = null
                        } ?: viewModel.send()
                    },
                    micButton = {
                        HoldToRecordButton(
                            onPressStart = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@HoldToRecordButton false
                                }
                                val started = runCatching { voiceRecorder.start() }.isSuccess
                                if (started) {
                                    recordStartedAt = System.currentTimeMillis()
                                    recordElapsed = 0
                                    isRecording = true
                                } else {
                                    scope.launch { snackbar.showSnackbar("Nahrávání se nezdařilo.") }
                                }
                                started
                            },
                            onPressEnd = {
                                isRecording = false
                                val clip = voiceRecorder.stop()
                                if (clip != null && clip.second > 400) {
                                    pendingVoice = clip
                                } else {
                                    clip?.first?.delete()
                                }
                            },
                        )
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            if (searchOpen) {
                ChatSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    matchIndex = matchPos,
                    matchCount = matchIds.size,
                    onPrev = { if (matchIds.isNotEmpty()) matchPos = (matchPos - 1 + matchIds.size) % matchIds.size },
                    onNext = { if (matchIds.isNotEmpty()) matchPos = (matchPos + 1) % matchIds.size },
                    onClose = { searchOpen = false; searchQuery = "" },
                )
            }
            val threadMedia = remember(messages) {
                messages.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(messages.reversed(), key = { it.messageId }) { message ->
                        GroupMessageBubble(
                            message,
                            senderNickname = message.senderContactId?.let { nicknamesById[it] },
                            senderAvatarPath = message.senderContactId?.let { avatarsById[it] },
                            searchQuery = if (searchOpen) searchQuery else "",
                            isCurrentMatch = searchOpen && matchIds.getOrNull(matchPos) == message.messageId,
                            threadMedia = threadMedia,
                            onDownload = viewModel::downloadMessage,
                            onOpenFile = { onOpenFile(it.messageId) },
                        )
                    }
                }
                if (showScrollDown && !searchOpen) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sjet dolů")
                    }
                }
            }
        }
    }

    if (membersDialogOpen) {
        AlertDialog(
            onDismissRequest = { membersDialogOpen = false },
            title = { Text("Členové skupiny") },
            text = {
                Column {
                    membersUi.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (member.avatarPath != null) {
                                    AsyncImage(
                                        model = File(member.avatarPath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    )
                                } else {
                                    Text(member.nickname.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Text(
                                if (member.isSelf) "Já" else member.nickname,
                                modifier = Modifier.padding(start = 10.dp).weight(1f),
                            )
                            if (isOwner && !member.isSelf) {
                                IconButton(onClick = { viewModel.removeMember(member.contactId) }) {
                                    Icon(Icons.Filled.PersonRemove, contentDescription = "Odebrat ${member.nickname}", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    if (isOwner) {
                        TextButton(onClick = { addMembersOpen = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Přidat člena")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { membersDialogOpen = false }) { Text("Zavřít") } },
        )
    }

    if (addMembersOpen) {
        var selected by remember { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { addMembersOpen = false },
            title = { Text("Přidat člena") },
            text = {
                if (addableContacts.isEmpty()) {
                    Text("Všechny tvoje kontakty jsou už ve skupině.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column {
                        Text(
                            "Přidat lze jen vzájemné kontakty",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        addableContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = if (contact.contactId in selected) selected - contact.contactId else selected + contact.contactId }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = contact.contactId in selected, onCheckedChange = null)
                                Text(contact.nickname, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = { viewModel.addMembers(selected.toList()); addMembersOpen = false },
                ) { Text("Přidat") }
            },
            dismissButton = { TextButton(onClick = { addMembersOpen = false }) { Text("Zrušit") } },
        )
    }

    editingImageUri?.let { uri ->
        PhotoEditorDialog(
            source = PhotoSource.UriSource(uri),
            jpegQuality = 85,
            onCancel = { editingImageUri = null },
            onConfirm = { bytes ->
                viewModel.stageAttachment(bytes, "image/jpeg", PayloadKind.IMAGE, fileName = null)
                editingImageUri = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Vyčistit konverzaci?") },
            text = { Text("Smaže se celá historie zpráv v této skupině na tomto zařízení. Členství ve skupině zůstane.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearChat() }) { Text("Vyčistit") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Zrušit") } },
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
private fun GroupMessageBubble(
    message: MessageEntity,
    senderNickname: String?,
    senderAvatarPath: String?,
    searchQuery: String = "",
    isCurrentMatch: Boolean = false,
    threadMedia: List<MessageEntity> = emptyList(),
    onDownload: (MessageEntity) -> Unit = {},
    onOpenFile: (MessageEntity) -> Unit = {},
) {
    val bubbleColor = when {
        isCurrentMatch -> MaterialTheme.colorScheme.primaryContainer
        message.fromMe -> MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        else -> HertzMatte.bubbleTheirs()
    }
    val textColor = when {
        isCurrentMatch -> MaterialTheme.colorScheme.onPrimaryContainer
        message.fromMe -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (!message.fromMe) {
                    Box(
                        modifier = Modifier.padding(end = 4.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (senderAvatarPath != null) {
                            AsyncImage(
                                model = File(senderAvatarPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Text(
                                senderNickname.orEmpty().take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Column {
                    val label = if (!message.fromMe) senderNickname else null
                    label?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    }
                    val bubbleShape = if (message.fromMe) HertzShapes.BubbleMine else HertzShapes.BubbleTheirs
                    val gallery = threadMedia.ifEmpty { listOf(message) }
                    val galleryIndex = threadMedia.indexOfFirst { it.messageId == message.messageId }.coerceAtLeast(0)
                    when (message.type) {
                        MessageType.IMAGE -> ImageBubble(message = message, threadMedia = gallery, mediaIndex = galleryIndex, onDownload = onDownload)
                        MessageType.VIDEO -> VideoBubble(message = message, threadMedia = gallery, mediaIndex = galleryIndex, onDownload = onDownload)
                        MessageType.VOICE -> Box(
                            modifier = Modifier.clip(bubbleShape).background(bubbleColor),
                        ) {
                            VoiceBubble(message, onSurface = textColor, accent = textColor, onDownload = onDownload)
                        }
                        MessageType.FILE -> Box(
                            modifier = Modifier.clip(bubbleShape).background(bubbleColor),
                        ) {
                            FileBubble(message, onSurface = textColor, onOpenFile = onOpenFile, onDownload = onDownload)
                        }
                        else -> Box(
                            modifier = Modifier
                                .clip(bubbleShape)
                                .background(bubbleColor)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            if (searchQuery.isBlank()) {
                                MarkdownText(message.text.orEmpty(), color = textColor)
                            } else {
                                Text(highlightQuery(message.text.orEmpty(), searchQuery), color = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
