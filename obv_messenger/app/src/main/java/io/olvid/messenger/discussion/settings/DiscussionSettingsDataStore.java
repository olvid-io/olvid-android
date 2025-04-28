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

package io.olvid.messenger.discussion.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.settings.SettingsActivity;

public class DiscussionSettingsDataStore extends PreferenceDataStore {
    private final DiscussionSettingsViewModel discussionSettingsViewModel;

    public DiscussionSettingsDataStore(DiscussionSettingsViewModel discussionSettingsViewModel) {
        this.discussionSettingsViewModel = discussionSettingsViewModel;
    }


    private interface PerformUpdateOnPossiblyNullDiscussionCustomizationAction {
        void update(@NonNull DiscussionCustomization discussionCustomization);
    }
    private void performUpdateOnPossiblyNullDiscussionCustomization(PerformUpdateOnPossiblyNullDiscussionCustomizationAction runnable) {
        DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
        boolean insert = false;
        if (discussionCustomization == null) {
            if (discussionSettingsViewModel.getDiscussionId() == null) {
                return;
            }
            discussionCustomization = new DiscussionCustomization(discussionSettingsViewModel.getDiscussionId());
            insert = true;
        }
        runnable.update(discussionCustomization);
        DiscussionCustomization finalDiscussionCustomization = discussionCustomization;
        if (insert) {
            App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().insert(finalDiscussionCustomization));
        } else {
            App.runThread(() -> AppDatabase.getInstance().discussionCustomizationDao().update(finalDiscussionCustomization));
        }
    }


    @Override
    public void putString(String key, @Nullable String value) {
        switch (key) {
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE: {
                discussionSettingsViewModel.setMessageRingtone(value);
                discussionSettingsViewModel.notifySettingsChangedListeners(discussionSettingsViewModel.getDiscussionCustomization().getValue());
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_VIBRATION_PATTERN: {
                discussionSettingsViewModel.setMessageVibrationPattern(value);
                discussionSettingsViewModel.notifySettingsChangedListeners(discussionSettingsViewModel.getDiscussionCustomization().getValue());
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR: {
                discussionSettingsViewModel.setMessageLedColor(value);
                discussionSettingsViewModel.notifySettingsChangedListeners(discussionSettingsViewModel.getDiscussionCustomization().getValue());
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_VIBRATION_PATTERN: {
                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> discussionCustomization.prefCallNotificationVibrationPattern = value);
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE: {
                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> discussionCustomization.prefCallNotificationRingtone = value);
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_READ_RECEIPT: {
                if (value == null) {
                    break;
                }

                final Boolean targetSendReadReceipt;
                switch (value) {
                    case "0":
                        targetSendReadReceipt = false;
                        break;
                    case "1":
                        targetSendReadReceipt = true;
                        break;
                    case "null":
                    default:
                        targetSendReadReceipt = null;
                        break;
                }

                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> discussionCustomization.prefSendReadReceipt = targetSendReadReceipt);

                try {
                    // propagate change to other owned devices if needed
                    Discussion discussion = discussionSettingsViewModel.getDiscussionLiveData().getValue();
                    if (discussion != null) {
                        switch (discussion.discussionType) {
                            case Discussion.TYPE_CONTACT:
                                AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(discussion.bytesOwnedIdentity, ObvSyncAtom.createContactSendReadReceiptChange(discussion.bytesDiscussionIdentifier, targetSendReadReceipt));
                                break;
                            case Discussion.TYPE_GROUP:
                                AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(discussion.bytesOwnedIdentity, ObvSyncAtom.createGroupV1SendReadReceiptChange(discussion.bytesDiscussionIdentifier, targetSendReadReceipt));
                                break;
                            case Discussion.TYPE_GROUP_V2:
                                AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(discussion.bytesOwnedIdentity, ObvSyncAtom.createGroupV2SendReadReceiptChange(discussion.bytesDiscussionIdentifier, targetSendReadReceipt));
                                break;
                        }
                        AppSingleton.getEngine().profileBackupNeeded(discussion.bytesOwnedIdentity);
                    }
                } catch (Exception e) {
                    Logger.w("Failed to propagate send read receipt change to other devices");
                    e.printStackTrace();
                }
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND: {
                if (value == null) {
                    break;
                }
                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> {
                    switch (value) {
                        case "0":
                            discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = false;
                            break;
                        case "1":
                            discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = true;
                            break;
                        case "null":
                        default:
                            discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages = null;
                            break;
                    }
                });
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES: {
                if (value == null) {
                    break;
                }
                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> {
                    switch (value) {
                        case "0":
                            discussionCustomization.prefRetainWipedOutboundMessages = false;
                            break;
                        case "1":
                            discussionCustomization.prefRetainWipedOutboundMessages = true;
                            break;
                        case "null":
                        default:
                            discussionCustomization.prefRetainWipedOutboundMessages = null;
                            break;
                    }
                });
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_COUNT: {
                if (value == null) {
                    break;
                }
                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> {
                    if ("".equals(value)) {
                        discussionCustomization.prefDiscussionRetentionCount = null;
                    } else {
                        try {
                            discussionCustomization.prefDiscussionRetentionCount = Long.parseLong(value);
                        } catch (Exception e) {
                            discussionCustomization.prefDiscussionRetentionCount = null;
                        }
                    }
                });
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_DURATION: {
                if (value == null) {
                    break;
                }
                performUpdateOnPossiblyNullDiscussionCustomization((DiscussionCustomization discussionCustomization) -> {
                    if ("null".equals(value)) {
                        discussionCustomization.prefDiscussionRetentionDuration = null;
                    } else {
                        try {
                            discussionCustomization.prefDiscussionRetentionDuration = Long.parseLong(value);
                        } catch (Exception e) {
                            discussionCustomization.prefDiscussionRetentionDuration = null;
                        }
                    }
                });
                break;
            }
        }
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        switch (key) {
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_RINGTONE: {
                if (discussionSettingsViewModel.getMessageRingtone() != null) {
                    return discussionSettingsViewModel.getMessageRingtone();
                }
                return SettingsActivity.PREF_KEY_MESSAGE_RINGTONE_DEFAULT;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_VIBRATION_PATTERN: {
                if (discussionSettingsViewModel.getMessageVibrationPattern() != null) {
                    return discussionSettingsViewModel.getMessageVibrationPattern();
                }
                return SettingsActivity.PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR: {
                return discussionSettingsViewModel.getMessageLedColor();
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_VIBRATION_PATTERN: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefCallNotificationVibrationPattern != null) {
                        return discussionCustomization.prefCallNotificationVibrationPattern;
                    }
                }
                return SettingsActivity.PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_RINGTONE: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefCallNotificationRingtone != null) {
                        return discussionCustomization.prefCallNotificationRingtone;
                    }
                }
                return SettingsActivity.PREF_KEY_CALL_RINGTONE_DEFAULT;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_READ_RECEIPT: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefSendReadReceipt != null) {
                        return discussionCustomization.prefSendReadReceipt ? "1" : "0";
                    }
                }
                return "null";
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages != null) {
                        return discussionCustomization.prefAutoOpenLimitedVisibilityInboundMessages ? "1" : "0";
                    }
                }
                return "null";
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefRetainWipedOutboundMessages != null) {
                        return discussionCustomization.prefRetainWipedOutboundMessages ? "1" : "0";
                    }
                }
                return "null";
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_COUNT: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefDiscussionRetentionCount != null) {
                        return Long.toString(discussionCustomization.prefDiscussionRetentionCount);
                    }
                }
                return "";
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_RETENTION_DURATION: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    if (discussionCustomization.prefDiscussionRetentionDuration != null) {
                        return Long.toString(discussionCustomization.prefDiscussionRetentionDuration);
                    }
                }
                return "null";
            }
            default:
                return null;
        }
    }


    @Override
    public void putBoolean(String key, boolean value) {
        switch (key) {
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_CUSTOM_NOTIFICATION: {
                discussionSettingsViewModel.setUseCustomMessageNotification(value);
                discussionSettingsViewModel.notifySettingsChangedListeners(discussionSettingsViewModel.getDiscussionCustomization().getValue());
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_CUSTOM_NOTIFICATION: {
                performUpdateOnPossiblyNullDiscussionCustomization(
                        (DiscussionCustomization discussionCustomization) -> discussionCustomization.prefUseCustomCallNotification = value);
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_USE_FLASH: {
                performUpdateOnPossiblyNullDiscussionCustomization(
                        (DiscussionCustomization discussionCustomization) -> discussionCustomization.prefCallNotificationUseFlash = value);
                break;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS: {
                performUpdateOnPossiblyNullDiscussionCustomization(
                        (DiscussionCustomization discussionCustomization) -> discussionCustomization.prefMuteNotifications = value);
                break;
            }
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        switch (key) {
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MESSAGE_CUSTOM_NOTIFICATION: {
                return discussionSettingsViewModel.useCustomMessageNotification();
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_CUSTOM_NOTIFICATION: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    return discussionCustomization.prefUseCustomCallNotification;
                }
                return false;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CALL_USE_FLASH: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    return discussionCustomization.prefCallNotificationUseFlash;
                }
                return false;
            }
            case DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS: {
                DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                if (discussionCustomization != null) {
                    return discussionCustomization.shouldMuteNotifications();
                }
                return false;
            }
            default:
                return false;
        }
    }
}
