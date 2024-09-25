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

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.InitialView

@Composable
fun InitialView(
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier.requiredSize(56.dp),
    initialViewSetup: (initialView: InitialView) -> Unit,
    unreadMessages: Boolean = false,
    admin: Boolean = false,
    muted: Boolean = false,
    locked: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
    ) {
        AndroidView(modifier = Modifier.fillMaxSize()
            .align(Alignment.BottomStart),
            factory = { context ->
                InitialView(context).apply {
                    initialViewSetup.invoke(this)
                    if (onClick != null) {
                        setOnClickListener { onClick() }
                    }
                }
            }) { initialView ->
            initialViewSetup(initialView)
        }
        if (unreadMessages && locked.not()) {
            Image(
                modifier = Modifier
                    .size(maxWidth.times(0.4f))
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                painter = painterResource(id = drawable.ic_dot_white_bordered),
                contentDescription = stringResource(
                    id = string.content_description_message_status
                )
            )
        } else if (admin) {
            Image(
                modifier = Modifier
                    .size(maxWidth.times(0.4f))
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                painter = painterResource(id = drawable.ic_crown_white_circle),
                contentDescription = stringResource(
                    id = string.content_description_message_status
                )
            )
        }

        if (muted) {
            Image(
                modifier = Modifier
                    .size(maxWidth.times(0.4f))
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp),
                painter = painterResource(id = drawable.ic_notification_muted_circle),
                contentDescription = stringResource(
                    id = string.content_description_message_status
                )
            )
        }
    }
}

@Preview
@Composable
private fun InitialViewPreview() {
    AppCompatTheme {
        InitialView(
            modifier = Modifier
                .padding(16.dp)
                .requiredSize(56.dp),
            unreadMessages = true,
            muted = true,
            initialViewSetup = { initialView -> initialView.setInitial(byteArrayOf(), "A") },
        )
    }
}