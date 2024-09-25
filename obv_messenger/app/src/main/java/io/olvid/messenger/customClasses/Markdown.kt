/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.text.style.UnderlineSpan
import android.util.SparseIntArray
import android.widget.EditText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.text.getSpans
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.ObvBase64
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.spans.BlockQuoteSpan
import io.olvid.messenger.customClasses.spans.CodeBlockSpan
import io.olvid.messenger.customClasses.spans.CodeSpan
import io.olvid.messenger.customClasses.spans.ListItemSpan
import io.olvid.messenger.customClasses.spans.OrderedListItemSpan
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.discussion.Utils
import io.olvid.messenger.discussion.mention.MentionUrlSpan
import io.olvid.messenger.discussion.message.INLINE_CONTENT_TAG
import io.olvid.messenger.discussion.message.MENTION_ANNOTATION_TAG
import io.olvid.messenger.discussion.message.QUOTE_BLOCK_START_ANNOTATION
import io.olvid.messenger.settings.SettingsActivity
import okhttp3.internal.indexOfFirstNonAsciiWhitespace
import okhttp3.internal.indexOfLastNonAsciiWhitespace
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
    val inline: Boolean
        get() = false
}

class MarkdownDelimiter(color: Int) : ForegroundColorSpan(color), MarkdownSpan

class MarkdownBold : StyleSpan(Typeface.BOLD), MarkdownSpan {
    override val delimiter: String = MarkdownTag.BOLD.delimiter
    override val inline: Boolean = true
}

class MarkdownItalic : StyleSpan(Typeface.ITALIC), MarkdownSpan {
    override val delimiter: String = MarkdownTag.ITALIC.delimiter
    override val inline: Boolean = true
}

class MarkdownHeading(val level: Int) : TextAppearanceSpan(
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
    override val inline: Boolean = true
}

class MarkdownListItem(override val level: Int = 0) : ListItemSpan(level), MarkdownSpan {
    override val singleMarker: Boolean
        get() = true
    override val delimiter: String = MarkdownTag.LIST_ITEM.delimiter
}

class MarkdownOrderedListItem(
    override val level: Int = 0,
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
    override val inline: Boolean = true
}

class MarkdownQuote :
    BlockQuoteSpan(),
    MarkdownSpan {
    override val singleMarker: Boolean
        get() = true
    override val delimiter: String = MarkdownTag.QUOTE.delimiter
}

fun EditText.insertMarkdown(markdownSpan: MarkdownSpan?) {
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
        } else if (markdownSpan.inline) {
            var index = selectionStart
            text.subSequence(selectionStart, selectionEnd).lines().forEach() {
                if (it.indexOfFirstNonAsciiWhitespace() < it.indexOfLastNonAsciiWhitespace()) {
                    text.insert(index + it.indexOfFirstNonAsciiWhitespace(), markdownSpan.delimiter)
                    index += markdownSpan.delimiter.length
                    if (!markdownSpan.singleMarker) {
                        text.insert(
                            index + it.indexOfLastNonAsciiWhitespace(),
                            markdownSpan.delimiter
                        )
                        index += markdownSpan.delimiter.length
                    }
                }
                index += it.length + 1
            }
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

class Visitor(val editable: Editable, private val highlightColor: Int, private val spanFlag: Int) : AbstractVisitor() {

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
        editable.setMarkdownSpanFromNode(MarkdownItalic(), emphasis, highlightColor, lineOffsets, spanFlag)
    }

    private val listCount = SparseIntArray()
    override fun visit(listItem: ListItem) {
        super.visit(listItem)
        val columnIndex = listItem.sourceSpans.first().columnIndex
        if (columnIndex != 0) {
            val lineStart = lineOffsets[listItem.sourceSpans.first().lineIndex]
            if (editable.substring(lineStart, lineStart + columnIndex).isNotBlank()) {
                // this list item is inlined (it does not start the line), don't handle
                return
            }
        }
        if (listItem.isOrderedList()) {
            val count = listCount.get(
                listItem.parent.hashCode(),
                (listItem.parent as OrderedList).markerStartNumber ?: 0
            )
            editable.setMarkdownSpanFromNode(
                MarkdownOrderedListItem(listItem.listLevel(), "$count. "),
                listItem,
                highlightColor,
                lineOffsets,
                spanFlag
            )
            listCount.put(listItem.parent.hashCode(), count + 1)
        } else {
            editable.setMarkdownSpanFromNode(
                MarkdownListItem(listItem.listLevel()),
                listItem,
                highlightColor,
                lineOffsets,
                spanFlag
            )
        }
    }

    override fun visit(orderedList: OrderedList) {
        super.visit(orderedList)
    }

    override fun visit(code: Code) {
        super.visit(code)
        editable.setMarkdownSpanFromNode(
            MarkdownCode(), code, highlightColor, lineOffsets, spanFlag
        )
    }

    override fun visit(codeBlock: FencedCodeBlock) {
        super.visit(codeBlock)
        editable.setMarkdownSpanFromNode(
            MarkdownCodeBlock(), codeBlock, highlightColor, lineOffsets, spanFlag
        )
    }

    override fun visit(strongEmphasis: StrongEmphasis) {
        super.visit(strongEmphasis)
        editable.setMarkdownSpanFromNode(
            MarkdownBold(),
            strongEmphasis,
            highlightColor,
            lineOffsets,
            spanFlag
        )
    }

    override fun visit(blockQuote: BlockQuote) {
        super.visit(blockQuote)
        if (blockQuote.sourceSpans.first().length > 1) {
            editable.setMarkdownSpanFromNode(
                MarkdownQuote(),
                blockQuote,
                highlightColor,
                lineOffsets,
                spanFlag
            )
        }
    }

    override fun visit(heading: Heading) {
        super.visit(heading)
        if (heading.sourceSpans.first().length > heading.level && heading.level < 6) {
            editable.setMarkdownSpanFromNode(
                MarkdownHeading(heading.level), heading, highlightColor, lineOffsets, spanFlag
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
                    lineOffsets,
                    spanFlag
                )
            }
        }
    }
}

// highlightColor is for delimiters, 0 means no highlight and removes markdown delimiters
fun Editable.formatMarkdown(highlightColor: Int, spanFlag: Int = SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) {
    // clear spans
    getSpans<MarkdownSpan>().forEach {
        removeSpan(it)
    }

    // parse and set spans
    try {
        parser.parse(this.toString()).accept(Visitor(this, highlightColor, spanFlag))
    } catch (ex: Exception) {
        Logger.w("Markdown processing issue ${ex.message}")
    }

    // remove delimiters if not highlighting
    if (highlightColor == 0) {
        try {
            getSpans<MarkdownDelimiter>().onEach {
                if (getSpanStart(it) >= 0 && getSpanEnd(it) <= length) {
                    delete(getSpanStart(it), getSpanEnd(it))
                }
                removeSpan(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            clearSpans()
        }
    }
}

private fun Editable.setMarkdownSpanFromNode(
    markdownSpan: MarkdownSpan,
    node: Node,
    highlightColor: Int,
    lineOffsets: List<Int>,
    spanFlag: Int = SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
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
                    spanFlag
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex],
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + node.openingDelimiter.length,
                    spanFlag
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length - node.closingDelimiter.length,
                    sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex] + sourceSpan.length,
                    spanFlag
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
                    spanFlag
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    start,
                    start + 1,
                    spanFlag
                )
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    end - 1,
                    end,
                    spanFlag
                )
            }
        }

        is FencedCodeBlock -> {
            node.sourceSpans.first().let { sourceSpan ->
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                val blockStart =
                    node.sourceSpans[1].columnIndex + lineOffsets[node.sourceSpans[1].lineIndex]
                val blockEnd = (blockStart + node.literal.length).coerceAtMost(length)
                val end =
                    (node.sourceSpans.last().columnIndex + lineOffsets[node.sourceSpans.last().lineIndex] + node.sourceSpans.last().length).coerceAtLeast(
                        blockStart
                    )
                val isClosed =
                    node.sourceSpans.size > 1 && node.sourceSpans.last().length == node.fenceLength && substring(
                        end - (node.openingFenceLength ?: 0),
                        end
                    ) == node.fenceChar.toString().repeat(node.openingFenceLength ?: 0)
                setSpan(
                    MarkdownDelimiter(color = highlightColor),
                    start,
                    blockStart,
                    spanFlag
                )
                if (isClosed) {
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        end - node.fenceLength,
                        end,
                        spanFlag
                    )
                }
                if (node.sourceSpans.size > 1) {
                    setSpan(
                        markdownSpan,
                        blockStart,
                        blockEnd,
                        spanFlag
                    )
                }
            }
        }

        is Heading -> {
            node.sourceSpans.first().let { sourceSpan ->
                val start = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                val delimiterStart = start + substring(start).indexOfFirstNonAsciiWhitespace()
                if (get(delimiterStart).toString() == MarkdownTag.HEADING.delimiter) { // ensure heading is triggered by # and not underlying - or =
                    val remainingString = substring(delimiterStart)
                    val delimiterLength = remainingString.indexOfFirstWhitespace()
                    val headingStart =
                        (delimiterStart + delimiterLength + remainingString.substring(
                            delimiterLength
                        ).indexOfFirstNonAsciiWhitespace()
                            .coerceAtLeast(0))
                            .coerceAtMost(length)
                    setSpan(
                        markdownSpan,
                        headingStart,
                        (start + sourceSpan.length).coerceAtLeast(headingStart),
                        spanFlag
                    )
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        start,
                        headingStart,
                        spanFlag
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
                    spanFlag
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
                        spanFlag
                    )
                }
                if (index == node.sourceSpans.lastIndex && end < length && get(end) in lineSeparators) { // highlight last new line for deletion
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        end,
                        end + 1,
                        spanFlag
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
                            spanFlag
                        )
                    }
                    val delimiterStart = sourceSpan.columnIndex + lineOffsets[sourceSpan.lineIndex]
                    val remainingString = substring(delimiterStart)
                    val delimiterLength =
                        remainingString.indexOfFirstWhitespace(remainingString.indexOfFirstNonAsciiWhitespace())
                    setSpan(
                        MarkdownDelimiter(color = highlightColor),
                        delimiterStart,
                        (delimiterStart + delimiterLength + remainingString.substring(
                            delimiterLength
                        ).indexOfFirstNonAsciiWhitespace()
                            .coerceAtLeast(0))
                            .coerceAtMost(length),
                        spanFlag
                    )
                }
            }
        }
    }
}

fun String.indexOfFirstWhitespace(startIndex: Int = 0): Int {
    for (i in startIndex until length) {
        when (this[i]) {
            '\t', '\u000C', ' ' -> return i
            else -> Unit
        }
    }
    return length
}

fun String.formatMarkdown(spanFlag: Int = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE): SpannableStringBuilder {
    return SpannableStringBuilder(this).apply {
        formatMarkdown(0, spanFlag)
    }
}

fun SpannableString.formatMarkdown(spanFlag: Int): SpannableStringBuilder {
    return SpannableStringBuilder(this).apply {
        formatMarkdown(0, spanFlag)
    }
}

data class MentionStringAnnotation(
    val bytesOwnedIdentityString: String,
    val start: Int,
    val end: Int
)

val headings = mapOf(1 to 27.sp, 2 to 24.sp, 3 to 21.sp, 4 to 18.sp, 5 to 18.sp)
val bullets = listOf("• ", "◦ ", "▪ ")

val lineSeparatorsRegex = Regex("(\r\n|\r|\n)")
val lineSeparators = listOf("\r\n", "\r", "\n")

fun SpannableStringBuilder.removeSpanEndLinebreak(span: MarkdownSpan) {
    lineSeparatorsRegex.find(this, getSpanStart(span))?.let {
        if (it.range.first == getSpanEnd(span)) {
            delete(it.range.first, it.range.last + 1)
        }
    }
}

fun SpannableStringBuilder.removePreviousLinebreak(start: Int) {
    for (separator in lineSeparators) {
        if (start >= separator.length
            && indexOf(separator, start - separator.length) == start - separator.length) {
            delete(start - separator.length, start)
            return
        }
    }
}

fun AnnotatedString.formatMarkdown(
    complete: Boolean = false,
    context: Context? = null,
    bytesOwnedIdentity: ByteArray? = null,
    message: Message? = null,
    backgroundColor: Color = Color.White.copy(alpha = .25f)
): AnnotatedString {
    try {
        val spanStyles = emptyList<AnnotatedString.Range<SpanStyle>>().toMutableList()
        val paragraphSpans = emptyList<MarkdownSpan>().toMutableList()
        val quoteStartIndexes = emptyList<Int>().toMutableList()
        val paragraphStyles = emptyList<AnnotatedString.Range<ParagraphStyle>>().toMutableList()
        val mentionStringAnnotations = mutableListOf<MentionStringAnnotation>()
        val spannableString = SpannableString(this).apply {
            if (context != null && message != null && bytesOwnedIdentity != null) {
                Utils.applyMentionSpans(context, bytesOwnedIdentity, message, this)
            }
        }.formatMarkdown(Spannable.SPAN_INCLUSIVE_EXCLUSIVE).apply {
            // first do all operations that may shift offsets (insert of delete)
            if (complete) {
                getSpans<MarkdownListItem>(0, length).forEach { span ->
                    // remove leading whitespace
                    delete(getSpanStart(span), toString().indexOfFirstNonAsciiWhitespace(getSpanStart(span)))
                    insert(
                        getSpanStart(span), bullets[span.level.coerceIn(
                            bullets.indices
                        )]
                    )
                    removeSpanEndLinebreak(span)
                    removePreviousLinebreak(getSpanStart(span))
                    paragraphSpans.add(span)
                }
                getSpans<MarkdownOrderedListItem>(0, length).forEach { span ->
                    // remove leading whitespace
                    delete(getSpanStart(span), toString().indexOfFirstNonAsciiWhitespace(getSpanStart(span)))
                    insert(
                        getSpanStart(span), span.delimiter
                    )
                    removeSpanEndLinebreak(span)
                    removePreviousLinebreak(getSpanStart(span))
                    paragraphSpans.add(span)
                }
                // remove ending line-breaks for code blocks before we start saving offsets
                getSpans<MarkdownCodeBlock>(0, length).forEach { span ->
                    removePreviousLinebreak(getSpanEnd(span))
                }

                getSpans<MarkdownQuote>(0, length).forEach { span ->
                    removeSpanEndLinebreak(span)
                    removePreviousLinebreak(getSpanStart(span))
                    insert(
                        getSpanStart(span), "\u200B"
                    )
                    paragraphSpans.add(span)
                }
            }
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
            getSpans<MentionUrlSpan>().forEach { mention ->
                val start = getSpanStart(mention)
                val end = getSpanEnd(mention)
                mention.color?.let {
                    spanStyles.add(
                        AnnotatedString.Range(
                            item = SpanStyle(color = Color(it)),
                            start,
                            end
                        )
                    )
                }
                mention.userIdentifier?.let {
                    mentionStringAnnotations.add(
                        MentionStringAnnotation(
                            ObvBase64.encode(it),
                            start,
                            end
                        )
                    )
                }
            }
            if (complete) {
                getSpans<MarkdownHeading>(0, length).forEach { span ->
                    spanStyles.add(
                        AnnotatedString.Range(
                            item = SpanStyle(
                                fontSize = headings.getOrDefault(span.level, 18.sp),
                                fontWeight = FontWeight.Medium
                            ),
                            start = getSpanStart(span),
                            end = getSpanEnd(span)
                        )
                    )
                    paragraphStyles.add(
                        AnnotatedString.Range(
                            item = ParagraphStyle(
                                lineHeight = headings.getOrDefault(span.level, 18.sp)
                            ),
                            start = getSpanStart(span),
                            end = getSpanEnd(span)
                        )
                    )
                }

                getSpans<MarkdownCodeBlock>(0, length).forEach { span ->
                    spanStyles.add(
                        AnnotatedString.Range(
                            item = SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = backgroundColor,
                                baselineShift = BaselineShift(0.2f)
                            ),
                            start = getSpanStart(span),
                            end = getSpanEnd(span)
                        )
                    )
                }
                getSpans<MarkdownCode>(0, length).forEach { span ->
                    spanStyles.add(
                        AnnotatedString.Range(
                            item = SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = backgroundColor,
                                baselineShift = BaselineShift(0.2f)
                            ),
                            start = getSpanStart(span),
                            end = getSpanEnd(span)
                        )
                    )
                }
                getSpans<MarkdownQuote>(0, length).forEach { span ->
                    quoteStartIndexes.add(getSpanStart(span))
                }
            }
            paragraphSpans.forEach { span ->
                when (span) {
                    is MarkdownListItem -> {
                        val margin = .8f*(span.level) + .4f
                        paragraphStyles.add(
                            AnnotatedString.Range(
                                item = ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = margin.em,
                                        restLine = (margin + .6f).em
                                    ),
                                ),
                                start = getSpanStart(span),
                                end = getSpanEnd(span)
                            )
                        )
                    }
                    is MarkdownOrderedListItem -> {
                        val margin = 1.1f*(span.level) + .4f
                        paragraphStyles.add(
                            AnnotatedString.Range(
                                item = ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = margin.em,
                                        restLine = (margin + 1.05f).em
                                    ),
                                ),
                                start = getSpanStart(span),
                                end = getSpanEnd(span)
                            )
                        )
                    }
                    is MarkdownQuote -> {
                        paragraphStyles.add(
                            AnnotatedString.Range(
                                item = ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = 2.em,
                                        restLine = 2.em
                                    ),
                                ),
                                start = getSpanStart(span),
                                end = getSpanEnd(span)
                            )
                        )
                    }

                    else -> {}
                }
            }
        }
        return AnnotatedString.Builder(
            AnnotatedString(
                text = spannableString.toString(),
                spanStyles = spanStyles,
                paragraphStyles = paragraphStyles.sortedBy { it.start }
            )
        ).apply {
            if (complete) {
                quoteStartIndexes.forEach {
                    addStringAnnotation(
                        INLINE_CONTENT_TAG,
                        QUOTE_BLOCK_START_ANNOTATION,
                        it,
                        it + 1
                    )
                }
            }
            mentionStringAnnotations.forEach {
                addStringAnnotation(
                    MENTION_ANNOTATION_TAG,
                    it.bytesOwnedIdentityString,
                    it.start,
                    it.end
                )
            }
        }.toAnnotatedString()
    } catch (e: Exception) {
        e.printStackTrace()
        return this
    }
}

fun String.formatSingleLineMarkdown(): AnnotatedString {
    return this.lines().formatSingleLineMarkdown()
}

fun List<String>.formatSingleLineMarkdown(): AnnotatedString {
    this.filter { it.trim().isNotEmpty() }
        .take(5) // only keep the first 5 lines
        .map { line ->
            return@map AnnotatedString(line.trim()).formatMarkdown()
        }
        .let {
            return if (it.isEmpty())
                AnnotatedString("")
            else
                it.reduce { acc, s ->
                    acc + AnnotatedString(" ") + s
                }
        }
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