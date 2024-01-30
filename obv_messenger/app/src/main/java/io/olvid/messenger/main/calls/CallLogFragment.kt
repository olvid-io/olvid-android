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

package io.olvid.messenger.main.calls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.olvid.messenger.App
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.dao.CallLogItemDao.CallLogItemAndContacts
import io.olvid.messenger.fragments.dialog.CallContactDialogFragment
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment

class CallLogFragment : Fragment() {

    private val callLogViewModel: CallLogViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            consumeWindowInsets = false
            setContent {
                CallLogScreen(
                    callLogViewModel = callLogViewModel,
                    onClick = ::callClicked,
                    onNewCallClick = ::startNewCall
                )
            }
        }
    }

    private fun startNewCall() {
        val callContactDialogFragment = CallContactDialogFragment.newInstance()
        activity?.supportFragmentManager?.let { callContactDialogFragment.show(it, "dialog") }
    }

    private fun callClicked(callLogItem: CallLogItemAndContacts) {
        if (callLogItem.contacts.size == 1) {
            App.startWebrtcCall(
                requireContext(),
                callLogItem.oneContact.bytesOwnedIdentity,
                callLogItem.oneContact.bytesContactIdentity
            )
        } else {
            val bytesContactIdentities = ArrayList<BytesKey>(callLogItem.contacts.size)
            for (callLogItemContactJoin in callLogItem.contacts) {
                bytesContactIdentities.add(BytesKey(callLogItemContactJoin.bytesContactIdentity))
            }
            val multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(
                callLogItem.callLogItem.bytesOwnedIdentity,
                callLogItem.callLogItem.bytesGroupOwnerAndUidOrIdentifier,
                bytesContactIdentities
            )
            activity?.supportFragmentManager?.let { multiCallStartDialogFragment.show(it, "dialog") }
        }
    }
}