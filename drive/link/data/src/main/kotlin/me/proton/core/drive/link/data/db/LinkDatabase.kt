/*
 * Copyright (c) 2021-2023 Proton AG.
 * This file is part of Proton Core.
 *
 * Proton Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Core.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.proton.core.drive.link.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.core.data.room.db.Database
import me.proton.core.data.room.db.migration.DatabaseMigration
import me.proton.core.drive.base.data.db.Column.SHARE_URL_ID
import me.proton.core.drive.base.data.db.Column.SHARE_URL_SHARE_ID

interface LinkDatabase : Database {
    val linkDao: LinkDao

    companion object {
        val MIGRATION_0 = object : DatabaseMigration {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                        ALTER TABLE `LinkEntity` ADD COLUMN $SHARE_URL_SHARE_ID TEXT DEFAULT NULL
                    """.trimIndent()
                )
                database.execSQL(
                    """
                        ALTER TABLE `LinkEntity` ADD COLUMN $SHARE_URL_ID TEXT DEFAULT NULL
                    """.trimIndent()
                )
            }
        }
    }
}
