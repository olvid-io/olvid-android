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

package io.olvid.messenger.main

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string


@Composable
fun EstablishingChannel() {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(id = string.label_creating_channel).uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            lineHeight = 24.sp,
            color = colorResource(
                id = color.greyTint
            )
        )
        AndroidView(factory = { context ->
            AnimatedVectorDrawableCompat.create(
                context,
                drawable.dots
            )?.let { animated ->
                animated.registerAnimationCallback(object :
                    AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable) {
                        Handler(Looper.getMainLooper()).post { animated.start() }
                    }
                })
                animated.start()
                ImageView(context).apply {
                    setImageDrawable(animated)
                }
            } ?: kotlin.run {
                ImageView(context).apply { setImageResource(R.drawable.dots) }
            }
        })
    }
}

@Preview
@Composable
private fun EstablishingChannelPreview() {
    AppCompatTheme {
        EstablishingChannel()
    }
}