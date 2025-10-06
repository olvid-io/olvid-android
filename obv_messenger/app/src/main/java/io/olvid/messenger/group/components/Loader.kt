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

package io.olvid.messenger.group.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec.RawRes
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import io.olvid.messenger.R

@Composable
fun Loader(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    state: LoaderState,
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    when (state) {
        LoaderState.NONE -> {}
        LoaderState.LOADING -> {
            CircularProgressIndicator(
                modifier = modifier,
                color = colorResource(id = R.color.olvid_gradient_light),
                strokeWidth = strokeWidth,
                strokeCap = StrokeCap.Round,
            )
        }

        LoaderState.SUCCESS -> {
            val successComposition by rememberLottieComposition(RawRes(R.raw.checkmark_success))
            val successProgress by animateLottieCompositionAsState(successComposition)
            if (successProgress < 1) {
                LottieAnimation(
                    modifier = modifier,
                    composition = successComposition,
                    progress = { successProgress }
                )
            } else {
                onSuccess()
            }
        }

        LoaderState.ERROR -> {
            onError()
        }
    }
}

enum class LoaderState {
    NONE, LOADING, SUCCESS, ERROR
}
