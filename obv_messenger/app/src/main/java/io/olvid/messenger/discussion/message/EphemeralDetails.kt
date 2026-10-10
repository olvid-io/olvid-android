/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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
import android.text.format.DateUtils
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.compose.EphemeralViewModel.Companion.existenceSetting
import io.olvid.messenger.discussion.compose.EphemeralViewModel.Companion.visibilitySetting
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * One line of the detailed ephemerality explanation of a message: what happens (title, colored
 * like the message timer) and when (description).
 */
data class EphemeralDetail(
    @DrawableRes val icon: Int,
    @ColorRes val color: Int,
    val title: String,
    val description: String
)

private val UNSENT_STATUSES = setOf(
    Message.STATUS_UNPROCESSED,
    Message.STATUS_PROCESSING,
    Message.STATUS_COMPUTING_PREVIEW
)

/**
 * Whether tapping the ephemeral timer of this message has something to explain. Mirrors the
 * cases handled by [computeEphemeralDetails] using only the data already available in the
 * message list (the message and its earliest expiration).
 */
fun Message.hasEphemeralDetails(expiration: MessageExpiration?): Boolean {
    if (expiration != null || wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ) {
        return true
    }
    if (jsonExpiration == null || wipeStatus != Message.WIPE_STATUS_NONE) {
        return false
    }
    return messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE
            || (messageType == Message.TYPE_OUTBOUND_MESSAGE && status in UNSENT_STATUSES)
}

fun computeEphemeralDetails(
    context: Context,
    message: Message,
    expirations: List<MessageExpiration>,
    retainWipedOutboundMessages: Boolean,
    now: Long = System.currentTimeMillis()
): List<EphemeralDetail> {
    val details = mutableListOf<EphemeralDetail>()
    val jsonExpiration = message.jsonExpiration?.let {
        runCatching {
            AppSingleton.getJsonObjectMapper().readValue(it, JsonExpiration::class.java)
        }.getOrNull()
    }
    val outbound = message.messageType == Message.TYPE_OUTBOUND_MESSAGE
    // inbound message not opened yet: its visibility countdown has not started
    val pendingOpen = message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE
    // outbound message not sent yet: its countdowns start once it is sent
    val pendingSend = outbound
            && message.wipeStatus == Message.WIPE_STATUS_NONE
            && message.status in UNSENT_STATUSES
            && expirations.isEmpty()

    // read once
    val readOnceDescription = when {
        message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ || (pendingSend && jsonExpiration?.readOnce == true) ->
            if (outbound && retainWipedOutboundMessages) {
                R.string.text_ephemeral_read_once_erased_on_leave
            } else {
                R.string.text_ephemeral_read_once_deleted_on_leave
            }

        pendingOpen && jsonExpiration?.readOnce == true ->
            R.string.text_ephemeral_read_once_deleted_on_leave_once_opened

        else -> null
    }
    readOnceDescription?.let {
        details += EphemeralDetail(
            icon = R.drawable.ic_burn,
            color = R.color.red,
            title = context.getString(R.string.text_visible_once),
            description = context.getString(it)
        )
    }

    // running countdowns
    val visibilityExpiration = expirations.visibilityExpiration(jsonExpiration)
    expirations.forEach { expiration ->
        val remaining = roundUpForDisplay(((expiration.expirationTimestamp - now) / 1000).coerceAtLeast(0))
        val visibility = expiration === visibilityExpiration
        details += EphemeralDetail(
            icon = if (visibility) R.drawable.ic_eye else R.drawable.ic_timer,
            color = if (visibility) R.color.orange else R.color.greyTint,
            title = if (visibility) visibilitySetting(context, remaining).orEmpty() else existenceSetting(remaining),
            description = deletionDescription(context, expiration.expirationTimestamp, expiration.wipeOnly)
        )
    }

    // countdowns not started yet
    if (pendingOpen || pendingSend) {
        val startDescription = context.getString(
            if (pendingOpen) R.string.text_ephemeral_countdown_starts_when_opened else R.string.text_ephemeral_countdown_starts_when_sent
        )
        jsonExpiration?.visibilityDuration?.let {
            details += EphemeralDetail(
                icon = R.drawable.ic_eye,
                color = R.color.orange,
                title = visibilitySetting(context, it).orEmpty(),
                description = startDescription
            )
        }
        if (pendingSend) {
            jsonExpiration?.existenceDuration?.let {
                details += EphemeralDetail(
                    icon = R.drawable.ic_timer,
                    color = R.color.greyTint,
                    title = existenceSetting(it),
                    description = startDescription
                )
            }
        }
    }
    return details
}

/**
 * The expiration (if any) that ends the visibility of the message. A wipe-only expiration always
 * is one; for outbound messages, when wiped messages are not retained, the visibility expiration
 * deletes the message like an existence one does, so we tell them apart by their order: the
 * expirations are sorted by timestamp, and so are the two durations of the settings.
 */
private fun List<MessageExpiration>.visibilityExpiration(jsonExpiration: JsonExpiration?): MessageExpiration? {
    firstOrNull { it.wipeOnly }?.let { return it }
    val visibilityDuration = jsonExpiration?.visibilityDuration ?: return null
    val existenceDuration = jsonExpiration.existenceDuration
    return when {
        size == 1 -> if (existenceDuration == null) first() else null
        size >= 2 -> if (existenceDuration == null || visibilityDuration <= existenceDuration) first() else this[1]
        else -> null
    }
}

// the compact timer floors the remaining time; here "7 days" should not read "6d" a second after being sent
private fun roundUpForDisplay(remainingSeconds: Long): Long {
    val unit = when {
        remainingSeconds < 60 -> 1L
        remainingSeconds < 3600 -> 60L
        remainingSeconds < 86400 -> 3600L
        remainingSeconds < 31536000 -> 86400L
        else -> 31536000L
    }
    return (remainingSeconds + unit - 1) / unit * unit
}

private fun deletionDescription(context: Context, timestamp: Long, wipeOnly: Boolean): String {
    val time = DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
    return if (DateUtils.isToday(timestamp)) {
        context.getString(
            if (wipeOnly) R.string.text_ephemeral_content_erased_at_time else R.string.text_ephemeral_deleted_at_time,
            time
        )
    } else {
        val date = DateUtils.formatDateTime(
            context,
            timestamp,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
        )
        context.getString(
            if (wipeOnly) R.string.text_ephemeral_content_erased_on_date_at_time else R.string.text_ephemeral_deleted_on_date_at_time,
            date,
            time
        )
    }
}

/**
 * Detailed ephemerality of [message], one row per rule, refreshed every second while a
 * countdown is running. Renders nothing when the message is not ephemeral.
 */
@Composable
fun EphemeralDetails(
    modifier: Modifier = Modifier,
    message: Message,
    forMessageDetailsActivity: Boolean = false,
) {
    val context = LocalContext.current
    val expirations by AppDatabase.getInstance().messageExpirationDao().getAllLive(message.id)
        .observeAsState(emptyList())
    val retainWipedOutboundMessages by produceState(initialValue = false, message.discussionId) {
        value = withContext(Dispatchers.IO) {
            AppDatabase.getInstance().discussionCustomizationDao().get(message.discussionId)?.prefRetainWipedOutboundMessages
                ?: SettingsActivity.defaultRetainWipedOutboundMessages
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expirations) {
        while (expirations.any { it.expirationTimestamp > now }) {
            delay(1.seconds)
            now = System.currentTimeMillis()
        }
    }
    val details = remember(message, expirations, retainWipedOutboundMessages, now) {
        computeEphemeralDetails(context, message, expirations, retainWipedOutboundMessages, now)
    }
    if (details.isNotEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy((if (forMessageDetailsActivity) 6 else 12).dp)
        ) {
            if (forMessageDetailsActivity) {
                Text(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    text = stringResource(id = R.string.dialog_title_ephemeral_message).uppercase(),
                    style = OlvidTypography.body2,
                    color = colorResource(id = R.color.primary700)
                )
            }
            details.forEach {
                EphemeralDetailRow(
                    detail = it,
                    forMessageDetailsActivity = forMessageDetailsActivity,
                )
            }
        }
    }
}

@Composable
private fun EphemeralDetailRow(
    modifier: Modifier = Modifier,
    detail: EphemeralDetail,
    forMessageDetailsActivity: Boolean) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy((if (forMessageDetailsActivity) 8 else 12).dp)
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = detail.icon),
            contentDescription = null
        )
        Column {
            Text(
                modifier = Modifier.padding(top = 3.dp),
                text = detail.title,
                style = (if (forMessageDetailsActivity) OlvidTypography.body2 else OlvidTypography.body1).copy(fontWeight = FontWeight.Medium),
                color = colorResource(id = detail.color)
            )
            Text(
                text = detail.description,
                style = (if (forMessageDetailsActivity) OlvidTypography.subtitle1 else OlvidTypography.body2),
                color = colorResource(id = R.color.greyTint)
            )
        }
    }
}

@Composable
fun EphemeralDetailsDialog(message: Message, onDismiss: () -> Unit) {
    DialogSecure(onDismissRequest = onDismiss) {
        BaseDialogContent(
            title = stringResource(id = R.string.dialog_title_ephemeral_message),
            content = {
                EphemeralDetails(message = message)
            },
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    text = stringResource(id = R.string.button_label_ok),
                    onClick = onDismiss
                )
            }
        )
    }
}
