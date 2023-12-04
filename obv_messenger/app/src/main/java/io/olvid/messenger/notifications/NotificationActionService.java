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

package io.olvid.messenger.notifications;

import android.app.IntentService;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.entity.jsons.JsonMessage;
import io.olvid.messenger.databases.tasks.CreateReadMessageMetadata;
import io.olvid.messenger.settings.SettingsActivity;


public class NotificationActionService extends IntentService {
    public static final String ACTION_ACCEPT_INVITATION = "accept_invitation";
    public static final String ACTION_REJECT_INVITATION = "reject_invitation";
    public static final String ACTION_DISCUSSION_REPLY = "discussion_reply";
    public static final String ACTION_DISCUSSION_CLEAR = "discussion_clear";
    public static final String ACTION_DISCUSSION_MARK_AS_READ = "mark_as_read";
    public static final String ACTION_MISSED_CALL_MESSAGE = "discussion_missed_call_message";
    public static final String ACTION_MESSAGE_REACTION_CLEAR = "message_reaction_clear";
    public static final String ACTION_DEVICE_TRUST = "device_trust";

    public static final String EXTRA_INVITATION_DIALOG_UUID = "dialog_uuid";
    public static final String EXTRA_DISCUSSION_ID = "discussion_id";
    public static final String EXTRA_MESSAGE_ID = "message_id";
    public static final String EXTRA_BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String EXTRA_DEVICE_UID = "device_uid";

    public static final String KEY_TEXT_REPLY = "text_reply";


    public NotificationActionService() {
        super("NotificationActionService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case ACTION_ACCEPT_INVITATION: {
                UUID dialogUuid = UUID.fromString(intent.getStringExtra(EXTRA_INVITATION_DIALOG_UUID));
                if (dialogUuid != null) {
                    try {
                        Invitation invitation = AppDatabase.getInstance().invitationDao().getByDialogUuid(dialogUuid);
                        if (invitation != null) {
                            switch (invitation.associatedDialog.getCategory().getId()) {
                                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptInvite(true);
                                    break;
                                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptMediatorInvite(true);
                                    break;
                                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                                case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptGroupInvite(true);
                                    break;
                                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptOneToOneInvitation(true);
                                    break;
                                default:
                                    throw new Exception("Bad dialog category");
                            }
                            AppSingleton.getEngine().respondToDialog(invitation.associatedDialog);
                        }
                        AndroidNotificationManager.clearInvitationNotification(dialogUuid);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case ACTION_REJECT_INVITATION: {
                UUID dialogUuid = UUID.fromString(intent.getStringExtra(EXTRA_INVITATION_DIALOG_UUID));
                if (dialogUuid != null) {
                    try {
                        Invitation invitation = AppDatabase.getInstance().invitationDao().getByDialogUuid(dialogUuid);
                        if (invitation != null) {
                            switch (invitation.associatedDialog.getCategory().getId()) {
                                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptInvite(false);
                                    break;
                                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptMediatorInvite(false);
                                    break;
                                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                                case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY:
                                case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptGroupInvite(false);
                                    break;
                                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                                    invitation.associatedDialog.setResponseToAcceptOneToOneInvitation(false);
                                    break;
                                default:
                                    throw new Exception("Bad dialog category");
                            }
                            AppSingleton.getEngine().respondToDialog(invitation.associatedDialog);
                        }
                        AndroidNotificationManager.clearInvitationNotification(dialogUuid);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case ACTION_DISCUSSION_CLEAR: {
                final long discussionId = intent.getLongExtra(EXTRA_DISCUSSION_ID, -1);
                if (discussionId != -1) {
                    AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId);
                }
                break;
            }
            case ACTION_MESSAGE_REACTION_CLEAR: {
                final long messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1);
                if (messageId != -1) {
                    AndroidNotificationManager.clearMessageReactionsNotification(messageId);
                }
                break;
            }
            case ACTION_DISCUSSION_MARK_AS_READ: {
                final long discussionId = intent.getLongExtra(EXTRA_DISCUSSION_ID, -1);
                if (discussionId != -1) {
                    AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId);
                    markAllDiscussionMessagesRead(discussionId);
                }
                break;
            }
            case ACTION_DISCUSSION_REPLY: {
                final long discussionId = intent.getLongExtra(EXTRA_DISCUSSION_ID, -1);
                if (discussionId != -1) {
                    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
                    if (remoteInput != null) {
                        CharSequence sequence = remoteInput.getCharSequence(KEY_TEXT_REPLY);
                        if (sequence != null) {
                            final String response = sequence.toString().trim();
                            final AppDatabase db = AppDatabase.getInstance();
                            final Discussion discussion = db.discussionDao().getById(discussionId);
                            if (discussion != null) {
                                if (response.length() > 0) {
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                                    final JsonExpiration jsonExpiration;
                                    if (discussionCustomization != null) {
                                        jsonExpiration = discussionCustomization.getExpirationJson();
                                    } else {
                                        jsonExpiration = null;
                                    }
                                    db.runInTransaction(() -> {
                                        discussion.lastOutboundMessageSequenceNumber++;
                                        db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
                                        discussion.updateLastMessageTimestamp(System.currentTimeMillis());
                                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);

                                        JsonMessage jsonMessage = new JsonMessage(response);
                                        if (jsonExpiration != null) {
                                            jsonMessage.setJsonExpiration(jsonExpiration);
                                        }
                                        Message message = new Message(
                                                db,
                                                discussion.lastOutboundMessageSequenceNumber,
                                                jsonMessage,
                                                null,
                                                System.currentTimeMillis(),
                                                Message.STATUS_UNPROCESSED,
                                                Message.TYPE_OUTBOUND_MESSAGE,
                                                discussionId,
                                                null,
                                                discussion.bytesOwnedIdentity,
                                                discussion.senderThreadIdentifier,
                                                0,
                                                0
                                        );
                                        message.id = db.messageDao().insert(message);
                                        message.post(false, null);

                                        OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(discussion.bytesOwnedIdentity);
                                        AndroidNotificationManager.displayReceivedMessageNotification(discussion, message, null, ownedIdentity);
                                    });
                                    markAllDiscussionMessagesRead(discussionId);
                                } else {
                                    AndroidNotificationManager.displayReceivedMessageNotification(discussion, null, null, null);
                                }
                                break;
                            }
                        }
                    }
                    AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId);
                }
                break;
            }
            case ACTION_MISSED_CALL_MESSAGE: {
                final long discussionId = intent.getLongExtra(EXTRA_DISCUSSION_ID, -1);
                if (discussionId != -1) {
                    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
                    if (remoteInput != null) {
                        CharSequence sequence = remoteInput.getCharSequence(KEY_TEXT_REPLY);
                        if (sequence != null) {
                            final String response = sequence.toString().trim();
                            final AppDatabase db = AppDatabase.getInstance();
                            final Discussion discussion = db.discussionDao().getById(discussionId);
                            if (discussion != null) {
                                AndroidNotificationManager.displayMissedCallNotification(discussion, response);
                                if (response.length() > 0) {
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                                    final JsonExpiration jsonExpiration;
                                    if (discussionCustomization != null) {
                                        jsonExpiration = discussionCustomization.getExpirationJson();
                                    } else {
                                        jsonExpiration = null;
                                    }
                                    db.runInTransaction(() -> {
                                        discussion.lastOutboundMessageSequenceNumber++;
                                        db.discussionDao().updateLastOutboundMessageSequenceNumber(discussion.id, discussion.lastOutboundMessageSequenceNumber);
                                        discussion.updateLastMessageTimestamp(System.currentTimeMillis());
                                        db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                        JsonMessage jsonMessage = new JsonMessage(response);
                                        if (jsonExpiration != null) {
                                            jsonMessage.setJsonExpiration(jsonExpiration);
                                        }
                                        Message message = new Message(
                                                db,
                                                discussion.lastOutboundMessageSequenceNumber,
                                                jsonMessage,
                                                null,
                                                System.currentTimeMillis(),
                                                Message.STATUS_UNPROCESSED,
                                                Message.TYPE_OUTBOUND_MESSAGE,
                                                discussionId,
                                                null,
                                                discussion.bytesOwnedIdentity,
                                                discussion.senderThreadIdentifier,
                                                0,
                                                0
                                        );
                                        message.id = db.messageDao().insert(message);
                                        message.post(false, null);
                                    });
                                }
                            } else {
                                AndroidNotificationManager.clearMissedCallNotification(discussionId);
                            }
                        }
                    }
                }
                break;
            } case ACTION_DEVICE_TRUST: {
                final byte[] deviceUid = intent.getByteArrayExtra(EXTRA_DEVICE_UID);
                final byte[] bytesOwnedIdentity = intent.getByteArrayExtra(EXTRA_BYTES_OWNED_IDENTITY);
                if (deviceUid != null && bytesOwnedIdentity != null) {
                    final AppDatabase db = AppDatabase.getInstance();
                    db.ownedDeviceDao().updateTrusted(bytesOwnedIdentity, deviceUid, true);
                    AndroidNotificationManager.clearDeviceTrustNotification(deviceUid);
                }
            }
        }
    }


    public static void markAllDiscussionMessagesRead(long discussionId) {
        Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
        if (discussion == null) {
            return;
        }

        DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
        final boolean sendReadReceipt;
        if (discussionCustomization != null && discussionCustomization.prefSendReadReceipt != null) {
            sendReadReceipt = discussionCustomization.prefSendReadReceipt;
        } else {
            sendReadReceipt = SettingsActivity.getDefaultSendReadReceipt();
        }

        final List<Message> messages = AppDatabase.getInstance().messageDao().getAllUnreadDiscussionMessagesSync(discussionId);
        App.runThread(() -> {
            for (Message message : messages) {
                if (message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                    if (sendReadReceipt) {
                        message.sendMessageReturnReceipt(discussion, Message.RETURN_RECEIPT_STATUS_READ);
                    }
                    new CreateReadMessageMetadata(message.id).run();
                }
            }
        });

        if (AppDatabase.getInstance().ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)) {
            Long timestamp = AppDatabase.getInstance().messageDao().getServerTimestampOfLatestUnreadInboundMessageInDiscussion(discussionId);
            if (timestamp != null) {
                Message.postDiscussionReadMessage(discussion, timestamp);
            }
        }
        AppDatabase.getInstance().messageDao().markAllDiscussionMessagesRead(discussionId);
        AppDatabase.getInstance().discussionDao().updateDiscussionUnreadStatus(discussionId, false);
    }
}
