package cz.kuclab.hertzchat.ui.common


import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.locale.SUPPORTED_LANGUAGES

/**
 * Shared between Onboarding and Settings so the language choice behaves the
 * same in both places: persists immediately, then recreates the Activity so
 * every screen re-reads its resources in the new language right away.
 */
@Composable
fun LanguagePickerRow(label: String, currentCode: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentLabel = SUPPORTED_LANGUAGES.firstOrNull { it.code == currentCode }?.nativeName ?: currentCode

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text(label)
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(currentLabel) }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SUPPORTED_LANGUAGES.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.nativeName) },
                        onClick = {
                            expanded = false
                            if (language.code != currentCode) {
                                onChange(language.code)
                                (context as? Activity)?.recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}
