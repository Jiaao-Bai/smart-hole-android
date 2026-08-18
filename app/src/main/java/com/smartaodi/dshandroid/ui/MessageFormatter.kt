package com.smartaodi.dshandroid.ui

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

internal sealed interface MessageBlock {
    data class Prose(
        val inlines: List<InlineSpan>,
        val headingLevel: Int? = null,
    ) : MessageBlock

    data class CodeBlock(
        val text: String,
        val language: String = "",
    ) : MessageBlock

    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val items: List<List<MessageBlock>>,
    ) : MessageBlock

    data class Quote(val blocks: List<MessageBlock>) : MessageBlock

    data class Table(
        val header: List<List<InlineSpan>>,
        val rows: List<List<List<InlineSpan>>>,
    ) : MessageBlock

    data object Divider : MessageBlock
}

internal data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val url: String? = null,
)

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val url: String? = null,
)

private val markdownParser = Parser.builder()
    .extensions(
        listOf(
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TablesExtension.create(),
        ),
    )
    .build()

internal fun parseMessageBlocks(text: String): List<MessageBlock> {
    if (text.isBlank()) return emptyList()
    return blockChildren(markdownParser.parse(text))
}

private fun blockChildren(parent: Node): List<MessageBlock> = buildList {
    var child = parent.firstChild
    while (child != null) {
        block(child)?.let(::add)
        child = child.next
    }
}

private fun block(node: Node): MessageBlock? = when (node) {
    is Heading -> MessageBlock.Prose(inlineChildren(node), headingLevel = node.level)
    is Paragraph -> MessageBlock.Prose(inlineChildren(node))
    is FencedCodeBlock -> MessageBlock.CodeBlock(node.literal.trimEnd(), node.info.trim())
    is IndentedCodeBlock -> MessageBlock.CodeBlock(node.literal.trimEnd())
    is BulletList -> listBlock(node, ordered = false, start = 1)
    is OrderedList -> listBlock(node, ordered = true, start = node.markerStartNumber)
    is BlockQuote -> MessageBlock.Quote(blockChildren(node))
    is TableBlock -> tableBlock(node)
    is ThematicBreak -> MessageBlock.Divider
    is HtmlBlock -> MessageBlock.Prose(listOf(InlineSpan(node.literal)))
    else -> blockChildren(node).takeIf { it.isNotEmpty() }?.let(MessageBlock::Quote)
}

private fun listBlock(node: Node, ordered: Boolean, start: Int): MessageBlock.ListBlock {
    val items = buildList {
        var item = node.firstChild
        while (item != null) {
            add(blockChildren(item))
            item = item.next
        }
    }
    return MessageBlock.ListBlock(ordered, start, items)
}

private fun tableBlock(table: TableBlock): MessageBlock.Table {
    var header = emptyList<List<InlineSpan>>()
    val rows = mutableListOf<List<List<InlineSpan>>>()
    var section = table.firstChild
    while (section != null) {
        when (section) {
            is TableHead -> header = tableRows(section).firstOrNull().orEmpty()
            is TableBody -> rows += tableRows(section)
        }
        section = section.next
    }
    return MessageBlock.Table(header, rows)
}

private fun tableRows(section: Node): List<List<List<InlineSpan>>> = buildList {
    var row = section.firstChild
    while (row != null) {
        if (row is TableRow) {
            val cells = buildList {
                var cell = row.firstChild
                while (cell != null) {
                    if (cell is TableCell) add(inlineChildren(cell))
                    cell = cell.next
                }
            }
            add(cells)
        }
        row = row.next
    }
}

private fun inlineChildren(parent: Node): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    appendInlineChildren(parent, InlineStyle(), spans)
    return mergeAdjacent(spans)
}

private fun appendInlineChildren(parent: Node, style: InlineStyle, spans: MutableList<InlineSpan>) {
    var child = parent.firstChild
    while (child != null) {
        when (child) {
            is Text -> spans += style.span(child.literal)
            is Code -> spans += style.copy(code = true).span(child.literal)
            is SoftLineBreak -> spans += style.span("\n")
            is HardLineBreak -> spans += style.span("\n")
            is StrongEmphasis -> appendInlineChildren(child, style.copy(bold = true), spans)
            is Emphasis -> appendInlineChildren(child, style.copy(italic = true), spans)
            is Strikethrough -> appendInlineChildren(child, style.copy(strikethrough = true), spans)
            is Link -> appendInlineChildren(child, style.copy(url = safeUrl(child.destination)), spans)
            is Image -> {
                val alt = mutableListOf<InlineSpan>()
                appendInlineChildren(child, style, alt)
                spans += style.span(alt.joinToString("") { it.text }.ifBlank { "图片" })
            }
            is HtmlInline -> spans += style.span(child.literal)
            else -> appendInlineChildren(child, style, spans)
        }
        child = child.next
    }
}

private fun InlineStyle.span(text: String) = InlineSpan(
    text = text,
    bold = bold,
    italic = italic,
    code = code,
    strikethrough = strikethrough,
    url = url,
)

private fun mergeAdjacent(spans: List<InlineSpan>): List<InlineSpan> = buildList {
    for (span in spans) {
        val previous = lastOrNull()
        if (previous != null && previous.copy(text = "") == span.copy(text = "")) {
            this[lastIndex] = previous.copy(text = previous.text + span.text)
        } else {
            add(span)
        }
    }
}

private fun safeUrl(value: String): String? {
    val normalized = value.trim()
    return normalized.takeIf {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true) ||
            it.startsWith("mailto:", ignoreCase = true)
    }
}
