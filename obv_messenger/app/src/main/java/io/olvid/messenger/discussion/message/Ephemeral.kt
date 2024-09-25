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

package io.olvid.messenger.discussion.message

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.compose.EphemeralViewModel.Companion.existenceSetting
import io.olvid.messenger.discussion.compose.EphemeralViewModel.Companion.visibilitySetting
import kotlinx.coroutines.delay

@Composable
fun EphemeralVisibilityExplanation(
    modifier: Modifier = Modifier,
    duration: Long? = null,
    readOnce: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    visibilitySetting(
        context = LocalContext.current,
        duration = duration,
        readOnce = readOnce
    )?.let {
        val icon = if (readOnce) R.drawable.ic_burn else R.drawable.ic_eye
        val color = if (readOnce) colorResource(id = R.color.red) else colorResource(
            id = R.color.orange
        )

        Column(
            modifier = modifier
                .clickable {
                    onClick?.invoke()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onClick != null) {
                Text(
                    text = stringResource(id = R.string.text_ephemeral_message_explanation),
                    textAlign = TextAlign.Center,
                    color = colorResource(
                        id = R.color.almostBlack
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(painter = painterResource(id = icon), contentDescription = null)
                Text(text = it, color = color)
            }
        }
    }
}

@Preview
@Composable
private fun EphemeralExplanationPreview() {
    AppCompatTheme {
        EphemeralVisibilityExplanation(
            duration = null,
            readOnce = true
        ) {}
    }
}

@Composable
fun EphemeralTimer(modifier: Modifier = Modifier, expiration: MessageExpiration?, readOnce: Boolean, bookmarked: Boolean) {
    val context = LocalContext.current
    var ephemeralState: EphemeralState? by remember {
        mutableStateOf(null)
    }
    LaunchedEffect(expiration, readOnce) {
        expiration?.let {
            while (expiration.expirationTimestamp > System.currentTimeMillis()) {
                ephemeralState =
                    expiration.getTimerResources(context, readOnce, bookmarked)
                delay(1000)
            }
        } ?: run {
            ephemeralState = expiration.getTimerResources(context, readOnce, bookmarked)
        }
    }
    ephemeralState?.let {
        EphemeralTimerContent(
            modifier = modifier,
            text = it.text,
            color = it.color,
            icon = it.icon,
            bookmarked = bookmarked
        )
    }
}

@Composable
private fun EphemeralTimerContent(modifier: Modifier = Modifier, text: String?, color: Int, icon: Int, bookmarked: Boolean) {
    Column(
        modifier = modifier.padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = if (bookmarked) R.drawable.ic_star else icon),
            tint = if (bookmarked) Color.Unspecified else colorResource(id = color),
            contentDescription = null
        )
        text?.let {
            Text(
                text = text,
                style = OlvidTypography.subtitle1,
                color = colorResource(id = color)
            )
        }
    }
}

data class EphemeralState(val text: String?, val color: Int, val icon: Int)

fun MessageExpiration?.getTimerResources(
    context: Context,
    readOnce: Boolean,
    bookmarked: Boolean
): EphemeralState {
    val remaining =
        (((this?.expirationTimestamp ?: 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    var color = R.color.lightGrey
    val icon: Int
    var text: String?
    if (remaining < 60) {
        text = context.getString(R.string.text_timer_s, remaining)
        color = R.color.red
    } else if (remaining < 3600) {
        text =
            context.getString(R.string.text_timer_m, remaining / 60)
        color = R.color.orange
    } else if (remaining < 86400) {
        text =
            context.getString(R.string.text_timer_h, remaining / 3600)
        color = R.color.greyTint

    } else if (remaining < 31536000) {
        text =
            context.getString(R.string.text_timer_d, remaining / 86400)
    } else {
        text =
            context.getString(R.string.text_timer_y, remaining / 31536000)
    }
    if (readOnce) {
        color = R.color.red
        icon = R.drawable.ic_burn_small
    } else {
        icon = if (this?.wipeOnly == true) {
            R.drawable.ic_eye_small
        } else {
            R.drawable.ic_timer_small
        }
    }
    if (remaining == 0L) {
        text = null
    }
    return EphemeralState(
        text = text,
        color = color,
        icon = if (bookmarked && !readOnce) R.drawable.ic_star else icon
    )
}

@Preview
@Composable
private fun EphemeralTimerPreview() {
    AppCompatTheme {
        EphemeralTimer(
            expiration = MessageExpiration(
                id = 0,
                messageId = 0,
                expirationTimestamp = System.currentTimeMillis() + 10_000,
                wipeOnly = false
            ),
            readOnce = true,
            bookmarked = false
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EphemeralJsonSharedSettings(modifier: Modifier = Modifier, message: Message) {
    var ephemeral = false
    val jsonSharedSettings = try {
        AppSingleton.getJsonObjectMapper()
            .readValue(message.contentBody, JsonSharedSettings::class.java)
    } catch (ignored: Exception) {
        null
    }

    FlowRow(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        if (jsonSharedSettings?.jsonExpiration?.getReadOnce() == true) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_burn_small),
                    contentDescription = null
                )
                Text(
                    text = stringResource(id = R.string.text_visible_once),
                    style = OlvidTypography.subtitle1,
                    color = colorResource(id = R.color.red)
                )
            }
            ephemeral = true
        }
        jsonSharedSettings?.jsonExpiration?.getVisibilityDuration()?.let {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_eye_small),
                    contentDescription = null
                )
                Text(
                    text = visibilitySetting(context = LocalContext.current, it).orEmpty(),
                    style = OlvidTypography.subtitle1,
                    color = colorResource(id = R.color.orange)
                )
            }
            ephemeral = true
        }
        jsonSharedSettings?.jsonExpiration?.getExistenceDuration()?.let {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_timer_small),
                    contentDescription = null
                )
                Text(
                    text = existenceSetting(it),
                    style = OlvidTypography.subtitle1,
                    color = colorResource(id = R.color.greyTint)
                )
            }
            ephemeral = true
        }
    }
    if (!ephemeral) {
        Text(
            modifier = modifier,
            text =
            stringResource(id = R.string.text_non_ephemeral_messages),
            style = OlvidTypography.subtitle1.copy(
                color = colorResource(id = R.color.greyTint),
                fontStyle = FontStyle.Italic
            )
        )
    }
}