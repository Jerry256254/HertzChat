package cz.kuclab.hertzchat.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders the small subset of Markdown that language models actually emit in chat
 * replies - bold, italic, inline code, fenced code blocks, headings and bullet lists.
 *
 * Models produce this markup whether or not anything asked them to, so without it the
 * assistant's answers show up with literal `**asterisks**` around every emphasised
 * phrase. Deliberately not a general Markdown implementation: no links, tables or
 * images, because nothing here would render them usefully anyway and a partial
 * implementation that silently mangles them is worse than leaving them as text.
 *
 * Only applied to assistant output. Human messages are shown verbatim - someone typing
 * `2 * 3 * 4` means asterisks, not italics.
 */
@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val annotated = remember(text) { buildMarkdown(text) }
    Text(annotated, color = color, modifier = modifier, style = LocalTextStyle.current)
}

private val InlinePattern = Regex(
    """\*\*\*(.+?)\*\*\*""" +   // 1 bold + italic
        """|___(.+?)___""" +    // 2 bold + italic
        """|\*\*(.+?)\*\*""" +  // 3 bold
        """|__(.+?)__""" +      // 4 bold
        """|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""" + // 5 italic
        """|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""" +   // 6 italic
        """|`(.+?)`""",         // 7 inline code
    RegexOption.DOT_MATCHES_ALL,
)

private val CodeSpan = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
private val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)
private val ItalicSpan = SpanStyle(fontStyle = FontStyle.Italic)
private val BoldItalicSpan = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)

private fun buildMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    val lines = source.lines()
    var inFence = false
    var firstLine = true

    lines.forEach { line ->
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            return@forEach // the fence markers themselves are never shown
        }
        if (!firstLine) append('\n')
        firstLine = false

        if (inFence) {
            withStyle(CodeSpan) { append(line) }
            return@forEach
        }

        val heading = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""").find(line)
        if (heading != null) {
            // Rendered as emphasis rather than a larger size: inside a chat bubble a
            // genuinely bigger heading looks broken, but the hierarchy still reads.
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(heading.groupValues[2])
            }
            return@forEach
        }

        val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""").find(line)
        if (bullet != null) {
            append(bullet.groupValues[1])
            append("•  ")
            appendInline(bullet.groupValues[2])
            return@forEach
        }

        appendInline(line)
    }
}

/** Applies the inline span rules to one line's worth of text. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(line: String) {
    var index = 0
    InlinePattern.findAll(line).forEach { match ->
        if (match.range.first > index) append(line.substring(index, match.range.first))
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() -> withStyle(BoldItalicSpan) { append(groups[1]) }
            groups[2].isNotEmpty() -> withStyle(BoldItalicSpan) { append(groups[2]) }
            groups[3].isNotEmpty() -> withStyle(BoldSpan) { append(groups[3]) }
            groups[4].isNotEmpty() -> withStyle(BoldSpan) { append(groups[4]) }
            groups[5].isNotEmpty() -> withStyle(ItalicSpan) { append(groups[5]) }
            groups[6].isNotEmpty() -> withStyle(ItalicSpan) { append(groups[6]) }
            groups[7].isNotEmpty() -> withStyle(CodeSpan) { append(groups[7]) }
        }
        index = match.range.last + 1
    }
    if (index < line.length) append(line.substring(index))
}
