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

package io.olvid.messenger.google_services

import android.content.Context
import androidx.core.content.edit
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import io.olvid.engine.Logger
import io.olvid.messenger.R
import io.olvid.messenger.onboarding.flow.ClipboardDeferredLink

/**
 * Queries the Play Store install referrer once per install and, if it is flagged as coming from an
 * Olvid link, tells [ClipboardDeferredLink] to look for that link in the clipboard during onboarding.
 * The referrer never carries the link itself.
 */
object InstallReferrerUtils {
    private const val PREF_KEY_INSTALL_REFERRER_PROCESSED = "pref_key_install_referrer_processed"

    /**
     * Fetches the install referrer unless it has already been processed for this install. A
     * transient Play failure leaves it unprocessed so the next zero-profile launch retries.
     */
    fun fetchDeferredInvitationIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(
            appContext.getString(R.string.preference_filename_app),
            Context.MODE_PRIVATE
        )
        if (prefs.getBoolean(PREF_KEY_INSTALL_REFERRER_PROCESSED, false)) {
            return
        }

        val client = InstallReferrerClient.newBuilder(appContext).build()
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerResponse.OK -> {
                            runCatching { client.installReferrer.installReferrer }
                                .onFailure {
                                    Logger.w("Install referrer: unable to read referrer details")
                                    Logger.x(it)
                                }
                                .getOrNull()
                                ?.let { referrer ->
                                    if (ClipboardDeferredLink.handleInstallReferrer(referrer)) {
                                        Logger.i("Install referrer: install flagged as coming from an Olvid link")
                                    }
                                }
                            prefs.edit { putBoolean(PREF_KEY_INSTALL_REFERRER_PROCESSED, true) }
                        }

                        InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                        InstallReferrerResponse.DEVELOPER_ERROR,
                        InstallReferrerResponse.PERMISSION_ERROR -> {
                            // permanent for this install: never ask again
                            prefs.edit { putBoolean(PREF_KEY_INSTALL_REFERRER_PROCESSED, true) }
                        }

                        else -> {
                            // SERVICE_UNAVAILABLE / SERVICE_DISCONNECTED: retry on next first launch
                            Logger.d("Install referrer: transient failure $responseCode, will retry")
                        }
                    }
                    runCatching { client.endConnection() }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    // a retry happens on the next zero-profile launch, nothing to do here
                }
            })
        }.onFailure {
            Logger.w("Install referrer: unable to connect to Play Store")
            Logger.x(it)
        }
    }
}
