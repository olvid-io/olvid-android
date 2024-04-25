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

package io.olvid.messenger.databases;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import java.nio.charset.StandardCharsets;

public class PragmaSQLiteOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {
    private final SupportOpenHelperFactory frameworkSQLiteOpenHelperFactory;

    public PragmaSQLiteOpenHelperFactory(@NonNull String dbKey) {
        frameworkSQLiteOpenHelperFactory = new SupportOpenHelperFactory(dbKey.getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration configuration) {
        PragmaCallbackWrapper callback = new PragmaCallbackWrapper(configuration.callback);
        SupportSQLiteOpenHelper.Configuration wrappedConfiguration = SupportSQLiteOpenHelper.Configuration.builder(configuration.context).name(configuration.name).callback(callback).build();
        return frameworkSQLiteOpenHelperFactory.create(wrappedConfiguration);
    }

    private static class PragmaCallbackWrapper extends SupportSQLiteOpenHelper.Callback {
        private final SupportSQLiteOpenHelper.Callback wrappedCallback;

        PragmaCallbackWrapper(SupportSQLiteOpenHelper.Callback wrappedCallback) {
            super(wrappedCallback.version);
            this.wrappedCallback = wrappedCallback;
        }

        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            wrappedCallback.onCreate(db);
        }

        @Override
        public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
            wrappedCallback.onUpgrade(db, oldVersion, newVersion);
        }

        @Override
        public void onConfigure(@NonNull SupportSQLiteDatabase db) {
            wrappedCallback.onConfigure(db);
            db.query("PRAGMA secure_delete=true;");
            db.query("PRAGMA legacy_alter_table=true;");
        }

        @Override
        public void onDowngrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
            wrappedCallback.onDowngrade(db, oldVersion, newVersion);
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            wrappedCallback.onOpen(db);
        }

        @Override
        public void onCorruption(@NonNull SupportSQLiteDatabase db) {
            wrappedCallback.onCorruption(db);
        }


    }
}
