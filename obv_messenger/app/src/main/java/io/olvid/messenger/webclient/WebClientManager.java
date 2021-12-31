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

package io.olvid.messenger.webclient;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.net.URI;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.datatypes.key.symmetric.AuthEncAES256ThenSHA256Key;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.datatypes.JsonSettings;
import io.olvid.messenger.webclient.listeners.AttachmentListener;
import io.olvid.messenger.webclient.listeners.DiscussionListener;
import io.olvid.messenger.webclient.listeners.DraftAttachmentListener;
import io.olvid.messenger.webclient.listeners.MessageListener;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.Colissimo;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass.ColissimoType;
import io.olvid.messenger.webclient.protobuf.ConnectionColissimoOuterClass.ConnectionColissimo;
import io.olvid.messenger.webclient.protobuf.ConnectionPingOuterClass;
import io.olvid.messenger.webclient.protobuf.datatypes.SettingsOuterClass;

public class WebClientManager {
    public enum State {
        STARTING,
        READY_FOR_CONNECTION,
        CONNECTED,
        REGISTERED_CONNECTION,
        REGISTERED_CORRESPONDING,
        PROTOCOL_1_SENT,
        PROTOCOL_2_RECEIVED,
        PROTOCOL_3_SENT,
        WAITING_FOR_SAS_VALIDATION,
        LISTENING,
        WAITING_FOR_RECONNECTION,
        RECONNECTING,
        FINISHING,

        ERROR,
        ERROR_INVALID_STEP
    }


    public enum StopServiceReason {
        PROTOCOL_ERROR,
        SESSION_CLOSED_REMOTELY,
        CONNECTION_ERROR,
        INTERNAL_ERROR,
        INVALID_QR_CODE,
        WEB_PERMISSION_DENIED,
    }

    // sub-services
    private final NoExceptionSingleThreadExecutor executor;
    @NonNull
    private final UnifiedForegroundService.WebClientSubService service;
    private final byte[] bytesOwnedIdentity;
    private WebsocketClient webSocketClient;
    private WebClientEstablishmentProtocol protocol;
    private final ColissimoMessageQueue colissimoMessageQueue;
    private Cryptography cryptography;

    // listeners
    private final DiscussionListener discussionListener;
    private final MessageListener messageListener;
    private final AttachmentListener attachmentListener;
    private final DraftAttachmentListener draftAttachmentListener;
    private SharedPreferences.OnSharedPreferenceChangeListener listenerSettings;


    // manager state
    private State currentState;
    private boolean isProtocolDone = false;

    // QrCode data
    private final String QrCodeBase64Data;
    private final String connectionIdentifier;
    private String correspondingIdentifier;
    private URI serverUri;
    private byte[] rawWebPublicKey;

    // Protocol data
    private final MutableLiveData<String> sasCodeLiveData;
    private String sasCode;
    private byte[] bytesConnectionColissimo;
    private AuthEncAES256ThenSHA256Key authEncKey;

    // Timeout and Timer
    private TimerTask closeConnection;
    private Timer timeOutWaitingForReconnection = null;
    private Timer protocolTimeout = null;
    private TimerTask protocolTimedOutTask = null;
    private Timer pingWebClientTimer = null;

    // reconnection
    // store timestamp that a unexpected newCorresponding message was received, if a corresponding disconnected message arrive just after it, ignore it
    // case: corresponding reconnect before corresponding disconnected message arrives
    private long unexpectedNewCorrespondingTimestamp = 0;
    private Timer reconnectionTimer = null;

    public WebClientManager(@NonNull UnifiedForegroundService.WebClientSubService webClientService, String QrCodeBase64Data) {
        this.service = webClientService;
        this.bytesOwnedIdentity = this.service.getBytesOwnedIdentity();
        this.executor = new NoExceptionSingleThreadExecutor("WebClientManager-TaskWrapper");
        this.colissimoMessageQueue = new ColissimoMessageQueue(this);

        this.QrCodeBase64Data = QrCodeBase64Data;
        this.connectionIdentifier = UUID.randomUUID().toString();

        this.discussionListener = new DiscussionListener(this, this.bytesOwnedIdentity);
        this.messageListener = new MessageListener(this);
        this.attachmentListener = new AttachmentListener(this);
        this.draftAttachmentListener = new DraftAttachmentListener(this);

        this.sasCodeLiveData = new MutableLiveData<>();

        this.closeConnection = new TimerTask() {
            @Override
            public void run() {
                if(currentState == State.WAITING_FOR_RECONNECTION) {
                    service.stopServiceWithNotification(StopServiceReason.CONNECTION_ERROR);
                }
            }
        };

        // set current state to STARTING manually to avoid crash when trying to access current state in updateState
        this.currentState = State.STARTING;
        updateState(State.STARTING);
    }

    public void stop() {
        this.updateState(State.FINISHING);
        this.protocol = null;
        if (this.colissimoMessageQueue != null) {
            this.colissimoMessageQueue.stop();
        }
        if (this.webSocketClient != null) {
            this.webSocketClient.close();
        }
        if (this.discussionListener != null) {
            discussionListener.stop();
        }
        this.stopAllListeners();
        // stop ping timer
        this.stopPingTimer();
        // stop timeout reconnection if it exists
        this.stopTimeouts();
        // stop reconnection timer task
        this.stopReconnectionTimerTask();
        //unregister listener on shared preference for WC
        PreferenceManager.getDefaultSharedPreferences(App.getContext()).unregisterOnSharedPreferenceChangeListener(this.listenerSettings);
        executor.shutdownNow();
        Logger.d("Stopped webclient manager");
    }

    protected void stopAllListeners() {
        if (this.messageListener != null) {
            messageListener.stop();
        }
        if (this.discussionListener != null) {
            discussionListener.stop();
        }
        if (this.attachmentListener != null) {
            attachmentListener.stop();
        }
        if (this.draftAttachmentListener != null) {
            draftAttachmentListener.stop();
        }
    }

    private void taskWrapper(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Logger.e("Unexpected exception occurred during WebClientManager task, exiting service", e);
                Logger.e("Current state: " + this.currentState.name());
                e.printStackTrace();
                updateState(State.ERROR);
            }
        });
    }

    private void updateState(State newState) {
        // avoid spamming reconnection -> reconnection logs
        if (!(this.currentState == State.RECONNECTING && newState == State.RECONNECTING)) {
            Logger.d("updating state : " + this.currentState.name() + " -> " + newState.name());
        }
        if (this.currentState == State.FINISHING) {
            Logger.d("Manager in FINISHING state, not updating to new State: " + newState.name());
            return ;
        }

        switch (newState) {
            // parse QrCode data and store it
            case STARTING:
                this.stopTimeouts();
                this.startProtocolTimeout();
                taskWrapper(() -> {
                    QrCodeParser.QrCodeParserResult result = QrCodeParser.parse(this.QrCodeBase64Data);
                    if (result == null) {
                        this.updateState(State.ERROR);
                    }
                    else {
                        this.correspondingIdentifier = result.getCorrespondingIdentifier();
                        this.serverUri = result.getServerUri();
                        this.rawWebPublicKey = result.getRawWebPublicKey();
                        this.updateState(State.READY_FOR_CONNECTION);
                    }
                });
                break;
            // connect to websocket server
            case READY_FOR_CONNECTION:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (currentState != State.STARTING) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                taskWrapper(() -> this.webSocketClient = new WebsocketClient(this.serverUri, this));
                break ;
            // register connection identifier on server
            // shall be called when reconnecting or when opening connection on "start"
            case CONNECTED:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (this.currentState != State.READY_FOR_CONNECTION && this.currentState != State.RECONNECTING) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                taskWrapper(() -> {
                    boolean res = this.webSocketClient.registerConnection(this.connectionIdentifier);
                    if (!res) {
                        Logger.e("Unable to register connection");
                        this.updateState(State.ERROR);
                    }
                });
                break;
            // register corresponding identifier on server
            // called by connectionRegisteredHandler, used in MessageHandler
            case REGISTERED_CONNECTION:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (this.currentState != State.CONNECTED) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                taskWrapper(() -> {
                    boolean res = this.webSocketClient.registerCorresponding(this.correspondingIdentifier);
                    if (!res) {
                        Logger.e("Unable to register corresponding");
                        this.updateState(State.ERROR);
                    }
                });
                break ;
            // create a protocol instance and send first protocol connectionColissimo if first connection or just change to LISTENING state if reconnecting
            // called by correspondingRegisteredHandler, used in MessageHandler
            case REGISTERED_CORRESPONDING:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (this.currentState != State.REGISTERED_CONNECTION) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                if (this.isProtocolDone) {
                    taskWrapper(() -> this.updateState(State.LISTENING));
                }
                else {
                    taskWrapper(() -> {
                        ConnectionColissimo connectionColissimo;

                        this.protocol = new WebClientEstablishmentProtocol();
                        connectionColissimo = this.protocol.prepareConnectionAppIdentifierPkKemCommitSeed(this.rawWebPublicKey, this.connectionIdentifier);
                        if (connectionColissimo == null || !this.sendConnectionColissimo(connectionColissimo)) {
                            this.updateState(State.ERROR);
                            return ;
                        }
                        this.updateState(State.PROTOCOL_1_SENT);
                    });
                }
                break;
            // do nothing, transition state, waiting for web protocol response
            case PROTOCOL_1_SENT:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (this.currentState != State.REGISTERED_CORRESPONDING) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                break;
            // parse web protocol response and send protocol response
            // changed by notifyProtocol2Received
            case PROTOCOL_2_RECEIVED:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (this.currentState != State.PROTOCOL_1_SENT) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                taskWrapper(() -> {
                    ConnectionColissimo connectionColissimo;

                    if (!this.protocol.handleBrowserKemSeed(this.bytesConnectionColissimo)) {
                        this.updateState(State.ERROR);
                        return ;
                    }
                    connectionColissimo = this.protocol.prepareDecommitment();
                    if (!this.sendConnectionColissimo(connectionColissimo)) {
                        this.updateState(State.ERROR);
                        return ;
                    }
                    this.updateState(State.PROTOCOL_3_SENT);
                });
                break;
            // calculate sas code and derive long term authenc keys, store sas code into a live data for fragment
            case PROTOCOL_3_SENT:
                this.stopTimeouts();
                this.startProtocolTimeout();
                if (this.currentState != State.PROTOCOL_2_RECEIVED) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                taskWrapper(() -> {
                    this.sasCode = this.protocol.calculateSasCode();
                    this.authEncKey = (AuthEncAES256ThenSHA256Key) this.protocol.derivateAuthEncKey();
                    this.cryptography = new Cryptography(authEncKey);
                    if (this.sasCode == null || this.authEncKey == null) {
                        Logger.e("Unable to calculate sas code, or to derive final AuthEnc key");
                        this.updateState(State.ERROR);
                    }
                    else {
                        Logger.d("Derived sascode : "+ sasCode);
                        //fragment observing this, will react to this change
                        this.sasCodeLiveData.postValue(this.sasCode);
                        this.updateState(State.WAITING_FOR_SAS_VALIDATION);
                    }
                });
                break;
            // transition state, do nothing, wait for
            case WAITING_FOR_SAS_VALIDATION:
                this.stopTimeouts();
                if (this.currentState != State.PROTOCOL_3_SENT) {
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                break;
            // startColissimoQueue and send ping to app (to notify that app is ready), this step is considered as end of protocol
            // for first connection: called from validateSasCode, reconnecting: called from registeredCorresponding, waiting for reconnection: called from handlerNewCorrespondingRegistered
            case LISTENING:
                this.stopTimeouts();
                this.startPingTimer();
                // end of protocol scenario (first connection)
                if(this.currentState == State.WAITING_FOR_SAS_VALIDATION) {
                    taskWrapper(() -> {
                        this.startReconnectionTimerTask();
                        this.isProtocolDone = true;
                        this.colissimoMessageQueue.start();
                        Colissimo pingColissimo = Colissimo.newBuilder()
                                .setType(ColissimoType.CONNECTION_PING)
                                .setConnectionPing(ConnectionPingOuterClass.ConnectionPing.newBuilder().setPing(true).build())
                                .build();
                        this.webSocketClient.sendColissimo(pingColissimo);
                        this.sendSettingsColissimo();
                        this.startListenersOnSettings();
                    });
                }
                // reconnection scenario (RECONNECTING -> CONNECTED -> REGISTERED_CONNECTION -> REGISTERED_CORRESPONDING -> LISTENING)
                else if (this.currentState == State.REGISTERED_CORRESPONDING) {
                    Logger.d("Reconnected, starting message queue");
                    this.colissimoMessageQueue.start();
                    Colissimo pingColissimo = Colissimo.newBuilder()
                            .setType(ColissimoType.CONNECTION_PING)
                            .setConnectionPing(ConnectionPingOuterClass.ConnectionPing.newBuilder().setPing(true).build())
                            .build();
                    this.webSocketClient.sendColissimo(pingColissimo);
                // waiting for reconnection scenario
                } else if (this.currentState == State.WAITING_FOR_RECONNECTION) {
                    Logger.e("Web reconnected, listening again");
                    // just in case
                    this.stopWaitingForReconnectionTimeout();
                } else {
                    this.updateState(State.ERROR_INVALID_STEP);
                }
                break;
            // called if received a correspondingDisconnected server message
            case WAITING_FOR_RECONNECTION:
                this.stopProtocolTimeout();
                this.stopPingTimer();
                if (this.currentState != State.LISTENING) {
                    Logger.e("Web disconnected during an invalid state, aborting, state: " + this.currentState.name());
                    this.updateState(State.ERROR_INVALID_STEP);
                    return ;
                }
                // waiting for reconnection timeout
                this.startWaitingForReconnectionTimeout();
                // stop all current uploads => maybe webclient can handle a disconnection in the middle of sending ?
                this.colissimoMessageQueue.stopAttachmentHandler();
                break;
            // called if websocket connection unexpectedly closed
            case RECONNECTING:
                this.stopProtocolTimeout();
                this.stopPingTimer();
                if (this.currentState == State.LISTENING) {
                    taskWrapper(() -> {
                        this.colissimoMessageQueue.stop();
                        this.webSocketClient.reconnect();
                    });
                } else if (this.currentState == State.RECONNECTING) {
                    taskWrapper(() -> {
                        if (!this.webSocketClient.isOpen()) {
                            this.webSocketClient.reconnect();
                        }
                        else {
                            Logger.e("reconnecting while connection is open, ignoring");
                        }
                    });
                } else if (this.currentState != State.FINISHING && this.isProtocolDone) {
                    Logger.e("Reconnecting in a special state: " + newState);
                    taskWrapper(() -> {
                        if (!this.webSocketClient.isOpen()) {
                            this.webSocketClient.reconnect();
                        }
                        else {
                            Logger.e("reconnecting while connection is open, ignoring");
                        }
                    });
                } else {
                    Logger.e("Connection to server lost");
                    this.updateState(State.ERROR);
                    return ;
                }
                break ;
            // called by this.stop method
            case FINISHING:
                this.stopTimeouts();
                break ;
            case ERROR:
            case ERROR_INVALID_STEP:
                this.stopTimeouts();
                if (this.isProtocolDone) {
                    this.service.stopServiceWithNotification(StopServiceReason.PROTOCOL_ERROR);
                }
                else {
                    this.service.stopServiceWithNotification(StopServiceReason.INTERNAL_ERROR);
                }
                break;
            default:
                this.stopTimeouts();
                throw new IllegalStateException("Unexpected value: " + newState);
        }
        this.currentState = newState;
    }

    // called when a connection colissimo is received
    public void notifyProtocol2Received(byte[] bytesConnectionColissimo) {
        if (this.currentState != State.PROTOCOL_1_SENT) {
            Logger.e("Received a connection message but current step was " + this.currentState.name() + ", ignoring");
            return ;
        }
        this.bytesConnectionColissimo = bytesConnectionColissimo;
        this.updateState(State.PROTOCOL_2_RECEIVED);
    }

    // called by fragment to check sas code validity
    public boolean sasCodeVerification(@Nullable String sasCode) {
        if (this.currentState != State.WAITING_FOR_SAS_VALIDATION) {
            Logger.e("Received sas code for verification but manager was not ready: " + this.getCurrentState().name());
            this.service.stopServiceWithNotification(StopServiceReason.PROTOCOL_ERROR);
            return false;
        }
        if (sasCode != null && sasCode.equals(this.sasCode)) {
            // update to LISTENING if sas code is valid
            this.updateState(State.LISTENING);
            return true;
        } else {
            //do nothing, user will retry until correct or leave
            Logger.e("Received Sas code does not match calculated sas code");
            return false ;
        }
    }

    // used as handler on websocekt onOpen method
    protected void handlerWebsocketConnected() {
        if (this.currentState == State.RECONNECTING || this.currentState == State.READY_FOR_CONNECTION) {
            this.updateState(State.CONNECTED);
        } else {
            Logger.e("Websocket connection opened while in an invalid step ! " + this.currentState.name());
        }
    }

    protected void handlerWebsocketClosed() {
        if (this.currentState == State.FINISHING) {
            Logger.d("Websocket closed while in FINISHING state");
        } else if (this.currentState == State.LISTENING || this.currentState == State.RECONNECTING || this.currentState == State.CONNECTED) {
            this.updateState(State.RECONNECTING);
        } else {
            this.service.stopServiceWithNotification(StopServiceReason.CONNECTION_ERROR);
        }
    }

    // used as handler on websocket onError method
    protected void handlerWebsocketError() {
    }

    // called if a connectionRegistered server message arrive
    protected void handlerConnectionRegistered() {
        this.updateState(State.REGISTERED_CONNECTION);
    }

    // called if a correspondingRegistered server message arrive
    protected void handlerCorrespondingRegistered() {
        this.updateState(State.REGISTERED_CORRESPONDING);
    }

    // called if a correspondingDisconnected server message arrive
    protected void handlerCorrespondingDisconnected() {
        if (this.currentState == State.LISTENING) {
            Logger.d("corresponding disconnected message received");
            this.updateState(State.WAITING_FOR_RECONNECTION);
        } else if (this.currentState == State.WAITING_FOR_RECONNECTION) {
            Logger.e("Received correspondingDisconnected in WAITING_FOR_RECONNECTION state, ignoring");
        } else if (this.currentState == State.FINISHING) {
            return;
        } else if (this.unexpectedNewCorrespondingTimestamp != 0 && this.unexpectedNewCorrespondingTimestamp > System.currentTimeMillis() - 30_000) {
            // if a newCorresponding message was received less than 30 seconds ago ignore correspondingDisconnected message received
            this.unexpectedNewCorrespondingTimestamp = 0;
            Logger.e("Received a correspondingDisconnected message just after a newCorresponding message, ignoring it");
        } else {
            Logger.e("Corresponding disconnected in an invalid state, aborting, state: " + this.currentState.name());
            this.service.stopServiceWithNotification(StopServiceReason.SESSION_CLOSED_REMOTELY);
        }
    }

    // called if a newCorresponding server message arrive
    protected void handlerNewCorresponding(String correspondingIdentifier) {
        if (!this.correspondingIdentifier.equals(correspondingIdentifier)) {
            Logger.e("New corresponding registered with an invalid identifier, critical error, exiting");
            this.service.stopService();
        }
        if (this.currentState == State.WAITING_FOR_RECONNECTION) {
            Logger.i("Web reconnected on socket");
            this.updateState(State.LISTENING);
        }
        else {
            Logger.d("Received unexpected newCorresponding message");
            this.unexpectedNewCorrespondingTimestamp = System.currentTimeMillis();
        }
    }

    // called if a bye colissimo is received
    protected void handlerByeColissimo() {
        // do not notify user that webclient was closed because it had been caused by user action
        this.service.stopService();
    }

    // handler called if an error message is received from server
    protected void handlerReceivedErrorMessageFromServer(int errorCode) {
        switch (errorCode) {
            case (0):
                Logger.e("Server sent an internal error error");
                this.service.stopServiceWithNotification(StopServiceReason.INTERNAL_ERROR);
                break ;
            case (1):
                Logger.i("Server sent a message not well formatted error");
                this.service.stopServiceWithNotification(StopServiceReason.INTERNAL_ERROR);
                break ;
            case (2):
                Logger.i("Server sent a corresponding not found error");
                this.service.stopServiceWithNotification(StopServiceReason.CONNECTION_ERROR);
                break ;
            case (3):
                if (this.currentState == State.WAITING_FOR_RECONNECTION) {
                    Logger.d("Unable to relay a message in WAITING_FOR_RECONNECTION state, ignoring");
                }
                else if (this.currentState == State.LISTENING) {
                    Logger.d("Unable to relay a message in LISTENING state, switching to WAITING_FOR_RECONNECTION");
                    this.updateState(State.WAITING_FOR_RECONNECTION);
                }
                else {
                    Logger.e("Unable to relay a message in an invalid state, exiting");
                    this.service.stopServiceWithNotification(StopServiceReason.CONNECTION_ERROR);
                }
                break ;
            case (4):
                Logger.i("Unable to connect, web client authentication required, and no permission");
                this.service.stopServiceWithNotification(StopServiceReason.WEB_PERMISSION_DENIED);
                break ;
            default:
                Logger.i("Unrecognized server error code: " + errorCode);
                this.service.stopServiceWithNotification(StopServiceReason.INTERNAL_ERROR);
                break ;
        }
    }

    // send connection colissimo on websocket connection
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean sendConnectionColissimo(ConnectionColissimo connectionColissimo) {
        if (this.currentState != State.REGISTERED_CORRESPONDING && this.currentState != State.PROTOCOL_2_RECEIVED) {
            Logger.e("Trying to send connection colissimo while in an invalid state: " + this.currentState.name());
            return false;
        }
        if (!this.webSocketClient.sendConnectionColissimo(connectionColissimo)) {
            Logger.e("Unable to send connection colissimo");
            return false;
        }
        return true;
    }

    // encrypt and send colissimo on websocket connection
    public boolean sendColissimo(Colissimo colissimo) {
        if (this.currentState != State.LISTENING || this.cryptography == null) {
            Logger.e("Trying to send colissimo in an invalid state, ignoring, state: " + this.currentState.name());
            return false;
        }
        if (!this.webSocketClient.sendColissimo(colissimo)) {
            Logger.e("Unable to send colissimo, ignoring");
            return false;
        }
        return true;
    }

    // Cryptography api: authEnc with long term keys derived after protocol
    public byte[] encrypt(byte[] payload) {
        if (this.cryptography == null) {
            Logger.e("Trying to encrypt message but cryptography is not available");
            return (null);
        }
        return this.cryptography.encrypt(payload);
    }
    public byte[] decrypt(byte[] payload) {
        if (this.cryptography == null) {
            Logger.e("Trying to decrypt message but cryptography is not available");
            return (null);
        }
        return this.cryptography.decrypt(payload);
    }

    public void sendSettingsColissimo() {
        String language = SettingsActivity.getLanguageWebclient();
        String theme = SettingsActivity.getThemeWebclient();
        boolean sendOnEnter = SettingsActivity.sendOnEnterEnabled();
        boolean notifications = SettingsActivity.notificationsSoundOnWebclient();
        boolean showNotifications = SettingsActivity.showNotificationsOnBrowser();
        final String defaultTheme;
        if((App.getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES){
            defaultTheme = "dark";
        } else {
            defaultTheme = "light";
        }
        JsonSettings jsonSettings = new JsonSettings(language,theme,sendOnEnter,notifications,showNotifications, Locale.getDefault().getLanguage(), defaultTheme, null);
        Colissimo.Builder colissimo = Colissimo.newBuilder();
        SettingsOuterClass.Settings.Builder settings = SettingsOuterClass.Settings.newBuilder();
        try {
            settings.setSettings(AppSingleton.getJsonObjectMapper().writeValueAsString(jsonSettings));
            colissimo.setType(ColissimoType.SETTINGS);
            colissimo.setSettings(settings);
            this.sendColissimo(colissimo.build());
        } catch (Exception e){
            Logger.w("Error in retrieving settings");
        }
    }

    private void startListenersOnSettings(){

        this.listenerSettings = (sharedPreferences, key) -> {
            if(key.equals("pref_key_language_webclient") ||
                key.equals("pref_key_theme_webclient") ||
                key.equals("pref_key_send_on_enter_webclient") ||
                key.equals("pref_key_notification_sound_webclient") ||
                key.equals("pref_key_notification_show_on_browser") ||
                key.equals("")){
                    sendSettingsColissimo();
            }
        };
        PreferenceManager.getDefaultSharedPreferences(App.getContext()).registerOnSharedPreferenceChangeListener(this.listenerSettings);
    }

    // timeout and timer
    private void stopTimeouts() {
        this.stopWaitingForReconnectionTimeout();
        this.stopProtocolTimeout();
    }

    private void startProtocolTimeout() {
        this.stopProtocolTimeout();
        protocolTimeout = new Timer("ProtocolTimeoutTimer");
        this.protocolTimedOutTask = new TimerTask() {
            @Override
            public void run() {
                if (isProtocolDone) {
                    Logger.d("webclient protocol time out is fine");
                    return ;
                }
                if (currentState == State.LISTENING || currentState == State.WAITING_FOR_SAS_VALIDATION ){
                    Logger.d("webclient protocol time out is fine");
                    return ;
                }
                Logger.w("Protocol timed out, exiting");
                App.toast(R.string.webclient_toast_connection_timedout, Toast.LENGTH_SHORT);
                service.stopService();
            }
        };
        try {
            protocolTimeout.schedule(this.protocolTimedOutTask, Constants.PROTOCOL_TIMEOUT);
        } catch (Exception e){
            Logger.e("Could not schedule protocol timeout");
        }
    }

    private void stopProtocolTimeout() {
        if(this.protocolTimeout != null){
            this.protocolTimeout.cancel();
            this.protocolTimeout = null;
        }
        if(this.protocolTimedOutTask != null){
            this.protocolTimedOutTask.cancel();
            this.protocolTimedOutTask = null;
        }
    }


    private void startWaitingForReconnectionTimeout() {
        this.stopWaitingForReconnectionTimeout();
        this.timeOutWaitingForReconnection = new Timer("WaitingForReconnectionTimeout");
        this.closeConnection = new TimerTask() {
            @Override
            public void run() {
                if(currentState == State.WAITING_FOR_RECONNECTION) {
                    Logger.i("Reconnection timed out, exiting");
                    service.stopServiceWithNotification(StopServiceReason.CONNECTION_ERROR);
                }
            }
        };
        try {
            this.timeOutWaitingForReconnection.schedule(this.closeConnection, Constants.RECONNECTION_TIMEOUT_MILLIS);
        } catch (Exception e) {
            Logger.e("Could not schedule reconnection timeout");
        }
    }

    private void stopWaitingForReconnectionTimeout() {
        if(this.timeOutWaitingForReconnection != null){
            this.timeOutWaitingForReconnection.cancel();
            this.timeOutWaitingForReconnection = null;
        }
        if(this.closeConnection != null){
            this.closeConnection.cancel();
            this.closeConnection = null;
        }
    }

    // schedule ping to keep aws connection opened
    private void startPingTimer() {
        this.stopPingTimer();
        pingWebClientTimer = new Timer();
        pingWebClientTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Colissimo pingColissimo = Colissimo.newBuilder()
                        .setType(ColissimoType.CONNECTION_PING)
                        .setConnectionPing(ConnectionPingOuterClass.ConnectionPing.newBuilder().setPing(true).build())
                        .build();
                webSocketClient.sendColissimo(pingColissimo);
            }
        }, Constants.PING_TIMER_DELAY, Constants.PING_TIMER_PERIOD);
    }

    private void stopPingTimer(){
        if(pingWebClientTimer != null){
            pingWebClientTimer.cancel();
            pingWebClientTimer = null;
        }
    }

    // schedule reconnection to server to avoid 2h timeout on aws
    private void startReconnectionTimerTask() {
        long reconnectionTimerDelay;

        this.stopReconnectionTimerTask();
        // delay: 1 hour + random from 0 to 20 minutes
        reconnectionTimerDelay = 3600_000 + new Random().nextInt(1200_000);
        reconnectionTimer = new Timer();
        reconnectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentState == State.LISTENING) {
                    Logger.d("Reconnection timer task executing");
                    closeWS();
                }
                else {
                    Logger.w("Unable to perform reconnection task in this state: " + currentState);
                }
            }
        }, reconnectionTimerDelay, reconnectionTimerDelay);
    }

    private void stopReconnectionTimerTask() {
        if (this.reconnectionTimer != null) {
            reconnectionTimer.cancel();
            reconnectionTimer = null;
        }
    }


    // listeners
    public MessageListener getMessageListener() { return this.messageListener; }
    public DiscussionListener getDiscussionListener() { return this.discussionListener; }
    public AttachmentListener getAttachmentListener() { return attachmentListener; }
    public DraftAttachmentListener getDraftAttachmentListener() { return draftAttachmentListener; }

    // getters
    public byte[] getBytesOwnedIdentity() { return bytesOwnedIdentity; }
    public ColissimoMessageQueue getColissimoMessageQueue() { return colissimoMessageQueue; }
    public State getCurrentState() { return this.currentState; }
    public MutableLiveData<String> getSasCodeLiveData() { return sasCodeLiveData; }
    @NonNull
    public UnifiedForegroundService.WebClientSubService getService() { return this.service;}

    // partial disconnect
    public void closeWS() {
        this.webSocketClient.close();
    }
}
