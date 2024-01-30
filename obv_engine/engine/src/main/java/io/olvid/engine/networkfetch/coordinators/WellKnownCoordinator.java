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

package io.olvid.engine.networkfetch.coordinators;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.CachedWellKnown;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.datatypes.WellKnownCacheDelegate;
import io.olvid.engine.networkfetch.operations.WellKnownDownloadOperation;

public class WellKnownCoordinator implements Operation.OnFinishCallback, Operation.OnCancelCallback, WellKnownCacheDelegate {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private NotificationPostingDelegate notificationPostingDelegate;
    private final ObjectMapper jsonObjectMapper;

    private boolean cacheInitialized;
    private final HashMap<String, JsonWellKnown> wellKnownCache;

    private final NoDuplicateOperationQueue wellKnownDownloadOperationQueue;
    private final Timer wellKnownDownloadTimer;

    public WellKnownCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, ObjectMapper jsonObjectMapper) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.jsonObjectMapper = jsonObjectMapper;

        this.cacheInitialized = false;
        this.wellKnownCache = new HashMap<>();

        this.wellKnownDownloadOperationQueue = new NoDuplicateOperationQueue();
        this.wellKnownDownloadOperationQueue.execute(1, "Engine-WellKnownDownloadCoordinator");

        this.wellKnownDownloadTimer = new Timer("Engine-WellKnownDownloadTimer");
    }

    public void setNotificationPostingDelegate(NotificationPostingDelegate notificationPostingDelegate) {
        this.notificationPostingDelegate = notificationPostingDelegate;
    }

    public void initialQueueing() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            List<CachedWellKnown> cachedWellKnowns = CachedWellKnown.getAll(fetchManagerSession);
            Identity[] ownedIdentities = fetchManagerSession.identityDelegate.getOwnedIdentities(fetchManagerSession.session);

            Set<String> servers = new HashSet<>();
            for (Identity ownedIdentity: ownedIdentities) {
                servers.add(ownedIdentity.getServer());
            }

            for (String server: servers) {
                queueNewWellKnownDownloadOperation(server);
            }

            // check for obsolete cache elements
            for (CachedWellKnown cachedWellKnown: cachedWellKnowns) {
                if (servers.contains(cachedWellKnown.getServer())) {
                    try {
                        wellKnownCache.put(cachedWellKnown.getServer(), jsonObjectMapper.readValue(cachedWellKnown.getSerializedWellKnown(), JsonWellKnown.class));
                    } catch (Exception e) {
                        // do nothing
                    }
                } else {
                    cachedWellKnown.delete();
                }
            }
            this.cacheInitialized = true;

            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WELL_KNOWN_CACHE_INITIALIZED, new HashMap<>());

            wellKnownDownloadTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
                        Identity[] ownedIdentities = fetchManagerSession.identityDelegate.getOwnedIdentities(fetchManagerSession.session);

                        Set<String> servers = new HashSet<>();
                        for (Identity ownedIdentity: ownedIdentities) {
                            servers.add(ownedIdentity.getServer());
                        }

                        for (String server: servers) {
                            queueNewWellKnownDownloadOperation(server);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, Constants.WELL_KNOWN_REFRESH_INTERVAL, Constants.WELL_KNOWN_REFRESH_INTERVAL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void queueNewWellKnownDownloadOperation(String server) {
        Logger.d("Requesting .well-known fetch for " + server);
        wellKnownDownloadOperationQueue.queue(new WellKnownDownloadOperation(fetchManagerSessionFactory, sslSocketFactory, server, jsonObjectMapper, this, this));
    }


    @Override
    public void onFinishCallback(Operation operation) {
        if (!(operation instanceof WellKnownDownloadOperation)) {
            return;
        }
        WellKnownDownloadOperation wellKnownDownloadOperation = (WellKnownDownloadOperation) operation;

        String server = wellKnownDownloadOperation.getServer();
        JsonWellKnown jsonWellKnown = wellKnownDownloadOperation.getDownloadedWellKnown();
        boolean updated = wellKnownDownloadOperation.isUpdated();
        wellKnownCache.put(server, jsonWellKnown);

        if (updated) {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_SERVER_KEY, server);
            userInfo.put(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_SERVER_CONFIG_KEY, jsonWellKnown.getServerConfig());
            userInfo.put(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_APP_INFO_KEY, jsonWellKnown.getAppInfo());
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED, userInfo);
        } else {
            HashMap<String, Object> userInfo = new HashMap<>();
            userInfo.put(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY, wellKnownDownloadOperation.getServer());
            userInfo.put(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY, jsonWellKnown.getAppInfo());
            notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS, userInfo);
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        if (!(operation instanceof WellKnownDownloadOperation)) {
            return;
        }
        WellKnownDownloadOperation wellKnownDownloadOperation = (WellKnownDownloadOperation) operation;

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY, wellKnownDownloadOperation.getServer());
        notificationPostingDelegate.postNotification(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED, userInfo);
    }

    public static class NotCachedException extends Exception {}

    @Override
    public String getWsUrl(String server) throws NotCachedException {
        if (!cacheInitialized) {
            throw new NotCachedException();
        }
        JsonWellKnown jsonWellKnown = wellKnownCache.get(server);
        if (jsonWellKnown == null) {
            queueNewWellKnownDownloadOperation(server);
            throw new NotCachedException();
        }
        if (jsonWellKnown.serverConfig == null) {
            return null;
        }
        return jsonWellKnown.serverConfig.getWebSocketUrl();
    }

    @Override
    public List<String> getTurnUrls(String server) throws NotCachedException {
        if (!cacheInitialized) {
            throw new NotCachedException();
        }
        JsonWellKnown jsonWellKnown = wellKnownCache.get(server);
        if (jsonWellKnown == null) {
            queueNewWellKnownDownloadOperation(server);
            throw new NotCachedException();
        }
        if (jsonWellKnown.serverConfig == null) {
            return null;
        }
        return jsonWellKnown.serverConfig.turnServerUrls;
    }

    @Override
    public String getOsmUrl(String server) throws NotCachedException {
        if (!cacheInitialized) {
            throw new NotCachedException();
        }
        JsonWellKnown jsonWellKnown = wellKnownCache.get(server);
        if (jsonWellKnown == null) {
            queueNewWellKnownDownloadOperation(server);
            throw new NotCachedException();
        }
        if (jsonWellKnown.serverConfig == null) {
            return null;
        }
        return jsonWellKnown.serverConfig.osmServerUrl;
    }

    @Override
    public String getAddressUrl(String server) throws NotCachedException {
        if (!cacheInitialized) {
            throw new NotCachedException();
        }
        JsonWellKnown jsonWellKnown = wellKnownCache.get(server);
        if (jsonWellKnown == null) {
            queueNewWellKnownDownloadOperation(server);
            throw new NotCachedException();
        }
        if (jsonWellKnown.serverConfig == null) {
            return null;
        }
        return jsonWellKnown.serverConfig.addressServerUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonWellKnown {
        JsonWellKnownServerConfig serverConfig;
        Map<String, Integer> appInfo;

        @JsonProperty("server")
        public JsonWellKnownServerConfig getServerConfig() {
            return serverConfig;
        }

        @JsonProperty("server")
        public void setServerConfig(JsonWellKnownServerConfig serverConfig) {
            this.serverConfig = serverConfig;
        }

        @JsonProperty("app")
        public Map<String, Integer> getAppInfo() {
            return appInfo;
        }

        @JsonProperty("app")
        public void setAppInfo(Map<String, Integer> appInfo) {
            this.appInfo = appInfo;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonWellKnownServerConfig {
        String webSocketUrl;
        List<String> turnServerUrls;
        String osmServerUrl;
        String addressServerUrl;

        @JsonProperty("ws_server")
        public String getWebSocketUrl() {
            return webSocketUrl;
        }

        @JsonProperty("ws_server")
        public void setWebSocketUrl(String webSocketUrl) {
            this.webSocketUrl = webSocketUrl;
        }

        @JsonProperty("turn_servers")
        public List<String> getTurnServerUrls() {
            return turnServerUrls;
        }

        @JsonProperty("turn_servers")
        public void setTurnServerUrls(List<String> turnServerUrls) {
            this.turnServerUrls = turnServerUrls;
        }

        @JsonProperty("osm_server")
        public String getOsmServerUrl() {
            return osmServerUrl;
        }

        @JsonProperty("osm_server")
        public void setOsmServerUrl(String osmServerUrl) {
            this.osmServerUrl = osmServerUrl;
        }

        @JsonProperty("address_server")
        public String getAddressServerUrl() {
            return addressServerUrl;
        }

        @JsonProperty("address_server")
        public void setAddressServerUrl(String addressServerUrl) {
            this.addressServerUrl = addressServerUrl;
        }
    }
}
