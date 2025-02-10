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

package io.olvid.messenger.webclient;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.datatypes.JsonMessage;

public class MessageHandler {
    private final WebClientManager manager;

    MessageHandler(WebClientManager manager) {
        this.manager = manager;
    }

    void handle(String message) {
        JsonMessage jsonMessage;

        // aws server might send empty message as responses
        if (message == null || message.equals("")) {
            return ;
        }

        try {
            jsonMessage = AppSingleton.getJsonObjectMapper().readValue(message, JsonMessage.class);
        } catch (JsonProcessingException e) {
            Logger.e("Unable to parse jsonMessage, ignoring it: " + message);
            e.printStackTrace();
            return ;
        }

        if (jsonMessage.getAction() == null) {
            Logger.e("Received jsonMessage does not contain an action: " + message);
            return;
        }

        switch (jsonMessage.getAction()) {
            case ("connectionRegistered"): {
                this.manager.handlerConnectionRegistered();
                break ;
            }
            case ("correspondingRegistered"): {
                this.manager.handlerCorrespondingRegistered();
                break ;
            }
            case ("relay"): {
                JsonMessage.Relay relayMessage;

                try {
                    relayMessage = AppSingleton.getJsonObjectMapper().readValue(message, JsonMessage.Relay.class);
                } catch (JsonProcessingException e) {
                    Logger.e("Unable to parse relay message", e);
                    return;
                }
                if (this.manager.getCurrentState() != WebClientManager.State.LISTENING || this.manager.getColissimoMessageQueue() == null) {
                    Logger.e("Received colissimo, but colissimo queue is not ready, ignoring");
                    break ;
                }
                this.manager.getColissimoMessageQueue().queue(relayMessage.getColissimo());
                break ;
            }
            // app is only supposed to receive a BrowserKemSeed connectionColissimo
            case ("connection"): {
                JsonMessage.Connection connectionMessage;

                try {
                    connectionMessage = AppSingleton.getJsonObjectMapper().readValue(message, JsonMessage.Connection.class);
                } catch (JsonProcessingException e) {
                    Logger.e("Unable to parse connection message", e);
                    return;
                }
                Logger.d("Connection colissimo received");
                this.manager.notifyProtocol2Received(connectionMessage.getColissimo());
                break ;
            }
            case ("newCorresponding"): {
                JsonMessage.NewCorresponding newCorresponding;

                try {
                    newCorresponding = AppSingleton.getJsonObjectMapper().readValue(message, JsonMessage.NewCorresponding.class);
                } catch (JsonProcessingException e) {
                    Logger.e("Unable to parse newCorresponding message", e);
                    return ;
                }
                Logger.i("New corresponding registered: " + newCorresponding.getIdentifier() + " version: " + newCorresponding.getVersion());
                if(newCorresponding.getVersion() != Constants.VERSION) {
                    Logger.e("Versions don't match !");
                }
                this.manager.handlerNewCorresponding(newCorresponding.getIdentifier());
                break ;
            }
            case ("correspondingDisconnected"): {
                try {
                    JsonMessage.CorrespondingDisconnected correspondingDisconnected = AppSingleton.getJsonObjectMapper().readValue(message, JsonMessage.CorrespondingDisconnected.class);
                } catch (JsonProcessingException e) {
                    Logger.e("Unable to parse correspondingDisconnected message", e);
                    return ;
                }
                this.manager.handlerCorrespondingDisconnected();
                break ;
            }
            case ("error"): {
                JsonMessage.ErrorMessage errorMessage;

                try {
                    errorMessage = AppSingleton.getJsonObjectMapper().readValue(message, JsonMessage.ErrorMessage.class);
                } catch (JsonProcessingException e) {
                    Logger.e("Unable to parse errorMessage message", e);
                    this.manager.handlerReceivedErrorMessageFromServer(-1);
                    return ;
                }
                Logger.e("Received error message from server: " + errorMessage.getError());
                this.manager.handlerReceivedErrorMessageFromServer(errorMessage.getError());
                break ;
            }
            default: {
                Logger.e("Unknown action: " + jsonMessage.getAction());
                break ;
            }
        }
    }
}
