package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzShapes

/** In-chat find bar: query field plus match counter with up/down navigation. */
@Composable
fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = HertzShapes.Pill,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Zavřít hledání", tint = HertzGlass.contentOnGlass())
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = HertzGlass.contentOnGlass()),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Hledat v konverzaci…", color = HertzGlass.contentOnGlass().copy(alpha = 0.55f))
                    inner()
                },
            )
            if (matchCount > 0) {
                Text(
                    "${matchIndex + 1}/${matchCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = HertzGlass.contentOnGlass().copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Předchozí výskyt", tint = HertzGlass.contentOnGlass().copy(alpha = if (matchCount > 0) 1f else 0.35f))
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Další výskyt", tint = HertzGlass.contentOnGlass().copy(alpha = if (matchCount > 0) 1f else 0.35f))
            }
        }
    }
}

/** Highlights every case-insensitive occurrence of [query] in [text] with the primary container. */
@Composable
fun highlightQuery(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (true) {
            val found = text.indexOf(query, from, ignoreCase = true)
            if (found == -1) break
            addStyle(highlight, found, found + query.length)
            from = found + query.length.coerceAtLeast(1)
        }
    }
}
