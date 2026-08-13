package cz.kuclab.hertzchat.ui.groupchat

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.ui.common.ChatInputAccentButton
import cz.kuclab.hertzchat.ui.common.ChatInputBar

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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět") } },
                actions = {
                    IconButton(onClick = { membersDialogOpen = true }) { Icon(Icons.Filled.Groups, contentDescription = "Členové") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.ExitToApp, contentDescription = "Možnosti") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Opustit skupinu") },
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
                    trailingButton = {
                        ChatInputAccentButton(
                            onClick = viewModel::send,
                            icon = Icons.Filled.Send,
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(bubbleColor)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(message.text.orEmpty(), color = textColor)
                    }
                }
            }
        }
    }
}
