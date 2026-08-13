package cz.kuclab.hertzchat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val blocked by viewModel.blockedContacts.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Nastavení") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { SectionTitle("Soukromí a síť") }
            item {
                SettingsSwitchRow(
                    title = "Být dosažitelný",
                    subtitle = "Vypne Tor síť a onion službu - nikdo tě nenajde ani ti nemůže poslat zprávu, dokud to znovu nezapneš. Žádný server (ani náš, ani cizí) do toho není nikdy zapojený - appka se spojuje přímo s veřejnou sítí Tor.",
                    checked = settings.discoverable,
                    onCheckedChange = viewModel::setDiscoverable,
                )
            }
            item { Divider() }

            item { SectionTitle("Média") }
            item {
                SettingsSwitchRow(
                    title = "Automaticky stahovat média",
                    subtitle = null,
                    checked = settings.autoDownloadMedia,
                    onCheckedChange = viewModel::setAutoDownloadMedia,
                )
            }
            item {
                MediaQualityRow(current = settings.mediaQuality, onChange = viewModel::setMediaQuality)
            }
            item { Divider() }

            if (blocked.isNotEmpty()) {
                item { SectionTitle("Blokovaní uživatelé") }
                items(blocked, key = { it.contactId }) { contact ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(contact.nickname)
                        TextButton(onClick = { viewModel.unblock(contact.contactId) }) { Text("Odblokovat") }
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
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MediaQualityRow(current: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("ORIGINAL" to "Původní kvalita", "HIGH" to "Vysoká", "BALANCED" to "Vyvážená")

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Kvalita odesílaných médií")
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
