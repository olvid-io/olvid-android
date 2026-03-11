/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

/**
 * A wrapper for [SupportSQLiteDatabase] that implements methods added in androidx.sqlite 2.5.0,
 * which might be missing from older SQLCipher implementations.
 */
class SupportSQLiteDatabaseWrapper(private val delegate: SupportSQLiteDatabase) : SupportSQLiteDatabase by delegate {
    override fun beginTransactionReadOnly() {
        try {
            delegate.beginTransactionReadOnly()
        } catch (_: AbstractMethodError) {
            delegate.beginTransaction()
        }
    }

    override fun execPerConnectionSQL(sql: String, bindArgs: Array<out Any?>?) {
        try {
            delegate.execPerConnectionSQL(sql, bindArgs)
        } catch (_: AbstractMethodError) {
            if (bindArgs == null) {
                delegate.execSQL(sql)
            } else {
                delegate.execSQL(sql, bindArgs)
            }
        }
    }
}

/**
 * A wrapper for [SupportSQLiteOpenHelper] that returns [SupportSQLiteDatabaseWrapper].
 */
class SupportSQLiteOpenHelperWrapper(private val delegate: SupportSQLiteOpenHelper) : SupportSQLiteOpenHelper by delegate {
    override val writableDatabase: SupportSQLiteDatabase
        get() = SupportSQLiteDatabaseWrapper(delegate.writableDatabase)

    override val readableDatabase: SupportSQLiteDatabase
        get() = SupportSQLiteDatabaseWrapper(delegate.readableDatabase)
}
