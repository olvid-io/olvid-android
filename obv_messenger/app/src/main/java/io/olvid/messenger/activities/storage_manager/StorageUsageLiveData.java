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

package io.olvid.messenger.activities.storage_manager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

class StorageUsageLiveData extends MediatorLiveData<StorageUsageLiveData.StorageUsage> {
    long total;
    long photos;
    long videos;
    long audio;

    public StorageUsageLiveData(LiveData<Long> totalUsageLiveData, LiveData<Long> photosUsageLiveData, LiveData<Long> videosUsageLiveData, LiveData<Long> audioUsageLiveData) {
        addSource(totalUsageLiveData, this::totalSizeObserver);
        addSource(photosUsageLiveData, this::photosSizeObserver);
        addSource(videosUsageLiveData, this::videosSizeObserver);
        addSource(audioUsageLiveData, this::audioSizeObserver);
    }

    void totalSizeObserver(Long totalSize) {
        if (totalSize == null) {
            total = 0;
        } else {
            total = totalSize;
        }
        update();
    }

    void photosSizeObserver(Long photosSize) {
        if (photosSize == null) {
            photos = 0;
        } else {
            photos = photosSize;
        }
        update();
    }

    void videosSizeObserver(Long videosSize) {
        if (videosSize == null) {
            videos = 0;
        } else {
            videos = videosSize;
        }
        update();
    }

    void audioSizeObserver(Long audioSize) {
        if (audioSize == null) {
            audio = 0;
        } else {
            audio = audioSize;
        }
        update();
    }

    void update() {
        setValue(new StorageUsage(total, photos, videos, audio));
    }

    public static class StorageUsage {
        public final long total;
        public final long photos;
        public final long videos;
        public final long audio;
        public final long other;

        public StorageUsage(long total, long photos, long videos, long audio) {
            this.total = total;
            this.photos = photos;
            this.videos = videos;
            this.audio = audio;
            this.other = total - photos - videos - audio;
        }
    }
}
