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

package io.olvid.messenger.designsystem.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCancellationBehavior
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.resetToBeginning
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.getCodePoints
import kotlinx.coroutines.launch


fun String.getEmojiAssetName() = "lottie_emoji/${
    getCodePoints().joinToString(separator = "_") { it.toString(16) }
}.zip"

@Composable
fun spInDp(value: Float): Dp = with(LocalDensity.current) { (1.175 * value * fontScale).dp }

@Composable
fun AnimatedEmoji(
    modifier: Modifier = Modifier,
    size: Float = 48f,
    shortEmoji: String,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    ignoreClicks: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val spec = LottieCompositionSpec.Asset(shortEmoji.getEmojiAssetName())
    val composition by rememberLottieComposition(spec = spec)
    composition?.let {
        val restProgress =
            composition?.getProgressForFrame(
                composition?.markers?.find { it.name == "rest" }?.startFrame
                    ?: composition?.endFrame ?: 0f
            ) ?: 0f
        val lottieAnimatable = rememberLottieAnimatable()
        LaunchedEffect(autoPlay) {
            if (autoPlay) {
                lottieAnimatable.animate(
                    iterations = if (loop) LottieConstants.IterateForever else 1,
                    composition = composition,
                    cancellationBehavior = LottieCancellationBehavior.OnIterationFinish
                )
            }
        }
        LaunchedEffect(lottieAnimatable.isPlaying) {
            if (lottieAnimatable.isPlaying.not()) {
                lottieAnimatable.snapTo(progress = restProgress)
            }
        }
        LottieAnimation(
            modifier = modifier
                .size(spInDp(size))
                .then(
                    if (ignoreClicks)
                        Modifier
                    else
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = false,
                                color = colorResource(R.color.almostBlack),
                                radius = (size *.6).dp
                            ),
                            onClick = {
                                onClick?.invoke()
                                if (loop.not()) {
                                    coroutineScope.launch {
                                        lottieAnimatable.resetToBeginning()
                                        lottieAnimatable.animate(
                                            composition = composition,
                                            continueFromPreviousAnimate = true,
                                            cancellationBehavior = LottieCancellationBehavior.OnIterationFinish
                                        )
                                    }
                                }
                            },
                            onLongClick = onLongClick,
                            onDoubleClick = onDoubleClick
                        )
                ),
            composition = composition,
            progress = { lottieAnimatable.progress })
    } ?: run {
        Text(text = shortEmoji, fontSize = size.sp, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
private fun AnimatedEmojiPreview() {
    AnimatedEmoji(shortEmoji = "😂")
}