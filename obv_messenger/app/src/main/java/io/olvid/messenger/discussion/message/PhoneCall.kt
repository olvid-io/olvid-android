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

package io.olvid.messenger.discussion.message

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.CallLogItemDao.CallLogItemAndContacts
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhoneCallInfo(message: Message, callBack: (callLogId : Long) -> Unit) {
    val (callStatus, callLogItemId) = parsePhoneCallInfoContent(message.contentBody)

    var callDescription by remember { mutableStateOf<String?>(null) }
    var callDurationInSeconds by remember { mutableIntStateOf(0) }
    var formattedCallDuration by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Image(
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = { callLogItemId?.let { callBack(it) } }
                ),
            painter = painterResource(id = getCallStatusImage(callStatus)),
            contentDescription = null
        )

        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)) {
            Text(
                maxLines = 3,
                textAlign = TextAlign.Start,
                text = callDescription ?: stringResource(id = getCallStatusText(callStatus)),
                color = colorResource(id = R.color.primary700)
            )
            formattedCallDuration?.let {
                // TODO align after message timestamp ?
                Text(text = it, style = OlvidTypography.subtitle1, color = Color(0xCC7D7D7D))
            }
        }
    }

    val context = LocalContext.current
    callLogItemId?.let { id ->
        LaunchedEffect(id) {
            val callDetails = withContext(Dispatchers.IO) {
                AppDatabase.getInstance().callLogItemDao()[id]
            }
            callDetails?.let {
                callDescription = getCallDescription(context, it, message.senderIdentifier)
                callDurationInSeconds = it.callLogItem.duration
                formattedCallDuration = formatCallDuration(context, callDurationInSeconds)
            }
        }
    }
}

private fun parsePhoneCallInfoContent(content: String?): Pair<Int, Long?> {
    var callStatus = CallLogItem.STATUS_MISSED
    var callLogItemId: Long? = null
    runCatching {
        val parts = content?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }
            ?.toTypedArray()
        callStatus = parts?.getOrNull(0)?.toInt() ?: CallLogItem.STATUS_MISSED
        callLogItemId = parts?.getOrNull(1)?.toLong()
    }
    return callStatus to callLogItemId
}

private fun getCallDescription(
    context: Context,
    callDetails: CallLogItemAndContacts,
    senderIdentifier: ByteArray
): String {
    return if (callDetails.callLogItem.callType == CallLogItem.TYPE_OUTGOING) {
        when (callDetails.callLogItem.callStatus) {
            CallLogItem.STATUS_BUSY -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_busy_outgoing_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_busy_outgoing_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callDetails.oneContact.getCustomDisplayName()
                )
            }

            CallLogItem.STATUS_REJECTED -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_rejected_outgoing_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_rejected_outgoing_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callDetails.oneContact.getCustomDisplayName()
                )
            }

            CallLogItem.STATUS_MISSED -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_unanswered_outgoing_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_unanswered_outgoing_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callDetails.oneContact.getCustomDisplayName()
                )
            }

            CallLogItem.STATUS_FAILED -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_failed_outgoing_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_failed_outgoing_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callDetails.oneContact.getCustomDisplayName()
                )
            }

            CallLogItem.STATUS_SUCCESSFUL -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_outgoing_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_outgoing_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callDetails.oneContact.getCustomDisplayName()
                )
            }

            else -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_outgoing_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_outgoing_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callDetails.oneContact.getCustomDisplayName()
                )
            }
        }
    } else {
        // first, find the caller
        var callerDisplayName =
            AppSingleton.getContactCustomDisplayName(senderIdentifier)
        if (callerDisplayName == null) {
            callerDisplayName =
                callDetails.oneContact.getCustomDisplayName()
        }

        when (callDetails.callLogItem.callStatus) {
            CallLogItem.STATUS_BUSY -> context.getString(
                R.string.text_busy_incoming_call_with_contacts,
                callerDisplayName
            )

            CallLogItem.STATUS_MISSED -> context.getString(
                R.string.text_missed_incoming_call_with_contacts,
                callerDisplayName
            )

            CallLogItem.STATUS_REJECTED, CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE -> context.getString(
                R.string.text_rejected_incoming_call_with_contacts,
                callerDisplayName
            )

            CallLogItem.STATUS_FAILED -> context.getString(
                R.string.text_failed_incoming_call_with_contacts,
                callerDisplayName
            )

            CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE -> context.getString(
                R.string.text_successful_call_other_device_with_contacts,
                callerDisplayName
            )

            CallLogItem.STATUS_SUCCESSFUL -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_incoming_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_incoming_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callerDisplayName
                )
            }

            else -> if (callDetails.contacts.size < 2) {
                context.getString(
                    R.string.text_incoming_call_with_contacts,
                    callDetails.oneContact.getCustomDisplayName()
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.text_incoming_group_call_with_contacts,
                    callDetails.contacts.size - 1,
                    callDetails.contacts.size - 1,
                    callerDisplayName
                )
            }
        }
    }
}

private fun formatCallDuration(context: Context, durationInSeconds: Int): String? {
    return if (durationInSeconds > 0) {
        if (durationInSeconds > 60) {
            context.getString(
                R.string.text_call_duration,
                durationInSeconds / 60,
                durationInSeconds % 60
            )
        } else {
            context.getString(R.string.text_call_duration_short, durationInSeconds)
        }
    } else {
        null
    }
}

fun getCallStatusImage(callStatus: Int): Int {
    return when (callStatus) {
        -CallLogItem.STATUS_BUSY -> {
            R.drawable.ic_phone_busy_out
        }

        -CallLogItem.STATUS_REJECTED -> {
            R.drawable.ic_phone_rejected_out
        }

        -CallLogItem.STATUS_MISSED -> {
            R.drawable.ic_phone_missed_out
        }

        -CallLogItem.STATUS_FAILED -> {
            R.drawable.ic_phone_failed_out
        }

        -CallLogItem.STATUS_SUCCESSFUL -> {
            R.drawable.ic_phone_success_out
        }

        CallLogItem.STATUS_BUSY -> {
            R.drawable.ic_phone_busy_in
        }

        CallLogItem.STATUS_FAILED -> {
            R.drawable.ic_phone_failed_in
        }

        CallLogItem.STATUS_SUCCESSFUL -> {
            R.drawable.ic_phone_success_in
        }

        CallLogItem.STATUS_REJECTED -> {
            R.drawable.ic_phone_rejected_in
        }

        CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE -> {
            R.drawable.ic_phone_success_in
        }

        CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE -> {
            R.drawable.ic_phone_rejected_in
        }

        CallLogItem.STATUS_MISSED -> {
            R.drawable.ic_phone_missed_in
        }

        else -> {
            R.drawable.ic_phone_missed_in
        }
    }
}

fun getCallStatusText(callStatus: Int): Int {
    return when (callStatus) {
        -CallLogItem.STATUS_BUSY -> {
            R.string.text_busy_outgoing_call
        }

        -CallLogItem.STATUS_REJECTED -> {
            R.string.text_rejected_call
        }

        -CallLogItem.STATUS_MISSED -> {
            R.string.text_unanswered_call
        }

        -CallLogItem.STATUS_FAILED -> {
            R.string.text_failed_call
        }

        -CallLogItem.STATUS_SUCCESSFUL -> {
            R.string.text_successful_call

        }

        CallLogItem.STATUS_BUSY -> {
            R.string.text_busy_call
        }

        CallLogItem.STATUS_FAILED -> {
            R.string.text_failed_call
        }

        CallLogItem.STATUS_SUCCESSFUL -> {
            R.string.text_successful_call
        }

        CallLogItem.STATUS_REJECTED -> {
            R.string.text_rejected_call
        }

        CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE -> {
            R.string.text_successful_call_other_device
        }

        CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE -> {
            R.string.text_rejected_call_other_device

        }

        CallLogItem.STATUS_MISSED -> {
            R.string.text_missed_call
        }

        else -> {
            R.string.text_missed_call
        }
    }
}