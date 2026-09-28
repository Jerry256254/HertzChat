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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import cz.kuclab.hertzchat.ui.common.GlassAmbientBackground
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.GlassDialogTheme
import cz.kuclab.hertzchat.ui.common.GlassSurface
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.TopBarScrim
import cz.kuclab.hertzchat.ui.common.MarkdownText
import cz.kuclab.hertzchat.ui.common.ThreadInputBar
import cz.kuclab.hertzchat.ui.common.highlightQuery
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

    val displayItems = remember(state.messages) { state.messages.asReversed() }

    LaunchedEffect(matchIds, matchPos) {
        val id = matchIds.getOrNull(matchPos) ?: return@LaunchedEffect
        val displayIndex = displayItems.indexOfFirst { it.messageId == id }
        if (displayIndex != -1) listState.animateScrollToItem(displayIndex)
    }

    // Stick to the bottom while new messages land - sent or received - but only
    // when already there: index 0/1 means the newest message is on screen (a fresh
    // insert shifts the previous bottom to 1), anything further up stays put.
    LaunchedEffect(state.messages.size) {
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
                        onPressEnd = { endRecording() },
                    )
                },
            )
        },
    ) { padding ->
        val threadMedia = remember(state.messages) {
            state.messages.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Ambient glass backdrop; the floating chrome above is translucent,
            // so content scrolls underneath it.
            Box(modifier = Modifier.fillMaxSize()) {
                GlassAmbientBackground()
                // Reversed so the thread opens at the newest message and sticks there -
                // index 0 is always the bottom, which is also what the scroll-down
                // button and search jumps animate to.
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 92.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                items(displayItems, key = { it.messageId }) { message ->
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
            }
            TopBarScrim(modifier = Modifier.align(Alignment.TopCenter))
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
                )
            } else {
                FloatingChatBar(
                    nickname = if (viewModel.isSelf) "$nickname (Ty)" else nickname,
                    avatarPath = avatarPath,
                    onBack = onBack,
                    onOpenDetails = { detailsOpen = true },
                    overflowOpen = overflowOpen,
                    onOverflowChange = { overflowOpen = it },
                    onSearch = { searchOpen = true },
                    onClear = { confirmClear = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
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
        GlassDialogTheme {
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
private fun FloatingChatBar(
    nickname: String,
    avatarPath: String?,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    overflowOpen: Boolean,
    onOverflowChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
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
        )
        GlassSurface(
            shape = HertzShapes.Pill,
            onClick = onOpenDetails,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarPath != null) {
                        AsyncImage(
                            model = File(avatarPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                        )
                    } else {
                        Text(
                            nickname.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    nickname,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HertzGlass.contentOnGlass(),
                    maxLines = 1,
                )
            }
        }
        Box {
            GlassCircleButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Možnosti konverzace",
                onClick = { onOverflowChange(true) },
            )
            ActionMenu(expanded = overflowOpen, onDismissRequest = { onOverflowChange(false) }) {
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
            }
        }
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
    val alignedRight = message.fromMe
    val alignment = if (alignedRight) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (alignedRight) HertzShapes.BubbleMine else HertzShapes.BubbleTheirs

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (alignedRight) Alignment.End else Alignment.Start) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            // Pure content bubbles: no clock, no ticks, no day chips - the message
            // itself is the whole UI. Glass edges carry the shape instead.
            when (message.type) {
                MessageType.TEXT -> GlassSurface(
                    shape = bubbleShape,
                    fill = bubbleFill,
                    shadowElevation = 0.dp,
                ) {
                    val pad = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    if (searchQuery.isBlank()) {
                        MarkdownText(message.text.orEmpty(), color = textColor, modifier = pad)
                    } else {
                        Text(highlightQuery(message.text.orEmpty(), searchQuery), color = textColor, modifier = pad)
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
                MessageType.VOICE -> GlassSurface(
                    shape = bubbleShape,
                    fill = bubbleFill,
                    shadowElevation = 0.dp,
                ) {
                    VoiceBubble(message, onSurface = textColor, accent = textColor, onDownload = onDownload)
                }
                MessageType.FILE -> GlassSurface(
                    shape = bubbleShape,
                    fill = bubbleFill,
                    shadowElevation = 0.dp,
                ) {
                    FileBubble(message, onSurface = textColor, onOpenFile = onOpenFile, onDownload = onDownload)
                }
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
