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
import cz.kuclab.hertzchat.ui.common.GlassAmbientBackground
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.GlassDialogTheme
import cz.kuclab.hertzchat.ui.common.GlassSurface
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.WindowBlurBehind
import cz.kuclab.hertzchat.ui.common.MarkdownText
import cz.kuclab.hertzchat.ui.common.ThreadInputBar
import cz.kuclab.hertzchat.ui.common.highlightQuery
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
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
    val hazeState = remember { HazeState() }
    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Single idempotent stop path: release, auto-stop and gesture-cancel all land
    // here, so a stuck or over-long recording can never wedge the UI.
    val endRecording = {
        if (isRecording) {
            isRecording = false
            val clip = voiceRecorder.stop()
            if (clip != null && clip.second > 400) {
                pendingVoice = clip
            } else {
                clip?.first?.delete()
            }
        }
    }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            recordElapsed = System.currentTimeMillis() - recordStartedAt
            if (recordElapsed > 120_000) {
                endRecording()
            } else {
                delay(250)
            }
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

    val displayItems = remember(messages) { messages.asReversed() }

    LaunchedEffect(matchIds, matchPos) {
        val id = matchIds.getOrNull(matchPos) ?: return@LaunchedEffect
        val displayIndex = displayItems.indexOfFirst { it.messageId == id }
        if (displayIndex != -1) listState.animateScrollToItem(displayIndex)
    }

    // Stick to the bottom while new messages land - sent or received - but only
    // when already there: index 0/1 means the newest message is on screen (a fresh
    // insert shifts the previous bottom to 1), anything further up stays put.
    LaunchedEffect(messages.size) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
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
        floatingActionButton = {
            if (showScrollDown && !searchOpen) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sjet dolů")
                }
            }
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
                            onPressEnd = { endRecording() },
                        )
                    },
                )
            }
        },
    ) { padding ->
        val threadMedia = remember(messages) {
            messages.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Flat backdrop plus the thread; the floating bars above blur this
            // content behind themselves, so it scrolls underneath the frost.
            Box(modifier = Modifier.fillMaxSize().haze(hazeState, HertzGlass.hazeStyle())) {
                GlassAmbientBackground()
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 92.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                items(displayItems, key = { it.messageId }) { message ->
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
            }
            if (searchOpen) {
                ChatSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    matchIndex = matchPos,
                    matchCount = matchIds.size,
                    onPrev = { if (matchIds.isNotEmpty()) matchPos = (matchPos - 1 + matchIds.size) % matchIds.size },
                    onNext = { if (matchIds.isNotEmpty()) matchPos = (matchPos + 1) % matchIds.size },
                    onClose = { searchOpen = false; searchQuery = "" },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                    hazeState = hazeState,
                )
            } else {
                FloatingGroupBar(
                    groupName = groupName,
                    memberCount = members.size + 1,
                    onBack = onBack,
                    onMembers = { membersDialogOpen = true },
                    overflowOpen = menuOpen,
                    onOverflowChange = { menuOpen = it },
                    onSearch = { searchOpen = true },
                    onClear = { confirmClear = true },
                    onLeave = { confirmLeave = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                    hazeState = hazeState,
                )
            }
        }
    }

    if (membersDialogOpen) {
        GlassDialogTheme {
        AlertDialog(
            onDismissRequest = { membersDialogOpen = false },
            title = { WindowBlurBehind(); Text("Členové skupiny") },
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
    }

    if (addMembersOpen) {
        var selected by remember { mutableStateOf(setOf<String>()) }
        GlassDialogTheme {
        AlertDialog(
            onDismissRequest = { addMembersOpen = false },
            title = { WindowBlurBehind(); Text("Přidat člena") },
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
        GlassDialogTheme {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { WindowBlurBehind(); Text("Vyčistit konverzaci?") },
            text = { Text("Smaže se celá historie zpráv v této skupině na tomto zařízení. Členství ve skupině zůstane.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearChat() }) { Text("Vyčistit") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Zrušit") } },
        )
        }
    }

    if (confirmLeave) {
        GlassDialogTheme {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { WindowBlurBehind(); Text("Opustit skupinu?") },
            text = { Text("Místní historie zpráv této skupiny se smaže. Ostatní členové o tom nebudou automaticky informováni.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leaveGroup(); onLeft() }) { Text("Opustit") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Zrušit") } },
        )
        }
    }
}

@Composable
private fun FloatingGroupBar(
    groupName: String,
    memberCount: Int,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    overflowOpen: Boolean,
    onOverflowChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
) {
    // No statusBarsPadding: the Scaffold content padding already offsets for the
    // status bar, and adding it again is what used to push the bar too low.
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Zpět",
            onClick = onBack,
            hazeState = hazeState,
        )
        GlassSurface(
            shape = HertzShapes.Pill,
            onClick = onMembers,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            hazeState = hazeState,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        groupName.ifBlank { "Skupina" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = HertzGlass.contentOnGlass(),
                        maxLines = 1,
                    )
                    Text(
                        "$memberCount členů",
                        style = MaterialTheme.typography.labelSmall,
                        color = HertzGlass.contentOnGlass().copy(alpha = 0.7f),
                    )
                }
            }
        }
        Box {
            GlassCircleButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Možnosti",
                onClick = { onOverflowChange(true) },
                hazeState = hazeState,
            )
            ActionMenu(expanded = overflowOpen, onDismissRequest = { onOverflowChange(false) }) {
                ActionMenuItem(
                    text = "Členové skupiny",
                    icon = Icons.Filled.Groups,
                    onClick = { onOverflowChange(false); onMembers() },
                )
                ActionMenuItem(
                    text = "Hledat v konverzaci",
                    icon = Icons.Filled.Search,
                    onClick = { onOverflowChange(false); onSearch() },
                )
                ActionMenuItem(
                    text = "Vyčistit konverzaci",
                    icon = Icons.Filled.DeleteSweep,
                    destructive = true,
                    onClick = { onOverflowChange(false); onClear() },
                )
                ActionMenuItem(
                    text = "Opustit skupinu",
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    destructive = true,
                    onClick = { onOverflowChange(false); onLeave() },
                )
            }
        }
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
    val bubbleFill = when {
        isCurrentMatch -> MaterialTheme.colorScheme.primaryContainer
        message.fromMe -> HertzGlass.bubbleMine()
        else -> HertzGlass.bubbleTheirs()
    }
    val textColor = when {
        isCurrentMatch -> MaterialTheme.colorScheme.onPrimaryContainer
        message.fromMe -> androidx.compose.ui.graphics.Color.White
        else -> HertzGlass.contentOnGlass()
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
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    val bubbleShape = if (message.fromMe) HertzShapes.BubbleMine else HertzShapes.BubbleTheirs
                    val gallery = threadMedia.ifEmpty { listOf(message) }
                    val galleryIndex = threadMedia.indexOfFirst { it.messageId == message.messageId }.coerceAtLeast(0)
                    // Pure content bubbles: no clock, no ticks, no day chips - the
                    // message itself is the whole UI. Glass edges carry the shape.
                    when (message.type) {
                        MessageType.IMAGE -> ImageBubble(message = message, threadMedia = gallery, mediaIndex = galleryIndex, onDownload = onDownload)
                        MessageType.VIDEO -> VideoBubble(message = message, threadMedia = gallery, mediaIndex = galleryIndex, onDownload = onDownload)
                        MessageType.VOICE -> GlassSurface(shape = bubbleShape, fill = bubbleFill, shadowElevation = 0.dp) {
                            VoiceBubble(message, onSurface = textColor, accent = textColor, onDownload = onDownload)
                        }
                        MessageType.FILE -> GlassSurface(shape = bubbleShape, fill = bubbleFill, shadowElevation = 0.dp) {
                            FileBubble(message, onSurface = textColor, onOpenFile = onOpenFile, onDownload = onDownload)
                        }
                        else -> GlassSurface(shape = bubbleShape, fill = bubbleFill, shadowElevation = 0.dp) {
                            val pad = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            if (searchQuery.isBlank()) {
                                MarkdownText(message.text.orEmpty(), color = textColor, modifier = pad)
                            } else {
                                Text(highlightQuery(message.text.orEmpty(), searchQuery), color = textColor, modifier = pad)
                            }
                        }
                    }
                }
            }
        }
    }
}
