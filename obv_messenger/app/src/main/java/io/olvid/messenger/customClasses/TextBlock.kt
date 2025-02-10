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

import android.graphics.Rect
import io.olvid.messenger.App
import io.olvid.messenger.databases.AppDatabase

data class TextBlock(val text: String, val boundingBox: Rect?, val elements: List<TextElement>?)
data class TextElement(val text: String, val boundingBox: Rect?)

fun List<TextBlock>.getText(): String {
    return joinToString(" ") { it.text }
}

fun List<TextBlock>.saveToDatabase(
    messageId: Long,
    fyleId: Long
) {
    forEach { textBlock ->
        val textBlockEntity = io.olvid.messenger.databases.entity.TextBlock(
            id = 0,
            messageId = messageId,
            fyleId = fyleId,
            text = textBlock.text,
            boundingBox = textBlock.boundingBox,
            isBlock = true,
            parentBlockId = null
        )
        App.runThread {
            textBlock.elements?.toEntities(
                messageId = messageId,
                fyleId = fyleId,
                parentBlockId = AppDatabase.getInstance().fyleMessageTextBlockDao()
                    .insert(textBlockEntity)
            )?.let {
                if (it.size > 1) { // don't insert solo element, the block is already inserted
                    AppDatabase.getInstance().fyleMessageTextBlockDao().insertAll(textBlocks = it)
                }
            }
        }
    }
}

fun List<TextElement>.toEntities(
    messageId: Long,
    fyleId: Long,
    parentBlockId: Long
): Array<io.olvid.messenger.databases.entity.TextBlock> {
    return map {
        io.olvid.messenger.databases.entity.TextBlock(
            id = 0,
            messageId = messageId,
            fyleId = fyleId,
            text = it.text,
            boundingBox = it.boundingBox,
            isBlock = false,
            parentBlockId = parentBlockId
        )
    }.toTypedArray()
}

