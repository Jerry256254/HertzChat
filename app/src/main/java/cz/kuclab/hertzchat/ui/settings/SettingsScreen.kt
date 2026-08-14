package cz.kuclab.hertzchat.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.BuildConfig
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.mistral.MISTRAL_MODEL_LARGE
import cz.kuclab.hertzchat.mistral.MISTRAL_MODEL_MEDIUM
import cz.kuclab.hertzchat.mistral.MISTRAL_MODEL_SMALL
import cz.kuclab.hertzchat.ui.common.AppDropdownMenu
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.LanguagePickerRow
import cz.kuclab.hertzchat.ui.onboarding.MISTRAL_CONSENT_TEXT

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val blocked by viewModel.blockedContacts.collectAsState()
    val mediaBytes by viewModel.mediaBytes.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val mistralEnabled by viewModel.mistralEnabled.collectAsState()
    val mistralConsentGiven by viewModel.mistralConsentGiven.collectAsState()
    val mistralShowAssistantContact by viewModel.mistralShowAssistantContact.collectAsState()
    val mistralModel by viewModel.mistralModel.collectAsState()
    val mistralKeys by viewModel.mistralKeys.collectAsState()
    var showMistralConsentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item { SectionTitle(Icons.Filled.DarkMode, stringResource(R.string.settings_section_appearance)) }
            item {
                SettingsCard {
                    ThemeModeRow(current = settings.themeMode, onChange = viewModel::setThemeMode)
                    HorizontalDivider()
                    LanguagePickerRow(
                        label = stringResource(R.string.settings_language_label),
                        currentCode = settings.languageCode,
                        onChange = viewModel::setLanguageCode,
                    )
                }
            }

            item { SectionTitle(Icons.Filled.Notifications, stringResource(R.string.settings_section_notifications)) }
            item {
                SettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.settings_notifications_title),
                        subtitle = stringResource(R.string.settings_notifications_subtitle),
                        checked = settings.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled,
                    )
                }
            }

            item { SectionTitle(Icons.Filled.Wifi, stringResource(R.string.settings_section_privacy)) }
            item {
                SettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Wifi,
                        title = stringResource(R.string.settings_discoverable_title),
                        subtitle = stringResource(R.string.settings_discoverable_subtitle),
                        checked = settings.discoverable,
                        onCheckedChange = viewModel::setDiscoverable,
                    )
                    HorizontalDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.PersonAdd,
                        title = stringResource(R.string.settings_auto_accept_title),
                        subtitle = stringResource(R.string.settings_auto_accept_subtitle),
                        checked = settings.autoAcceptFriendRequests,
                        onCheckedChange = viewModel::setAutoAcceptFriendRequests,
                    )
                    HorizontalDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.SmartToy,
                        title = stringResource(R.string.settings_allow_mistral_title),
                        subtitle = stringResource(R.string.settings_allow_mistral_subtitle),
                        checked = settings.allowMistralOnMyMessages,
                        onCheckedChange = viewModel::setAllowMistralOnMyMessages,
                    )
                }
            }

            item { SectionTitle(Icons.Filled.SmartToy, stringResource(R.string.settings_section_mistral)) }
            item {
                SettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Filled.SmartToy,
                        title = stringResource(R.string.settings_mistral_enabled_title),
                        subtitle = stringResource(R.string.settings_mistral_enabled_subtitle),
                        checked = mistralEnabled,
                        onCheckedChange = { turningOn ->
                            if (turningOn && !mistralConsentGiven) {
                                showMistralConsentDialog = true
                            } else {
                                viewModel.setMistralEnabled(turningOn)
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.Visibility,
                        title = stringResource(R.string.settings_mistral_show_contact_title),
                        subtitle = stringResource(R.string.settings_mistral_show_contact_subtitle),
                        checked = mistralShowAssistantContact,
                        onCheckedChange = viewModel::setMistralShowAssistantContact,
                    )
                    HorizontalDivider()
                    MistralModelRow(current = mistralModel, onChange = viewModel::setMistralModel)
                    HorizontalDivider()
                    MistralApiKeysRow(keys = mistralKeys, onAdd = viewModel::addMistralKey, onRemove = viewModel::removeMistralKey)
                    HorizontalDivider()
                    MistralHelperLinksRow(context = context)
                }
            }

            item { SectionTitle(Icons.Filled.Storage, stringResource(R.string.settings_section_media)) }
            item {
                SettingsCard {
                    MediaQualityRow(current = settings.mediaQuality, onChange = viewModel::setMediaQuality)
                    HorizontalDivider()
                    StorageRow(bytes = mediaBytes, onClear = viewModel::clearMediaCache)
                }
            }

            if (blocked.isNotEmpty()) {
                item { SectionTitle(Icons.Filled.Block, stringResource(R.string.settings_section_blocked)) }
                item {
                    SettingsCard {
                        blocked.forEachIndexed { index, contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(contact.nickname)
                                TextButton(onClick = { viewModel.unblock(contact.contactId) }) { Text(stringResource(R.string.settings_unblock)) }
                            }
                            if (index != blocked.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            item { SectionTitle(Icons.Filled.SystemUpdate, stringResource(R.string.settings_section_updates)) }
            item {
                SettingsCard {
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

            item { SectionTitle(Icons.Filled.Info, stringResource(R.string.settings_section_about)) }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Hertz Chat ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        stringResource(R.string.settings_about_description),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "github.com/Jerry256254/HertzChat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    if (showMistralConsentDialog) {
        AlertDialog(
            onDismissRequest = { showMistralConsentDialog = false },
            title = { Text(stringResource(R.string.settings_mistral_consent_title)) },
            text = {
                Text(
                    MISTRAL_CONSENT_TEXT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMistralConsentDialog = false
                    viewModel.confirmMistralConsent()
                }) { Text(stringResource(R.string.settings_mistral_consent_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showMistralConsentDialog = false }) { Text(stringResource(R.string.settings_mistral_consent_cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    AppCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
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
            icon?.let {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
            }
            Column {
                Text(title)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MistralModelRow(current: String, onChange: (String) -> Unit) {
    val options = listOf(
        MISTRAL_MODEL_SMALL to "Small",
        MISTRAL_MODEL_MEDIUM to "Medium",
        MISTRAL_MODEL_LARGE to "Large",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Image(
            painter = painterResource(R.drawable.mistral_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(24.dp).clip(CircleShape),
        )
        Text(stringResource(R.string.settings_mistral_model_label), modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
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
private fun MistralApiKeysRow(keys: List<String>, onAdd: (String) -> Unit, onRemove: (Int) -> Unit) {
    var newKey by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(stringResource(R.string.settings_mistral_keys_label))
        if (keys.isEmpty()) {
            Text(
                stringResource(R.string.settings_mistral_keys_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            keys.forEachIndexed { index, key ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(maskApiKey(key), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_mistral_key_remove))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.settings_mistral_key_placeholder)) },
                singleLine = true,
            )
            TextButton(onClick = { onAdd(newKey); newKey = "" }, enabled = newKey.isNotBlank()) {
                Text(stringResource(R.string.settings_mistral_key_add))
            }
        }
    }
}

private fun maskApiKey(key: String): String {
    if (key.length <= 8) return "•".repeat(key.length)
    return key.take(4) + "…" + key.takeLast(4)
}

@Composable
private fun MistralHelperLinksRow(context: android.content.Context) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            stringResource(R.string.settings_mistral_helper_text),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kuclab.org/15mail"))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_mistral_helper_email)) }
        TextButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.mistral.ai/api-keys"))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_mistral_helper_key)) }
    }
}

@Composable
private fun ThemeModeRow(current: String, onChange: (String) -> Unit) {
    val options = listOf(
        "SYSTEM" to stringResource(R.string.settings_theme_system),
        "LIGHT" to stringResource(R.string.settings_theme_light),
        "DARK" to stringResource(R.string.settings_theme_dark),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        Text(stringResource(R.string.settings_theme_label), modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
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
    val options = listOf(
        "ORIGINAL" to stringResource(R.string.settings_media_quality_original),
        "HIGH" to stringResource(R.string.settings_media_quality_high),
        "BALANCED" to stringResource(R.string.settings_media_quality_balanced),
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_media_quality_label))
        TextButton(onClick = { expanded = true }) {
            Text(options.firstOrNull { it.first == current }?.second ?: current)
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_installed_version))
            Text(currentVersion, style = MaterialTheme.typography.labelSmall)
        }
        if (state is UpdateCheckState.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.settings_check_update)) }
        }
    }
    when (state) {
        is UpdateCheckState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Text("  " + stringResource(R.string.settings_up_to_date), color = MaterialTheme.colorScheme.secondary)
        }
        is UpdateCheckState.Available -> Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.settings_update_available, state.version), color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = { onOpenRelease(state.url) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_open_release))
            }
        }
        is UpdateCheckState.Error -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 12.dp),
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
            Text(stringResource(R.string.settings_media_storage_label, mb))
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(stringResource(R.string.settings_media_clear))
        }
    }
}
