package cz.kuclab.hertzchat.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.BuildConfig
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.ui.common.AppDropdownMenu
import cz.kuclab.hertzchat.ui.common.GlassBar
import cz.kuclab.hertzchat.ui.common.GlassMenuItem
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.LanguagePickerRow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val mediaBytes by viewModel.mediaBytes.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val context = LocalContext.current

    val hazeState = remember { HazeState() }
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().haze(hazeState, HertzGlass.hazeStyle()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 92.dp, bottom = 24.dp),
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
                        onDownload = viewModel::downloadAndInstall,
                        onOpenInstallSettings = viewModel::openInstallSettings,
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
        GlassBar(
            title = stringResource(R.string.settings_title),
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        }
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
                GlassMenuItem(text = label, onClick = { onChange(value); expanded = false })
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
    onDownload: (apkUrl: String, version: String) -> Unit,
    onOpenInstallSettings: () -> Unit,
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
            if (state.apkUrl != null) {
                Button(onClick = { onDownload(state.apkUrl, state.version) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_download_install))
                }
                TextButton(onClick = { onOpenRelease(state.url) }) {
                    Text(stringResource(R.string.settings_open_release))
                }
            } else {
                Button(onClick = { onOpenRelease(state.url) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_open_release))
                }
            }
        }
        is UpdateCheckState.Downloading -> Column(modifier = Modifier.padding(bottom = 12.dp)) {
            val indeterminate = state.progress < 0f
            if (indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(R.string.settings_downloading, (state.progress.coerceAtLeast(0f) * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is UpdateCheckState.InstallBlocked -> Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.settings_install_blocked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onOpenInstallSettings) {
                    Text(stringResource(R.string.settings_open_install_settings))
                }
                OutlinedButton(onClick = { onDownload(state.apkUrl, state.version) }) {
                    Text(stringResource(R.string.settings_download_install))
                }
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
