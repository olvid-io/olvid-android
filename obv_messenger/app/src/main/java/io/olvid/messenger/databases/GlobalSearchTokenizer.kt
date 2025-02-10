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

package io.olvid.messenger.databases

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.olvid.messenger.App
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class GlobalSearchTokenizer {
    companion object {
        private val INSTANCE: GlobalSearchTokenizer by lazy {
            GlobalSearchTokenizer()
        }

        fun tokenize(query: String): List<String> {
            return INSTANCE.tokenize(query)
        }
    }

    private val database: SupportSQLiteDatabase;

    init {
        val supportSQLiteOpenHelper = SupportOpenHelperFactory(null).create(
            configuration = SupportSQLiteOpenHelper.Configuration(
                context = App.getContext(),
                name = null, // in memory db
                callback = object :
                    SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE VIRTUAL TABLE tokenizer USING fts3tokenize(unicode61, \"remove_diacritics=2\");");
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {}
                })
        )

        database = supportSQLiteOpenHelper.readableDatabase
    }

    private fun tokenize(query: String): List<String> {
        val cursor = database.query("SELECT token FROM `tokenizer` WHERE input = ?", arrayOf(query))
        val tokens = mutableListOf<String>()
        with(cursor) {
            while (moveToNext()) {
                try {
                    tokens.add(getString(0))
                } catch (_ : Exception) {}
            }
        }
        cursor.close()
        return tokens
    }
}