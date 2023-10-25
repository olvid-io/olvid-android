/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.gallery;


import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageExpiration;

public class GalleryViewModel extends ViewModel {
    private final MutableLiveData<Long> discussionId;
    private final MutableLiveData<Long> messageId;
    private final MutableLiveData<String> ownedIdentitySortOrder;
    private final MutableLiveData<Boolean> ownedIdentitySortOrderAscending;
    private final MutableLiveData<byte[]> bytesOwnedIdentity;
    private final LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> imageAndVideoFyleAndStatusList;
    private final MutableLiveData<Integer> currentPagerPosition;
    private final LiveData<FyleMessageJoinWithStatusDao.FyleAndStatus> currentFyleAndStatus;
    private final LiveData<Message> currentAssociatedMessage;
    private final LiveData<MessageExpiration> currentAssociatedMessageExpiration;

    enum GalleryType {
        DISCUSSION,
        DRAFT,
        MESSAGE,
        OWNED_IDENTITY,
    }
    private GalleryType galleryType = null;

    public GalleryViewModel() {
        discussionId = new MutableLiveData<>(null);
        messageId = new MutableLiveData<>(null);
        ownedIdentitySortOrder = new MutableLiveData<>(null);
        ownedIdentitySortOrderAscending = new MutableLiveData<>(null);
        bytesOwnedIdentity = new MutableLiveData<>(null);

        imageAndVideoFyleAndStatusList = new TripleLiveData(discussionId, messageId, ownedIdentitySortOrder, ownedIdentitySortOrderAscending, bytesOwnedIdentity);

        currentPagerPosition = new MutableLiveData<>(null);

        currentFyleAndStatus = new PositionAndListLiveData(currentPagerPosition, imageAndVideoFyleAndStatusList);
        currentAssociatedMessage = Transformations.switchMap(currentFyleAndStatus, (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) -> {
            if (fyleAndStatus != null) {
                return  AppDatabase.getInstance().messageDao().getLive(fyleAndStatus.fyleMessageJoinWithStatus.messageId);
            }
            return null;
        });
        currentAssociatedMessageExpiration = Transformations.switchMap(currentFyleAndStatus, (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus) -> {
            if (fyleAndStatus != null) {
                return  AppDatabase.getInstance().messageExpirationDao().getLive(fyleAndStatus.fyleMessageJoinWithStatus.messageId);
            }
            return null;
        });
    }

    public void setDiscussionId(long discussionId) {
        this.discussionId.postValue(discussionId);
        galleryType = GalleryType.DISCUSSION;
    }

    public void setMessageId(long messageId, boolean draft) {
        this.messageId.postValue(messageId);
        if (draft) {
            galleryType = GalleryType.DRAFT;
        } else {
            galleryType = GalleryType.MESSAGE;
        }
    }

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity, @Nullable String sortOrder, @Nullable Boolean ascending) {
        this.ownedIdentitySortOrder.postValue(sortOrder);
        this.ownedIdentitySortOrderAscending.postValue(ascending);
        this.bytesOwnedIdentity.postValue(bytesOwnedIdentity);
        galleryType = GalleryType.OWNED_IDENTITY;
    }

    @Nullable
    public Integer getCurrentPagerPosition() {
        return currentPagerPosition.getValue();
    }

    public void setCurrentPagerPosition(int position) {
        this.currentPagerPosition.postValue(position);
    }

    public LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> getImageAndVideoFyleAndStatusList() {
        return imageAndVideoFyleAndStatusList;
    }

    public LiveData<FyleMessageJoinWithStatusDao.FyleAndStatus> getCurrentFyleAndStatus() {
        return currentFyleAndStatus;
    }

    public LiveData<Message> getCurrentAssociatedMessage() {
        return currentAssociatedMessage;
    }

    public LiveData<MessageExpiration> getCurrentAssociatedMessageExpiration() {
        return currentAssociatedMessageExpiration;
    }

    public GalleryType getGalleryType() {
        return galleryType;
    }

    public static class MessageAndFyleId {
        final long messageId;
        final long fyleId;

        public MessageAndFyleId(long messageId, long fyleId) {
            this.messageId = messageId;
            this.fyleId = fyleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageAndFyleId that = (MessageAndFyleId) o;
            return messageId == that.messageId &&
                    fyleId == that.fyleId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, fyleId);
        }
    }

    public static class TripleLiveData extends MediatorLiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> {
        private final AppDatabase db;
        private byte[] bytesOwnedIdentity = null;
        private String sortOrder = null;
        private Boolean ascending = null;

        public TripleLiveData(MutableLiveData<Long> discussionId, MutableLiveData<Long> messageId, MutableLiveData<String> ownedIdentitySortOrder, MutableLiveData<Boolean> ascending, MutableLiveData<byte[]> bytesOwnedIdentity) {
            this.db = AppDatabase.getInstance();
            addSource(discussionId, this::onDiscussionChanged);
            addSource(messageId, this::onMessageChanged);
            addSource(ownedIdentitySortOrder, this::onSortOrderChanged);
            addSource(ascending, this::onAscendingChanged);
            addSource(bytesOwnedIdentity, this::onOwnedIdentityChanged);
        }

        private LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> source;


        public void onDiscussionChanged(Long discussionId) {
            LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> newSource;
            if (discussionId != null) {
                newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForDiscussion(discussionId);
            } else {
                newSource = null;
            }
            if (newSource == source) {
                return;
            }
            if (source != null) {
                removeSource(source);
            }
            source = newSource;
            if (source != null) {
                addSource(source, this::setValue);
            }
        }

        public void onMessageChanged(Long messageId) {
            LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> newSource;
            if (messageId != null) {
                newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusForMessage(messageId);
            } else {
                newSource = null;
            }
            if (newSource == source) {
                return;
            }
            if (source != null) {
                removeSource(source);
            }
            source = newSource;
            if (source != null) {
                addSource(source, this::setValue);
            }
        }

        public void onOwnedIdentityChanged(byte[] bytesOwnedIdentity) {
            this.bytesOwnedIdentity = bytesOwnedIdentity;
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending);
        }

        public void onSortOrderChanged(String sortOrder) {
            this.sortOrder = sortOrder;
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending);
        }
        public void onAscendingChanged(Boolean ascending) {
            this.ascending = ascending;
            updateOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending);
        }

        private void updateOwnedIdentity(byte[] bytesOwnedIdentity, String sortOrder, Boolean ascending) {
            LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> newSource;
            if (bytesOwnedIdentity != null) {
                if ("size".equals(sortOrder)) {
                    if (ascending != null && ascending) {
                        newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForOwnedIdentityBySizeAscending(bytesOwnedIdentity);
                    } else {
                        newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForOwnedIdentityBySize(bytesOwnedIdentity);
                    }
                } else if ("name".equals(sortOrder)) {
                    if (ascending != null && !ascending) {
                        newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForOwnedIdentityByName(bytesOwnedIdentity);
                    } else {
                        newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForOwnedIdentityByNameAscending(bytesOwnedIdentity);
                    }
                } else {
                    if (ascending != null && ascending) {
                        newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForOwnedIdentityAscending(bytesOwnedIdentity);
                    } else {
                        newSource = db.fyleMessageJoinWithStatusDao().getImageAndVideoFylesAndStatusesForOwnedIdentity(bytesOwnedIdentity);
                    }
                }
            } else {
                newSource = null;
            }
            if (newSource == source) {
                return;
            }
            if (source != null) {
                removeSource(source);
            }
            source = newSource;
            if (source != null) {
                addSource(source, this::setValue);
            }
        }
    }

    public static class PositionAndListLiveData extends MediatorLiveData<FyleMessageJoinWithStatusDao.FyleAndStatus> {
        public PositionAndListLiveData(LiveData<Integer> currentPagerPosition, LiveData<List<FyleMessageJoinWithStatusDao.FyleAndStatus>> imageAndVideoFyleAndStatusList) {
            addSource(currentPagerPosition, this::onPositionChanged);
            addSource(imageAndVideoFyleAndStatusList, this::onListChanged);
        }

        private Integer position;
        private List<FyleMessageJoinWithStatusDao.FyleAndStatus> list;

        private void onPositionChanged(Integer position) {
            this.position = position;
            updateValue();
        }

        private void onListChanged(List<FyleMessageJoinWithStatusDao.FyleAndStatus> list) {
            this.list = list;
            updateValue();
        }

        private void updateValue() {
            if (position != null && list != null && position >= 0 && position < list.size()) {
                setValue(list.get(position));
            } else {
                setValue(null);
            }
        }
    }
}
