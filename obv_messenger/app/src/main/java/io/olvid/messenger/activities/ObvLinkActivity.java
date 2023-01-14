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

package io.olvid.messenger.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.engine.types.identities.ObvUrlIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.onboarding.OnboardingActivity;

public class ObvLinkActivity extends AppCompatActivity {
    public static final String URL_INVITATION_HOST = ObvUrlIdentity.URL_INVITATION_HOST;
    public static final String URL_CONFIGURATION_HOST = "configuration.olvid.io";
    public static final String URL_WEB_CLIENT_HOST = "web.olvid.io";

    public static final Pattern INVITATION_PATTERN = Pattern.compile("(" + ObvUrlIdentity.URL_PROTOCOL + "|" + ObvUrlIdentity.URL_PROTOCOL_OLVID + ")" + Pattern.quote("://" + URL_INVITATION_HOST) + "/#([-_a-zA-Z0-9]+)");
    public static final Pattern CONFIGURATION_PATTERN = Pattern.compile("(" + ObvUrlIdentity.URL_PROTOCOL + "|" + ObvUrlIdentity.URL_PROTOCOL_OLVID + ")" + Pattern.quote("://" + URL_CONFIGURATION_HOST) + "/#([-_a-zA-Z0-9]+)");
    public static final Pattern WEB_CLIENT_PATTERN = Pattern.compile("(" + ObvUrlIdentity.URL_PROTOCOL + "|" + ObvUrlIdentity.URL_PROTOCOL_OLVID + ")" + Pattern.quote("://" + URL_WEB_CLIENT_HOST) + "/#([-_a-zA-Z0-9]+)");

    public static final Pattern ANY_PATTERN = Pattern.compile("(" + INVITATION_PATTERN.pattern() + "|" + CONFIGURATION_PATTERN.pattern() + "|" + WEB_CLIENT_PATTERN.pattern() + ")");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            boolean parsed = false;
            final Uri uri = intent.getData();
            if (uri != null) {
                Matcher m = ANY_PATTERN.matcher(uri.toString());
                if (m.find()) {
                    // we found a pattern
                    // --> if we already have an identity, forward to the main activity who will propose to use an existing profile (plus_button)
                    // --> if not offer to create a new one (onboarding)
                    App.runThread(() -> {
                        boolean firstIdentity = AppDatabase.getInstance().ownedIdentityDao().countAll() == 0;
                        if (firstIdentity) {
                            Intent linkIntent = new Intent(App.getContext(), OnboardingActivity.class);
                            linkIntent.putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, true);
                            linkIntent.putExtra(OnboardingActivity.LINK_URI_INTENT_EXTRA, uri.toString());
                            startActivity(linkIntent);
                        } else {
                            Intent linkIntent = new Intent(App.getContext(), MainActivity.class);
                            linkIntent.setAction(MainActivity.LINK_ACTION);
                            linkIntent.putExtra(MainActivity.LINK_URI_INTENT_EXTRA, uri.toString());
                            startActivity(linkIntent);
                        }
                    });
                    parsed = true;
                }
            }
            if (!parsed) {
                App.toast(R.string.toast_message_unparsable_url, Toast.LENGTH_SHORT);
            }
        }
        finish();
    }
}