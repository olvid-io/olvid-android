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

package io.olvid.engine.channel.datatypes;


import java.sql.SQLException;

import io.olvid.engine.Logger;
import io.olvid.engine.channel.databases.ObliviousChannel;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelApplicationMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelProtocolMessageToSend;
import io.olvid.engine.datatypes.containers.MessageToSend;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;

public abstract class NetworkChannel extends Channel {
    public abstract MessageToSend.Header wrapMessageKey(AuthEncKey messageKey, PRNGService prng, boolean partOfFullRatchetProtocol);

    public static NetworkChannel[] acceptableChannelsForPosting(ChannelManagerSession channelManagerSession, ChannelMessageToSend message) throws Exception {
        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.OBLIVIOUS_CHANNEL_TYPE:
            case SendChannelInfo.ALL_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_ON_SAME_SERVER_TYPE:
            case SendChannelInfo.ALL_OWNED_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_TYPE:
            case SendChannelInfo.OBLIVIOUS_CHANNEL_OR_PRE_KEY_TYPE:
                return ObliviousChannel.acceptableChannelsForPosting(channelManagerSession, message);
            case SendChannelInfo.ASYMMETRIC_CHANNEL_TYPE:
            case SendChannelInfo.ASYMMETRIC_BROADCAST_CHANNEL_TYPE:
                return AsymmetricChannel.acceptableChannelsForPosting(message, channelManagerSession.encryptionForIdentityDelegate);
            default:
                return new NetworkChannel[0];
        }
    }


    public static UID post(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        if (channelManagerSession.networkSendDelegate == null) {
            Logger.w("NetworkSendDelegate not set yet when posting a ChannelMessageToSend.");
            throw new Exception();
        }

        NetworkChannel[] networkChannels = acceptableChannelsForPosting(channelManagerSession, message);

        if (networkChannels.length == 0) {
            Logger.i("No acceptable channels were found for posting");
            throw new NoAcceptableChannelException();
        }

        // get the minimum suite version of all network channels
        int suiteVersion = Suite.LATEST_VERSION;
        for (NetworkChannel networkChannel: networkChannels) {
            if (networkChannel.getObliviousEngineVersion() < suiteVersion) {
                suiteVersion = networkChannel.getObliviousEngineVersion();
            }
        }

        AuthEnc authEnc = Suite.getDefaultAuthEnc(suiteVersion);

        MessageToSend messageToSend;
        UID messageUid = new UID(prng);
        switch (message.getMessageType()) {
            case MessageType.APPLICATION_MESSAGE_TYPE: {
                if (!(message instanceof ChannelApplicationMessageToSend)) {
                    Logger.w("Trying to post a message of type " + message.getMessageType() + " that is not a ChannelApplicationMessageToSend.");
                    throw new Exception();
                }
                ChannelApplicationMessageToSend channelApplicationMessageToSend = (ChannelApplicationMessageToSend) message;
                ChannelApplicationMessageToSend.Attachment[] attachments = channelApplicationMessageToSend.getAttachments();
                Encoded[] listOfEncodedAttachments = new Encoded[attachments.length + 1];
                MessageToSend.Attachment[] messageToSendAttachments = new MessageToSend.Attachment[attachments.length];

                for (int i = 0; i < attachments.length; i++) {
                    AuthEncKey attachmentKey = authEnc.generateKey(prng);
                    listOfEncodedAttachments[i] = Encoded.of(new Encoded[]{
                            Encoded.of(attachmentKey),
                            Encoded.of(attachments[i].getMetadata())
                    });
                    messageToSendAttachments[i] = new MessageToSend.Attachment(attachments[i].getUrl(), attachments[i].isDeleteAfterSend(), attachments[i].getAttachmentLength(), attachmentKey);
                }
                // add the message payload after the attachment keys and metadata
                listOfEncodedAttachments[attachments.length] = Encoded.of(channelApplicationMessageToSend.getMessagePayload());

                Encoded plaintextContent = Encoded.of(new Encoded[]{
                        Encoded.of(MessageType.APPLICATION_MESSAGE_TYPE),
                        Encoded.of(listOfEncodedAttachments)
                });

                ////////
                // Add a padding to message to obfuscate content length. Commented out for now
                byte[] paddedPlaintext = new byte[((plaintextContent.getBytes().length - 1) | 511) + 1];
                System.arraycopy(plaintextContent.getBytes(), 0, paddedPlaintext, 0, plaintextContent.getBytes().length);

                AuthEncKey messageKey = authEnc.generateMessageKey(prng, paddedPlaintext);

                MessageToSend.Header[] headers = generateHeaders(networkChannels, false, messageKey, prng);

                // check that all headers are for the same server
                String server = getServer(headers);


                EncryptedBytes encryptedContent = authEnc.encrypt(messageKey, paddedPlaintext, prng);

                final EncryptedBytes encryptedExtendedContent;
                if (channelApplicationMessageToSend.getExtendedMessagePayload() != null) {
                    PRNG extendedMessagePRNG = Suite.getDefaultPRNG(0, Seed.of(messageKey));
                    AuthEncKey extendedMessageAuthEncKey = authEnc.generateKey(extendedMessagePRNG);
                    encryptedExtendedContent = authEnc.encrypt(extendedMessageAuthEncKey, channelApplicationMessageToSend.getExtendedMessagePayload(), prng);
                } else {
                    encryptedExtendedContent = null;
                }
                messageToSend = new MessageToSend(message.getSendChannelInfo().getFromIdentity(), messageUid, server, encryptedContent, encryptedExtendedContent, headers, messageToSendAttachments, channelApplicationMessageToSend.hasUserContent(), channelApplicationMessageToSend.isVoipMessage());
                break;
            }
            case MessageType.PROTOCOL_MESSAGE_TYPE: {
                if (!(message instanceof ChannelProtocolMessageToSend)) {
                    Logger.w("Trying to post a message of type " + message.getMessageType() + " that is not a ChannelProtocolMessageToSend.");
                    throw new Exception();
                }
                ChannelProtocolMessageToSend channelProtocolMessageToSend = (ChannelProtocolMessageToSend) message;
                Encoded plaintextContent = Encoded.of(new Encoded[]{
                        Encoded.of(MessageType.PROTOCOL_MESSAGE_TYPE),
                        channelProtocolMessageToSend.getEncodedElements()
                });

                ////////
                // Add a padding to message to obfuscate content length. Commented out for now
                byte[] paddedPlaintext = new byte[((plaintextContent.getBytes().length - 1) | 511) + 1];
                System.arraycopy(plaintextContent.getBytes(), 0, paddedPlaintext, 0, plaintextContent.getBytes().length);

                AuthEncKey messageKey = authEnc.generateMessageKey(prng, paddedPlaintext);

                MessageToSend.Header[] headers = generateHeaders(networkChannels, channelProtocolMessageToSend.isPartOfFullRatchetProtocolOfTheSendSeed(), messageKey, prng);

                // check that all headers are for the same server
                String server = getServer(headers);


                EncryptedBytes encryptedContent = authEnc.encrypt(messageKey, paddedPlaintext, prng);
                messageToSend = new MessageToSend(message.getSendChannelInfo().getFromIdentity(), messageUid, server, encryptedContent, headers, channelProtocolMessageToSend.hasUserContent());
                break;
            }
            default:
                Logger.w("Trying to post a message of type " + message.getMessageType() + " on a network channel.");
                throw new Exception();
        }
        channelManagerSession.networkSendDelegate.post(channelManagerSession.session, messageToSend);
        return messageUid;
    }

    private static MessageToSend.Header[] generateHeaders(NetworkChannel[] networkChannels, boolean partOfFullRatchetProtocol, AuthEncKey messageKey, PRNGService prng) {
        MessageToSend.Header[] headers = new MessageToSend.Header[networkChannels.length];
        for (int i=0; i<networkChannels.length; i++) {
            headers[i] = networkChannels[i].wrapMessageKey(messageKey, prng, partOfFullRatchetProtocol);
        }

        return headers;
    }

    private static String getServer(MessageToSend.Header[] headers) throws Exception {
        // check that all headers are for the same server
        String server = headers[0].getToIdentity().getServer();
        for (int i=1; i<headers.length; i++) {
            if (!server.equals(headers[i].getToIdentity().getServer())) {
                Logger.w("Server mismatch in the headers of a ChannelMessageToSend");
                throw new Exception();
            }
        }
        return server;
    }
}
