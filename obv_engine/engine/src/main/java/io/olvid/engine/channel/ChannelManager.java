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

package io.olvid.engine.channel;


import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.olvid.engine.Logger;
import io.olvid.engine.channel.coordinators.ChannelCoordinator;
import io.olvid.engine.channel.databases.ObliviousChannel;
import io.olvid.engine.channel.databases.Provision;
import io.olvid.engine.channel.databases.ProvisionedKeyMaterial;
import io.olvid.engine.channel.datatypes.Channel;
import io.olvid.engine.channel.datatypes.ChannelManagerSession;
import io.olvid.engine.channel.datatypes.ChannelManagerSessionFactory;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.CreateSessionDelegate;
import io.olvid.engine.metamanager.FullRatchetProtocolStarterDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NetworkFetchDelegate;
import io.olvid.engine.metamanager.NetworkSendDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;
import io.olvid.engine.metamanager.ProcessDownloadedMessageDelegate;
import io.olvid.engine.metamanager.ProtocolDelegate;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;

public class ChannelManager implements ChannelDelegate, ProcessDownloadedMessageDelegate, ChannelManagerSessionFactory, ObvManager {
    private final ChannelCoordinator channelCoordinator;


    private CreateSessionDelegate createSessionDelegate;
    private NetworkSendDelegate networkSendDelegate;
    private NetworkFetchDelegate networkFetchDelegate;
    private FullRatchetProtocolStarterDelegate fullRatchetProtocolStarterDelegate;
    private ProtocolDelegate protocolDelegate;
    private ProtocolStarterDelegate protocolStarterDelegate;
    private IdentityDelegate identityDelegate;
    private EncryptionForIdentityDelegate encryptionForIdentityDelegate;
    private NotificationPostingDelegate notificationPostingDelegate;

    public ChannelManager(MetaManager metaManager) {
        this.channelCoordinator = new ChannelCoordinator(this);

        metaManager.requestDelegate(this, CreateSessionDelegate.class);
        metaManager.requestDelegate(this, FullRatchetProtocolStarterDelegate.class);
        metaManager.requestDelegate(this, NetworkFetchDelegate.class);
        metaManager.requestDelegate(this, NetworkSendDelegate.class);
        metaManager.requestDelegate(this, ProtocolDelegate.class);
        metaManager.requestDelegate(this, ProtocolStarterDelegate.class);
        metaManager.requestDelegate(this, IdentityDelegate.class);
        metaManager.requestDelegate(this, EncryptionForIdentityDelegate.class);
        metaManager.requestDelegate(this, NotificationPostingDelegate.class);
        metaManager.registerImplementedDelegates(this);
    }

    @Override
    public void initialisationComplete() {
        try (ChannelManagerSession channelManagerSession = getSession()) {
            // clean expired provisions
            ObliviousChannel.clean(channelManagerSession);

            {
                // re-provision all existing channels
                ObliviousChannel[] obliviousChannels = ObliviousChannel.getAllConfirmed(channelManagerSession);
                for (ObliviousChannel obliviousChannel : obliviousChannels) {
                    Provision latestProvision = obliviousChannel.getLatestProvision();
                    if (latestProvision != null) {
                        latestProvision.selfRatchetIfRequired();
                    }
                }
            }

            {
                HashMap<UID, Identity> ownedIdentityFromDeviceUid = new HashMap<>();
                // clear all channels with deviceUids of contacts that do not exist
                // at the same time, try to detect contact devices without a channel
                ObliviousChannel[] obliviousChannels = ObliviousChannel.getAll(channelManagerSession);
                Map<Identity, Map<Identity, Set<UID>>> deviceUidsMap = identityDelegate.getAllDeviceUidsOfAllContactsOfAllOwnedIdentities(channelManagerSession.session);
                for (Identity ownedIdentity : identityDelegate.getOwnedIdentities(channelManagerSession.session)) {
                    UID[] ownedDeviceUids = identityDelegate.getDeviceUidsOfOwnedIdentity(channelManagerSession.session, ownedIdentity);
                    if (!deviceUidsMap.containsKey(ownedIdentity)) {
                        deviceUidsMap.put(ownedIdentity, new HashMap<>());
                    }
                    Map<Identity, Set<UID>> ownedIdentityMap = deviceUidsMap.get(ownedIdentity);
                    ownedIdentityMap.put(ownedIdentity, new HashSet<>(Arrays.asList(ownedDeviceUids)));
                }

                for (ObliviousChannel obliviousChannel : obliviousChannels) {
                    Identity ownedIdentity = ownedIdentityFromDeviceUid.get(obliviousChannel.getCurrentDeviceUid());
                    if (ownedIdentity == null) {
                        ownedIdentity = identityDelegate.getOwnedIdentityForCurrentDeviceUid(channelManagerSession.session, obliviousChannel.getCurrentDeviceUid());
                        if (ownedIdentity == null) {
                            continue;
                        }
                        ownedIdentityFromDeviceUid.put(obliviousChannel.getCurrentDeviceUid(), ownedIdentity);
                    }
                    boolean found = false;
                    Map<Identity, Set<UID>> ownedIdentityMap = deviceUidsMap.get(ownedIdentity);
                    if (ownedIdentityMap != null) {
                        Set<UID> deviceUids = ownedIdentityMap.get(obliviousChannel.getRemoteIdentity());
                        if (deviceUids != null) {
                            found = deviceUids.remove(obliviousChannel.getRemoteDeviceUid());
                        }
                    }
                    if (!found) {
                        // the device this channel is connected to no longer exists! Delete the channel
                        Logger.i("Found an orphan oblivious channel -> deleting it!");
                        obliviousChannel.delete();
                    }
                }

                // now that we have removed (from the HashMap) all devices for which we have a channel, we walk through the deviceUidsMap to check for channel-less deviceUids
                for (Identity ownedIdentity: deviceUidsMap.keySet()) {
                    // first check if some channels with owned devices should be restarted
                    if (identityDelegate.isActiveOwnedIdentity(channelManagerSession.session, ownedIdentity)) {
                        for (UID ownedDeviceUid : identityDelegate.getOtherDeviceUidsOfOwnedIdentity(channelManagerSession.session, ownedIdentity)) {
                            try {
                                boolean channelExists = checkIfObliviousChannelExists(channelManagerSession.session, ownedIdentity, ownedDeviceUid, ownedIdentity);
                                boolean channelCreationInProgress = protocolDelegate.isChannelCreationInProgress(channelManagerSession.session, ownedIdentity, ownedIdentity, ownedDeviceUid);
                                if (!channelExists && !channelCreationInProgress) {
                                    // we found a device without a channel and no channel creation is in progress
                                    //  --> we delete the device and start a device discovery protocol
                                    Logger.i("Found an owned device with no channel and no channel creation. Restarting channel creation.");
                                    protocolStarterDelegate.startChannelCreationProtocolWithOwnedDevice(channelManagerSession.session, ownedIdentity, ownedDeviceUid);
                                }
                            } catch (Exception e) {
                                // nothing to do
                            }
                        }
                    }


                    Map<Identity, Set<UID>> ownedIdentityMap = deviceUidsMap.get(ownedIdentity);
                    if (ownedIdentityMap == null) {
                        continue;
                    }
                    for (Identity contactIdentity: ownedIdentityMap.keySet()) {
                        if (contactIdentity.equals(ownedIdentity)) {
                            continue;
                        }
                        Set<UID> deviceUidSet = ownedIdentityMap.get(contactIdentity);
                        if (deviceUidSet == null) {
                            continue;
                        }
                        boolean deviceDiscoveryNeeded = false;
                        for (UID contactDeviceUid: deviceUidSet) {
                            // check if a ChannelCreationProtocolInstance exists for this device
                            try {
                                if (!protocolDelegate.isChannelCreationInProgress(channelManagerSession.session, ownedIdentity, contactIdentity, contactDeviceUid)) {
                                    // we found a device without a channel and no channel creation is in progress
                                    //  --> we delete the device and start a device discovery protocol
                                    Logger.i("Found a contact device with no channel and no channel creation. Restarting device discovery.");
                                    identityDelegate.removeDeviceForContactIdentity(channelManagerSession.session, ownedIdentity, contactIdentity, contactDeviceUid);
                                    deviceDiscoveryNeeded = true;
                                }
                            } catch (Exception e) {
                                // nothing to do
                            }
                        }
                        if (deviceDiscoveryNeeded) {
                            try {
                                protocolStarterDelegate.startDeviceDiscoveryProtocolWithinTransaction(channelManagerSession.session, ownedIdentity, contactIdentity);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            channelManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setDelegate(CreateSessionDelegate createSessionDelegate) {
        this.createSessionDelegate = createSessionDelegate;

        try (ChannelManagerSession channelManagerSession = getSession()) {
            ObliviousChannel.createTable(channelManagerSession.session);
            Provision.createTable(channelManagerSession.session);
            ProvisionedKeyMaterial.createTable(channelManagerSession.session);
            channelManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to create channel databases");
        }
    }

    public static void upgradeTables(Session session, int oldVersion, int newVersion) throws SQLException {
        ObliviousChannel.upgradeTable(session, oldVersion, newVersion);
        Provision.upgradeTable(session, oldVersion, newVersion);
        ProvisionedKeyMaterial.upgradeTable(session, oldVersion, newVersion);
    }


    public void setDelegate(FullRatchetProtocolStarterDelegate fullRatchetProtocolStarterDelegate) {
        this.fullRatchetProtocolStarterDelegate = fullRatchetProtocolStarterDelegate;
    }

    public void setDelegate(NetworkSendDelegate networkSendDelegate) {
        this.networkSendDelegate = networkSendDelegate;
    }

    public void setDelegate(NetworkFetchDelegate networkFetchDelegate) {
        this.networkFetchDelegate = networkFetchDelegate;
    }

    public void setDelegate(ProtocolDelegate protocolDelegate) {
        this.protocolDelegate = protocolDelegate;
    }

    public void setDelegate(ProtocolStarterDelegate protocolStarterDelegate) {
        this.protocolStarterDelegate = protocolStarterDelegate;
    }

    public void setDelegate(IdentityDelegate identityDelegate) {
        this.identityDelegate = identityDelegate;
    }

    public void setDelegate(EncryptionForIdentityDelegate encryptionForIdentityDelegate) {
        this.encryptionForIdentityDelegate = encryptionForIdentityDelegate;
    }

    public void setDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }




    // region Implementing ProcessDownloadedMessageDelegate
    @Override
    public void processDownloadedMessage(NetworkReceivedMessage networkReceivedMessage) {
        if (networkReceivedMessage == null) {
            Logger.i("Could not process null NetworkReceivedMessage");
            return;
        }
        channelCoordinator.decryptAndProcess(networkReceivedMessage);
    }
    // endregion

    // region Implementing ChannelDelegate
    @Override
    public UID post(Session session, ChannelMessageToSend message, PRNGService prng) throws Exception {
        return Channel.post(wrapSession(session), message, prng);
    }

    @Override
    public void createObliviousChannel(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity, Seed seed, int obliviousEngineVersion) throws Exception {
        if (identityDelegate == null) {
            Logger.w("Calling createObliviousChannel while the IdentityDelegate is not yet set");
            throw new Exception();
        }
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel.create(wrapSession(session), currentDeviceUid, remoteDeviceUid, remoteIdentity, seed, obliviousEngineVersion);
    }

    @Override
    public void confirmObliviousChannel(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws Exception {
        ObliviousChannel channel = getObliviousChannel(session, ownedIdentity, remoteDeviceUid, remoteIdentity);
        if (channel != null) {
            channel.confirm();
        }
    }

    @Override
    public void updateObliviousChannelSendSeed(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity, Seed seed, int obliviousEngineVersion) throws Exception {
        ObliviousChannel channel = getObliviousChannel(session, ownedIdentity, remoteDeviceUid, remoteIdentity);
        if (channel != null) {
            channel.updateSendSeed(seed, obliviousEngineVersion);
        }
    }

    @Override
    public void updateObliviousChannelReceiveSeed(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity, Seed seed, int obliviousEngineVersion) throws Exception {
        ObliviousChannel channel = getObliviousChannel(session, ownedIdentity, remoteDeviceUid, remoteIdentity);
        if (channel != null) {
            channel.createNewProvision(seed, obliviousEngineVersion);
        }
    }

    private ObliviousChannel getObliviousChannel(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws Exception {
        if (identityDelegate == null) {
            Logger.w("Calling getObliviousChannel while the IdentityDelegate is not yet set");
            return null;
        }
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        return ObliviousChannel.get(wrapSession(session), currentDeviceUid, remoteDeviceUid, remoteIdentity,false);
    }

    @Override
    public UID[] getConfirmedObliviousChannelDeviceUids(Session session, Identity ownedIdentity, Identity remoteIdentity) throws Exception {
        UID[] remoteUids;
        if (Objects.equals(ownedIdentity, remoteIdentity)) { // channels with owned devices
            remoteUids = identityDelegate.getOtherDeviceUidsOfOwnedIdentity(session, ownedIdentity);
        } else {
            remoteUids = identityDelegate.getDeviceUidsOfContactIdentity(session, ownedIdentity, remoteIdentity);
        }
        if ((remoteUids == null) || (remoteUids.length == 0)) {
            return new UID[0];
        }
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel[] obliviousChannels = ObliviousChannel.getMany(wrapSession(session), currentDeviceUid, remoteUids, remoteIdentity, true);
        UID[] uids = new UID[obliviousChannels.length];
        for (int i=0; i<obliviousChannels.length; i++) {
            uids[i] = obliviousChannels[i].getRemoteDeviceUid();
        }
        return uids;
    }

    @Override
    public void deleteObliviousChannelsWithContact(Session session, Identity ownedIdentity, Identity remoteIdentity) throws Exception {
        UID[] remoteUids = identityDelegate.getDeviceUidsOfContactIdentity(session, ownedIdentity, remoteIdentity);
        if ((remoteUids == null) || (remoteUids.length == 0)) {
            return;
        }
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel.deleteMany(wrapSession(session), currentDeviceUid, remoteUids, remoteIdentity);
    }

    @Override
    public void deleteObliviousChannelIfItExists(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws Exception {
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel obliviousChannel = ObliviousChannel.get(wrapSession(session), currentDeviceUid, remoteDeviceUid, remoteIdentity, false);
        if (obliviousChannel != null) {
            // delete the channel
            obliviousChannel.delete();
        }
    }

    @Override
    public void deleteAllChannelsForOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException {
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel.deleteAll(wrapSession(session), currentDeviceUid);
    }

    public boolean checkIfObliviousChannelExists(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws SQLException {
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel obliviousChannel = ObliviousChannel.get(wrapSession(session), currentDeviceUid, remoteDeviceUid, remoteIdentity, false);
        return obliviousChannel != null;
    }

    @Override
    public boolean checkIfObliviousChannelIsConfirmed(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws SQLException {
        UID currentDeviceUid = identityDelegate.getCurrentDeviceUidOfOwnedIdentity(session, ownedIdentity);
        ObliviousChannel obliviousChannel = ObliviousChannel.get(wrapSession(session), currentDeviceUid, remoteDeviceUid, remoteIdentity, true);
        return obliviousChannel != null;

    }

    // endregion

    // region Implementing ChannelManagerSessionFactory methods
    @Override
    public ChannelManagerSession getSession() throws SQLException {
        if (createSessionDelegate == null) {
            throw new SQLException("No CreateSessionDelegate was set in ChannelManager.");
        }
        return new ChannelManagerSession(createSessionDelegate.getSession(), fullRatchetProtocolStarterDelegate, networkFetchDelegate, networkSendDelegate, protocolDelegate, encryptionForIdentityDelegate, identityDelegate, notificationPostingDelegate);
    }

    private ChannelManagerSession wrapSession(Session session) {
        return new ChannelManagerSession(session, fullRatchetProtocolStarterDelegate, networkFetchDelegate, networkSendDelegate, protocolDelegate, encryptionForIdentityDelegate, identityDelegate, notificationPostingDelegate);
    }
    // endregion

}
