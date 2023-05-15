/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.main.calls

import androidx.annotation.DrawableRes
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.CallLogItemDao.CallLogItemAndContacts
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.databases.entity.OwnedIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallLogViewModel : ViewModel() {

    val callLogLiveData: LiveData<List<CallLogItemAndContacts>> =
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().callLogItemDao()
                .getWithContactForOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
        }

    fun initialViewSetup(initialView: InitialView, callLogItemAndContacts: CallLogItemAndContacts) {
        if (callLogItemAndContacts.contacts.size == 1) {
            initialView.setContact(callLogItemAndContacts.oneContact)
        } else {
            if (callLogItemAndContacts.group == null) {
                initialView.setInitial(
                    ByteArray(0),
                    callLogItemAndContacts.contacts.size.toString()
                )
            } else {
                initialView.setGroup(callLogItemAndContacts.group)
            }
            val separator: String =
                initialView.context.getString(R.string.text_contact_names_separator)
            val sb = StringBuilder()
            var first = true
            for (callLogItemContactJoin in callLogItemAndContacts.contacts) {
                val contactDisplayName =
                    AppSingleton.getContactCustomDisplayName(callLogItemContactJoin.bytesContactIdentity)
                if (contactDisplayName != null) {
                    if (!first) {
                        sb.append(separator)
                    }
                    first = false
                    sb.append(contactDisplayName)
                }
            }
        }
    }

    fun delete(callLogItem: CallLogItem) {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance().callLogItemDao().delete(callLogItem)
        }
    }
}

@DrawableRes
fun CallLogItem.getStatusImageResource(): Int {
    return if (callType == CallLogItem.TYPE_INCOMING) {
        when (callStatus) {
            CallLogItem.STATUS_SUCCESSFUL -> R.drawable.ic_call_incoming
            CallLogItem.STATUS_BUSY -> R.drawable.ic_phone_busy_in
            CallLogItem.STATUS_REJECTED -> R.drawable.ic_phone_rejected_in
            CallLogItem.STATUS_MISSED,
            CallLogItem.STATUS_FAILED -> R.drawable.ic_call_missed
            else -> 0
        }
    } else {
        when (callStatus) {
            CallLogItem.STATUS_SUCCESSFUL -> R.drawable.ic_call_outgoing
            CallLogItem.STATUS_BUSY -> R.drawable.ic_phone_busy_out
            CallLogItem.STATUS_REJECTED -> R.drawable.ic_phone_rejected_out
            CallLogItem.STATUS_MISSED,
            CallLogItem.STATUS_FAILED -> R.drawable.ic_call_failed
            else -> 0
        }
    }
}

fun CallLogItem.getAnnotatedDate(): AnnotatedString {
    return AnnotatedString(
        if (callStatus == CallLogItem.STATUS_SUCCESSFUL && duration > 0) {
            App.getContext().getString(
                R.string.text_call_timestamp_and_duration,
                StringUtils.getLongNiceDateString(
                    App.getContext(),
                    timestamp
                ),
                duration / 60,
                duration % 60
            )
        } else {
            StringUtils.getLongNiceDateString(
                App.getContext(),
                timestamp
            ).toString()
        }
    )
}

fun CallLogItemAndContacts.getAnnotatedTitle(): AnnotatedString {
    return AnnotatedString(
        if (contacts.size == 1) {
            oneContact.getCustomDisplayName()
        } else {
            val separator: String = App.getContext().getString(R.string.text_contact_names_separator)
            val sb = java.lang.StringBuilder()
            var first = true
            for (callLogItemContactJoin in contacts) {
                val contactDisplayName =
                    AppSingleton.getContactCustomDisplayName(callLogItemContactJoin.bytesContactIdentity)
                if (contactDisplayName != null) {
                    if (!first) {
                        sb.append(separator)
                    }
                    first = false
                    sb.append(contactDisplayName)
                }
            }
            sb.toString()
        }
    )
}