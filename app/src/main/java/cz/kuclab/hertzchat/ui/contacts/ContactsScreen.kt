package cz.kuclab.hertzchat.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ContactsScreen(
    onOpenChat: (String) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    val peers by viewModel.discoveredPeers.collectAsState()
    val requests by viewModel.incomingRequests.collectAsState()
    val myId = viewModel.identityKeyManager.contactId()

    Scaffold(topBar = { TopAppBar(title = { Text("Kontakty") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Moje ID", fontWeight = FontWeight.SemiBold)
                        Text(myId, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Toto ID sdílej s přáteli, aby tě mohli najít, když jsi online.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (requests.isNotEmpty()) {
                item { Text("Žádosti o přátelství", fontWeight = FontWeight.SemiBold) }
                items(requests, key = { it.contactId }) { request ->
                    Card {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(request.nickname)
                            Row {
                                TextButton(onClick = { viewModel.respond(request, false) }) { Text("Odmítnout") }
                                Button(onClick = { viewModel.respond(request, true) }) { Text("Přijmout") }
                            }
                        }
                    }
                }
            }

            item { Text("Online nyní", fontWeight = FontWeight.SemiBold) }
            items(peers, key = { it.contactId }) { peer ->
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(peer.nickname)
                            Text(peer.contactId, style = MaterialTheme.typography.labelSmall)
                        }
                        if (peer.alreadyContact) {
                            OutlinedButton(onClick = { onOpenChat(peer.contactId) }) { Text("Chat") }
                        } else {
                            Button(onClick = { viewModel.sendFriendRequest(peer.contactId) }) { Text("Přidat") }
                        }
                    }
                }
            }
        }
    }
}
