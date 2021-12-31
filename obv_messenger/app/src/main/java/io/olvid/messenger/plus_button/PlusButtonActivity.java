/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.messenger.plus_button;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class PlusButtonActivity extends LockableActivity implements EngineNotificationListener {
    public static final String LINK_URI_INTENT_EXTRA = "link_uri";

    private PlusButtonViewModel plusButtonViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        plusButtonViewModel = new ViewModelProvider(this).get(PlusButtonViewModel.class);
        plusButtonViewModel.setCurrentIdentity(AppSingleton.getCurrentIdentityLiveData().getValue());

        if (plusButtonViewModel.getCurrentIdentity() == null) {
            finish();
            return;
        }

        AppSingleton.getCurrentIdentityLiveData().observe(this, (OwnedIdentity ownedIdentity) -> {
            if (Objects.equals(ownedIdentity, plusButtonViewModel.getCurrentIdentity())) {
                plusButtonViewModel.setCurrentIdentity(ownedIdentity);
            } else {
                finish();
            }
        });

        setContentView(R.layout.activity_plus_button);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        Intent intent = getIntent();
        if (navHostFragment != null && intent.hasExtra(LINK_URI_INTENT_EXTRA)) {
            String uri = intent.getStringExtra(LINK_URI_INTENT_EXTRA);
            if (uri != null) {
                if (ObvLinkActivity.INVITATION_PATTERN.matcher(uri).find()) {
                    plusButtonViewModel.setScannedUri(uri);
                    plusButtonViewModel.setDeepLinked(true);
                    navHostFragment.getNavController().popBackStack();
                    navHostFragment.getNavController().navigate(R.id.invitation_scanned);
                } else if (ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri).find()) {
                    plusButtonViewModel.setScannedUri(uri);
                    plusButtonViewModel.setDeepLinked(true);
                    navHostFragment.getNavController().popBackStack();
                    navHostFragment.getNavController().navigate(R.id.configuration_scanned);
                } else if (ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(uri).find()) {
                    plusButtonViewModel.setScannedUri(uri);
                    plusButtonViewModel.setDeepLinked(true);
                    navHostFragment.getNavController().popBackStack();
                    navHostFragment.getNavController().navigate(R.id.webclient_scanned);
                }
            }
        }

        engineNotificationRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED, this);
    }



    private Long engineNotificationRegistrationNumber;

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED.equals(notificationName)) {
            if (plusButtonViewModel != null && plusButtonViewModel.getMutualScanUrl() != null) {
                byte[] signature = (byte[]) userInfo.get(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_NONCE_KEY);
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY);

                if (Arrays.equals(plusButtonViewModel.getMutualScanUrl().getBytesIdentity(), bytesOwnedIdentity)
                        && Arrays.equals(plusButtonViewModel.getMutualScanBytesContactIdentity(), bytesContactIdentity)
                        && Arrays.equals(plusButtonViewModel.getMutualScanUrl().signature, signature)) {
                    LiveData<Contact> contactLiveData = AppDatabase.getInstance().contactDao().getAsync(bytesOwnedIdentity, bytesContactIdentity);
                    runOnUiThread(() -> contactLiveData.observe(this, (Contact contact) -> {
                        if (contact != null) {
                            finish();
                            App.openOneToOneDiscussionActivity(this, bytesOwnedIdentity, bytesContactIdentity, true);
                        }
                    }));
                }
            }
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        if (engineNotificationRegistrationNumber != null) {
            return engineNotificationRegistrationNumber;
        }
        return -1;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber != null;
    }
}
