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

package io.olvid.messenger.google_services

import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.TextBlock
import io.olvid.messenger.customClasses.TextElement

class GoogleTextRecognizer {
    companion object {
        @JvmStatic
        fun recognizeTextFromImage(
            uri: Uri,
            onSuccess: (List<TextBlock>?) -> Unit
        ) {
            App.runThread {
                val recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(
                    InputImage.fromFilePath(
                        App.getContext(),
                        uri
                    )
                ).addOnSuccessListener {
                    val textBlocks = mutableListOf<TextBlock>()
                    it.textBlocks.forEach { block ->
                                textBlocks.add(
                                    TextBlock(
                                        text = block.text,
                                        boundingBox = block.boundingBox,
                                        elements = block.lines.flatMap { line ->
                                            line.elements.map { element ->
                                                TextElement(
                                                    element.text,
                                                    element.boundingBox
                                                )
                                            }
                                        }
                                    )
                                )
                    }
                    onSuccess(textBlocks)
                }.addOnFailureListener { e -> Logger.e("Text recognition failed", e) }
            }
        }
    }
}