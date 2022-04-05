/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.annotation.NonNull;

import io.olvid.messenger.App;
import io.olvid.messenger.databases.dao.ActionShortcutConfigurationDao;
import io.olvid.messenger.databases.dao.CallLogItemDao;
import io.olvid.messenger.databases.dao.ContactDao;
import io.olvid.messenger.databases.dao.ContactGroupJoinDao;
import io.olvid.messenger.databases.dao.DiscussionCustomizationDao;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.dao.FyleDao;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.dao.GroupDao;
import io.olvid.messenger.databases.dao.InvitationDao;
import io.olvid.messenger.databases.dao.KnownCertificateDao;
import io.olvid.messenger.databases.dao.LatestDiscussionSenderSequenceNumberDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.dao.MessageExpirationDao;
import io.olvid.messenger.databases.dao.MessageMetadataDao;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.databases.dao.PendingGroupMemberDao;
import io.olvid.messenger.databases.dao.RawDao;
import io.olvid.messenger.databases.dao.ReactionDao;
import io.olvid.messenger.databases.dao.ReactionRequestDao;
import io.olvid.messenger.databases.dao.RemoteDeleteAndEditRequestDao;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.ContactGroupJoin;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.KnownCertificate;
import io.olvid.messenger.databases.entity.LatestDiscussionSenderSequenceNumber;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;
import io.olvid.messenger.databases.entity.MessageMetadata;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.PendingGroupMember;
import io.olvid.messenger.databases.entity.Reaction;
import io.olvid.messenger.databases.entity.ReactionRequest;
import io.olvid.messenger.databases.entity.RemoteDeleteAndEditRequest;

@Database(
        entities = {
                Contact.class,
                Group.class,
                ContactGroupJoin.class,
                PendingGroupMember.class,
                Discussion.class,
                DiscussionCustomization.class,
                Fyle.class,
                FyleMessageJoinWithStatus.class,
                Invitation.class,
                Message.class,
                MessageExpiration.class,
                MessageMetadata.class,
                MessageRecipientInfo.class,
                OwnedIdentity.class,
                CallLogItem.class,
                CallLogItemContactJoin.class,
                Reaction.class,
                RemoteDeleteAndEditRequest.class,
                KnownCertificate.class,
                LatestDiscussionSenderSequenceNumber.class,
                ReactionRequest.class,
                ActionShortcutConfiguration.class,
        },
        version = AppDatabase.DB_SCHEMA_VERSION
)
@TypeConverters({ObvTypeConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    public static final int DB_SCHEMA_VERSION = 53;
    public static final String DB_FILE_NAME = "app_database";

    public abstract ContactDao contactDao();
    public abstract GroupDao groupDao();
    public abstract ContactGroupJoinDao contactGroupJoinDao();
    public abstract PendingGroupMemberDao pendingGroupMemberDao();
    public abstract DiscussionDao discussionDao();
    public abstract DiscussionCustomizationDao discussionCustomizationDao();
    public abstract FyleDao fyleDao();
    public abstract FyleMessageJoinWithStatusDao fyleMessageJoinWithStatusDao();
    public abstract InvitationDao invitationDao();
    public abstract MessageDao messageDao();
    public abstract MessageExpirationDao messageExpirationDao();
    public abstract MessageMetadataDao messageMetadataDao();
    public abstract MessageRecipientInfoDao messageRecipientInfoDao();
    public abstract OwnedIdentityDao ownedIdentityDao();
    public abstract CallLogItemDao callLogItemDao();
    public abstract RawDao rawDao();
    public abstract ReactionDao reactionDao();
    public abstract RemoteDeleteAndEditRequestDao remoteDeleteAndEditRequestDao();
    public abstract KnownCertificateDao knownCertificateDao();
    public abstract LatestDiscussionSenderSequenceNumberDao latestDiscussionSenderSequenceNumberDao();
    public abstract ReactionRequestDao reactionRequestDao();
    public abstract ActionShortcutConfigurationDao actionShortcutConfigurationDao();

    private static final RoomDatabase.Callback roomDatabaseOpenCallback = new RoomDatabase.Callback(){
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            App.runThread(new AppDatabaseOpenCallback(instance));
        }
    };


    private static volatile AppDatabase instance;

    public static AppDatabase getInstance() {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, App.absolutePathFromRelative(DB_FILE_NAME))
                            .openHelperFactory(new PragmaSQLiteOpenHelperFactory())
                            .addCallback(roomDatabaseOpenCallback)
                            .addMigrations(AppDatabaseMigrations.MIGRATIONS)
                            .build();
                }
            }
        }
        return instance;
    }
}
