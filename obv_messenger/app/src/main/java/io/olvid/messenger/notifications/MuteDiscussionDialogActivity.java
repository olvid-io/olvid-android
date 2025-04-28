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

package io.olvid.messenger.notifications;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.customClasses.MuteNotificationDialog;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.ApplySyncAtomTaskKt;
import io.olvid.messenger.settings.SettingsActivity;

public class MuteDiscussionDialogActivity extends AppCompatActivity {
    private Long discussionId = null;

    public static final String DISCUSSION_ID_INTENT_EXTRA = "discussion_id"; // long

    @Override
    protected void attachBaseContext(Context baseContext) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            discussionId = savedInstanceState.getLong(DISCUSSION_ID_INTENT_EXTRA);
        }
        if (discussionId == null) {
            discussionId = getIntent().getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1);
            if (discussionId == -1) {
                discussionId = null;
            }
        }

        if (discussionId == null) {
            finish();
        } else {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            }
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId);
            App.runThread(() -> {
                DiscussionCustomization discussionCust = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
                new Handler(Looper.getMainLooper()).post(() -> {
                    MuteNotificationDialog muteNotificationDialog = new MuteNotificationDialog(this, (Long muteExpirationTimestamp, boolean muteWholeProfile, boolean muteExceptMentioned) -> App.runThread(() -> {
                        Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
                        if (discussion == null || discussion.isLocked() || discussion.isPreDiscussion()) {
                            return;
                        }
                        if (muteWholeProfile) {
                            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(discussion.bytesOwnedIdentity);
                            if (ownedIdentity != null) {
                                ownedIdentity.prefMuteNotifications = true;
                                ownedIdentity.prefMuteNotificationsTimestamp = muteExpirationTimestamp;
                                ownedIdentity.prefMuteNotificationsExceptMentioned = muteExceptMentioned;
                                AppDatabase.getInstance().ownedIdentityDao().updateMuteNotifications(ownedIdentity.bytesOwnedIdentity, ownedIdentity.prefMuteNotifications, ownedIdentity.prefMuteNotificationsTimestamp, ownedIdentity.prefMuteNotificationsExceptMentioned);
                            }
                        } else {
                            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
                            boolean insert = false;
                            if (discussionCustomization == null) {
                                discussionCustomization = new DiscussionCustomization(discussionId);
                                insert = true;
                            }
                            discussionCustomization.prefMuteNotifications = true;
                            discussionCustomization.prefMuteNotificationsTimestamp = muteExpirationTimestamp;
                            discussionCustomization.prefMuteNotificationsExceptMentioned = muteExceptMentioned;
                            if (insert) {
                                AppDatabase.getInstance().discussionCustomizationDao().insert(discussionCustomization);
                            } else {
                                AppDatabase.getInstance().discussionCustomizationDao().update(discussionCustomization);
                            }
                            ApplySyncAtomTaskKt.propagateMuteSettings(discussion, discussionCustomization);
                            AppSingleton.getEngine().profileBackupNeeded(discussion.bytesOwnedIdentity);
                        }
                    }), MuteNotificationDialog.MuteType.DISCUSSION_OR_PROFILE, discussionCust == null || discussionCust.prefMuteNotificationsExceptMentioned);
                    muteNotificationDialog.setOnDismissListener(dialog -> finish());

                    muteNotificationDialog.show();
                });
            });
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (discussionId != null) {
            outState.putLong(DISCUSSION_ID_INTENT_EXTRA, discussionId);
        }
    }
}
