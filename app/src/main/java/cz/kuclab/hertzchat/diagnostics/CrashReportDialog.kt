package cz.kuclab.hertzchat.diagnostics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shows the previous run's crash, if there was one, as soon as the app comes back up.
 *
 * The trace is the whole point, so it's presented verbatim and copyable rather than
 * summarised - "něco se pokazilo" would throw away the only useful information.
 */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(CrashReporter.lastCrash(context)) }

    val text = report ?: return

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Aplikace minule spadla") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Tohle je záznam pádu. Zkopíruj ho prosím a pošli vývojáři - je v něm přesná příčina.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Zkopírovat") }
        },
        dismissButton = {
            TextButton(onClick = { CrashReporter.clear(context); report = null }) { Text("Zavřít") }
        },
    )
}
