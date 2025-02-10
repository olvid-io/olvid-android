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

package io.olvid.messenger.customClasses;

import android.net.Uri;

import androidx.annotation.NonNull;

import net.openid.appauth.connectivity.ConnectionBuilder;
import net.openid.appauth.connectivity.DefaultConnectionBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

import io.olvid.messenger.AppSingleton;

public class NoExceptionConnectionBuilder implements ConnectionBuilder {
    private static final ConnectionBuilder INSTANCE = DefaultConnectionBuilder.INSTANCE;

    @NonNull
    @Override
    public HttpURLConnection openConnection(@NonNull Uri uri) throws IOException {
        try {
            HttpURLConnection connection = INSTANCE.openConnection(uri);
            if (connection instanceof HttpsURLConnection && AppSingleton.getSslSocketFactory() != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(AppSingleton.getSslSocketFactory());
            }
            String userAgentProperty = System.getProperty("http.agent");
            if (userAgentProperty != null) {
                connection.setRequestProperty("User-Agent", userAgentProperty);
            }
            return connection;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
