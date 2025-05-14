/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.customClasses

import android.icu.lang.UCharacter
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat
import androidx.core.util.PatternsCompat
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiSpan
import io.olvid.messenger.customClasses.StringUtils.isEmojiCodepoint
import io.olvid.messenger.customClasses.StringUtils.unAccentPattern
import java.text.Normalizer
import java.util.BitSet
import java.util.Locale


class StringUtils2 {
    companion object {
        fun getLink(s: String?): Pair<String, String?>? {
            s?.let { _s ->
                val source = SpannableString(_s)
                val urlSpan = source
                    .apply {
                        LinkifyCompat.addLinks(
                            this,
                            PatternsCompat.WEB_URL,
                            "https://",
                            { s: CharSequence?, start: Int, _: Int ->
                                s?.let {
                                    (start == 0) || (it[start - 1] != '@')
                                } ?: false
                            },
                            null
                        )
                    }
                    .getSpans(0, _s.length, URLSpan::class.java).firstOrNull()
                return urlSpan?.let {
                    Pair(
                        _s.subSequence(source.getSpanStart(it), source.getSpanEnd(it)).toString(),
                        it.url
                    )
                }
            }
            return null
        }

        fun getExtensionFromFilename(fileName: String): String? {
            val pos = fileName.lastIndexOf('.')
            if (pos != -1) {
                return fileName.substring(pos + 1)
            }
            return null
        }


        fun normalize(source: CharSequence?): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                UCharacter.toLowerCase(unAccentPattern.matcher(Normalizer.normalize(source, Normalizer.Form.NFKD)).replaceAll(""))
            } else {
                unAccentPattern.matcher(Normalizer.normalize(source, Normalizer.Form.NFKD)).replaceAll("").lowercase(Locale.getDefault())
            }
        }

        fun computeHighlightRanges(input: String, unaccentedRegexes: List<Regex>): List<Pair<Int, Int>> {
            val normalizedInput: String = normalize(input)
            val positionMapping = PositionsMapping(input)

            val highlighted = BitSet(input.length)
            unaccentedRegexes.forEach { regex ->
                regex.findAll(normalizedInput).forEach {
                    highlighted.set(
                        positionMapping.getIndex(it.range.first, true),
                        positionMapping.getIndex(it.range.last, false) + 1 // range.last is inclusive!
                    )
                }
            }


            if (highlighted.isEmpty) {
                return emptyList()
            }

            val result: MutableList<Pair<Int, Int>> = ArrayList()
            var start = highlighted.nextSetBit(0)
            while (start != -1) {
                val end = highlighted.nextClearBit(start + 1)
                result.add(Pair(start, end))
                start = highlighted.nextSetBit(end)
            }

            return result
        }
    }
}

fun SpannableString.linkify(): SpannableString {
    return apply {
        LinkifyCompat.addLinks(
            this,
            Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
        )
        getSpans(0, length, URLSpan::class.java).onEach { urlSpan ->
            setSpan(
                SecureUrlSpan(urlSpan.url),
                getSpanStart(urlSpan),
                getSpanEnd(urlSpan),
                getSpanFlags(urlSpan)
            )
            removeSpan(urlSpan)
        }
    }
}
fun List<String>.fullTextSearchEscape(): String {
    return this.joinToString(separator = " ", transform = {
        "$it*"
    })
}


data class PositionsMapping(val input: String) {

    // Differences from normalized text to input text.
    private var deltas: MutableMap<Int, Int> = LinkedHashMap()

    // Indexes in normalized text that are ignored in input text.
    private val gaps: BitSet = BitSet(input.length)

    init {
        compute()
    }

    /**
     * @param pos the position in normalize text
     * @param start whether the given position represents start or end of a range.
     * @return the position in the input text.
     */
    fun getIndex(pos: Int, start: Boolean): Int {
        // this SHOULD never happen, but it happened during a test...
        if ((pos < 0) || (pos >= gaps.size())) {
            return pos
        }
        var p = pos
        if (gaps[pos]) {
            // If the position is in a gap, find the first position outside it.
            p = if (start) gaps.previousClearBit(p) else gaps.nextClearBit(p)
        }
        var offset = 0
        // Deals with all deltas before the given position.
        for (delta in deltas.entries) {
            if (delta.key > p) {
                break
            }
            offset += delta.value
        }
        return p + offset
    }

    private fun compute() {
        // Compute Diacritical Marks and ligature delta from normalisation
        var cur = 0
        for (i in input.indices) {
            val ch = input.substring(i, i + 1)
            val normalized = StringUtils2.normalize(ch)
            // Compute the delta between a substring of length one input and its normalization
            val delta = ch.length - normalized.length
            if (delta != 0) {
                deltas[cur + normalized.length] = delta
            }
            // If the normalized is larger that the input string, mark sub position as gap.
            if (delta < 0) {
                gaps[cur + 1] = cur + normalized.length
            }
            cur += normalized.length
        }
    }
}

fun String.getCodePoints(): IntArray {
    /**
     * Returns an array of code points from this string.
     *
     * This function is equivalent to the `codePoints()` method in Java's String class.
     * It iterates through the string and extracts each Unicode code point,
     * handling surrogate pairs correctly.
     *
     * @return An IntArray containing the Unicode code points of the string.
     */
    val codePoints = mutableListOf<Int>()
    var i = 0
    while (i < length) {
        val codePoint = codePointAt(i)
        codePoints.add(codePoint)
        i += Character.charCount(codePoint)
    }
    return codePoints.toIntArray()
}

fun String.isStringOnlyEmojis(): Boolean {
    if (isEmpty()) {
        return false
    }

    for (codePoint in getCodePoints()) {
        if (!isEmojiCodepoint(codePoint)) {
            return false
        }
    }
    return true
}


fun String.getShortEmojis(maxLength: Int): List<String> {
    val emojiSequence = EmojiCompat.get().process(this, 0, length)
    if (emojiSequence is Spanned) {
        var regionEnd: Int
        var regionStart = 0
        return buildList {
            val spans = emojiSequence.getSpans(
                regionStart, emojiSequence.length,
                EmojiSpan::class.java
            ).asList()
            if (spans.isEmpty() || spans.size > maxLength || isStringOnlyEmojis().not()) {
                return emptyList()
            }
            spans.forEach {
                regionEnd = emojiSequence.getSpanEnd(it)
                add(emojiSequence.subSequence(regionStart, regionEnd).toString())
                regionStart = regionEnd
            }
        }
    }
    return emptyList()
}
