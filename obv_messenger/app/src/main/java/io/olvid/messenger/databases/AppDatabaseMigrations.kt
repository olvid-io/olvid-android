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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.olvid.engine.Logger
import io.olvid.engine.crypto.Hash
import io.olvid.engine.crypto.Suite
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.Arrays
import java.util.regex.Pattern


internal object AppDatabaseMigrations {
    val MIGRATIONS: Array<Migration> = arrayOf(

        object : Migration(76, 77) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 76 TO 77")
                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(75, 76) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 75 TO 76")
                db.execSQL("CREATE TABLE IF NOT EXISTS `fyle_message_text_block` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `fyle_id` INTEGER NOT NULL, `text` TEXT NOT NULL, `bounding_box` TEXT, `is_block` INTEGER NOT NULL, `parent_block_id` INTEGER, FOREIGN KEY(`fyle_id`) REFERENCES `fyle_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_text_block_fyle_id` ON `fyle_message_text_block` (`fyle_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_text_block_message_id` ON `fyle_message_text_block` (`message_id`)")
            }
        },

        object : Migration(74, 75) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 74 TO 75")

                db.execSQL("CREATE TABLE IF NOT EXISTS `message_return_receipt_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nonce` BLOB NOT NULL, `payload` BLOB NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_return_receipt_table_return_receipt_nonce` ON `message_return_receipt_table` (`nonce`)")
            }
        },

        object : Migration(73, 74) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 73 TO 74")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_location_type` ON `message_table` (`location_type`)")
            }
        },

        object : Migration(72, 73) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 72 TO 73")
                db.execSQL("DROP INDEX `index_message_table_discussion_id_status`")
                db.execSQL("DROP INDEX `index_message_table_sort_index`")
                db.execSQL("DROP INDEX `index_message_table_timestamp`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_discussion_id_sort_index` ON `message_table` (`discussion_id`, `sort_index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_discussion_id_status_sort_index` ON `message_table` (`discussion_id`, `status`, `sort_index`)")
            }
        },

        object : Migration(71, 72) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 71 TO 72")

                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `last_remote_delete_timestamp` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(70, 71) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 70 TO 71")

                db.execSQL("ALTER TABLE `fyle_message_join_with_status` DROP COLUMN `progress`")
            }
        },

        object : Migration(69, 70) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 69 TO 70")

                db.execSQL("ALTER TABLE `group_table` ADD COLUMN `full_search_field` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `group2_table` ADD COLUMN `full_search_field` TEXT NOT NULL DEFAULT ''")
            }
        },

        object : Migration(68, 69) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 68 TO 69")

                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `pre_key_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `recently_online` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `owned_device_table` ADD COLUMN `has_pre_key` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(67, 68) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 67 TO 68")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `bookmarked` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(66, 67) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 66 TO 67")

                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `message_table_fts` USING FTS4(`content_body` TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=2`, content=`message_table`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_table_fts_BEFORE_UPDATE BEFORE UPDATE ON `message_table` BEGIN DELETE FROM `message_table_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_table_fts_BEFORE_DELETE BEFORE DELETE ON `message_table` BEGIN DELETE FROM `message_table_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_table_fts_AFTER_UPDATE AFTER UPDATE ON `message_table` BEGIN INSERT INTO `message_table_fts`(`docid`, `content_body`) VALUES (NEW.`rowid`, NEW.`content_body`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_table_fts_AFTER_INSERT AFTER INSERT ON `message_table` BEGIN INSERT INTO `message_table_fts`(`docid`, `content_body`) VALUES (NEW.`rowid`, NEW.`content_body`); END")


                db.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `text_extracted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `text_content` TEXT DEFAULT NULL")

                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `fyle_message_join_with_status_fts` USING FTS4(`file_name` TEXT NOT NULL, `text_content` TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=2`, content=`fyle_message_join_with_status`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_fyle_message_join_with_status_fts_BEFORE_UPDATE BEFORE UPDATE ON `fyle_message_join_with_status` BEGIN DELETE FROM `fyle_message_join_with_status_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_fyle_message_join_with_status_fts_BEFORE_DELETE BEFORE DELETE ON `fyle_message_join_with_status` BEGIN DELETE FROM `fyle_message_join_with_status_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_fyle_message_join_with_status_fts_AFTER_UPDATE AFTER UPDATE ON `fyle_message_join_with_status` BEGIN INSERT INTO `fyle_message_join_with_status_fts`(`docid`, `file_name`, `text_content`) VALUES (NEW.`rowid`, NEW.`file_name`, NEW.`text_content`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_fyle_message_join_with_status_fts_AFTER_INSERT AFTER INSERT ON `fyle_message_join_with_status` BEGIN INSERT INTO `fyle_message_join_with_status_fts`(`docid`, `file_name`, `text_content`) VALUES (NEW.`rowid`, NEW.`file_name`, NEW.`text_content`); END")

                // some users seem to have lost indexes in the SQLcipher migration... attempt to recreate all missing indexes, just in case!
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_display_name` ON `contact_table` (`display_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_sort_display_name` ON `contact_table` (`sort_display_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_table_name` ON `group_table` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_group_join_bytes_group_owner_and_uid_bytes_owned_identity` ON `contact_group_join` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_group_join_bytes_contact_identity_bytes_owned_identity` ON `contact_group_join` (`bytes_contact_identity`, `bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_group_member_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `pending_group_member_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_discussion_table_bytes_owned_identity_discussion_type_bytes_discussion_identifier` ON `discussion_table` (`bytes_owned_identity`, `discussion_type`, `bytes_discussion_identifier`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_discussion_table_title` ON `discussion_table` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_discussion_customization_table_discussion_id` ON `discussion_customization_table` (`discussion_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fyle_table_sha256` ON `fyle_table` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_fyle_id` ON `fyle_message_join_with_status` (`fyle_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_message_id` ON `fyle_message_join_with_status` (`message_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_engine_message_identifier_engine_number` ON `fyle_message_join_with_status` (`engine_message_identifier`, `engine_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_message_id_engine_number` ON `fyle_message_join_with_status` (`message_id`, `engine_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_bytes_owned_identity` ON `fyle_message_join_with_status` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invitation_table_bytes_owned_identity` ON `invitation_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_discussion_id` ON `message_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_engine_message_identifier` ON `message_table` (`engine_message_identifier`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_sort_index` ON `message_table` (`sort_index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_timestamp` ON `message_table` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_message_type_status` ON `message_table` (`message_type`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_discussion_id_status` ON `message_table` (`discussion_id`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_expiration_table_message_id` ON `message_expiration_table` (`message_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_expiration_table_expiration_timestamp` ON `message_expiration_table` (`expiration_timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_message_id` ON `message_metadata_table` (`message_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_message_id_kind` ON `message_metadata_table` (`message_id`, `kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_timestamp` ON `message_metadata_table` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_recipient_info_table_message_id` ON `message_recipient_info_table` (`message_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_recipient_info_table_bytes_contact_identity` ON `message_recipient_info_table` (`bytes_contact_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_recipient_info_table_return_receipt_nonce` ON `message_recipient_info_table` (`return_receipt_nonce`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_recipient_info_table_engine_message_identifier` ON `message_recipient_info_table` (`engine_message_identifier`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_recipient_info_table_message_id_bytes_contact_identity` ON `message_recipient_info_table` (`message_id`, `bytes_contact_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity` ON `call_log_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_timestamp` ON `call_log_table` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `call_log_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_item_contact_join_call_log_item_id` ON `call_log_item_contact_join` (`call_log_item_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_item_contact_join_bytes_owned_identity_bytes_contact_identity` ON `call_log_item_contact_join` (`bytes_owned_identity`, `bytes_contact_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reactions_table_message_id` ON `reactions_table` (`message_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reactions_table_message_id_bytes_identity` ON `reactions_table` (`message_id`, `bytes_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_delete_and_edit_request_table_discussion_id` ON `remote_delete_and_edit_request_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_delete_and_edit_request_table_server_timestamp` ON `remote_delete_and_edit_request_table` (`server_timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_certificate_domain_name` ON `known_certificate` (`domain_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_discussion_id` ON `reaction_request_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_server_timestamp` ON `reaction_request_table` (`server_timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_discussion_id_sender_identifier_sender_thread_identifier_sender_sequence_number` ON `reaction_request_table` (`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_shortcut_configuration_table_discussion_id` ON `action_shortcut_configuration_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group2_table_bytes_owned_identity` ON `group2_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group2_pending_member_table_bytes_owned_identity` ON `group2_pending_member_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group2_pending_member_table_bytes_owned_identity_bytes_group_identifier` ON `group2_pending_member_table` (`bytes_owned_identity`, `bytes_group_identifier`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group2_member_table_bytes_owned_identity` ON `group2_member_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group2_member_table_bytes_owned_identity_bytes_group_identifier` ON `group2_member_table` (`bytes_owned_identity`, `bytes_group_identifier`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group2_member_table_bytes_owned_identity_bytes_contact_identity` ON `group2_member_table` (`bytes_owned_identity`, `bytes_contact_identity`)")
            }
        },

        object : Migration(65, 66) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 65 TO 66")

                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `first_name` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `group2_pending_member_table` ADD COLUMN `first_name` TEXT DEFAULT NULL")
            }
        },

        object : Migration(64, 65) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 64 TO 65")

                db.execSQL("ALTER TABLE `owned_device_table` ADD COLUMN `trusted` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(63, 64) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 63 TO 64")

                db.execSQL("CREATE TABLE IF NOT EXISTS `owned_device_table` (`bytes_owned_identity` BLOB NOT NULL, `bytes_device_uid` BLOB NOT NULL, `display_name` TEXT, `current_device` INTEGER NOT NULL, `channel_confirmed` INTEGER NOT NULL, `last_registration_timestamp` INTEGER, `expiration_timestamp` INTEGER, PRIMARY KEY(`bytes_owned_identity`, `bytes_device_uid`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        },

        object : Migration(62, 63) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 62 TO 63")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `mentioned` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(61, 62) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 61 TO 62")

                db.execSQL("CREATE TABLE IF NOT EXISTS `call_log_table_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_group_owner_and_uid` BLOB, `timestamp` INTEGER NOT NULL, `call_type` INTEGER NOT NULL, `call_status` INTEGER NOT NULL, `duration` INTEGER NOT NULL, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP INDEX `index_call_log_table_bytes_owned_identity`")
                db.execSQL("DROP INDEX `index_call_log_table_timestamp`")
                db.execSQL("DROP INDEX `index_call_log_table_bytes_group_owner_and_uid_bytes_owned_identity`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity` ON `call_log_table_temp` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_timestamp` ON `call_log_table_temp` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `call_log_table_temp` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                db.execSQL("INSERT INTO `call_log_table_temp` (id, bytes_owned_identity, bytes_group_owner_and_uid, timestamp, call_type, call_status, duration) SELECT id, bytes_owned_identity, bytes_group_owner_and_uid, timestamp, call_type, call_status, duration FROM `call_log_table`")

                db.execSQL("DROP TABLE `call_log_table`")
                db.execSQL("ALTER TABLE `call_log_table_temp` RENAME TO `call_log_table`")
            }
        },

        object : Migration(60, 61) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 60 TO 61")

                db.execSQL("ALTER TABLE `invitation_table` ADD COLUMN `discussion_id` INTEGER DEFAULT NULL")
            }
        },

        object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 59 TO 60")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_mentions` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `remote_delete_and_edit_request_table` ADD COLUMN `mentions` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_mute_notifications_except_mentioned` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_mute_notifications_except_mentioned` INTEGER NOT NULL DEFAULT 1")
            }
        },

        object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 58 TO 59")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `link_preview_fyle_id` INTEGER DEFAULT NULL")
            }
        },

        object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 57 TO 58")

                db.execSQL("CREATE TABLE `reactions_table_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `bytes_identity` BLOB, `emoji` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL(
                    "INSERT INTO `reactions_table_new` " +
                            " SELECT `id`, `message_id`, `bytes_identity`, `emoji`, `timestamp` " +
                            " FROM `reactions_table`"
                )

                db.execSQL("DROP INDEX `index_reactions_table_message_id`")
                db.execSQL("DROP INDEX `index_reactions_table_message_id_bytes_identity`")
                db.execSQL("DROP TABLE `reactions_table`")

                db.execSQL("ALTER TABLE `reactions_table_new` RENAME TO `reactions_table`")
                db.execSQL("CREATE INDEX `index_reactions_table_message_id` ON `reactions_table` (`message_id`)")
                db.execSQL("CREATE UNIQUE INDEX `index_reactions_table_message_id_bytes_identity` ON `reactions_table` (`message_id`, `bytes_identity`)")


                db.execSQL("CREATE TABLE IF NOT EXISTS `reaction_request_table_new` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `reacter` BLOB NOT NULL, `server_timestamp` INTEGER NOT NULL, `reaction` TEXT, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`, `reacter`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL(
                    "INSERT INTO `reaction_request_table_new` " +
                            " SELECT `discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`, `reacter`, `server_timestamp`, `reaction` " +
                            " FROM `reaction_request_table`"
                )

                db.execSQL("DROP INDEX `index_reaction_request_table_discussion_id`")
                db.execSQL("DROP INDEX `index_reaction_request_table_server_timestamp`")
                db.execSQL("DROP INDEX `index_reaction_request_table_discussion_id_sender_identifier_sender_thread_identifier_sender_sequence_number`")
                db.execSQL("DROP TABLE `reaction_request_table`")

                db.execSQL("ALTER TABLE `reaction_request_table_new` RENAME TO `reaction_request_table`")
                db.execSQL("CREATE INDEX `index_reaction_request_table_discussion_id` ON `reaction_request_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX `index_reaction_request_table_server_timestamp` ON `reaction_request_table` (`server_timestamp`)")
                db.execSQL("CREATE INDEX `index_reaction_request_table_discussion_id_sender_identifier_sender_thread_identifier_sender_sequence_number` ON `reaction_request_table` (`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`)")
            }
        },

        object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 56 TO 57")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `limited_visibility` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `message_table` SET `limited_visibility` = 1 WHERE `message_type` = 6") // set old inbound_ephemeral messages to limited_visibility
            }
        },  // groups v2


        object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 55 TO 56")

                db.execSQL("ALTER TABLE `discussion_table` RENAME TO `discussion_table_old`")
                db.execSQL("DROP INDEX  `index_discussion_table_bytes_owned_identity`")
                db.execSQL("DROP INDEX  `index_discussion_table_bytes_contact_identity_bytes_owned_identity`")
                db.execSQL("DROP INDEX  `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity`")
                db.execSQL("DROP INDEX  `index_discussion_table_title`")

                db.execSQL("CREATE TABLE `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `discussion_type` INTEGER NOT NULL, `bytes_discussion_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `last_message_timestamp` INTEGER NOT NULL, `photo_url` TEXT, `keycloak_managed` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `active` INTEGER NOT NULL, `trust_level` INTEGER, `status` INTEGER NOT NULL, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_owned_identity_discussion_type_bytes_discussion_identifier` ON `discussion_table` (`bytes_owned_identity`, `discussion_type`, `bytes_discussion_identifier`)")
                db.execSQL("CREATE INDEX `index_discussion_table_title` ON `discussion_table` (`title`)")

                // insert one to one discussions
                db.execSQL(
                    "INSERT INTO `discussion_table` " +
                            " SELECT `id`, `title`, `bytes_owned_identity`, 1, `bytes_contact_identity`, `sender_thread_identifier`, `last_outbound_message_sequence_number`, `last_message_timestamp`, `photo_url`, `keycloak_managed`, `unread`, 0, `active`, `trust_level`, 1 " +
                            " FROM `discussion_table_old`" +
                            " WHERE `bytes_contact_identity` IS NOT NULL "
                )

                // insert group discussions
                db.execSQL(
                    "INSERT INTO `discussion_table` " +
                            " SELECT `id`, `title`, `bytes_owned_identity`, 2, `bytes_group_owner_and_uid`, `sender_thread_identifier`, `last_outbound_message_sequence_number`, `last_message_timestamp`, `photo_url`, `keycloak_managed`, `unread`, 0, `active`, `trust_level`, 1 " +
                            " FROM `discussion_table_old`" +
                            " WHERE `bytes_group_owner_and_uid` IS NOT NULL "
                )

                // insert locked discussions with a random bytes_discussion_identifier
                db.execSQL(
                    "INSERT INTO `discussion_table` " +
                            " SELECT `id`, `title`, `bytes_owned_identity`, 2, 'lockedRandomIdentifier' || randomblob(32), `sender_thread_identifier`, `last_outbound_message_sequence_number`, `last_message_timestamp`, `photo_url`, `keycloak_managed`, `unread`, 0, `active`, `trust_level`, 2 " +
                            " FROM `discussion_table_old`" +
                            " WHERE `bytes_contact_identity` IS NULL " +
                            " AND `bytes_group_owner_and_uid` IS NULL "
                )

                db.execSQL("DROP TABLE `discussion_table_old`")

                db.execSQL("CREATE TABLE `group2_table` (`bytes_owned_identity` BLOB NOT NULL, `bytes_group_identifier` BLOB NOT NULL, `keycloak_managed` INTEGER NOT NULL, `name` TEXT, `photo_url` TEXT, `group_members_names` TEXT NOT NULL, `update_in_progress` INTEGER NOT NULL, `new_published_details` INTEGER NOT NULL, `own_permission_admin` INTEGER NOT NULL, `own_permission_remote_delete_anything` INTEGER NOT NULL, `own_permission_edit_or_remote_delete_own_messages` INTEGER NOT NULL, `own_permission_change_settings` INTEGER NOT NULL, `own_permission_send_message` INTEGER NOT NULL, `custom_name` TEXT, `custom_photo_url` TEXT, `personal_note` TEXT, PRIMARY KEY(`bytes_owned_identity`, `bytes_group_identifier`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX `index_group2_table_bytes_owned_identity` ON `group2_table` (`bytes_owned_identity`)")

                db.execSQL("CREATE TABLE `group2_pending_member_table` (`bytes_owned_identity` BLOB NOT NULL, `bytes_group_identifier` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `sort_display_name` BLOB NOT NULL, `full_search_display_name` TEXT NOT NULL, `identity_details` TEXT, `permission_admin` INTEGER NOT NULL, `permission_remote_delete_anything` INTEGER NOT NULL, `permission_edit_or_remote_delete_own_messages` INTEGER NOT NULL, `permission_change_settings` INTEGER NOT NULL, `permission_send_message` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_group_identifier`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`, `bytes_group_identifier`) REFERENCES `group2_table`(`bytes_owned_identity`, `bytes_group_identifier`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX `index_group2_pending_member_table_bytes_owned_identity` ON `group2_pending_member_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX `index_group2_pending_member_table_bytes_owned_identity_bytes_group_identifier` ON `group2_pending_member_table` (`bytes_owned_identity`, `bytes_group_identifier`)")

                db.execSQL("CREATE TABLE `group2_member_table` (`bytes_owned_identity` BLOB NOT NULL, `bytes_group_identifier` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `permission_admin` INTEGER NOT NULL, `permission_remote_delete_anything` INTEGER NOT NULL, `permission_edit_or_remote_delete_own_messages` INTEGER NOT NULL, `permission_change_settings` INTEGER NOT NULL, `permission_send_message` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_group_identifier`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`, `bytes_group_identifier`) REFERENCES `group2_table`(`bytes_owned_identity`, `bytes_group_identifier`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`, `bytes_contact_identity`) REFERENCES `contact_table`(`bytes_owned_identity`, `bytes_contact_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX `index_group2_member_table_bytes_owned_identity` ON `group2_member_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX `index_group2_member_table_bytes_owned_identity_bytes_group_identifier` ON `group2_member_table` (`bytes_owned_identity`, `bytes_group_identifier`)")
                db.execSQL("CREATE INDEX `index_group2_member_table_bytes_owned_identity_bytes_contact_identity` ON `group2_member_table` (`bytes_owned_identity`, `bytes_contact_identity`)")


                // add the GROUP_MEMBERS_NAMES column to legacy groups table
                db.execSQL("ALTER TABLE `group_table` ADD COLUMN `group_members_names` TEXT NOT NULL DEFAULT ''")

                db.query("SELECT `bytes_owned_identity`, `bytes_group_owner_and_uid` FROM `group_table`")
                    .use { cursor ->
                        val joiner =
                            App.getContext().getString(R.string.text_contact_names_separator)
                        while (cursor.moveToNext()) {
                            val bytesOwnedIdentity = cursor.getBlob(0)
                            val bytesGroupOwnerAndUid = cursor.getBlob(1)
                            db.query(
                                "SELECT GROUP_CONCAT(CASE WHEN c.custom_display_name IS NULL THEN c.display_name ELSE c.custom_display_name END, ?) " +
                                        " FROM `contact_table` AS c " +
                                        " INNER JOIN `contact_group_join` AS cgj " +
                                        " ON c.bytes_owned_identity = cgj.bytes_owned_identity" +
                                        " AND c.bytes_contact_identity = cgj.bytes_contact_identity" +
                                        " WHERE cgj.bytes_owned_identity = ? " +
                                        " AND cgj.bytes_group_owner_and_uid = ? " +
                                        " ORDER BY c.sort_display_name ASC ", arrayOf<Any?>(
                                    joiner,
                                    bytesOwnedIdentity,
                                    bytesGroupOwnerAndUid
                                )
                            ).use { res ->
                                if (res.moveToNext()) {
                                    var groupMembersNames = res.getString(0)
                                    if (groupMembersNames == null) {
                                        groupMembersNames = ""
                                    }
                                    db.execSQL(
                                        "UPDATE `group_table` " +
                                                " SET `group_members_names` = ? " +
                                                " WHERE `bytes_owned_identity` = ? " +
                                                " AND `bytes_group_owner_and_uid` = ? ",
                                        arrayOf<Any?>(
                                            groupMembersNames,
                                            bytesOwnedIdentity,
                                            bytesGroupOwnerAndUid
                                        )
                                    )
                                }
                            }
                        }
                    }
                // add the EXPIRATION_START_TIMESTAMP column to message table
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `expiration_start_timestamp` INTEGER NOT NULL DEFAULT 0")
            }
        },  // location messages

        object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 54 TO 55")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_location` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `location_type` INTEGER NOT NULL DEFAULT 0")
            }
        },  // custom notifications per discussions

        object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 53 TO 54")

                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_use_custom_message_notification` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_message_notification_ringtone` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_message_notification_vibration_pattern` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_message_notification_led_color` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_use_custom_call_notification` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_call_notification_ringtone` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_call_notification_vibration_pattern` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_call_notification_use_flash` INTEGER NOT NULL DEFAULT 0")
            }
        },


        object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 52 TO 53")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `forwarded` INTEGER NOT NULL DEFAULT 0")
            }
        },


        object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 51 TO 52")

                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `capability_one_to_one_contacts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `capability_one_to_one_contacts` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `one_to_one` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `trust_level` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `trust_level` INTEGER DEFAULT NULL")

                // delete all pending invitations of the categories we removed
                db.execSQL("DELETE FROM `invitation_table` WHERE `category_id` IN (9, 10, 11, 12)")
                db.execSQL("ALTER TABLE `invitation_table` ADD COLUMN `bytes_contact_identity` BLOB DEFAULT NULL")
            }
        },


        object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 50 TO 51")

                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `capability_webrtc_continuous_ice` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `capability_groups_v2` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `capability_webrtc_continuous_ice` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `capability_groups_v2` INTEGER NOT NULL DEFAULT 0")
            }
        },


        object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 49 TO 50")

                db.execSQL("CREATE TABLE IF NOT EXISTS `action_shortcut_configuration_table` (`app_widget_id` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `serialized_configuration` TEXT NOT NULL, PRIMARY KEY(`app_widget_id`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_shortcut_configuration_table_discussion_id` ON `action_shortcut_configuration_table` (`discussion_id`)")
            }
        },


        object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 48 TO 49")

                db.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `reception_status` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_message_id_engine_number` ON `fyle_message_join_with_status` (`message_id`, `engine_number`)")

                db.execSQL("ALTER TABLE `message_recipient_info_table` ADD COLUMN `undelivered_attachment_numbers` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `message_recipient_info_table` ADD COLUMN `unread_attachment_numbers` TEXT DEFAULT NULL")
            }
        },


        object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 47 TO 48")

                db.execSQL("CREATE TABLE IF NOT EXISTS `reaction_request_table` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `reacter` BLOB NOT NULL, `server_timestamp` INTEGER NOT NULL, `reaction` TEXT NOT NULL, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`, `reacter`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_discussion_id` ON `reaction_request_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_server_timestamp` ON `reaction_request_table` (`server_timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_discussion_id_sender_identifier_sender_thread_identifier_sender_sequence_number` ON `reaction_request_table` (`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`)")
            }
        },


        object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 46 TO 47")

                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `custom_display_name` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `unlock_password` BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `unlock_salt` BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_mute_notifications` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_mute_notifications_timestamp` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_show_neutral_notification_when_hidden` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("DELETE FROM `invitation_table`")
                db.execSQL("ALTER TABLE `invitation_table` ADD COLUMN `category_id` INTEGER NOT NULL DEFAULT 0")
            }
        },


        object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 45 TO 46")

                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")
            }
        },


        object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 44 TO 45")

                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `missed_message_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `latest_discussion_sender_sequence_number_table` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `latest_sequence_number` INTEGER NOT NULL, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        },


        object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 43 TO 44")

                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `personal_note` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `group_table` ADD COLUMN `personal_note` TEXT DEFAULT NULL")
            }
        },


        object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 42 TO 43")

                db.execSQL("ALTER TABLE `fyle_table` RENAME TO `old_fyle`")
                db.execSQL("DROP INDEX `index_fyle_table_sha256`")
                db.execSQL("CREATE TABLE `fyle_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `permanent_file_path` TEXT, `sha256` BLOB)")
                db.execSQL("CREATE UNIQUE INDEX `index_fyle_table_sha256` ON `fyle_table` (`sha256`)")
                db.execSQL("INSERT INTO `fyle_table` SELECT `id`, `permanent_file_path`, `sha256` FROM `old_fyle`")
                db.execSQL("DROP TABLE `old_fyle`")
            }
        },


        object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 41 TO 42")

                db.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `audio_played` INTEGER NOT NULL DEFAULT 1")
            }
        },


        object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 40 TO 41")

                db.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `mini_preview` BLOB DEFAULT NULL")
            }
        },


        object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 39 TO 40")

                db.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `image_resolution` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `image_resolutions` TEXT DEFAULT NULL")
            }
        },


        object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 38 TO 39")

                db.execSQL("CREATE TABLE IF NOT EXISTS `known_certificate` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `domain_name` TEXT NOT NULL, `certificate_bytes` BLOB NOT NULL, `trust_timestamp` INTEGER, `expiration_timestamp` INTEGER NOT NULL, `issuers` TEXT NOT NULL, `encoded_full_chain` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_certificate_domain_name` ON `known_certificate` (`domain_name`)")
            }
        },


        object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 37 TO 38")
                db.execSQL("DROP TABLE IF EXISTS `anonymous_usage_log`")
                db.execSQL("DROP TABLE IF EXISTS `anonymizer_for_owned_identity`")
            }
        },

        object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 36 TO 37")
                db.execSQL("ALTER TABLE `call_log_table` RENAME TO `old_call_log_table`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `call_log_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_group_owner_and_uid` BLOB, `timestamp` INTEGER NOT NULL, `call_type` INTEGER NOT NULL, `call_status` INTEGER NOT NULL, `duration` INTEGER NOT NULL, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP INDEX `index_call_log_table_bytes_owned_identity`")
                db.execSQL("DROP INDEX `index_call_log_table_timestamp`")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity` ON `call_log_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_timestamp` ON `call_log_table` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `call_log_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `call_log_item_contact_join` (`call_log_item_id` INTEGER NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, PRIMARY KEY(`call_log_item_id`, `bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`call_log_item_id`) REFERENCES `call_log_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`, `bytes_contact_identity`) REFERENCES `contact_table`(`bytes_owned_identity`, `bytes_contact_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_item_contact_join_call_log_item_id` ON `call_log_item_contact_join` (`call_log_item_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_item_contact_join_bytes_owned_identity_bytes_contact_identity` ON `call_log_item_contact_join` (`bytes_owned_identity`, `bytes_contact_identity`)")

                db.query("SELECT `id`, `bytes_contact_identity`, `bytes_owned_identity`, `timestamp`, `call_type`, `call_status`, `duration` FROM `old_call_log_table`")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val bytesContactIdentity = cursor.getBlob(1)
                            val bytesOwnedIdentity = cursor.getBlob(2)
                            val timestamp = cursor.getLong(3)
                            val callType = cursor.getInt(4)
                            val callStatus = cursor.getInt(5)
                            val duration = cursor.getInt(6)

                            db.execSQL(
                                "INSERT INTO `call_log_table` VALUES (?,?,?,?,?,?,?)",
                                arrayOf<Any?>(
                                    id,
                                    bytesOwnedIdentity,
                                    null,
                                    timestamp,
                                    callType,
                                    callStatus,
                                    duration,
                                )
                            )
                            db.execSQL(
                                "INSERT INTO `call_log_item_contact_join` VALUES (?,?,?)",
                                arrayOf<Any?>(
                                    id,
                                    bytesOwnedIdentity,
                                    bytesContactIdentity,
                                )
                            )
                        }
                    }
                db.execSQL("DROP TABLE `old_call_log_table`")
            }
        },


        object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 35 TO 36")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `custom_name_hue` INTEGER DEFAULT NULL")
            }
        },

        object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 34 TO 35")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `identity_details` TEXT DEFAULT NULL")

                db.execSQL("ALTER TABLE `contact_table` RENAME TO  `old_contact_table`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `contact_table` (`bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_display_name` TEXT, `display_name` TEXT NOT NULL, `sort_display_name` BLOB NOT NULL, `full_search_display_name` TEXT NOT NULL, `identity_details` TEXT, `new_published_details` INTEGER NOT NULL, `device_count` INTEGER NOT NULL, `established_channel_count` INTEGER NOT NULL, `photo_url` TEXT, `custom_photo_url` TEXT, `keycloak_managed` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.query("SELECT `bytes_contact_identity`, `bytes_owned_identity`, `custom_display_name`, `display_name`, `identity_details`, `new_published_details`, `device_count`, `established_channel_count`, `photo_url`, `custom_photo_url`, `keycloak_managed` FROM `old_contact_table`")
                    .use { cursor ->
                        db.execSQL("DROP INDEX `index_contact_table_bytes_owned_identity`")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)")
                        db.execSQL("DROP INDEX `index_contact_table_display_name`")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_display_name` ON `contact_table` (`display_name`)")
                        db.execSQL("DROP INDEX `index_contact_table_custom_display_name`")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_sort_display_name` ON `contact_table` (`sort_display_name`)")

                        val collator = Collator.getInstance()
                        while (cursor.moveToNext()) {
                            val bytesContactIdentity = cursor.getBlob(0)
                            val bytesOwnedIdentity = cursor.getBlob(1)
                            val customDisplayName = cursor.getString(2)
                            val displayName = cursor.getString(3)
                            val identityDetails = cursor.getString(4)
                            val newPublishedDetails = cursor.getInt(5)
                            val deviceCount = cursor.getInt(6)
                            val establishedChannelCount = cursor.getInt(7)
                            val photoUrl = cursor.getString(8)
                            val customPhotoUrl = cursor.getString(9)
                            val keycloakManaged = cursor.getInt(10)

                            var sortDisplayName: ByteArray?
                            var fullSearchDisplayName: String?
                            if (customDisplayName != null) {
                                sortDisplayName =
                                    collator.getCollationKey(customDisplayName).toByteArray()
                                fullSearchDisplayName =
                                    StringUtils.unAccent("$customDisplayName $displayName")
                            } else {
                                sortDisplayName =
                                    collator.getCollationKey(displayName).toByteArray()
                                fullSearchDisplayName = StringUtils.unAccent(displayName)
                            }

                            db.execSQL(
                                "INSERT INTO `contact_table` VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?)",
                                arrayOf<Any?>(
                                    bytesContactIdentity,
                                    bytesOwnedIdentity,
                                    customDisplayName,
                                    displayName,
                                    sortDisplayName,
                                    fullSearchDisplayName,
                                    identityDetails,
                                    newPublishedDetails,
                                    deviceCount,
                                    establishedChannelCount,
                                    photoUrl,
                                    customPhotoUrl,
                                    keycloakManaged
                                )
                            )
                        }
                    }
                db.execSQL("DROP TABLE `old_contact_table`")
            }
        },

        object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 33 TO 34")
                db.execSQL("CREATE TABLE IF NOT EXISTS `remote_delete_and_edit_request_table` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `server_timestamp` INTEGER NOT NULL, `request_type` INTEGER NOT NULL, `body` TEXT, `remote_deleter` BLOB, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_delete_request_table_discussion_id` ON `remote_delete_and_edit_request_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_delete_request_table_server_timestamp` ON `remote_delete_and_edit_request_table` (`server_timestamp`)")
            }
        },

        object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 32 TO 33")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `keycloak_managed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `keycloak_managed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `keycloak_managed` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 31 TO 32")
                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `unread` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 30 TO 31")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `reactions` TEXT DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reactions_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `bytes_identity` BLOB, `emoji` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reactions_table_message_id` ON `reactions_table` (`message_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reactions_table_message_id_bytes_identity` ON `reactions_table` (`message_id`, `bytes_identity`)")
            }
        },

        object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 29 TO 30")
                db.execSQL("ALTER TABLE `message_metadata_table` ADD COLUMN `bytes_remote_identity` BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `edited` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 28 TO 29")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_mute_notifications_timestamp` BIGINT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_discussion_retention_count` BIGINT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_discussion_retention_duration` BIGINT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `shared_settings_version` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `setting_existence_duration` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `setting_visibility_duration` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `setting_read_once` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 27 TO 28")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_expiration` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `wipe_status` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `wiped_attachment_count` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_auto_open_limited_visibility_inbound` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_retain_wiped_outbound_messages` INTEGER DEFAULT NULL")

                db.execSQL("CREATE TABLE IF NOT EXISTS `message_metadata_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_message_id` ON `message_metadata_table` (`message_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_message_id_kind` ON `message_metadata_table` (`message_id`, `kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_timestamp` ON `message_metadata_table` (`timestamp`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `message_expiration_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `expiration_timestamp` INTEGER NOT NULL, `wipe_only` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_expiration_table_message_id` ON `message_expiration_table` (`message_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_expiration_table_expiration_timestamp` ON `message_expiration_table` (`expiration_timestamp`)")
            }
        },

        object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 26 TO 27")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `api_key_permissions` BIGINT NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `api_key_expiration_timestamp` BIGINT DEFAULT NULL")
            }
        },

        object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 25 TO 26")
                db.execSQL("ALTER TABLE `message_recipient_info_table` ADD COLUMN `unsent_attachment_numbers` TEXT DEFAULT NULL")
            }
        },

        object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 24 TO 25")
                db.execSQL("CREATE TABLE IF NOT EXISTS `call_log_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, `call_type` INTEGER NOT NULL, `call_status` INTEGER NOT NULL, `duration` INTEGER NOT NULL, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`, `bytes_contact_identity`) REFERENCES `contact_table`(`bytes_owned_identity`, `bytes_contact_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity` ON `call_log_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity_bytes_contact_identity` ON `call_log_table` (`bytes_owned_identity`, `bytes_contact_identity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_timestamp` ON `call_log_table` (`timestamp`)")
            }
        },

        object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 23 TO 24")

                var pattern = Pattern.compile("^.*/(discussion_backgrounds/[0-9]+-[0-9]+)$")
                db.query("SELECT `discussion_id`, `background_image_url` FROM `discussion_customization_table`")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val discussionId = cursor.getLong(0)
                            val imageUrl = cursor.getString(1)
                            if (imageUrl != null) {
                                val m = pattern.matcher(imageUrl)
                                var newUrl: String? = null
                                if (m.find()) {
                                    newUrl = m.group(1)
                                }
                                db.execSQL(
                                    "UPDATE `discussion_customization_table` SET `background_image_url` = ? WHERE `discussion_id` = ?",
                                    arrayOf<Any?>(newUrl, discussionId)
                                )
                            }
                        }
                    }
                pattern = Pattern.compile("^.*/(fyles/[0-9A-F]+)$")
                db.query("SELECT `sha256`, `permanent_file_path` FROM `fyle_table`")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val sha256 = cursor.getBlob(0)
                            val filePath = cursor.getString(1)
                            if (filePath != null) {
                                val m = pattern.matcher(filePath)
                                var newPath: String? = null
                                if (m.find()) {
                                    newPath = m.group(1)
                                }
                                db.execSQL(
                                    "UPDATE `fyle_table` SET `permanent_file_path` = ? WHERE `sha256` = ?",
                                    arrayOf<Any?>(newPath, sha256)
                                )
                            }
                        }
                    }
                pattern = Pattern.compile("^.*/(fyles/[0-9A-F]+)$")
                val patternIncomplete =
                    Pattern.compile("^.*/inbound_attachments/(.+)@([0-9A-F]+)-([0-9A-F]+)-([0-9A-F]+/[0-9]+)$")
                val sha256 = Suite.getHash(Hash.SHA256)
                db.query("SELECT `fyle_id`, `message_id`, `file_path` FROM `fyle_message_join_with_status`")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val fileId = cursor.getLong(0)
                            val messageId = cursor.getLong(1)
                            val filePath = cursor.getString(2)
                            var m = pattern.matcher(filePath)
                            if (m.find()) {
                                val newPath = m.group(1)
                                db.execSQL(
                                    "UPDATE `fyle_message_join_with_status` SET `file_path` = ? WHERE `fyle_id` = ? AND `message_id` = ?",
                                    arrayOf<Any?>(newPath, fileId, messageId)
                                )
                            } else {
                                m = patternIncomplete.matcher(filePath)
                                if (m.find()) {
                                    try {
                                        val serverBytes =
                                            (m.group(1).replace("https:/", "https://")).toByteArray(
                                                StandardCharsets.UTF_8
                                            )
                                        val compactAuthKey = Logger.fromHexString(m.group(2))
                                        val compactEncKey = Logger.fromHexString(m.group(3))
                                        val suffix = m.group(4)

                                        val identityBytes =
                                            ByteArray(serverBytes.size + 1 + compactAuthKey.size + compactEncKey.size)
                                        System.arraycopy(
                                            serverBytes,
                                            0,
                                            identityBytes,
                                            0,
                                            serverBytes.size
                                        )
                                        identityBytes[serverBytes.size] = 0x00.toByte()
                                        System.arraycopy(
                                            compactAuthKey,
                                            0,
                                            identityBytes,
                                            serverBytes.size + 1,
                                            compactAuthKey.size
                                        )
                                        System.arraycopy(
                                            compactEncKey,
                                            0,
                                            identityBytes,
                                            serverBytes.size + 1 + compactAuthKey.size,
                                            compactEncKey.size
                                        )
                                        val uid = sha256.digest(identityBytes)
                                        val newPath =
                                            "inbound_attachments/" + Logger.toHexString(uid) + "-" + suffix

                                        db.execSQL(
                                            "UPDATE `fyle_message_join_with_status` SET `file_path` = ? WHERE `fyle_id` = ? AND `message_id` = ?",
                                            arrayOf<Any?>(newPath, fileId, messageId)
                                        )
                                    } catch (e: Exception) {
                                        db.execSQL(
                                            "DELETE FROM `fyle_message_join_with_status` WHERE `fyle_id` = ? AND `message_id` = ?",
                                            arrayOf<Any?>(fileId, messageId)
                                        )
                                    }
                                } else {
                                    db.execSQL(
                                        "DELETE FROM `fyle_message_join_with_status` WHERE `fyle_id` = ? AND `message_id` = ?",
                                        arrayOf<Any?>(fileId, messageId)
                                    )
                                }
                            }
                        }
                    }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_discussion_customization_table_discussion_id` ON `discussion_customization_table` (`discussion_id`)")
            }
        },

        object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 22 TO 23")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `custom_photo_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `group_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `group_table` ADD COLUMN `custom_photo_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_mute_notifications` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 21 TO 22")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_return_receipt` TEXT DEFAULT NULL")
            }
        },

        object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 20 TO 21")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `background_image_url` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_send_read_receipt` INTEGER DEFAULT NULL")
            }
        },

        object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 19 TO 20")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_recipient_info_table` (`message_id` INTEGER NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `return_receipt_nonce` BLOB, `return_receipt_key` BLOB, `engine_message_identifier` BLOB, `timestamp_sent` INTEGER, `timestamp_delivered` INTEGER, `timestamp_read` INTEGER, PRIMARY KEY(`message_id`, `bytes_contact_identity`), FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE  INDEX `index_message_recipient_info_table_engine_message_identifier` ON `message_recipient_info_table` (`engine_message_identifier`)")
                db.execSQL("CREATE  INDEX `index_message_recipient_info_table_bytes_contact_identity` ON `message_recipient_info_table` (`bytes_contact_identity`)")
                db.execSQL("CREATE  INDEX `index_message_recipient_info_table_message_id` ON `message_recipient_info_table` (`message_id`)")
                db.execSQL("CREATE  INDEX `index_message_recipient_info_table_return_receipt_nonce` ON `message_recipient_info_table` (`return_receipt_nonce`)")
                db.execSQL("CREATE  INDEX `index_message_recipient_info_table_message_id_bytes_contact_identity` ON `message_recipient_info_table` (`message_id`, `bytes_contact_identity`)")
            }
        },

        object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 18 TO 19")
                db.execSQL("ALTER TABLE discussion_table ADD COLUMN last_message_timestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE discussion_table SET last_message_timestamp = IFNULL((SELECT MAX(timestamp) FROM message_table WHERE message_table.discussion_id = discussion_table.id), 0)")
            }
        },

        object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 17 TO 18")
                db.execSQL("ALTER TABLE message_table RENAME TO old_message_table")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `content_body` TEXT, `json_reply` TEXT, `sort_index` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `message_type` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `engine_message_identifier` BLOB, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `total_attachment_count` INTEGER NOT NULL, `image_count` INTEGER NOT NULL, FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.execSQL("DROP INDEX `index_message_table_discussion_id`")
                db.execSQL("DROP INDEX `index_message_table_engine_message_identifier`")
                db.execSQL("DROP INDEX `index_message_table_sort_index`")
                db.execSQL("DROP INDEX `index_message_table_timestamp`")
                db.execSQL("DROP INDEX `index_message_table_message_type_status`")
                db.execSQL("DROP INDEX `index_message_table_discussion_id_status`")
                db.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`")

                db.execSQL("CREATE INDEX `index_message_table_discussion_id` ON `message_table` (`discussion_id`)")
                db.execSQL("CREATE INDEX `index_message_table_engine_message_identifier` ON `message_table` (`engine_message_identifier`)")
                db.execSQL("CREATE INDEX `index_message_table_sort_index` ON `message_table` (`sort_index`)")
                db.execSQL("CREATE INDEX `index_message_table_timestamp` ON `message_table` (`timestamp`)")
                db.execSQL("CREATE INDEX `index_message_table_message_type_status` ON `message_table` (`message_type`, `status`)")
                db.execSQL("CREATE INDEX `index_message_table_discussion_id_status` ON `message_table` (`discussion_id`, `status`)")
                db.execSQL("CREATE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)")

                db.execSQL(
                    "INSERT INTO message_table " +
                            " SELECT m.id, m.sender_sequence_number, m.content_body, m.json_reply, m.sort_index, m.timestamp, m.status, m.message_type, m.discussion_id, m.engine_message_identifier, m.sender_identifier, m.sender_thread_identifier, IFNULL(attachment_count,0), IFNULL(image_count,0) " +
                            " FROM old_message_table AS m " +
                            " LEFT JOIN (SELECT message_id AS mid_un, COUNT(*) AS attachment_count FROM fyle_message_join_with_status GROUP BY message_id)" +
                            " ON m.id = mid_un" +
                            " LEFT JOIN (SELECT message_id AS mid_deux, COUNT(*) AS image_count FROM fyle_message_join_with_status WHERE file_type LIKE 'image/%' OR file_type LIKE 'video/%' GROUP BY message_id)" +
                            " ON m.id = mid_deux"
                )

                db.execSQL("DROP TABLE old_message_table")
            }
        },

        object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 16 TO 17")
                db.execSQL("CREATE TABLE IF NOT EXISTS `discussion_customization_table` (`discussion_id` INTEGER NOT NULL, `serialized_color_json` TEXT, PRIMARY KEY(`discussion_id`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        },

        object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 15 TO 16")
                db.execSQL("ALTER TABLE discussion_table RENAME TO old_discussion_table")
                db.execSQL("CREATE TABLE IF NOT EXISTS `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `bytes_group_owner_and_uid` BLOB, `bytes_contact_identity` BLOB, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

                db.execSQL("DROP INDEX `index_discussion_table_bytes_owned_identity`")
                db.execSQL("DROP INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity`")
                db.execSQL("DROP INDEX `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity`")
                db.execSQL("DROP INDEX `index_discussion_table_title`")

                db.execSQL("CREATE  INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity` ON `discussion_table` (`bytes_contact_identity`, `bytes_owned_identity`)")
                db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `discussion_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_discussion_table_title` ON `discussion_table` (`title`)")

                db.execSQL("INSERT INTO discussion_table SELECT * FROM old_discussion_table")
                db.execSQL("DROP TABLE old_discussion_table")
            }
        },

        object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 14 TO 15")
                db.execSQL("ALTER TABLE identity_table ADD COLUMN unpublished_details INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 13 TO 14")
                db.execSQL("ALTER TABLE group_table RENAME TO old_group_table")
                db.execSQL("CREATE TABLE IF NOT EXISTS `group_table` (`bytes_group_owner_and_uid` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_name` TEXT, `name` TEXT NOT NULL, `new_published_details` INTEGER NOT NULL, `bytes_group_owner_identity` BLOB, PRIMARY KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

                db.execSQL("DROP  INDEX `index_group_table_name`")
                db.execSQL("DROP  INDEX `index_group_table_bytes_owned_identity`")
                db.execSQL("DROP  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity`")

                db.execSQL("CREATE  INDEX `index_group_table_name` ON `group_table` (`name`)")
                db.execSQL("CREATE  INDEX `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)")

                db.execSQL("INSERT INTO `group_table` SELECT bytes_group_owner_and_uid, bytes_owned_identity, custom_name, name, new_published_details, bytes_group_owner_identity  FROM  `old_group_table`")

                db.execSQL("DROP TABLE `old_group_table`")
            }
        },

        object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 12 TO 13")

                // groups
                db.execSQL("ALTER TABLE group_table RENAME TO old_group_table")
                db.execSQL("CREATE TABLE IF NOT EXISTS `group_table` (`bytes_group_owner_and_uid` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_name` TEXT, `name` TEXT NOT NULL, `new_published_details` INTEGER NOT NULL, `bytes_group_owner_identity` BLOB, `leaving` INTEGER NOT NULL, PRIMARY KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

                db.query("SELECT `group_id`, `bytes_owned_identity`, `name`, `bytes_group_owner_identity` FROM `old_group_table`")
                    .use { cursor ->
                        db.execSQL("DROP  INDEX `index_group_table_name`")
                        db.execSQL("DROP  INDEX `index_group_table_bytes_owned_identity`")
                        db.execSQL("DROP  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity`")

                        db.execSQL("CREATE  INDEX `index_group_table_name` ON `group_table` (`name`)")
                        db.execSQL("CREATE  INDEX `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)")
                        db.execSQL("CREATE  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)")
                        while (cursor.moveToNext()) {
                            val groupId = cursor.getBlob(0)
                            val bytesOwnedIdentity = cursor.getBlob(1)
                            val name = cursor.getString(2)
                            val bytesGroupOwnerIdentity = cursor.getBlob(3)

                            db.execSQL(
                                "INSERT INTO `group_table` VALUES (?,?,?,?,?,?,?)", arrayOf<Any?>(
                                    groupId,
                                    bytesOwnedIdentity,
                                    null,
                                    name,
                                    0,
                                    bytesGroupOwnerIdentity,
                                    0,
                                )
                            )
                        }
                    }
                // contact group join
                db.execSQL("ALTER TABLE contact_group_join RENAME TO old_contact_group_join")
                db.execSQL("CREATE TABLE IF NOT EXISTS `contact_group_join` (`bytes_group_owner_and_uid` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.query("SELECT `group_id`, `bytes_contact_identity`, `bytes_owned_identity`, `timestamp` FROM `old_contact_group_join`")
                    .use { cursor ->
                        db.execSQL("DROP  INDEX `index_contact_group_join_group_id_bytes_owned_identity`")
                        db.execSQL("DROP  INDEX `index_contact_group_join_bytes_contact_identity_bytes_owned_identity`")

                        db.execSQL("CREATE  INDEX `index_contact_group_join_bytes_group_owner_and_uid_bytes_owned_identity` ON `contact_group_join` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                        db.execSQL("CREATE  INDEX `index_contact_group_join_bytes_contact_identity_bytes_owned_identity` ON `contact_group_join` (`bytes_contact_identity`, `bytes_owned_identity`)")
                        while (cursor.moveToNext()) {
                            val groupId = cursor.getBlob(0)
                            val bytesContactIdentity = cursor.getBlob(1)
                            val bytesOwnedIdentity = cursor.getBlob(2)
                            val timestamp = cursor.getLong(3)

                            db.execSQL(
                                "INSERT INTO `contact_group_join` VALUES (?,?,?,?)", arrayOf<Any?>(
                                    groupId,
                                    bytesContactIdentity,
                                    bytesOwnedIdentity,
                                    timestamp
                                )
                            )
                        }
                    }
                // pending group members
                db.execSQL("DROP TABLE pending_group_member_table")

                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_group_member_table` (`bytes_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_group_owner_and_uid` BLOB NOT NULL, `declined` INTEGER NOT NULL, PRIMARY KEY(`bytes_identity`, `bytes_owned_identity`, `bytes_group_owner_and_uid`), FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE  INDEX `index_pending_group_member_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `pending_group_member_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")


                // discussion
                db.execSQL("ALTER TABLE discussion_table RENAME TO old_discussion_table")
                db.execSQL("CREATE TABLE IF NOT EXISTS `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `bytes_group_owner_and_uid` BLOB, `bytes_contact_identity` BLOB, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

                db.query("SELECT `id`, `title`, `bytes_owned_identity`, `sender_thread_identifier`, `last_outbound_message_sequence_number`, `group_id`, `bytes_contact_identity` FROM `old_discussion_table`")
                    .use { cursor ->
                        db.execSQL("DROP  INDEX `index_discussion_table_bytes_owned_identity`")
                        db.execSQL("DROP  INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity`")
                        db.execSQL("DROP  INDEX `index_discussion_table_group_id_bytes_owned_identity`")
                        db.execSQL("DROP  INDEX `index_discussion_table_title`")

                        db.execSQL("CREATE  INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)")
                        db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity` ON `discussion_table` (`bytes_contact_identity`, `bytes_owned_identity`)")
                        db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `discussion_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)")
                        db.execSQL("CREATE  INDEX `index_discussion_table_title` ON `discussion_table` (`title`)")
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val title = cursor.getString(1)
                            val bytesOwnedIdentity = cursor.getBlob(2)
                            val senderThreadIdentifier = cursor.getString(3)
                            val lastOutbound = cursor.getLong(4)
                            val groupId = cursor.getBlob(5)
                            val bytesContactIdentity = cursor.getBlob(6)

                            if (groupId != null) {
                                db.execSQL(
                                    "INSERT INTO `discussion_table` VALUES (?,?,?,?,?,?,?)",
                                    arrayOf<Any?>(
                                        id,
                                        title,
                                        bytesOwnedIdentity,
                                        senderThreadIdentifier,
                                        lastOutbound,
                                        groupId,
                                        bytesContactIdentity,
                                    )
                                )
                            } else {
                                db.execSQL(
                                    "INSERT INTO `discussion_table` VALUES (?,?,?,?,?,?,?)",
                                    arrayOf<Any?>(
                                        id,
                                        title,
                                        bytesOwnedIdentity,
                                        senderThreadIdentifier,
                                        lastOutbound,
                                        null,
                                        bytesContactIdentity,
                                    )
                                )
                            }
                        }
                    }
                // clear invitations
                db.execSQL("DELETE FROM `invitation_table`")


                db.execSQL("DROP TABLE `old_discussion_table`")
                db.execSQL("DROP TABLE `old_contact_group_join`")
                db.execSQL("DROP TABLE `old_group_table`")
            }
        },

        object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 11 TO 12")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `new_published_details` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 10 TO 11")
                db.execSQL("ALTER TABLE `contact_table` RENAME TO `old_contact_table`")
                db.execSQL("ALTER TABLE `invitation_table` RENAME TO `old_invitation_table`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `contact_table` (`bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_display_name` TEXT, `display_name` TEXT NOT NULL, `identity_details` TEXT NOT NULL, `device_count` INTEGER NOT NULL, `established_channel_count` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP  INDEX `index_contact_table_bytes_owned_identity`")
                db.execSQL("DROP  INDEX `index_contact_table_display_name`")
                db.execSQL("DROP  INDEX `index_contact_table_custom_display_name`")
                db.execSQL("CREATE  INDEX `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_contact_table_display_name` ON `contact_table` (`display_name`)")
                db.execSQL("CREATE  INDEX `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)")
                db.execSQL("INSERT INTO `contact_table` SELECT `bytes_contact_identity`, `bytes_owned_identity`, `custom_display_name`, `display_name`, ('{\"first_name\":\"' || `display_name` || '\"}'), `device_count`, `established_channel_count` FROM `old_contact_table`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `invitation_table` (`dialog_uuid` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `associated_dialog` BLOB NOT NULL, `invitation_timestamp` INTEGER NOT NULL, PRIMARY KEY(`dialog_uuid`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP  INDEX `index_invitation_table_bytes_owned_identity`")
                db.execSQL("CREATE  INDEX `index_invitation_table_bytes_owned_identity` ON `invitation_table` (`bytes_owned_identity`)")

                db.execSQL("DROP TABLE `old_contact_table`")
                db.execSQL("DROP TABLE `old_invitation_table`")
            }
        },

        object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 9 TO 10")
                db.execSQL("ALTER TABLE `contact_table` ADD COLUMN `custom_display_name` TEXT DEFAULT NULL")
                db.execSQL("CREATE  INDEX `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)")
            }
        },

        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 8 TO 9")
                db.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_reply` TEXT DEFAULT NULL")
            }
        },

        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 7 TO 8")

                db.execSQL("ALTER TABLE `identity_table` RENAME TO  `old_identity_table`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `identity_table` (`bytes_owned_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `api_key_status` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`))")
                db.execSQL("INSERT INTO `identity_table` SELECT `bytes_owned_identity`, `display_name`, `api_key_status` FROM `old_identity_table`")
                db.execSQL("DROP TABLE `old_identity_table`")
            }
        },

        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 6 TO 7")
                // contacts
                db.execSQL("ALTER TABLE `contact_table` RENAME TO  `old_contact_table`;")
                db.execSQL("CREATE TABLE IF NOT EXISTS `contact_table` (`bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `device_count` INTEGER NOT NULL, `established_channel_count` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP INDEX `index_contact_table_direct_discussion_id`")
                db.execSQL("DROP INDEX `index_contact_table_bytes_owned_identity`")
                db.execSQL("CREATE  INDEX `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_contact_table_display_name` ON `contact_table` (`display_name`)")
                db.execSQL("INSERT INTO `contact_table` SELECT `bytes_identity`, `bytes_owned_identity`, `display_name`, `device_count`, `established_channel_count` FROM `old_contact_table`")

                // group
                db.execSQL("CREATE TABLE IF NOT EXISTS `group_table` (`group_id` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `name` TEXT NOT NULL, `bytes_group_owner_identity` BLOB, PRIMARY KEY(`group_id`, `bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
                db.execSQL("CREATE  INDEX `index_group_table_name` ON `group_table` (`name`)")
                db.execSQL("CREATE  INDEX `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)")
                db.query("SELECT `group_id`, `bytes_owned_identity`, `title` FROM `discussion_table` WHERE `group_id` NOT NULL")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val groupId = cursor.getBlob(0)
                            val bytesOwnedIdentity = cursor.getBlob(1)
                            val name = cursor.getString(2)
                            var bytesGroupOwnerIdentity: ByteArray? =
                                Arrays.copyOfRange(groupId, 0, groupId.size - 32)
                            db.query(
                                "SELECT * FROM `contact_table` WHERE `bytes_contact_identity` = ? AND `bytes_owned_identity` = ?",
                                arrayOf<Any?>(bytesGroupOwnerIdentity, bytesOwnedIdentity)
                            ).use { cursor1 ->
                                if (!cursor1.moveToFirst()) {
                                    bytesGroupOwnerIdentity = null
                                }
                            }
                            db.execSQL(
                                "INSERT INTO `group_table` VALUES (?,?,?,?)", arrayOf<Any?>(
                                    groupId,
                                    bytesOwnedIdentity,
                                    name,
                                    bytesGroupOwnerIdentity
                                )
                            )
                        }
                    }
                // contact group join
                db.execSQL("CREATE TABLE IF NOT EXISTS `contact_group_join` (`group_id` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`group_id`, `bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`group_id`, `bytes_owned_identity`) REFERENCES `group_table`(`group_id`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE  INDEX `index_contact_group_join_group_id_bytes_owned_identity` ON `contact_group_join` (`group_id`, `bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_contact_group_join_bytes_contact_identity_bytes_owned_identity` ON `contact_group_join` (`bytes_contact_identity`, `bytes_owned_identity`)")
                db.execSQL("INSERT INTO `contact_group_join` SELECT discussion.`group_id`, `contact_bytes_identity`, `contact_bytes_owned_identity`, `timestamp` FROM `contact_discussion_join` INNER JOIN `discussion_table` AS discussion ON discussion.id = `discussion_id` WHERE discussion.`group_id` NOT NULL")

                // pending group member
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_group_member_table` (`bytes_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `group_id` BLOB NOT NULL, PRIMARY KEY(`bytes_identity`, `bytes_owned_identity`, `group_id`), FOREIGN KEY(`group_id`, `bytes_owned_identity`) REFERENCES `group_table`(`group_id`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE  INDEX `index_pending_group_member_table_group_id_bytes_owned_identity` ON `pending_group_member_table` (`group_id`, `bytes_owned_identity`)")

                // discussion
                db.execSQL("ALTER TABLE `discussion_table` RENAME TO  `old_discussion_table`;")
                db.execSQL("CREATE TABLE IF NOT EXISTS `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `group_id` BLOB, `bytes_contact_identity` BLOB, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`group_id`, `bytes_owned_identity`) REFERENCES `group_table`(`group_id`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE SET NULL )")
                db.execSQL("DROP INDEX `index_discussion_table_bytes_owned_identity`")
                db.execSQL("DROP INDEX `index_discussion_table_group_id`")
                db.execSQL("DROP INDEX `index_discussion_table_title`")
                db.execSQL("CREATE  INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)")
                db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity` ON `discussion_table` (`bytes_contact_identity`, `bytes_owned_identity`)")
                db.execSQL("CREATE UNIQUE INDEX `index_discussion_table_group_id_bytes_owned_identity` ON `discussion_table` (`group_id`, `bytes_owned_identity`)")
                db.execSQL("CREATE  INDEX `index_discussion_table_title` ON `discussion_table` (`title`)")
                db.execSQL("INSERT INTO `discussion_table` SELECT `id`, `title`, `bytes_owned_identity`, `own_sender_identifier`, `last_outbound_message_sequence_number`, `group_id`, NULL FROM `old_discussion_table` WHERE `group_id` NOT NULL")
                db.execSQL("INSERT INTO `discussion_table` SELECT `id`, `title`, `bytes_owned_identity`, `own_sender_identifier`, `last_outbound_message_sequence_number`, NULL, `contact_bytes_identity` FROM `old_discussion_table` INNER JOIN `contact_discussion_join` ON `id` = `discussion_id` WHERE `group_id` IS NULL")

                // fyle_message_join_with_status
                db.execSQL("ALTER TABLE `fyle_message_join_with_status` RENAME TO  `old_fyle_message_join_with_status`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `fyle_message_join_with_status` (`fyle_id` INTEGER NOT NULL, `message_id` INTEGER NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `file_path` TEXT NOT NULL, `file_name` TEXT NOT NULL, `file_type` TEXT, `status` INTEGER NOT NULL, `size` INTEGER NOT NULL, `progress` REAL NOT NULL, `engine_message_identifier` BLOB, `engine_number` INTEGER, PRIMARY KEY(`fyle_id`, `message_id`), FOREIGN KEY(`fyle_id`) REFERENCES `fyle_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
                db.execSQL("DROP INDEX `index_fyle_message_join_with_status_fyle_id`")
                db.execSQL("DROP INDEX `index_fyle_message_join_with_status_message_id`")
                db.execSQL("DROP INDEX `index_fyle_message_join_with_status_engine_message_identifier_engine_number`")
                db.execSQL("DROP INDEX `index_fyle_message_join_with_status_bytes_owned_identity`")
                db.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_fyle_id` ON `fyle_message_join_with_status` (`fyle_id`)")
                db.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_message_id` ON `fyle_message_join_with_status` (`message_id`)")
                db.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_engine_message_identifier_engine_number` ON `fyle_message_join_with_status` (`engine_message_identifier`, `engine_number`)")
                db.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_bytes_owned_identity` ON `fyle_message_join_with_status` (`bytes_owned_identity`)")
                db.execSQL("INSERT INTO `fyle_message_join_with_status` SELECT * FROM  `old_fyle_message_join_with_status`")

                // invitation
                db.execSQL("ALTER TABLE `invitation_table` RENAME TO `old_invitation_table`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `invitation_table` (`dialog_uuid` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `associated_dialog` BLOB NOT NULL, `contact_identity` BLOB, `progress` INTEGER NOT NULL, `invitation_timestamp` INTEGER NOT NULL, PRIMARY KEY(`dialog_uuid`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP INDEX `index_invitation_table_bytes_owned_identity`")
                db.execSQL("CREATE  INDEX `index_invitation_table_bytes_owned_identity` ON `invitation_table` (`bytes_owned_identity`)")
                db.execSQL("INSERT INTO `invitation_table` SELECT * FROM  `old_invitation_table`")

                // identity_table
                db.execSQL("ALTER TABLE `identity_table` RENAME TO `old_identity_table`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `identity_table` (`bytes_owned_identity` BLOB NOT NULL, `obv_owned_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `api_key_status` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`))")
                db.execSQL("INSERT INTO `identity_table` SELECT * FROM  `old_identity_table`")

                // anonymizer
                db.execSQL("ALTER TABLE `anonymizer_for_owned_identity` RENAME TO `old_anonymizer_for_owned_identity`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `anonymizer_for_owned_identity` (`bytes_owned_identity` BLOB NOT NULL, `pseudo` TEXT NOT NULL, PRIMARY KEY(`bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `anonymizer_for_owned_identity` SELECT * FROM `old_anonymizer_for_owned_identity`")

                // message
                db.execSQL("CREATE  INDEX `index_message_table_discussion_id_status` ON `message_table` (`discussion_id`, `status`)")

                db.execSQL("DROP TABLE `old_contact_table`")
                db.execSQL("DROP TABLE `contact_discussion_join`")
                db.execSQL("DROP TABLE `old_discussion_table`")
                db.execSQL("DROP TABLE `old_fyle_message_join_with_status`")
                db.execSQL("DROP TABLE `old_invitation_table`")
                db.execSQL("DROP TABLE `old_identity_table`")
                db.execSQL("DROP TABLE `old_anonymizer_for_owned_identity`")
            }
        },

        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 5 TO 6")
                db.execSQL("CREATE INDEX `index_discussion_table_group_id` ON `discussion_table` (`group_id`)")
                db.execSQL("CREATE INDEX `index_discussion_table_title` ON `discussion_table` (`title`)")
                db.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`")
                db.execSQL("CREATE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)")
                db.execSQL("CREATE INDEX `index_message_table_message_type_status` ON `message_table` (`message_type`, `status`)")
                db.execSQL("CREATE INDEX `index_message_table_timestamp` ON `message_table` (`timestamp`)")
                db.execSQL("CREATE INDEX `index_message_table_sort_index` ON `message_table` (`sort_index`)")
            }
        },

        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 4 TO 5")
                db.execSQL("ALTER TABLE `message_table` RENAME TO  `old_message_table`;")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `content_body` TEXT, `sort_index` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `message_type` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `engine_message_identifier` BLOB, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `has_attachments` INTEGER NOT NULL, FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("DROP INDEX `index_message_table_discussion_id`")
                db.execSQL("CREATE INDEX `index_message_table_discussion_id` ON `message_table` (`discussion_id`)")
                db.execSQL("DROP INDEX `index_message_table_engine_message_identifier_engine_number`")
                db.execSQL("CREATE INDEX `index_message_table_engine_message_identifier` ON `message_table` (`engine_message_identifier`)")
                db.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`")
                db.execSQL("CREATE UNIQUE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)")
                db.execSQL("INSERT INTO `message_table` SELECT `id`, `sender_sequence_number`, `json_body`, `sort_index`, `timestamp`, `status`, `message_type`, `discussion_id`, `engine_message_identifier`, `sender_identifier`, `sender_thread_identifier`, `has_attachments` FROM `old_message_table`")
                db.execSQL("DROP TABLE `old_message_table`")
            }
        },

        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 3 TO 4")
                db.execSQL("ALTER TABLE `anonymous_usage_log` ADD COLUMN `api_key` TEXT NOT NULL DEFAULT ''")
            }
        },

        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 2 TO 3")
                db.execSQL("ALTER TABLE `identity_table` ADD COLUMN `api_key_status` INTEGER NOT NULL DEFAULT 0")
            }
        },

        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Logger.w("ROOM MIGRATING FROM VERSION 1 TO 2")
                db.execSQL("ALTER TABLE `message_table` RENAME TO  `old_message_table`;")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `json_body` TEXT NOT NULL, `sort_index` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `message_type` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `engine_message_identifier` BLOB, `engine_number` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `has_attachments` INTEGER NOT NULL, FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("DROP INDEX `index_message_table_discussion_id`")
                db.execSQL("CREATE INDEX `index_message_table_discussion_id` ON `message_table` (`discussion_id`)")
                db.execSQL("DROP INDEX `index_message_table_engine_message_identifier_engine_number`")
                db.execSQL("CREATE INDEX `index_message_table_engine_message_identifier_engine_number` ON `message_table` (`engine_message_identifier`, `engine_number`)")
                db.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`")
                db.execSQL("CREATE UNIQUE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)")
                db.execSQL("INSERT INTO `message_table` SELECT `id`, `sender_sequence_number`, `json_body`, `sort_index`, `timestamp`, `status`, `is_outbound`, `discussion_id`, `engine_message_identifier`, `engine_number`, `sender_identifier`, `sender_thread_identifier`, `has_attachments` FROM `old_message_table`")
                db.execSQL("DROP TABLE `old_message_table`")
            }
        },
    )
}
