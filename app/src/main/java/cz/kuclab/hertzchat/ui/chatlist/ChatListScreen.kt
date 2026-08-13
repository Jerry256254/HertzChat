package cz.kuclab.hertzchat.ui.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hertz Chat") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.Person, contentDescription = "Profil")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Nastavení")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenContacts,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nový chat")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Zatím žádné chaty. Přidej si přátele přes tlačítko + (sdílej nebo naskenuj Hertz ID).",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(items, key = { it.contactId }) { item ->
                    ChatListRow(
                        item = item,
                        onClick = { onOpenChat(item.contactId) },
                        onTogglePin = { viewModel.togglePin(item) },
                        onBlock = { viewModel.block(item.contactId) },
                    )
                    HorizontalDivider()
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
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (item.pinned) "Odepnout" else "Připnout") },
                onClick = { menuOpen = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text("Blokovat") },
                onClick = { menuOpen = false; onBlock() },
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (item.avatarPath != null) {
                AsyncImage(
                    model = java.io.File(item.avatarPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(item.nickname.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.nickname, fontWeight = FontWeight.SemiBold)
                if (item.pinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.PushPin, contentDescription = "Připnuto", modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = item.lastMessagePreview ?: "Zatím žádné zprávy",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
