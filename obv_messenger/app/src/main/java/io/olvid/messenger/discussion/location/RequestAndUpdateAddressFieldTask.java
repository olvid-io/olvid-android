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

package io.olvid.messenger.discussion.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import io.olvid.engine.Logger;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.settings.SettingsActivity;

public class RequestAndUpdateAddressFieldTask implements Runnable {
    public static final double MIN_ZOOM_FOR_REQUESTS = 15;

    private final double latitude;
    private final double longitude;
    private final AddressCallback addressCallback;
    private final static List<String> featuresLayersPriority = new ArrayList<>(Arrays.asList("address", "street", "venue", "country", "macroregion", "region")); // venue: points of interest, businesses, things with walls
    private final String peliasServer;

    RequestAndUpdateAddressFieldTask(@NonNull String peliasServer, @NonNull LatLngWrapper latLngWrapper, AddressCallback addressCallback) {
        this.peliasServer = peliasServer;
        this.latitude = latLngWrapper.getLatitude();
        this.longitude = latLngWrapper.getLongitude();
        this.addressCallback = addressCallback;
    }

    @Override
    public void run() {
        try {
            String lang = SettingsActivity.getLocationOpenStreetMapLanguage();
            URL requestUrl = (lang != null) ?
                    new URL(String.format(Locale.ENGLISH, "%s/v1/reverse?point.lat=%f&point.lon=%f&lang=%s", peliasServer, latitude, longitude, lang))
                    :
                    new URL(String.format(Locale.ENGLISH, "%s/v1/reverse?point.lat=%f&point.lon=%f", peliasServer, latitude, longitude));
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            if (connection instanceof HttpsURLConnection && AppSingleton.getSslSocketFactory() != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(AppSingleton.getSslSocketFactory());
            }
            String userAgentProperty = System.getProperty("http.agent");
            if (userAgentProperty != null) {
                connection.setRequestProperty("User-Agent", userAgentProperty);
            }

            connection.setConnectTimeout(5000);
            connection.setDoInput(true);

            int serverResponseStatusCode = connection.getResponseCode();
            if (serverResponseStatusCode == 200) {
                try (InputStream is = connection.getInputStream()) {
                    BufferedInputStream bis = new BufferedInputStream(is);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                    // read response
                    int numberOfBytesRead;
                    byte[] buffer = new byte[8192];
                    while ((numberOfBytesRead = bis.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, numberOfBytesRead);
                    }
                    byteArrayOutputStream.flush();

                    // parse response
                    PeliasReverseResponse serverResponse = AppSingleton.getJsonObjectMapper().readValue(byteArrayOutputStream.toByteArray(), PeliasReverseResponse.class);

                    if (serverResponse == null) {
                        throw new IOException("Invalid server response: " + serverResponse);
                    }

                    if (serverResponse.getFeatures().size() != 0) {
                        PeliasReverseResponse.FeaturesItem mostRelevantFeature = null;
                        // select value: we some layers are more relevant
                        for (PeliasReverseResponse.FeaturesItem feature : serverResponse.getFeatures()) {
                            if (mostRelevantFeature == null) {
                                mostRelevantFeature = feature;
                            } else {
                                // determine features priority (if both are not in priority list keep the previous mostRelevantFeature)
                                int featurePriority = featuresLayersPriority.indexOf(feature.getProperties().getLayer());
                                featurePriority = featurePriority != -1 ? featurePriority : 1000;
                                int mostRelevantFeaturePriority = featuresLayersPriority.indexOf(mostRelevantFeature.getProperties().getLayer());
                                mostRelevantFeaturePriority = mostRelevantFeaturePriority != -1 ? mostRelevantFeaturePriority : 999;
                                if (featurePriority < mostRelevantFeaturePriority) {
                                    mostRelevantFeature = feature;
                                }
                            }
                        }

                        if (mostRelevantFeature != null
                                && mostRelevantFeature.getProperties().getLabel() != null
                                && !mostRelevantFeature.getProperties().getLabel().isEmpty()) {
                            // update address live data value
                            if (addressCallback != null) {
                                addressCallback.onAddressFound(this, mostRelevantFeature.getProperties().getLabel());
                            }
                            return;
                        } else {
                            throw new IOException("Invalid result returned by server" + serverResponse);
                        }
                    }
                }
            } else {
                Logger.w("RequestAndUpdateAddressFieldTask: Invalid server response code: " + serverResponseStatusCode);
            }
        } catch (IOException e) {
            Logger.e("RequestAndUpdateAddressFieldTask: Exception during pelias reverse request", e);
        }
        if (addressCallback != null) {
            addressCallback.onAddressFound(this, null);
        }
    }

    interface  AddressCallback {
        public void onAddressFound(RequestAndUpdateAddressFieldTask requestAndUpdateAddressFieldTask, @Nullable String address);
    }
}
