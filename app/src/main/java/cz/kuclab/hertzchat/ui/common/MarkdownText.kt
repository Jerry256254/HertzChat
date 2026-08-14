package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the subset of Markdown that actually turns up in chat: bold, italic,
 * strikethrough, inline code, fenced code blocks, headings, bullet and numbered lists,
 * block quotes, horizontal rules, links (shown as their label - the URL syntax is noise
 * in a message bubble), and tables as a real grid.
 *
 * Applied to everyone's messages, not just the assistant's - people type `*takhle*`
 * expecting it to work, the same as in WhatsApp. The inline rules are deliberately
 * strict about word boundaries so ordinary text containing punctuation survives
 * untouched: `2 * 3 * 4`, `snake_case_name` and `5*3` all render exactly as typed.
 */
@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Prose -> Text(block.content, color = color, style = LocalTextStyle.current)
                is MdBlock.Table -> MarkdownTable(block, color)
            }
        }
    }
}

private sealed interface MdBlock {
    data class Prose(val content: AnnotatedString) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
}

private val InlinePattern = Regex(
    """\*\*\*(.+?)\*\*\*""" +   // 1 bold + italic
        """|___(.+?)___""" +    // 2 bold + italic
        """|\*\*(.+?)\*\*""" +  // 3 bold
        """|__(.+?)__""" +      // 4 bold
        """|~~(.+?)~~""" +      // 5 strikethrough
        """|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""" + // 6 italic
        """|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""" +   // 7 italic
        """|`(.+?)`""" +        // 8 inline code
        """|!?\[(.*?)]\((\S*?)\)""", // 9 link label, 10 url
    RegexOption.DOT_MATCHES_ALL,
)

private val CodeSpan = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
private val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)
private val ItalicSpan = SpanStyle(fontStyle = FontStyle.Italic)
private val BoldItalicSpan = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
private val StrikeSpan = SpanStyle(textDecoration = TextDecoration.LineThrough)
private val LinkSpan = SpanStyle(textDecoration = TextDecoration.Underline)
private val QuoteSpan = SpanStyle(fontStyle = FontStyle.Italic)

private val HeadingPattern = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
private val BulletPattern = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NumberedPattern = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val QuotePattern = Regex("""^\s*>+\s?(.*)$""")
// Written out per character rather than with a backreference: a backreference inside a
// character class is a compile error in Java's regex engine, which would have blown up
// at class-init the first time any message was rendered.
private val RulePattern = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")

private val TableRowPattern = Regex("""^\s*\|?(.+\|.+?)\|?\s*$""")
private val TableSeparatorPattern = Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)+\|?\s*$""")

/** Headings shrink toward body size as they get deeper; a chat bubble can't carry six real levels. */
private fun headingSize(level: Int): TextUnit = when (level) {
    1 -> 20.sp
    2 -> 18.sp
    3 -> 17.sp
    else -> 16.sp
}

private fun parseBlocks(source: String): List<MdBlock> {
    val lines = source.lines()
    val blocks = mutableListOf<MdBlock>()
    val prose = mutableListOf<String>()

    fun flushProse() {
        if (prose.isEmpty()) return
        blocks += MdBlock.Prose(buildMarkdown(prose))
        prose.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)
        // A table is a header row followed by a |---|---| separator row - that pairing
        // is specific enough that plain text using pipes for something else won't match.
        if (TableRowPattern.matches(line) && next != null && TableSeparatorPattern.matches(next)) {
            flushProse()
            val rows = mutableListOf(splitTableRow(line))
            var j = i + 2
            while (j < lines.size && TableRowPattern.matches(lines[j])) {
                rows += splitTableRow(lines[j])
                j++
            }
            blocks += MdBlock.Table(rows)
            i = j
            continue
        }
        prose += line
        i++
    }
    flushProse()
    return blocks
}

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

@Composable
private fun MarkdownTable(table: MdBlock.Table, color: Color) {
    val columnCount = table.rows.maxOf { it.size }
    val columnWidths = IntArray(columnCount) { col ->
        table.rows.maxOf { row -> row.getOrNull(col)?.length ?: 0 }.coerceAtLeast(3)
    }

    Surface(
        modifier = Modifier.padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
            table.rows.forEachIndexed { index, row ->
                Row {
                    Text(
                        formatTableRow(row, columnWidths),
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (index == 0) {
                    Text(
                        columnWidths.joinToString(" │ ") { "─".repeat(it) },
                        color = color.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun formatTableRow(row: List<String>, columnWidths: IntArray): String =
    columnWidths.indices.joinToString(" │ ") { col -> (row.getOrNull(col) ?: "").padEnd(columnWidths[col]) }

private fun buildMarkdown(sourceLines: List<String>): AnnotatedString = buildAnnotatedString {
    var inFence = false
    var firstLine = true

    sourceLines.forEach { line ->
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

        if (RulePattern.matches(line)) {
            withStyle(SpanStyle(color = Color.Gray)) { append("────────") }
            return@forEach
        }

        HeadingPattern.find(line)?.let { heading ->
            val level = heading.groupValues[1].length
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSize(level))) {
                appendInline(heading.groupValues[2])
            }
            return@forEach
        }

        QuotePattern.find(line)?.let { quote ->
            withStyle(QuoteSpan) {
                append("│  ")
                appendInline(quote.groupValues[1])
            }
            return@forEach
        }

        BulletPattern.find(line)?.let { bullet ->
            append(bullet.groupValues[1])
            append("•  ")
            appendInline(bullet.groupValues[2])
            return@forEach
        }

        NumberedPattern.find(line)?.let { numbered ->
            append(numbered.groupValues[1])
            append(numbered.groupValues[2])
            append(".  ")
            appendInline(numbered.groupValues[3])
            return@forEach
        }

        appendInline(line)
    }
}

/** Applies the inline span rules to one line's worth of text. */
private fun AnnotatedString.Builder.appendInline(line: String) {
    var index = 0
    InlinePattern.findAll(line).forEach { match ->
        if (match.range.first > index) append(line.substring(index, match.range.first))
        val g = match.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(BoldItalicSpan) { append(g[1]) }
            g[2].isNotEmpty() -> withStyle(BoldItalicSpan) { append(g[2]) }
            g[3].isNotEmpty() -> withStyle(BoldSpan) { append(g[3]) }
            g[4].isNotEmpty() -> withStyle(BoldSpan) { append(g[4]) }
            g[5].isNotEmpty() -> withStyle(StrikeSpan) { append(g[5]) }
            g[6].isNotEmpty() -> withStyle(ItalicSpan) { append(g[6]) }
            g[7].isNotEmpty() -> withStyle(ItalicSpan) { append(g[7]) }
            g[8].isNotEmpty() -> withStyle(CodeSpan) { append(g[8]) }
            // A link renders as its label; an image renders as its alt text. The raw URL
            // is dropped rather than shown - it's unusable here and only adds clutter.
            g[9].isNotEmpty() -> withStyle(LinkSpan) { append(g[9]) }
            g[10].isNotEmpty() -> withStyle(LinkSpan) { append(g[10]) }
        }
        index = match.range.last + 1
    }
    if (index < line.length) append(line.substring(index))
}
