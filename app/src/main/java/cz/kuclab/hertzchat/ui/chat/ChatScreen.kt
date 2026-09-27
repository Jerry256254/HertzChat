package cz.kuclab.hertzchat.ui.chat

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
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
import cz.kuclab.hertzchat.data.db.DeliveryState
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.media.VoiceRecorder
import cz.kuclab.hertzchat.ui.common.ActionMenu
import cz.kuclab.hertzchat.ui.common.ActionMenuItem
import cz.kuclab.hertzchat.ui.common.AttachmentMenu
import cz.kuclab.hertzchat.ui.common.ChatInputPillIcon
import cz.kuclab.hertzchat.ui.common.ChatSearchBar
import cz.kuclab.hertzchat.ui.common.HoldToRecordButton
import cz.kuclab.hertzchat.ui.common.MarkdownText
import cz.kuclab.hertzchat.ui.common.ThreadInputBar
import cz.kuclab.hertzchat.ui.common.highlightQuery
import cz.kuclab.hertzchat.ui.theme.HertzGreen
import cz.kuclab.hertzchat.ui.theme.HertzMatte
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit, onOpenFile: (String) -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val nickname by viewModel.contactNickname.collectAsState()
    val avatarPath by viewModel.contactAvatarPath.collectAsState()
    val contactQrText by viewModel.contactQrText.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val context = LocalContext.current

    var attachMenuOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var matchPos by remember { mutableStateOf(0) }

    var isRecording by remember { mutableStateOf(false) }
    var recordStartedAt by remember { mutableLongStateOf(0L) }
    var recordElapsed by remember { mutableLongStateOf(0L) }
    var pendingVoice by remember { mutableStateOf<Pair<File, Long>?>(null) }
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
        val ascendingIndex = state.messages.indexOfFirst { it.messageId == id }
        if (ascendingIndex != -1) listState.animateScrollToItem(state.messages.size - 1 - ascendingIndex)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = HertzMatte.topBarColors(),
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
                actions = {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Možnosti konverzace")
                        }
                        ActionMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            ActionMenuItem(
                                text = "Hledat v konverzaci",
                                icon = Icons.Filled.Search,
                                onClick = { overflowOpen = false; searchOpen = true },
                            )
                            ActionMenuItem(
                                text = "Vyčistit konverzaci",
                                icon = Icons.Filled.DeleteSweep,
                                destructive = true,
                                onClick = { overflowOpen = false; confirmClear = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ThreadInputBar(
                draft = draft,
                onDraftChange = viewModel::onDraftChange,
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
            val threadMedia = remember(state.messages) {
                state.messages.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
            }
            Box(modifier = Modifier.weight(1f)) {
                // Reversed so the thread opens at the newest message and sticks there -
                // index 0 is always the bottom, which is also what the scroll-down
                // button and search jumps animate to.
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.messages.reversed(), key = { it.messageId }) { message ->
                        MessageBubble(
                            message = message,
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

    editingImageUri?.let { uri ->
        val quality by viewModel.imageJpegQuality.collectAsState()
        PhotoEditorDialog(
            source = PhotoSource.UriSource(uri),
            jpegQuality = quality,
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
            text = { Text("Smaže se celá historie zpráv v tomto chatu na tomto zařízení. Kontakt zůstane - jen jeho zprávy zmizí.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearChat() }) { Text("Vyčistit") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Zrušit") }
            },
        )
    }

    if (detailsOpen) {
        ContactDetailsSheet(
            nickname = if (viewModel.isSelf) "$nickname (Ty)" else nickname,
            avatarPath = avatarPath,
            hertzId = viewModel.contactId,
            qrText = contactQrText,
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
private fun MessageBubble(
    message: MessageEntity,
    searchQuery: String = "",
    isCurrentMatch: Boolean = false,
    threadMedia: List<MessageEntity> = emptyList(),
    onDownload: (MessageEntity) -> Unit = {},
    onOpenFile: (MessageEntity) -> Unit = {},
) {
    val bubbleColor = when {
        isCurrentMatch -> MaterialTheme.colorScheme.primaryContainer
        message.fromMe -> HertzGreen.copy(alpha = 0.92f)
        else -> HertzMatte.bubbleTheirs()
    }
    val textColor = when {
        isCurrentMatch -> MaterialTheme.colorScheme.onPrimaryContainer
        message.fromMe -> androidx.compose.ui.graphics.Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignedRight = message.fromMe
    val alignment = if (alignedRight) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (alignedRight) HertzShapes.BubbleMine else HertzShapes.BubbleTheirs

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (alignedRight) Alignment.End else Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            when (message.type) {
                MessageType.TEXT -> Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (searchQuery.isBlank()) {
                        MarkdownText(message.text.orEmpty(), color = textColor)
                    } else {
                        Text(highlightQuery(message.text.orEmpty(), searchQuery), color = textColor)
                    }
                }
                MessageType.IMAGE -> ImageBubble(
                    message = message,
                    threadMedia = threadMedia.ifEmpty { listOf(message) },
                    mediaIndex = threadMedia.indexOfFirst { it.messageId == message.messageId }.coerceAtLeast(0),
                    onDownload = onDownload,
                )
                MessageType.VIDEO -> VideoBubble(
                    message = message,
                    threadMedia = threadMedia.ifEmpty { listOf(message) },
                    mediaIndex = threadMedia.indexOfFirst { it.messageId == message.messageId }.coerceAtLeast(0),
                    onDownload = onDownload,
                )
                MessageType.VOICE -> Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor),
                ) {
                    VoiceBubble(message, onSurface = textColor, accent = textColor, onDownload = onDownload)
                }
                MessageType.FILE -> Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleColor),
                ) {
                    FileBubble(message, onSurface = textColor, onOpenFile = onOpenFile, onDownload = onDownload)
                }
            }
        }
        // No delivery receipts under messages anymore: a small spinner until the peer
        // confirms receipt (SENT only means the bytes left this device), an error
        // mark if it failed for good.
        if (message.fromMe) {
            when (message.deliveryState) {
                DeliveryState.PENDING, DeliveryState.SENDING, DeliveryState.SENT -> CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).size(12.dp),
                    strokeWidth = 2.dp,
                )
                DeliveryState.FAILED -> Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = "Nepodařilo se odeslat",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).size(14.dp),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ContactDetailsSheet(
    nickname: String,
    avatarPath: String?,
    hertzId: String,
    qrText: String?,
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
            if (qrText != null) {
                val bitmap = remember(qrText) { cz.kuclab.hertzchat.ui.migration.generateQrBitmap(qrText) }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR kód kontaktu",
                    modifier = Modifier.size(180.dp).padding(top = 8.dp),
                )
                Text(
                    "Naskenuj pro přidání kontaktu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
