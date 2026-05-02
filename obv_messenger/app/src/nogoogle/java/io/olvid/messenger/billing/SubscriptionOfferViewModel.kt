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

package io.olvid.messenger.billing

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.App
import io.olvid.messenger.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubscriptionOfferViewModel : ViewModel() {
    var bytesOwnedIdentity: ByteArray? by mutableStateOf(null)
        private set

    var freeTrialResults: Boolean? by mutableStateOf(null)
    var freeTrialFailed by mutableStateOf(false)
    var freeTrialButtonEnabled: Boolean by mutableStateOf(false)

    var freeTrialJob: Job? = null

    fun updateBytesOwnedIdentity(bytesOwnedIdentity: ByteArray?) {
        if (this.bytesOwnedIdentity?.contentEquals(bytesOwnedIdentity) != true) {
            this.bytesOwnedIdentity = bytesOwnedIdentity

            // identity changed --> reset all properties
            freeTrialResults = null
            freeTrialFailed = false
            freeTrialButtonEnabled = true

            freeTrialJob?.cancel()
        }
    }

    fun initiateFreeTrialQuery() {
        val identity = bytesOwnedIdentity ?: return

        freeTrialJob?.cancel()
        freeTrialJob = viewModelScope.launch {
            // Timeout
            val timeoutJob = launch {
                delay(SubscriptionRepository.QUERY_TIMEOUT)
                if (freeTrialResults == null) {
                    freeTrialFailed = true
                }
            }

            launch {
                SubscriptionRepository.freeTrialEvents(identity).collect { event ->
                    when (event) {
                        is SubscriptionRepository.FreeTrialEvent.QuerySuccess -> {
                            timeoutJob.cancel()
                            freeTrialResults = event.available
                        }
                        is SubscriptionRepository.FreeTrialEvent.QueryFailed -> {
                            timeoutJob.cancel()
                            freeTrialFailed = true
                        }
                        is SubscriptionRepository.FreeTrialEvent.RetrieveSuccess -> {
                            App.toast(R.string.toast_message_free_trial_started, Toast.LENGTH_LONG)
                            updateBytesOwnedIdentity(null)
                        }
                        is SubscriptionRepository.FreeTrialEvent.RetrieveFailed -> {
                            freeTrialButtonEnabled = true
                            App.toast(R.string.toast_message_failed_to_start_free_trial, Toast.LENGTH_LONG)
                        }
                    }
                }
            }

            SubscriptionRepository.queryFreeTrial(identity)
        }
    }

    fun startFreeTrial() {
        val identity = bytesOwnedIdentity ?: return
        freeTrialButtonEnabled = false
        SubscriptionRepository.startFreeTrial(identity)
    }
}
