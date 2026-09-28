package cz.kuclab.hertzchat.ui.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.data.repository.IncomingFriendRequest
import cz.kuclab.hertzchat.network.p2p.I2pState
import cz.kuclab.hertzchat.ui.common.GlassAmbientBackground
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.GlassMenu
import cz.kuclab.hertzchat.ui.common.GlassMenuItem
import cz.kuclab.hertzchat.ui.common.GlassSurface
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.theme.HertzMatte
import cz.kuclab.hertzchat.ui.theme.HertzIcons
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalMaterial3Api::class)
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
    val i2pState by viewModel.i2pState.collectAsState()
    val bootstrapPercent by viewModel.bootstrapPercent.collectAsState()
    val bootstrapLabel by viewModel.bootstrapLabel.collectAsState()
    val i2pError by viewModel.i2pError.collectAsState()
    val requests by viewModel.incomingRequests.collectAsState()
    val myAvatarPath by viewModel.myAvatarPath.collectAsState()
    val hazeState = remember { HazeState() }

    Scaffold(
        floatingActionButton = {
            // IntrinsicSize.Max stretches the column to the wider pill and both
            // pills fill it - Hertz Agent and Nový chat are exactly the same
            // width however long their labels are.
            Column(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassSurface(
                    shape = HertzShapes.Pill,
                    hazeState = hazeState,
                    onClick = onOpenAssistant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null, tint = HertzGlass.contentOnGlass(), modifier = Modifier.size(20.dp))
                        Text("  Hertz Agent", style = MaterialTheme.typography.labelLarge, color = HertzGlass.contentOnGlass())
                    }
                }
                GlassSurface(
                    shape = HertzShapes.Pill,
                    fill = MaterialTheme.colorScheme.primary,
                    onClick = onOpenContacts,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        Text("  Nový chat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Flat backdrop plus the list; the floating bar above blurs this
            // content behind itself, so rows scroll underneath the frost.
            Box(modifier = Modifier.fillMaxSize().haze(hazeState, HertzGlass.hazeStyle())) {
            GlassAmbientBackground()
            if (items.isEmpty() && requests.isEmpty() && i2pState == I2pState.CONNECTED) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 56.dp).padding(horizontal = 32.dp),
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
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 92.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                item {
                    AnimatedVisibility(visible = i2pState != I2pState.CONNECTED) {
                        I2pConnectBanner(
                            state = i2pState,
                            percent = bootstrapPercent,
                            label = bootstrapLabel,
                            error = i2pError,
                            onRetry = viewModel::retryI2p,
                        )
                    }
                }
                if (requests.isNotEmpty()) {
                    item {
                        Text(
                            "Žádosti o přátelství",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(requests, key = { it.contactId }) { request ->
                        FriendRequestRow(
                            request = request,
                            onRespond = viewModel::respond,
                        )
                    }
                }
                if (items.isEmpty() && requests.isEmpty()) {
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
                        pinnedCount = items.count { it.pinned },
                        onClick = {
                            when (item.kind) {
                                ChatListItemKind.CONTACT -> onOpenChat(item.contactId)
                                ChatListItemKind.GROUP -> onOpenGroup(item.contactId)
                            }
                        },
                        onTogglePin = { viewModel.togglePin(item) },
                        onMovePinned = { up -> viewModel.movePinned(item, up) },
                        onBlock = { viewModel.block(item.contactId) },
                    )
                }
            }
            }
            }
            FloatingHomeBar(
                myAvatarPath = myAvatarPath,
                onOpenProfile = onOpenProfile,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.TopCenter),
                hazeState = hazeState,
            )
        }
    }
}

@Composable
private fun FloatingHomeBar(
    myAvatarPath: String?,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
) {
    // No statusBarsPadding: the Scaffold content padding already offsets for the
    // status bar, and adding it again is what used to push the bar too low.
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(
            shape = CircleShape,
            shadowElevation = 8.dp,
            onClick = onOpenProfile,
            modifier = Modifier.size(44.dp),
            hazeState = hazeState,
        ) {
            if (myAvatarPath != null) {
                AsyncImage(
                    model = java.io.File(myAvatarPath),
                    contentDescription = "Profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } else {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = "Profil", tint = HertzGlass.contentOnGlass())
                }
            }
        }
        GlassSurface(
            shape = HertzShapes.Pill,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            hazeState = hazeState,
        ) {
            Text(
                "Hertz Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = HertzGlass.contentOnGlass(),
                modifier = Modifier.align(Alignment.Center).padding(vertical = 11.dp),
            )
        }
        GlassCircleButton(
            icon = HertzIcons.Settings,
            contentDescription = "Nastavení",
            onClick = onOpenSettings,
            hazeState = hazeState,
        )
    }
}

/**
 * The connection status lives on the main screen now, not buried in Contacts:
 * while I2P is still bootstrapping this banner shows the live progress, and the
 * moment the router connects it fades away on its own.
 */
@Composable
private fun I2pConnectBanner(
    state: I2pState?,
    percent: Int,
    label: String?,
    error: String?,
    onRetry: () -> Unit,
) {
    AppCard(containerColor = HertzMatte.cardRaised()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (error != null) {
                Icon(
                    Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                CircularProgressIndicator(
                    progress = { (percent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    when {
                        error != null -> "Nepodařilo se připojit k I2P"
                        state == I2pState.STOPPED -> "Síť je vypnutá"
                        else -> "Connecting to I2P - $percent%"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    error ?: label ?: "Navazuje se spojení…",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (error != null) {
                TextButton(onClick = onRetry) { Text("Zkusit znovu") }
            }
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: IncomingFriendRequest,
    onRespond: (IncomingFriendRequest, Boolean) -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        request.nickname.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(request.nickname, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Chce si tě přidat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { onRespond(request, false) }) { Text("Odmítnout") }
            Button(onClick = { onRespond(request, true) }) { Text("Přijmout") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    item: ChatListItem,
    pinnedCount: Int,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onMovePinned: (up: Boolean) -> Unit,
    onBlock: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isGroup = item.kind == ChatListItemKind.GROUP

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (item.pinned) HertzMatte.pinned() else HertzMatte.card(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                GlassMenuItem(
                    text = if (item.pinned) "Odepnout" else "Připnout",
                    onClick = { menuOpen = false; onTogglePin() },
                )
                if (item.pinned && pinnedCount > 1) {
                    GlassMenuItem(
                        text = "Posunout nahoru",
                        onClick = { menuOpen = false; onMovePinned(true) },
                    )
                    GlassMenuItem(
                        text = "Posunout dolů",
                        onClick = { menuOpen = false; onMovePinned(false) },
                    )
                }
                if (!item.isSelf) {
                    GlassMenuItem(
                        text = "Blokovat",
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
                if (isGroup) {
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Připnuto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (item.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
