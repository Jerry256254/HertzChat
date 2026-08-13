package cz.kuclab.hertzchat.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.BuildConfig

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val blocked by viewModel.blockedContacts.collectAsState()
    val mediaBytes by viewModel.mediaBytes.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Nastavení") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { SectionTitle("Vzhled") }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ThemeModeRow(current = settings.themeMode, onChange = viewModel::setThemeMode)
                    }
                }
            }

            item { SectionTitle("Oznámení") }
            item {
                Card {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.Notifications,
                            title = "Oznámení o nových zprávách",
                            subtitle = "Vyskakovací upozornění, i když je appka zavřená (pokud má telefon internet)",
                            checked = settings.notificationsEnabled,
                            onCheckedChange = viewModel::setNotificationsEnabled,
                        )
                    }
                }
            }

            item { SectionTitle("Soukromí a síť") }
            item {
                Card {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsSwitchRow(
                            icon = null,
                            title = "Být dosažitelný",
                            subtitle = "Vypnutím se úplně zastaví síť Tor i služba na pozadí - nikdo tě nenajde ani ti nemůže poslat zprávu, ale appka nespotřebovává baterii navíc. Žádný server (ani náš, ani cizí) do toho není nikdy zapojený.",
                            checked = settings.discoverable,
                            onCheckedChange = viewModel::setDiscoverable,
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            icon = null,
                            title = "Automaticky přijímat žádosti o přátelství",
                            subtitle = "Nové kontakty se přidají rovnou, bez tvého potvrzení. Pohodlnější, ale méně kontroly nad tím, kdo tě může kontaktovat.",
                            checked = settings.autoAcceptFriendRequests,
                            onCheckedChange = viewModel::setAutoAcceptFriendRequests,
                        )
                    }
                }
            }

            item { SectionTitle("Média") }
            item {
                Card {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MediaQualityRow(current = settings.mediaQuality, onChange = viewModel::setMediaQuality)
                        HorizontalDivider()
                        StorageRow(bytes = mediaBytes, onClear = viewModel::clearMediaCache)
                    }
                }
            }

            if (blocked.isNotEmpty()) {
                item { SectionTitle("Blokovaní uživatelé") }
                item {
                    Card {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            blocked.forEachIndexed { index, contact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(contact.nickname)
                                    TextButton(onClick = { viewModel.unblock(contact.contactId) }) { Text("Odblokovat") }
                                }
                                if (index != blocked.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Aktualizace") }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        UpdateCheckRow(
                            currentVersion = viewModel.currentVersion,
                            state = updateCheckState,
                            onCheck = viewModel::checkForUpdates,
                            onOpenRelease = { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        )
                    }
                }
            }

            item { SectionTitle("O aplikaci") }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Hertz Chat ${BuildConfig.VERSION_NAME}")
                        }
                        Text(
                            "Open source, žádný server, end-to-end šifrované přes Signal Protokol, přenos přes síť Tor.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "github.com/Jerry256254/HertzChat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) }
            Column {
                Text(title)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeModeRow(current: String, onChange: (String) -> Unit) {
    val options = listOf("SYSTEM" to "Podle systému", "LIGHT" to "Světlý", "DARK" to "Tmavý")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        Text("Motiv", modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        options.forEach { (value, label) ->
            AssistChip(
                onClick = { onChange(value) },
                label = { Text(label) },
                colors = if (current == value) {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        labelColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
private fun MediaQualityRow(current: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("ORIGINAL" to "Původní kvalita", "HIGH" to "Vysoká", "BALANCED" to "Vyvážená")

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Kvalita odesílaných obrázků")
        TextButton(onClick = { expanded = true }) {
            Text(options.firstOrNull { it.first == current }?.second ?: current)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onChange(value); expanded = false })
            }
        }
    }
}

@Composable
private fun UpdateCheckRow(
    currentVersion: String,
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Nainstalovaná verze")
            Text(currentVersion, style = MaterialTheme.typography.labelSmall)
        }
        if (state is UpdateCheckState.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            OutlinedButton(onClick = onCheck) { Text("Zkontrolovat") }
        }
    }
    when (state) {
        is UpdateCheckState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Text("  Máš nejnovější verzi", color = MaterialTheme.colorScheme.secondary)
        }
        is UpdateCheckState.Available -> Column(modifier = Modifier.padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("  K dispozici je verze ${state.version}", color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = { onOpenRelease(state.url) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Otevřít stránku ke stažení")
            }
        }
        is UpdateCheckState.Error -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit
    }
}

@Composable
private fun StorageRow(bytes: Long, onClear: () -> Unit) {
    val mb = bytes / (1024.0 * 1024.0)
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text("Média uložená na zařízení: %.1f MB".format(mb))
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Vymazat stažená média")
        }
    }
}
