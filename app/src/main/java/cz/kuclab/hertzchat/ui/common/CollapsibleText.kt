package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A message past this many source lines (or characters) collapses behind an expander. */
const val COLLAPSE_LINES_THRESHOLD = 20
const val COLLAPSE_CHARS_THRESHOLD = 1500
private const val COLLAPSED_PREVIEW_CHARS = 1200

/**
 * True when [text] is long enough to collapse: 20+ lines, or a wall of text past
 * 1500 characters. Pure so the threshold stays pinned by MessageCollapseTest.
 */
internal fun shouldCollapseMessage(text: String): Boolean =
    text.lines().size >= COLLAPSE_LINES_THRESHOLD || text.length >= COLLAPSE_CHARS_THRESHOLD

/**
 * The collapsed preview: the first ~1200 characters cut at a word boundary, with an
 * ellipsis marker. Never returns the whole text - a preview that shows everything
 * would make the expander pointless.
 */
internal fun collapsedPreview(text: String): String {
    if (text.length <= COLLAPSED_PREVIEW_CHARS) {
        return text.lines().take(COLLAPSE_LINES_THRESHOLD - 1).joinToString("\n") + "\n…"
    }
    val cut = text.take(COLLAPSED_PREVIEW_CHARS).trimEnd()
    val boundary = cut.lastIndexOfAny(charArrayOf(' ', '\n')).takeIf { it > COLLAPSED_PREVIEW_CHARS / 2 } ?: cut.length
    return cut.take(boundary).trimEnd() + "…"
}

/**
 * A text bubble's content: short messages render whole, long ones (20+ lines) start
 * collapsed behind a "show whole message" button so they don't swallow the thread.
 */
@Composable
fun CollapsibleMessageText(text: String, color: Color, modifier: Modifier = Modifier) {
    if (!shouldCollapseMessage(text)) {
        MarkdownText(text, color = color, modifier = modifier)
        return
    }
    var expanded by remember(text) { mutableStateOf(false) }
    Column(modifier = modifier) {
        MarkdownText(if (expanded) text else collapsedPreview(text), color = color)
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.Start).padding(top = 2.dp),
        ) {
            Text(
                if (expanded) "Skrýt zprávu" else "Zobrazit celou zprávu",
                style = MaterialTheme.typography.labelLarge,
                color = color.copy(alpha = 0.85f),
            )
        }
    }
}
