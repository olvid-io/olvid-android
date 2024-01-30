/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.engine.protocol.protocols;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class DeviceCapabilitiesDiscoveryProtocol extends ConcreteProtocol {
   public DeviceCapabilitiesDiscoveryProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
      super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
   }

   @Override
   public int getProtocolId() {
      return DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID;
   }

   // region States

   static final int FINISHED_STATE_ID = 1;

   @Override
   public int[] getFinalStateIds() {
      return new int[]{FINISHED_STATE_ID};
   }

   @Override
   protected Class<?> getStateClass(int stateId) {
      switch (stateId) {
         case INITIAL_STATE_ID:
            return InitialProtocolState.class;
         case FINISHED_STATE_ID:
            return FinishedProtocolState.class;
         default:
            return null;
      }
   }

   public static class FinishedProtocolState extends ConcreteProtocolState {
      @SuppressWarnings({"unused", "RedundantSuppression"})
      public FinishedProtocolState(Encoded encodedState) throws Exception {
         super(FINISHED_STATE_ID);
         Encoded[] list = encodedState.decodeList();
         if (list.length != 0) {
            throw new Exception();
         }
      }

      public FinishedProtocolState() {
         super(FINISHED_STATE_ID);
      }

      @Override
      public Encoded encode() {
         return Encoded.of(new Encoded[0]);
      }
   }


   // endregion







   // region Messages

   static final int INITIAL_FOR_ADDING_OWN_CAPABILITIES_MESSAGE_ID = 0;
   static final int INITIAL_SINGLE_CONTACT_DEVICE_MESSAGE_ID = 1;
   static final int INITIAL_SINGLE_OWNED_DEVICE_MESSAGE_ID = 2;
   static final int OWN_CAPABILITIES_TO_CONTACT_MESSAGE_ID = 3;
   static final int OWN_CAPABILITIES_TO_SELF_MESSAGE_ID = 4;

   @Override
   protected Class<?> getMessageClass(int protocolMessageId) {
      switch (protocolMessageId) {
         case INITIAL_FOR_ADDING_OWN_CAPABILITIES_MESSAGE_ID:
            return InitialForAddingOwnCapabilitiesMessage.class;
         case INITIAL_SINGLE_CONTACT_DEVICE_MESSAGE_ID:
            return InitialSingleContactDeviceMessage.class;
         case INITIAL_SINGLE_OWNED_DEVICE_MESSAGE_ID:
            return InitialSingleOwnedDeviceMessage.class;
         case OWN_CAPABILITIES_TO_CONTACT_MESSAGE_ID:
            return OwnCapabilitiesToContactMessage.class;
         case OWN_CAPABILITIES_TO_SELF_MESSAGE_ID:
            return OwnCapabilitiesToSelfMessage.class;
         default:
            return null;
      }
   }


   @SuppressWarnings("unused")
   public static class InitialForAddingOwnCapabilitiesMessage extends ConcreteProtocolMessage {
      private final List<ObvCapability> newOwnCapabilities;

      public InitialForAddingOwnCapabilitiesMessage(CoreProtocolMessage coreProtocolMessage, List<ObvCapability> newOwnCapabilities) {
         super(coreProtocolMessage);
         this.newOwnCapabilities = newOwnCapabilities;
      }

      public InitialForAddingOwnCapabilitiesMessage(ReceivedMessage receivedMessage) throws Exception {
         super(new CoreProtocolMessage(receivedMessage));
         if (receivedMessage.getInputs().length != 1) {
            throw new Exception();
         }
         String[] rawCapabilities = receivedMessage.getInputs()[0].decodeStringArray();
         newOwnCapabilities = new ArrayList<>();
         for (String rawCapability : rawCapabilities) {
            ObvCapability capability = ObvCapability.fromString(rawCapability);
            if (capability != null) {
               newOwnCapabilities.add(capability);
            } else {
               throw new Exception("Unknown capability: " + rawCapability);
            }
         }
      }

      @Override
      public int getProtocolMessageId() {
         return INITIAL_FOR_ADDING_OWN_CAPABILITIES_MESSAGE_ID;
      }

      @Override
      public Encoded[] getInputs() {
         return new Encoded[]{
                Encoded.of(ObvCapability.capabilityListToStringArray(newOwnCapabilities)),
         };
      }
   }



   @SuppressWarnings("unused")
   public static class InitialSingleContactDeviceMessage extends ConcreteProtocolMessage {
      private final Identity contactIdentity;
      private final UID contactDeviceUid;
      private final boolean isResponse;

      public InitialSingleContactDeviceMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, UID contactDeviceUid, boolean isResponse) {
         super(coreProtocolMessage);
         this.contactIdentity = contactIdentity;
         this.contactDeviceUid = contactDeviceUid;
         this.isResponse = isResponse;
      }

      public InitialSingleContactDeviceMessage(ReceivedMessage receivedMessage) throws Exception {
         super(new CoreProtocolMessage(receivedMessage));
         if (receivedMessage.getInputs().length != 3) {
            throw new Exception();
         }
         this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
         this.contactDeviceUid = receivedMessage.getInputs()[1].decodeUid();
         this.isResponse = receivedMessage.getInputs()[2].decodeBoolean();
      }

      @Override
      public int getProtocolMessageId() {
         return INITIAL_SINGLE_CONTACT_DEVICE_MESSAGE_ID;
      }

      @Override
      public Encoded[] getInputs() {
         return new Encoded[]{
                 Encoded.of(contactIdentity),
                 Encoded.of(contactDeviceUid),
                 Encoded.of(isResponse),
         };
      }
   }



   @SuppressWarnings("unused")
   public static class InitialSingleOwnedDeviceMessage extends ConcreteProtocolMessage {
      private final UID otherOwnedDeviceUid;
      private final boolean isResponse;

      public InitialSingleOwnedDeviceMessage(CoreProtocolMessage coreProtocolMessage, UID otherOwnedDeviceUid, boolean isResponse) {
         super(coreProtocolMessage);
         this.otherOwnedDeviceUid = otherOwnedDeviceUid;
         this.isResponse = isResponse;
      }

      public InitialSingleOwnedDeviceMessage(ReceivedMessage receivedMessage) throws Exception {
         super(new CoreProtocolMessage(receivedMessage));
         if (receivedMessage.getInputs().length != 2) {
            throw new Exception();
         }
         this.otherOwnedDeviceUid = receivedMessage.getInputs()[0].decodeUid();
         this.isResponse = receivedMessage.getInputs()[1].decodeBoolean();
      }

      @Override
      public int getProtocolMessageId() {
         return INITIAL_SINGLE_OWNED_DEVICE_MESSAGE_ID;
      }

      @Override
      public Encoded[] getInputs() {
         return new Encoded[]{
                 Encoded.of(otherOwnedDeviceUid),
                 Encoded.of(isResponse),
         };
      }
   }



   @SuppressWarnings("unused")
   public static class OwnCapabilitiesToContactMessage extends ConcreteProtocolMessage {
      private final String[] rawContactDeviceCapabilities;
      private final boolean isResponse;

      public OwnCapabilitiesToContactMessage(CoreProtocolMessage coreProtocolMessage, String[] rawContactDeviceCapabilities, boolean isResponse) {
         super(coreProtocolMessage);
         this.rawContactDeviceCapabilities = rawContactDeviceCapabilities;
         this.isResponse = isResponse;
      }

      public OwnCapabilitiesToContactMessage(ReceivedMessage receivedMessage) throws Exception {
         super(new CoreProtocolMessage(receivedMessage));
         if (receivedMessage.getInputs().length != 2) {
            throw new Exception();
         }
         this.rawContactDeviceCapabilities = receivedMessage.getInputs()[0].decodeStringArray();
         this.isResponse = receivedMessage.getInputs()[1].decodeBoolean();
      }

      @Override
      public int getProtocolMessageId() {
         return OWN_CAPABILITIES_TO_CONTACT_MESSAGE_ID;
      }

      @Override
      public Encoded[] getInputs() {
         return new Encoded[]{
                 Encoded.of(rawContactDeviceCapabilities),
                 Encoded.of(isResponse),
         };
      }
   }



   @SuppressWarnings("unused")
   public static class OwnCapabilitiesToSelfMessage extends ConcreteProtocolMessage {
      private final String[] rawOtherOwnedDeviceCapabilities;
      private final boolean isResponse;

      public OwnCapabilitiesToSelfMessage(CoreProtocolMessage coreProtocolMessage, String[] rawOtherOwnedDeviceCapabilities, boolean isResponse) {
         super(coreProtocolMessage);
         this.rawOtherOwnedDeviceCapabilities = rawOtherOwnedDeviceCapabilities;
         this.isResponse = isResponse;
      }

      public OwnCapabilitiesToSelfMessage(ReceivedMessage receivedMessage) throws Exception {
         super(new CoreProtocolMessage(receivedMessage));
         if (receivedMessage.getInputs().length != 2) {
            throw new Exception();
         }
         this.rawOtherOwnedDeviceCapabilities = receivedMessage.getInputs()[0].decodeStringArray();
         this.isResponse = receivedMessage.getInputs()[1].decodeBoolean();
      }

      @Override
      public int getProtocolMessageId() {
         return OWN_CAPABILITIES_TO_SELF_MESSAGE_ID;
      }

      @Override
      public Encoded[] getInputs() {
         return new Encoded[]{
                 Encoded.of(rawOtherOwnedDeviceCapabilities),
                 Encoded.of(isResponse),
         };
      }
   }


   // endregion








   // region Steps

   @Override
   protected Class<?>[] getPossibleStepClasses(int stateId) {
      switch (stateId) {
         case INITIAL_STATE_ID:
            return new Class[]{
                    AddOwnCapabilitiesAndSendThemToAllContactsAndOwnedDevicesStep.class,
                    SendOwnCapabilitiesToContactDeviceStep.class,
                    SendOwnCapabilitiesToOtherOwnedDeviceStep.class,
                    ProcessReceivedContactDeviceCapabilitiesStep.class,
                    ProcessReceivedOwnedDeviceCapabilitiesStep.class,
            };
         case FINISHED_STATE_ID:
         default:
            return new Class[0];
      }
   }


   public static class AddOwnCapabilitiesAndSendThemToAllContactsAndOwnedDevicesStep extends ProtocolStep {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final InitialProtocolState startState;
      private final InitialForAddingOwnCapabilitiesMessage receivedMessage;

      public AddOwnCapabilitiesAndSendThemToAllContactsAndOwnedDevicesStep(InitialProtocolState startState, InitialForAddingOwnCapabilitiesMessage receivedMessage, DeviceCapabilitiesDiscoveryProtocol protocol) throws Exception {
         super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
         this.startState = startState;
         this.receivedMessage = receivedMessage;
      }

      @Override
      public ConcreteProtocolState executeStep() throws Exception {
         ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

         boolean gainedOneToOneCapability;
         {
            // check whether the current device has different capabilities and update them

            List<ObvCapability> currentCapabilities = protocolManagerSession.identityDelegate.getCurrentDevicePublishedCapabilities(protocolManagerSession.session, getOwnedIdentity());
            // convert to HashSet for comparison
            HashSet<ObvCapability> currentSet = new HashSet<>(currentCapabilities);
            HashSet<ObvCapability> newSet = new HashSet<>(receivedMessage.newOwnCapabilities);
            if (Objects.equals(currentSet, newSet)) {
               // nothing changed, nothing to do:)
               return new FinishedProtocolState();
            }

            gainedOneToOneCapability = newSet.contains(ObvCapability.ONE_TO_ONE_CONTACTS) && !currentSet.contains(ObvCapability.ONE_TO_ONE_CONTACTS);

            // something changed --> update the device
            protocolManagerSession.identityDelegate.setCurrentDevicePublishedCapabilities(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.newOwnCapabilities);
         }

         {
            // if we just gained the oneToOne capability, notify all contacts of their status
            if (gainedOneToOneCapability) {
               UID childProtocolInstanceUid = new UID(getPrng());
               CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                       SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                       ONE_TO_ONE_CONTACT_INVITATION_PROTOCOL_ID,
                       childProtocolInstanceUid,
                       false
               );
               ChannelMessageToSend messageToSend = new OneToOneContactInvitationProtocol.InitiateOneToOneStatusSyncWithAllContactsMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
               protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            }
         }

         {
            // notify all contacts

            Identity[] contactIdentities = protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());

            if (contactIdentities.length > 0) {
               SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(contactIdentities, getOwnedIdentity());
               for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                  try {
                     CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(sendChannelInfo);
                     ChannelMessageToSend messageToSend = new OwnCapabilitiesToContactMessage(coreProtocolMessage, ObvCapability.capabilityListToStringArray(receivedMessage.newOwnCapabilities), false).generateChannelProtocolMessageToSend();
                     protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                  } catch (Exception e) {
                     Logger.d("One contact with no channel during DeviceCapabilitiesDiscoveryProtocol.AddOwnCapabilitiesAndSendThemToAllContactsAndOwnedDevicesStep");
                  }
               }
            }
         }

         {
            // notify other owned devices

            int numberOfOtherDevices = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity()).length;
            if (numberOfOtherDevices > 0) {
               try {
                  CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                  ChannelMessageToSend messageToSend = new OwnCapabilitiesToSelfMessage(coreProtocolMessage, ObvCapability.capabilityListToStringArray(receivedMessage.newOwnCapabilities), false).generateChannelProtocolMessageToSend();
                  protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
               } catch (NoAcceptableChannelException ignored) { }
            }
         }

         return new FinishedProtocolState();
      }
   }


   public static class SendOwnCapabilitiesToContactDeviceStep extends ProtocolStep {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final InitialProtocolState startState;
      private final InitialSingleContactDeviceMessage receivedMessage;

      public SendOwnCapabilitiesToContactDeviceStep(InitialProtocolState startState, InitialSingleContactDeviceMessage receivedMessage, DeviceCapabilitiesDiscoveryProtocol protocol) throws Exception {
         super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
         this.startState = startState;
         this.receivedMessage = receivedMessage;
      }


      @Override
      public ConcreteProtocolState executeStep() throws Exception {
         ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

         List<ObvCapability> currentCapabilities = protocolManagerSession.identityDelegate.getCurrentDevicePublishedCapabilities(protocolManagerSession.session, getOwnedIdentity());


         CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(receivedMessage.contactIdentity, getOwnedIdentity(), new UID[]{receivedMessage.contactDeviceUid}, true));
         ChannelMessageToSend messageToSend = new OwnCapabilitiesToContactMessage(coreProtocolMessage, ObvCapability.capabilityListToStringArray(currentCapabilities), receivedMessage.isResponse).generateChannelProtocolMessageToSend();
         protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

         return new FinishedProtocolState();
      }
   }


   public static class SendOwnCapabilitiesToOtherOwnedDeviceStep extends ProtocolStep {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final InitialProtocolState startState;
      private final InitialSingleOwnedDeviceMessage receivedMessage;

      public SendOwnCapabilitiesToOtherOwnedDeviceStep(InitialProtocolState startState, InitialSingleOwnedDeviceMessage receivedMessage, DeviceCapabilitiesDiscoveryProtocol protocol) throws Exception {
         super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
         this.startState = startState;
         this.receivedMessage = receivedMessage;
      }


      @Override
      public ConcreteProtocolState executeStep() throws Exception {
         ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

         List<ObvCapability> currentCapabilities = protocolManagerSession.identityDelegate.getCurrentDevicePublishedCapabilities(protocolManagerSession.session, getOwnedIdentity());


         CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{receivedMessage.otherOwnedDeviceUid}, true));
         ChannelMessageToSend messageToSend = new OwnCapabilitiesToSelfMessage(coreProtocolMessage, ObvCapability.capabilityListToStringArray(currentCapabilities), receivedMessage.isResponse).generateChannelProtocolMessageToSend();
         protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

         return new FinishedProtocolState();
      }
   }



   public static class ProcessReceivedContactDeviceCapabilitiesStep extends ProtocolStep {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final InitialProtocolState startState;
      private final OwnCapabilitiesToContactMessage receivedMessage;

      public ProcessReceivedContactDeviceCapabilitiesStep(InitialProtocolState startState, OwnCapabilitiesToContactMessage receivedMessage, DeviceCapabilitiesDiscoveryProtocol protocol) throws Exception {
         super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
         this.startState = startState;
         this.receivedMessage = receivedMessage;
      }


      @Override
      public ConcreteProtocolState executeStep() throws Exception {
         ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

         String[] initialContactDeviceCapabilities = protocolManagerSession.identityDelegate.getContactDeviceCapabilities(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid());


         if (!receivedMessage.isResponse && initialContactDeviceCapabilities.length == 0) {
            // this is the first time this contact sends us some capabilities --> we send them our own capabilities

            UID childProtocolInstanceUid = new UID(getPrng());
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                    DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                    childProtocolInstanceUid,
                    false
            );
            ChannelMessageToSend messageToSend = new InitialSingleContactDeviceMessage(coreProtocolMessage, receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), true).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
         }

         protocolManagerSession.identityDelegate.setContactDeviceCapabilities(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), receivedMessage.rawContactDeviceCapabilities);

         return new FinishedProtocolState();
      }
   }


   public static class ProcessReceivedOwnedDeviceCapabilitiesStep extends ProtocolStep {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final InitialProtocolState startState;
      private final OwnCapabilitiesToSelfMessage receivedMessage;

      public ProcessReceivedOwnedDeviceCapabilitiesStep(InitialProtocolState startState, OwnCapabilitiesToSelfMessage receivedMessage, DeviceCapabilitiesDiscoveryProtocol protocol) throws Exception {
         super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
         this.startState = startState;
         this.receivedMessage = receivedMessage;
      }


      @Override
      public ConcreteProtocolState executeStep() throws Exception {
         ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

         String[] initialOtherOwnedDeviceCapabilities = protocolManagerSession.identityDelegate.getOtherOwnedDeviceCapabilities(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid());


         if (!receivedMessage.isResponse && initialOtherOwnedDeviceCapabilities.length == 0) {
            // this is the first time this other owned device sends us some capabilities --> we send it our own capabilities

            UID childProtocolInstanceUid = new UID(getPrng());
            CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                    SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                    DEVICE_CAPABILITIES_DISCOVERY_PROTOCOL_ID,
                    childProtocolInstanceUid,
                    false
            );
            ChannelMessageToSend messageToSend = new InitialSingleOwnedDeviceMessage(coreProtocolMessage, receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), true).generateChannelProtocolMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
         }

         protocolManagerSession.identityDelegate.setOtherOwnedDeviceCapabilities(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), receivedMessage.rawOtherOwnedDeviceCapabilities);

         return new FinishedProtocolState();
      }
   }

   // endregion
}
