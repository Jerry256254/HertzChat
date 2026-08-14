package cz.kuclab.hertzchat.ui.mistral

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.data.db.AssistantConversationEntity
import cz.kuclab.hertzchat.data.db.AssistantMessageEntity
import cz.kuclab.hertzchat.data.db.AssistantRole
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.ChatInputAccentButton
import cz.kuclab.hertzchat.ui.common.ChatInputBar
import java.text.DateFormat
import java.util.Date

@Composable
fun AssistantChatScreen(onBack: () -> Unit, viewModel: AssistantChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val chatsSheetOpen by viewModel.chatsSheetOpen.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConversationId by viewModel.activeConversationId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MistralAvatar(size = 36.dp)
                        Text("Mistral AI", modifier = Modifier.padding(start = 12.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openChatsSheet) {
                        Icon(Icons.Filled.History, contentDescription = "Historie konverzací (/chats)")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                ChatInputBar(
                    value = draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = "Zpráva, nebo /new a /chats",
                    trailingButton = {
                        ChatInputAccentButton(
                            onClick = viewModel::send,
                            icon = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Odeslat",
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
                AssistantMessageBubble(message)
            }
        }
    }

    if (chatsSheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        var renamingConversation by remember { mutableStateOf<AssistantConversationEntity?>(null) }
        ModalBottomSheet(onDismissRequest = viewModel::closeChatsSheet, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    "Konverzace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                TextButton(onClick = viewModel::startNewConversation, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Nová konverzace (/new)")
                }
                if (conversations.isEmpty()) {
                    Text(
                        "Zatím žádné konverzace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        conversations.forEach { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                active = conversation.conversationId == activeConversationId,
                                onClick = { viewModel.switchConversation(conversation.conversationId) },
                                onRename = { renamingConversation = conversation },
                                onDelete = { viewModel.deleteConversation(conversation.conversationId) },
                            )
                        }
                    }
                }
            }
        }

        renamingConversation?.let { conversation ->
            var newTitle by remember(conversation.conversationId) { mutableStateOf(conversation.title) }
            AlertDialog(
                onDismissRequest = { renamingConversation = null },
                title = { Text("Přejmenovat konverzaci") },
                text = {
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.renameConversation(conversation.conversationId, newTitle); renamingConversation = null }) {
                        Text("Uložit")
                    }
                },
                dismissButton = { TextButton(onClick = { renamingConversation = null }) { Text("Zrušit") } },
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: AssistantConversationEntity,
    active: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(conversation.title, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    relativeDay(conversation.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Přejmenovat", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Smazat", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun relativeDay(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffMinutes = diffMs / 60_000
    return when {
        diffMinutes < 1 -> "Teď"
        diffMinutes < 60 -> "Před $diffMinutes min"
        diffMinutes < 24 * 60 -> "Před ${diffMinutes / 60} h"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
    }
}

@Composable
private fun AssistantMessageBubble(message: AssistantMessageEntity) {
    when (message.role) {
        AssistantRole.ERROR -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                message.text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        AssistantRole.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(message.text, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        AssistantRole.ASSISTANT -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            MistralAvatar(size = 24.dp)
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                if (message.text.isEmpty()) {
                    TypingIndicator()
                } else {
                    Text(message.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
fun MistralAvatar(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.mistral_avatar),
        contentDescription = "Mistral AI",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape),
    )
}
