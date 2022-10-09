/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.engine.engine;

import java.util.HashMap;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelDialogMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.notifications.ChannelNotifications;
import io.olvid.engine.datatypes.notifications.ProtocolNotifications;
import io.olvid.engine.engine.databases.UserInterfaceDialog;
import io.olvid.engine.engine.datatypes.EngineSession;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerChannelsAndProtocols implements NotificationListener {
    private final Engine engine;

    public NotificationListenerChannelsAndProtocols(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG,
                ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED,
                ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED,
                ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED,
                ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED,
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG: {
                try {
                    Session session = (Session) userInfo.get(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG_SESSION_KEY);
                    ChannelDialogMessageToSend channelDialogMessageToSend = (ChannelDialogMessageToSend) userInfo.get(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG_CHANNEL_DIALOG_MESSAGE_TO_SEND_KEY);
                    if (channelDialogMessageToSend == null) {
                        break;
                    }
                    // check whether it is a new/updated dialog, or a delete dialog
                    if (channelDialogMessageToSend.getSendChannelInfo().getDialogType().id == DialogType.DELETE_DIALOG_ID) {
                        UserInterfaceDialog userInterfaceDialog = UserInterfaceDialog.get(engine.wrapSession(session), channelDialogMessageToSend.getSendChannelInfo().getDialogUuid());
                        if (userInterfaceDialog != null) {
                            userInterfaceDialog.delete();
                        }
                    } else {
                        UserInterfaceDialog.createOrReplace(engine.wrapSession(session), engine.createDialog(channelDialogMessageToSend));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED: {
                try (EngineSession engineSession = engine.getSession()) {
                    Identity contactIdentity = (Identity) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED_REMOTE_IDENTITY_KEY);
                    UID currentDeviceUid = (UID) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_CONFIRMED_CURRENT_DEVICE_UID_KEY);
                    if (contactIdentity == null || currentDeviceUid == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    Identity ownedIdentity = engine.identityManager.getOwnedIdentityForDeviceUid(engineSession.session, currentDeviceUid);
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                    engine.postEngineNotification(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED: {
                try (EngineSession engineSession = engine.getSession()) {
                    Identity contactIdentity = (Identity) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_REMOTE_IDENTITY_KEY);
                    UID currentDeviceUid = (UID) userInfo.get(ChannelNotifications.NOTIFICATION_OBLIVIOUS_CHANNEL_DELETED_CURRENT_DEVICE_UID_KEY);
                    if (contactIdentity == null || currentDeviceUid == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    Identity ownedIdentity = engine.identityManager.getOwnedIdentityForDeviceUid(engineSession.session, currentDeviceUid);
                    if (ownedIdentity == null) {
                        break;
                    }
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                    engine.postEngineNotification(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED: {
                Identity ownedIdentity = (Identity) userInfo.get(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_CONTACT_IDENTITY_KEY);
                byte[] nonce = (byte[]) userInfo.get(ProtocolNotifications.NOTIFICATION_MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY);

                if (ownedIdentity == null || contactIdentity == null || nonce == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_NONCE_KEY, nonce);

                engine.postEngineNotification(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED, engineInfo);
                break;
            }
            case ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_OWNED_IDENTITY_KEY);
                GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_GROUP_IDENTIFIER_KEY);
                Boolean error = (Boolean) userInfo.get(ProtocolNotifications.NOTIFICATION_GROUP_V2_UPDATE_FAILED_ERROR_KEY);

                if (ownedIdentity == null || groupIdentifier == null || error == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_GROUP_IDENTIFIER_KEY, groupIdentifier.getBytes());
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_FAILED_ERROR_KEY, error);

                engine.postEngineNotification(EngineNotifications.GROUP_V2_UPDATE_FAILED, engineInfo);
                break;
            }            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
