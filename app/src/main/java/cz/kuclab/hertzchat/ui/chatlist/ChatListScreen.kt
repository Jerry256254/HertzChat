package cz.kuclab.hertzchat.ui.chatlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.ui.common.ActionMenu
import cz.kuclab.hertzchat.ui.common.ActionMenuItem
import cz.kuclab.hertzchat.ui.common.AppCard

@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val mistralEnabled by viewModel.mistralEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hertz Chat", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.Person, contentDescription = "Profil")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Nastavení")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenContacts,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nový chat") },
            )
        },
    ) { padding ->
        // The assistant row is always present now, so "no chats yet" has to mean
        // "no real conversations yet" rather than "nothing in the list".
        val hasRealChats = items.any { it.kind != ChatListItemKind.ASSISTANT }
        if (!hasRealChats && items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text(
                    "Zatím žádné chaty",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    "Přidej si přátele přes tlačítko dole (sdílej nebo naskenuj Hertz ID).",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!hasRealChats) {
                    item {
                        Text(
                            "Zatím tu nemáš žádné chaty - přidej si přátele tlačítkem dole (sdílej nebo naskenuj Hertz ID).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    }
                }
                items(items, key = { it.contactId }) { item ->
                    ChatListRow(
                        item = item,
                        onClick = {
                            when (item.kind) {
                                ChatListItemKind.CONTACT -> onOpenChat(item.contactId)
                                ChatListItemKind.GROUP -> onOpenGroup(item.contactId)
                                ChatListItemKind.ASSISTANT -> if (mistralEnabled) onOpenAssistant() else onOpenSettings()
                            }
                        },
                        onTogglePin = { viewModel.togglePin(item) },
                        onBlock = { viewModel.block(item.contactId) },
                        onHideAssistant = { viewModel.hideAssistant() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    item: ChatListItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onBlock: () -> Unit,
    onHideAssistant: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isAssistant = item.kind == ChatListItemKind.ASSISTANT
    val isGroup = item.kind == ChatListItemKind.GROUP

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (item.pinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ActionMenuItem(
                    text = if (item.pinned) "Odepnout" else "Připnout",
                    icon = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    onClick = { menuOpen = false; onTogglePin() },
                )
                if (isAssistant) {
                    ActionMenuItem(
                        text = "Skrýt asistenta",
                        icon = Icons.Filled.VisibilityOff,
                        onClick = { menuOpen = false; onHideAssistant() },
                    )
                } else if (!item.isSelf) {
                    ActionMenuItem(
                        text = "Blokovat",
                        icon = Icons.Filled.Block,
                        destructive = true,
                        onClick = { menuOpen = false; onBlock() },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (isAssistant) {
                    Image(
                        painter = painterResource(R.drawable.mistral_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else if (isGroup) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                } else if (item.avatarPath != null) {
                    AsyncImage(
                        model = java.io.File(item.avatarPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        item.nickname.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.nickname, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (item.isSelf) {
                        Text(
                            "  (Ty)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.pinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.PushPin, contentDescription = "Připnuto", modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = item.lastMessagePreview ?: "Zatím žádné zprávy",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
