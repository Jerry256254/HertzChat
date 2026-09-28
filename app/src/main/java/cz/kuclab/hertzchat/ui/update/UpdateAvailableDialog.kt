package cz.kuclab.hertzchat.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.common.GlassDialogTheme
import cz.kuclab.hertzchat.ui.common.WindowBlurBehind
import cz.kuclab.hertzchat.ui.settings.UpdateCheckState
import cz.kuclab.hertzchat.update.UpdateInfo

/**
 * The cold-start nudge: a newer release exists, and new releases carry security
 * fixes - so the user hears about it without having to open settings. Updates
 * in place through the same installer the settings screen uses.
 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    updateState: UpdateCheckState,
    onUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialogTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { WindowBlurBehind(); Text("Je tu nová verze ${info.latestVersion}") },
            text = {
                Column {
                    Text("Máš starou verzi - aktualizuj, nové verze přinášejí i bezpečnostní opravy.")
                    when (updateState) {
                        is UpdateCheckState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { updateState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            Text(
                                "Stahuji verzi ${updateState.version}…",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        is UpdateCheckState.InstallBlocked -> {
                            Text(
                                "Povol instalaci z tohoto zdroje a zkus to znovu.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        is UpdateCheckState.Error -> {
                            Text(
                                updateState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                when (updateState) {
                    is UpdateCheckState.Downloading -> Unit
                    is UpdateCheckState.InstallBlocked -> {
                        TextButton(onClick = onOpenInstallSettings) { Text("Otevřít nastavení") }
                    }
                    else -> {
                        if (info.apkUrl != null) {
                            TextButton(onClick = onUpdate) { Text("Aktualizovat") }
                        } else {
                            TextButton(onClick = onOpenReleasePage) { Text("Otevřít GitHub") }
                        }
                    }
                }
            },
            dismissButton = {
                if (updateState !is UpdateCheckState.Downloading) {
                    TextButton(onClick = onDismiss) { Text("Později") }
                }
            },
        )
    }
}
