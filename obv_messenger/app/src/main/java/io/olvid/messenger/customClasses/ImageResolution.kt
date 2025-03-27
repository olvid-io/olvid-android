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

import androidx.compose.ui.unit.Dp


class ImageResolution(resolutionString: String) {
    enum class KIND {
        IMAGE,
        ANIMATED,
        VIDEO,
    }

    var kind: KIND
    val width: Int
    val height: Int

    init {
        var rs = resolutionString
        if (rs.startsWith("a")) {
            kind = KIND.ANIMATED
            rs = rs.substring(1)
        } else if (rs.startsWith("v")) {
            kind = KIND.VIDEO
            rs = rs.substring(1)
        } else {
            kind = KIND.IMAGE
        }

        val parts = rs.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 2) {
            throw Exception()
        }

        width = parts[0].toInt()
        height = parts[1].toInt()
    }

    fun getPreferredHeight(displayWidth: Dp, wide: Boolean, wideCorrection: Dp): Dp {
        if (kind == KIND.ANIMATED) {
            return if (width > height) {
                (displayWidth * height) / width
            } else {
                displayWidth
            }
        } else {
            if (wide) {
                return displayWidth / 2 - wideCorrection
            }
            return displayWidth
        }
    }

    companion object {
        fun parseMultiple(resolutionsString: String?): Array<ImageResolution> {
            if (resolutionsString.isNullOrEmpty()) {
                return emptyList<ImageResolution>().toTypedArray()
            }
            return  resolutionsString.split(";").map { ImageResolution(it) }.toTypedArray()
        }
    }
}
