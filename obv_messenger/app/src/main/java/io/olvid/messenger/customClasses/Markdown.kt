/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:JvmName("Markdown")

package io.olvid.messenger.customClasses

import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.text.style.UnderlineSpan
import android.util.SparseIntArray
import android.widget.EditText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.spans.BlockQuoteSpan
import io.olvid.messenger.customClasses.spans.CodeBlockSpan
import io.olvid.messenger.customClasses.spans.CodeSpan
import io.olvid.messenger.customClasses.spans.ListItemSpan
import io.olvid.messenger.customClasses.spans.OrderedListItemSpan
import io.olvid.messenger.settings.SettingsActivity
import okhttp3.internal.indexOfFirstNonAsciiWhitespace
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

val parser: Parser by lazy {
    val extensions = listOf(StrikethroughExtension.Builder().requireTwoTildes(true).build())
    Parser.builder().extensions(extensions)
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()
}

enum class MarkdownTag(val delimiter: String) {
    BOLD("**"),
    CODE("`"),
    CODE_BLOCK("```"),
    ITALIC("*"),
    STRIKETHROUGH("~~"),
    HEADING("#"),
    LIST_ITEM("- "),
    ORDERED_LIST_ITEM("1. "),
    QUOTE("> "),
}

interface MarkdownSpan {
    val singleMarker: Boolean
        get() = false
    val delimiter: String
        get() = ""
}

class MarkdownDelimiter(color: Int) : ForegroundColorSpan(color), MarkdownSpan

class MarkdownBold : StyleSpan(Typeface.BOLD), MarkdownSpan {
    override val delimiter: String = MarkdownTag.BOLD.delimiter
}

class MarkdownItalic : StyleSpan(Typeface.ITALIC), MarkdownSpan {
    override val delimiter: String = MarkdownTag.ITALIC.delimiter
}

class MarkdownHeading(level: Int) : TextAppearanceSpan(
    SettingsActivity.overrideContextScales(App.getContext()), when (level) {
        1 -> R.style.Heading1
        2 -> R.style.Heading2
        3 -> R.style.Heading3
        4 -> R.style.Heading4
        else -> R.style.Heading5
    }
), MarkdownSpan {
    override val singleMarker: Boolean
        get() = true
    override val delimiter: String = "${MarkdownTag.HEADING.delimiter.repeat(level)} "
}

class MarkdownStrikeThrough : StrikethroughSpan(), MarkdownSpan {
    override val delimiter: String = MarkdownTag.STRIKETHROUGH.delimiter
}

class MarkdownListItem(level: Int = 0) : ListItemSpan(level), MarkdownSpan {
    override val singleMarker: Boolean
        get() = true
    override val delimiter: String = MarkdownTag.LIST_ITEM.delimiter
}

class MarkdownOrderedListItem(
    level: Int = 0,
    number: String = MarkdownTag.ORDERED_LIST_ITEM.delimiter
) : OrderedListItemSpan(level, number), MarkdownSpan {
    override val singleMarker: Boolean
        get() = true
    override val delimiter: String = number
}

class MarkdownCodeBlock : CodeBlockSpan(), MarkdownSpan {
    override val delimiter: String = MarkdownTag.CODE_BLOCK.delimiter
}

class MarkdownCode : CodeSpan(), MarkdownSpan {
    override val delimiter: String = MarkdownTag.CODE.delimiter
}

class MarkdownQuote :
    BlockQuoteSpan(),
    MarkdownSpan {
    override val singleMarker: Boolean
        get() = true
    override val delimiter: String = MarkdownTag.QUOTE.delimiter
}

fun EditText.insertMarkdown(markdownSpan: MarkdownSpan?) {
    // TODO insert delimited on each lines
    if (markdownSpan != null && text != null && selectionStart > -1 && selectionEnd > selectionStart && selectionEnd <= length()) {
        if (markdownSpan is MarkdownListItem || markdownSpan is MarkdownOrderedListItem) {
            var index = selectionStart
            text.subSequence(selectionStart, selectionEnd).lines().forEach {
                text.insert(
                    index + it.indexOfFirstNonAsciiWhitespace(), markdownSpan.delimiter
                )
                index += it.length + markdownSpan.delimiter.length + 1
            }
        } else if (markdownSpan is MarkdownCode &&
            text.subSequence(selectionStart, selectionEnd).contains("\n")
        ) {
            // code block exception
            text.replace(
                selectionStart,
                selectionEnd,
                "${MarkdownTag.CODE_BLOCK.delimiter}\n${
                    text.subSequence(
                        selectionStart,
                        selectionEnd
                    )
                }\n${MarkdownTag.CODE_BLOCK.delimiter}"
            )
        } else {
            text.replace(
                selectionStart,
                selectionEnd,
                "${markdownSpan.delimiter}${
                    text.subSequence(
                        selectionStart,
                        selectionEnd
                    )
                }${if (markdownSpan.singleMarker) "" else markdownSpan.delimiter}"
            )
        }
    } else {
        setSelection(selectionEnd.coerceIn(0, length()))
    }
}

class Visitor(val editable: Editable, private val highlightColor: Int) : AbstractVisitor() {

    private val lineOffsets = mutableListOf(0).apply {
        var offset = 0
        val lineSeparators = listOf('\r', '\n')
        editable.lines().map { it.length }
            .forEach { lineLength ->
                offset += lineLength
                while (offset < editable.length && editable[offset] in lineSeparators) {
                    offset++
                }
                add(offset)
            }
    }

    override fun visit(emphasis: Emphasis) {
        super.visit(emphasis)
        editable.setMarkdownSpanFromNode(MarkdownItalic(), emphasis, highlightColor, lineOffsets)
    }

    private val listCount = SparseIntArray()
    override fun visit(listItem: ListItem) {
        super.visit(listItem)
        if (listItem.isOrderedList()) {
            val count = listCount.get(
                listItem.parent.hashCode(),
                (listItem.parent as OrderedList).startNumber
            )
            editable.setMarkdownSpanFromNode(
                MarkdownOrderedListItem(listItem.listLevel(), "$count. "),
                listItem,
                highlightColor,
                lineOffsets
            )
            listCount.put(listItem.parent.hashCode(), count + 1)
        } else {
            editable.setMarkdownSpanFromNode(
                MarkdownListItem(listItem.listLevel()),
                listItem,
                highlightColor,
                lineOffsets
            )
        }
    }

    override fun visit(orderedList: OrderedList) {
        super.visit(orderedList)
    }

    override fun visit(code: Code) {
        super.visit(code)
        editable.setMarkdownSpanFromNode(
            MarkdownCode(), code, highlightColor, lineOffsets
        )
    }

    override fun visit(codeBlock: FencedCodeBlock) {
        super.visit(codeBlock)
        editable.setMarkdownSpanFromNode(
            MarkdownCodeBlock(), codeBlock, highlightColor, lineOffsets
        )
    }

    override fun visit(strongEmphasis: StrongEmphasis) {
        super.visit(strongEmphasis)
        editable.setMarkdownSpanFromNode(
            MarkdownBold(),
            strongEmphasis,
            highlightColor,
            lineOffsets
        )
    }

    override fun visit(blockQuote: BlockQuote) {
        super.visit(blockQuote)
        if (blockQuote.sourceSpans.first().length > 1) {
            editable.setMarkdownSpanFromNode(
                MarkdownQuote(),
                blockQuote,
                highlightColor,
                lineOffsets
            )
        }
    }

    override fun visit(heading: Heading) {
        super.visit(heading)
        if (heading.sourceSpans.first().length > heading.level && heading.level < 6) {
            editable.setMarkdownSpanFromNode(
                MarkdownHeading(heading.level), heading, highlightColor, lineOffsets
            )
        }
    }

    override fun visit(customNode: CustomNode) {
        super.visit(customNode)
        when (customNode) {
            is Strikethrough -> {
                editable.setMarkdownSpanFromNode(
                    MarkdownStrikeThrough(),
                    customNode,
                    highlightColor,
                    lineOffsets
                )
            }
        }
    }
}

// highlightColor is for delimiters, 0 means no highlight and removes markdown delimiters
fun Editable.formatMarkdown(highlightColor: Int) {
    // clear spans
    getSpans<MarkdownSpan>().forEach {
        removeSpan(it)
    }

    // parse and set spans
    try {
        parser.parse(this.toString()).accept(Visitor(this, highlightColor))
    } catch (ex: Exception) {
        Logger.w("Markdown processing issue ${ex.message}")
    }

    // remove delimiters if not highlighting
    if (highlightColor == 0) {
        getSpans<MarkdownDelimiter>().onEach {
            delete(getSpanStart(it), getSpanEnd(it))
            removeSpan(it)
        }
    }
}

private fun Editable.setMarkdownSpanFromNode(
    markdownSpan: MarkdownSpan,
    node: Node,
    highlightColor: Int,
    lineOffsets: List<Int>
) {
    val lineSeparators = listOf('\r', '\n')
    when (node) {
        is Delimited -> {
            if (node.sourceSpans.size != 1) return // don't handle multiline for inline markdown has we want to keep user new lines
            node.sourceSpans.first().let { sourceSpan ->
                setSpan(
                    markdownSpan,
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + node.openingDelimiter.length,
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length - node.closingDelimiter.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex],
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + node.openingDelimiter.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length - node.closingDelimiter.length,
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        is Code -> {
            node.sourceSpans.first().let { sourceSpan ->
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                val end =
                    (node.sourceSpans.last().columnIndex + lineOffsets[node.sourceSpans.last().lineIndex] + node.sourceSpans.last().length).coerceAtLeast(
                        start
                    )
                setSpan(
                    markdownSpan,
                    start + 1,
                    end - 1,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    start,
                    start + 1,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    end - 1,
                    end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        is FencedCodeBlock -> {
            node.sourceSpans.first().let { sourceSpan ->
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                if (node.sourceSpans.size < 2 || (getOrElse(start + MarkdownTag.CODE_BLOCK.delimiter.length) { ' ' } in lineSeparators).not()) return
                val blockStart = node.sourceSpans[1].columnIndex + lineOffsets[node.sourceSpans[1].lineIndex]
                val blockEnd = (blockStart + node.literal.length).coerceAtMost(length)
                val end = (node.sourceSpans.last().columnIndex + lineOffsets[node.sourceSpans.last().lineIndex] + node.sourceSpans.last().length).coerceAtLeast(
                    blockStart
                )
                val isClosed = node.sourceSpans.size > 1 && node.sourceSpans.last().length == node.fenceLength && substring(
                    end - node.fenceLength,
                    end
                ) == node.fenceChar.toString().repeat(node.fenceLength)
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    start,
                    blockStart,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (isClosed) {
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        end - node.fenceLength,
                        end,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (node.sourceSpans.size > 1) {
                    setSpan(
                        markdownSpan,
                        blockStart,
                        blockEnd,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        is Heading -> {
            node.sourceSpans.first().let { sourceSpan ->
                val delimiterStart = indexOf(MarkdownTag.HEADING.delimiter.repeat(node.level))
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                if (get(delimiterStart).toString() == MarkdownTag.HEADING.delimiter) { // ensure heading is triggered by # and not underlying - or =
                    setSpan(
                        markdownSpan,
                        delimiterStart + node.level + 1,
                        (sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length).coerceAtLeast(
                            delimiterStart + node.level + 1
                        ),
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        start,
                        delimiterStart + node.level + 1,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        is BlockQuote -> {
            node.sourceSpans.first()?.let { sourceSpan ->
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                setSpan(
                    markdownSpan,
                    start,
                    (node.sourceSpans.last().columnIndex + lineOffsets[node.sourceSpans.last().lineIndex] + node.sourceSpans.last().length).coerceAtLeast(
                        start
                    ),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            node.sourceSpans.forEachIndexed { index, sourceSpan ->
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                val end = start + sourceSpan.length
                if (get(start) == '>') {
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        start,
                        start + 1 + substring(start + 1).indexOfFirst {
                            listOf(
                                ' ',
                                '\t',
                                '\u000C'
                            ).contains(it).not()
                        },
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (index == node.sourceSpans.lastIndex && end < length && get(end) in lineSeparators) { // highlight last new line for deletion
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        end,
                        end + 1,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        is ListItem -> {
            node.sourceSpans.first().let { sourceSpan ->
                if (sourceSpan.length > markdownSpan.delimiter.length) {
                    val start = lineOffsets[sourceSpan.lineIndex]
                    if (highlightColor == 0) { // don't render list item while editing
                        setSpan(
                            markdownSpan,
                            start,
                            (sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length).coerceAtLeast(
                                start
                            ),
                            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    val delimiterStart = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        delimiterStart,
                        (delimiterStart + markdownSpan.delimiter.length + substring(delimiterStart + markdownSpan.delimiter.length).indexOfFirstNonAsciiWhitespace()
                            .coerceAtLeast(0))
                            .coerceAtMost(length),
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }
}

fun SpannableString.formatMarkdown(): SpannableStringBuilder {
    return SpannableStringBuilder(this).apply {
        formatMarkdown(0)
    }
}

fun AnnotatedString.formatMarkdown(): AnnotatedString {
    val spanStyles = emptyList<AnnotatedString.Range<SpanStyle>>().toMutableList()
    val spannableString = SpannableString(this).formatMarkdown().toSpannable().apply {
        getSpans<StyleSpan>(0, length).forEach { span ->
            when (span.style) {
                Typeface.BOLD -> spanStyles.add(
                    AnnotatedString.Range(
                        item = SpanStyle(fontWeight = FontWeight.Bold),
                        start = getSpanStart(span),
                        end = getSpanEnd(span)
                    )
                )

                Typeface.ITALIC -> spanStyles.add(
                    AnnotatedString.Range(
                        item = SpanStyle(fontStyle = FontStyle.Italic),
                        start = getSpanStart(span),
                        end = getSpanEnd(span)
                    )
                )

                Typeface.BOLD_ITALIC -> spanStyles.add(
                    AnnotatedString.Range(
                        item = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        ),
                        start = getSpanStart(span),
                        end = getSpanEnd(span)
                    )
                )

                else -> {}
            }
        }
        getSpans<StrikethroughSpan>(0, length).forEach { span ->
            spanStyles.add(
                AnnotatedString.Range(
                    item = SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start = getSpanStart(span),
                    end = getSpanEnd(span)
                )
            )
        }
        getSpans<UnderlineSpan>(0, length).forEach { span ->
            spanStyles.add(
                AnnotatedString.Range(
                    item = SpanStyle(textDecoration = TextDecoration.Underline),
                    start = getSpanStart(span),
                    end = getSpanEnd(span)
                )
            )
        }
    }
    return AnnotatedString(text = spannableString.toString(), spanStyles = spanStyles)
}

fun Node.listLevel(): Int {
    var level = 0
    var parent: Node? = parent
    while (parent != null) {
        if (parent is ListItem) {
            level += 1
        }
        parent = parent.parent
    }
    return level
}

fun Node.isOrderedList(): Boolean {
    var parent = parent
    while (parent != null) {
        if (parent is OrderedList) {
            return true
        }
        if (parent is BulletList) {
            return false
        }
        parent = parent.parent
    }
    return false
}