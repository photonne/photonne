package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.photonne.app.ui.theme.Spacing

/*
 * Release notes come from GitHub as Markdown and used to be printed raw: "##",
 * "**" and "[#12](https://…)" all over the card. This is the smallest subset
 * that makes them readable — headings, bullet lists, bold and http(s) links —
 * and nothing else: no HTML, no images, and a link to any other scheme is shown
 * as its plain text, never made tappable.
 */

/** A run of text inside a block. [url] is set only for http and https links. */
data class MdSpan(val text: String, val bold: Boolean = false, val url: String? = null)

sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    /** [marker] is "•" for unordered items and "3." for numbered ones. */
    data class ListItem(val depth: Int, val marker: String, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
}

private val HeadingLine = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletLine = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NumberedLine = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val RuleLine = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")

/** Splits [markdown] into blocks. Blank lines end paragraphs; lines of one
 *  paragraph are joined with a space, as Markdown does. */
fun parseReleaseNotes(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
            paragraph.clear()
        }
    }
    for (raw in markdown.replace("\r\n", "\n").split('\n')) {
        val line = raw.trimEnd()
        if (line.isBlank() || RuleLine.matches(line)) {
            flush()
            continue
        }
        HeadingLine.matchEntire(line)?.let { m ->
            flush()
            blocks += MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2]))
        } ?: BulletLine.matchEntire(line)?.let { m ->
            flush()
            blocks += MdBlock.ListItem(depthOf(m.groupValues[1]), "•", parseInline(m.groupValues[2]))
        } ?: NumberedLine.matchEntire(line)?.let { m ->
            flush()
            blocks += MdBlock.ListItem(depthOf(m.groupValues[1]), "${m.groupValues[2]}.", parseInline(m.groupValues[3]))
        } ?: run {
            // A wrapped list item continues on the next, indented line.
            val last = blocks.lastOrNull()
            if (paragraph.isEmpty() && last is MdBlock.ListItem && raw.startsWith(" ")) {
                blocks[blocks.lastIndex] = last.copy(spans = last.spans + MdSpan(" ") + parseInline(line.trim()))
            } else {
                paragraph += line.trim()
            }
        }
    }
    flush()
    return blocks
}

private fun depthOf(indent: String): Int =
    (indent.replace("\t", "    ").length / 2).coerceAtMost(3)

/**
 * Bold (`**…**` / `__…__`), links (`[text](url)`), bare http(s) URLs and inline
 * code (shown as its text). Anything unmatched stays as literal text.
 */
fun parseInline(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    var bold = false
    fun emit(span: MdSpan) {
        if (plain.isNotEmpty()) {
            spans += MdSpan(plain.toString(), bold)
            plain.clear()
        }
        spans += span
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            // A marker toggles bold only when it has a partner: a stray "**"
            // stays as text instead of bolding the rest of the line.
            (text.startsWith("**", i) || text.startsWith("__", i)) &&
                (bold || text.indexOf(text.substring(i, i + 2), i + 2) >= 0) -> {
                if (plain.isNotEmpty()) {
                    spans += MdSpan(plain.toString(), bold)
                    plain.clear()
                }
                bold = !bold
                i += 2
            }
            c == '[' -> {
                val close = text.indexOf("](", i + 1)
                val end = if (close > 0) text.indexOf(')', close + 2) else -1
                if (close > 0 && end > 0) {
                    val label = text.substring(i + 1, close).replace("`", "")
                    val url = text.substring(close + 2, end).trim()
                    emit(MdSpan(label, bold, url.takeIf { isSafeLink(it) }))
                    i = end + 1
                } else {
                    plain.append(c)
                    i++
                }
            }
            c == '`' -> i++
            text.startsWith("https://", i) || text.startsWith("http://", i) -> {
                var end = i
                while (end < text.length && !text[end].isWhitespace() && text[end] != ')') end++
                // Sentence punctuation right after a URL isn't part of it.
                while (end > i && text[end - 1] in ".,;:!?") end--
                val url = text.substring(i, end)
                emit(MdSpan(url, bold, url.takeIf { isSafeLink(it) }))
                i = end
            }
            else -> {
                plain.append(c)
                i++
            }
        }
    }
    if (plain.isNotEmpty()) spans += MdSpan(plain.toString(), bold)
    // Merge neighbours that ended up with the same style.
    return spans.fold(mutableListOf()) { acc, span ->
        val last = acc.lastOrNull()
        if (last != null && last.url == null && span.url == null && last.bold == span.bold) {
            acc[acc.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            acc += span
        }
        acc
    }
}

/** Only web links open anything; `javascript:`, `file:`, intents and the rest stay text. */
internal fun isSafeLink(url: String): Boolean {
    val lower = url.lowercase()
    return (lower.startsWith("https://") || lower.startsWith("http://")) &&
        url.none { it.isWhitespace() } && url.length > lower.indexOf("://") + 3
}

/** [spans] as styled text, links underlined in [linkColor] and tappable. */
fun spansToAnnotatedString(spans: List<MdSpan>, linkColor: Color): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        val weight = if (span.bold) FontWeight.SemiBold else null
        val url = span.url
        if (url != null) {
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(
                        SpanStyle(color = linkColor, fontWeight = weight, textDecoration = TextDecoration.Underline)
                    )
                )
            ) { append(span.text) }
        } else if (weight != null) {
            withStyle(SpanStyle(fontWeight = weight)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}

/** A single tappable URL, as the release link is shown. */
fun linkAnnotatedString(url: String, linkColor: Color): AnnotatedString =
    spansToAnnotatedString(listOf(MdSpan(url, url = url.takeIf { isSafeLink(it) })), linkColor)

@Composable
fun ReleaseNotesText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> Text(
                    spansToAnnotatedString(block.spans, linkColor),
                    style = if (block.level <= 2) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
                is MdBlock.ListItem -> Row(
                    modifier = Modifier.padding(start = Spacing.md * block.depth),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(block.marker, style = MaterialTheme.typography.bodySmall)
                    Text(spansToAnnotatedString(block.spans, linkColor), style = MaterialTheme.typography.bodySmall)
                }
                is MdBlock.Paragraph -> Text(
                    spansToAnnotatedString(block.spans, linkColor),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
