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
package io.olvid.messenger.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.OnboardingFlowActivity
import java.util.regex.Pattern

class ObvLinkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            var parsed = false
            intent.data?.let { uri ->
                val m = ANY_PATTERN.matcher(uri.toString())
                if (m.find()) {
                    // we found a pattern
                    // --> if we already have an identity, forward to the main activity who will propose to use an existing profile (plus_button)
                    // --> if not offer to create a new one (onboarding)
                    App.runThread {
                        val firstIdentity =
                            AppDatabase.getInstance().ownedIdentityDao().countAll() == 0
                        val linkIntent: Intent
                        if (firstIdentity) {
                            val configurationLink = CONFIGURATION_PATTERN.matcher(uri.toString())
                            if (configurationLink.find()) {
                                // only forward to the old onboarding if it is a configuration link
                                linkIntent = Intent(App.getContext(), OnboardingActivity::class.java)
                                    .putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, true)
                                    .putExtra(OnboardingActivity.LINK_URI_INTENT_EXTRA, uri.toString())
                            } else {
                                // otherwise simply start an onboarding
                                linkIntent = Intent(App.getContext(), OnboardingFlowActivity::class.java)
                            }
                        } else {
                            linkIntent = Intent(App.getContext(), MainActivity::class.java)
                            linkIntent.action = MainActivity.LINK_ACTION
                            linkIntent.putExtra(MainActivity.LINK_URI_INTENT_EXTRA, uri.toString())
                        }
                        startActivity(linkIntent)
                    }
                    parsed = true
                }
            }
            if (!parsed) {
                App.toast(string.toast_message_unparsable_url, Toast.LENGTH_SHORT)
            }
        }
        finish()
    }

    companion object {
        private const val URL_CONFIGURATION_HOST = "configuration.olvid.io"
        private const val URL_WEB_CLIENT_HOST = "web.olvid.io"

        @JvmField
        val INVITATION_PATTERN: Pattern = ObvUrlIdentity.INVITATION_PATTERN

        @JvmField
        val MUTUAL_SCAN_PATTERN: Pattern = ObvMutualScanUrl.MUTUAL_SCAN_PATTERN

        @JvmField
        val CONFIGURATION_PATTERN: Pattern = Pattern.compile(
            "(" + ObvUrlIdentity.URL_PROTOCOL + "|" + ObvUrlIdentity.URL_PROTOCOL_OLVID + ")" + Pattern.quote(
                "://$URL_CONFIGURATION_HOST"
            ) + "/#([-_a-zA-Z\\d]+)"
        )

        @JvmField
        val WEB_CLIENT_PATTERN: Pattern = Pattern.compile(
            "(" + ObvUrlIdentity.URL_PROTOCOL + "|" + ObvUrlIdentity.URL_PROTOCOL_OLVID + ")" + Pattern.quote(
                "://$URL_WEB_CLIENT_HOST"
            ) + "/#([-_a-zA-Z\\d]+)"
        )

        @JvmField
        val ANY_PATTERN: Pattern =
            Pattern.compile("(" + INVITATION_PATTERN.pattern() + "|" + MUTUAL_SCAN_PATTERN.pattern() + "|" + CONFIGURATION_PATTERN.pattern() + "|" + WEB_CLIENT_PATTERN.pattern() + ")")
    }
}