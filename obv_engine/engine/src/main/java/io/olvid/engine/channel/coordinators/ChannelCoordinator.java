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

package io.olvid.engine.channel.coordinators;


import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.channel.databases.ObliviousChannel;
import io.olvid.engine.channel.datatypes.AsymmetricChannel;
import io.olvid.engine.channel.datatypes.PreKeyChannel;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.containers.AuthEncKeyAndChannelInfo;
import io.olvid.engine.channel.datatypes.ChannelManagerSession;
import io.olvid.engine.channel.datatypes.ChannelManagerSessionFactory;
import io.olvid.engine.channel.datatypes.ChannelReceivedApplicationMessage;
import io.olvid.engine.channel.datatypes.ChannelReceivedMessage;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.containers.ProtocolReceivedMessage;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;

public class ChannelCoordinator {
    private final ChannelManagerSessionFactory channelManagerSessionFactory;


    public ChannelCoordinator(ChannelManagerSessionFactory channelManagerSessionFactory) {
        this.channelManagerSessionFactory = channelManagerSessionFactory;
    }



    public void decryptAndProcess(NetworkReceivedMessage networkReceivedMessage) {
        try (ChannelManagerSession channelManagerSession = channelManagerSessionFactory.getSession()) {
            channelManagerSession.session.startTransaction();
            // try to decrypt with an ObliviousChannel
            AuthEncKeyAndChannelInfo authEncKeyAndChannelInfo = ObliviousChannel.unwrapMessageKey(channelManagerSession, networkReceivedMessage.getHeader());
            if (authEncKeyAndChannelInfo != null) {
                Logger.d("The message can be decrypted through an ObliviousChannel.");
                // the message was encrypted using an ObliviousChannel -> we do the processing ourselves
                decryptAndProcess(channelManagerSession, networkReceivedMessage, authEncKeyAndChannelInfo);
                channelManagerSession.session.commit();
                return;
            }

            // try to decrypt with a PreKey
            authEncKeyAndChannelInfo = PreKeyChannel.unwrapMessageKey(channelManagerSession, networkReceivedMessage.getHeader());
            if (authEncKeyAndChannelInfo != null) {
                Logger.d("The message can be decrypted with a PreKey. ");
                decryptAndProcess(channelManagerSession, networkReceivedMessage, authEncKeyAndChannelInfo);
                channelManagerSession.session.commit();
                return;
            }

            // try to decrypt with an AsymmetricChannel
            authEncKeyAndChannelInfo = AsymmetricChannel.unwrapMessageKey(channelManagerSession, networkReceivedMessage.getHeader());
            if (authEncKeyAndChannelInfo != null) {
                Logger.d("The message can be decrypted through an AsymmetricChannel.");
                decryptAndProcess(channelManagerSession, networkReceivedMessage, authEncKeyAndChannelInfo);
                channelManagerSession.session.commit();
                return;
            }

            // we were not able to decrypt the message -> we delete it
            if (channelManagerSession.networkFetchDelegate != null) {
                Logger.d("The message cannot be decrypted.");
                channelManagerSession.networkFetchDelegate.messageCannotBeDecrypted(channelManagerSession.session, networkReceivedMessage.getOwnedIdentity(), networkReceivedMessage.getMessageUid());
                channelManagerSession.session.commit();
            } else {
                Logger.w("Unable to delete a networkReceivedMessage because the NetworkFetchDelegate is not set yet.");
            }
        } catch (SQLException e) {
            Logger.i("Unable to decryptAndProcess networkReceivedMessage with uid " + networkReceivedMessage.getMessageUid());
        }
    }



    private void decryptAndProcess(ChannelManagerSession channelManagerSession, NetworkReceivedMessage networkReceivedMessage, AuthEncKeyAndChannelInfo authEncKeyAndChannelInfo) {
        if (channelManagerSession.networkFetchDelegate == null) {
            return;
        }
        ChannelReceivedMessage channelReceivedMessage;
        try {
            channelReceivedMessage = new ChannelReceivedMessage(networkReceivedMessage, authEncKeyAndChannelInfo.getAuthEncKey(), authEncKeyAndChannelInfo.getReceptionChannelInfo());
        } catch (Exception e) {
            channelManagerSession.networkFetchDelegate.deleteMessageAndAttachments(channelManagerSession.session, networkReceivedMessage.getOwnedIdentity(), networkReceivedMessage.getMessageUid());
            return;
        }

        // for preKey encrypted messages, check that the contact exists, otherwise, put the message on hold until the contact is added
        if (channelReceivedMessage.getReceptionChannelInfo().getChannelType() == ReceptionChannelInfo.PRE_KEY_CHANNEL_TYPE) {
            Identity ownedIdentity = networkReceivedMessage.getOwnedIdentity();
            Identity contactIdentity = channelReceivedMessage.getReceptionChannelInfo().getRemoteIdentity();
            if (!Objects.equals(ownedIdentity, contactIdentity)) {
                try {
                    // the message is from a contact
                    if (!channelManagerSession.identityDelegate.isIdentityAContactOfOwnedIdentity(channelManagerSession.session, ownedIdentity, contactIdentity)) {
                        // contact unknown, set the from identity of the inbox message to reprocess it once the contact is created
                        Logger.i("Received a PreKey encrypted message from an unknown contact, putting it on hold...");
                        channelManagerSession.networkFetchDelegate.setInboxMessageFromIdentityForMissingPreKeyContact(
                                channelManagerSession.session,
                                networkReceivedMessage.getOwnedIdentity(),
                                networkReceivedMessage.getMessageUid(),
                                contactIdentity);
                        return;
                    } else {
                        EnumSet<ObvContactActiveOrInactiveReason> reasons = channelManagerSession.identityDelegate.getContactActiveOrInactiveReasons(channelManagerSession.session, ownedIdentity, contactIdentity);
                        if (reasons != null && reasons.contains(ObvContactActiveOrInactiveReason.REVOKED) && !reasons.contains(ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED)) {
                            // the contact is blocked, discard the message
                            Logger.w("Received a PreKey encrypted message from a blocked contact, discarding it!");
                            channelManagerSession.networkFetchDelegate.deleteMessageAndAttachments(channelManagerSession.session, networkReceivedMessage.getOwnedIdentity(), networkReceivedMessage.getMessageUid());
                            return;
                        } else if (!channelManagerSession.identityDelegate.isContactDeviceKnown(channelManagerSession.session, ownedIdentity, contactIdentity, channelReceivedMessage.getReceptionChannelInfo().getRemoteDeviceUid())) {
                            channelManagerSession.protocolStarterDelegate.startDeviceDiscoveryProtocolWithinTransaction(channelManagerSession.session, ownedIdentity, contactIdentity);
                        }
                    }
                } catch (Exception e) {
                    Logger.x(e);
                }
            }
        }

        switch (channelReceivedMessage.getMessageType()) {
            case MessageType.PROTOCOL_MESSAGE_TYPE:
                if (channelManagerSession.protocolDelegate == null) {
                    Logger.w("Received a protocol message, but no ProtocolDelegate is set.");
                    return;
                }
                try {
                    ProtocolReceivedMessage protocolReceivedMessage = ProtocolReceivedMessage.of(channelReceivedMessage);
                    channelManagerSession.protocolDelegate.process(channelManagerSession.session, protocolReceivedMessage);
                } catch (Exception e) {
                    Logger.i("Error while processing a ProtocolReceivedMessage.");
                } finally {
                    channelManagerSession.networkFetchDelegate.deleteMessageAndAttachments(channelManagerSession.session, networkReceivedMessage.getOwnedIdentity(), networkReceivedMessage.getMessageUid());
                }
                break;
            case MessageType.APPLICATION_MESSAGE_TYPE:
                try {
                    ChannelReceivedApplicationMessage channelReceivedApplicationMessage = ChannelReceivedApplicationMessage.of(channelReceivedMessage);
                    if (channelReceivedApplicationMessage == null) {
                        Logger.e("Error parsing a ChannelReceivedMessage, deleting it");
                        channelManagerSession.networkFetchDelegate.deleteMessageAndAttachments(channelManagerSession.session, networkReceivedMessage.getOwnedIdentity(), networkReceivedMessage.getMessageUid());
                        break;
                    }

                    channelManagerSession.networkFetchDelegate.setAttachmentKeyAndMetadataAndMessagePayload(
                            channelManagerSession.session,
                            channelReceivedApplicationMessage.getOwnedIdentity(),
                            channelReceivedApplicationMessage.getMessageUid(),
                            authEncKeyAndChannelInfo.getReceptionChannelInfo().getRemoteIdentity(),
                            authEncKeyAndChannelInfo.getReceptionChannelInfo().getRemoteDeviceUid(),
                            channelReceivedApplicationMessage.getAttachmentsKeyAndMetadata(),
                            channelReceivedApplicationMessage.getMessagePayload(),
                            channelReceivedMessage.getExtendedPayloadKey()
                    );
                } catch (Exception e) {
                    Logger.x(e);
                    Logger.i("Error while processing a ChannelReceivedApplicationMessage.");
                    channelManagerSession.networkFetchDelegate.deleteMessageAndAttachments(channelManagerSession.session, networkReceivedMessage.getOwnedIdentity(), networkReceivedMessage.getMessageUid());
                }
                break;
            default:
                Logger.w("The ChannelReceivedMessage contains an unknown MessageType: " + channelReceivedMessage.getMessageType());
        }
    }
}
