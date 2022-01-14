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

package io.olvid.messenger.webclient.listeners;

import androidx.lifecycle.LiveData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public abstract class AbstractObserver<keyType, elementType> implements androidx.lifecycle.Observer<List<elementType>> {
    final LiveData<List<elementType>> liveData;
    HashMap<keyType, elementType> cacheMemory = null;

    AbstractObserver(LiveData<List<elementType>> liveData) {
        this.liveData = liveData;
    }

    // return true if implemented false if not used
    // shall be sending a batched message of all new elements
    // care shall launch thread if need to access database (MessageListener)
    abstract boolean batchedElementHandler(List<elementType> elements);

    abstract boolean equals(elementType element1, elementType element2);

    abstract keyType getElementKey(elementType element);

    abstract void newElementHandler(elementType element);

    abstract void deletedElementHandler(elementType element);

    abstract void modifiedElementHandler(elementType element);

    @Override
    public void onChanged(List<elementType> elements) {
        keyType elementKey;
        Set<keyType> keySet;

        if (this.cacheMemory == null) {
            this.cacheMemory = new HashMap<>();
        }

        // batch element if this is first call
        if (cacheMemory.keySet().isEmpty()) {
            // if return batchedElementHandler true, job had been done, else, continue normal process not batching
            if (batchedElementHandler(elements)) {
                // fill cache with all sent elements
                for (elementType element : elements) {
                    cacheMemory.put(getElementKey(element), element);
                }
                return;
            }
        }

        List<elementType> newElements = new LinkedList<>();

        keySet = new HashSet<>(this.cacheMemory.keySet());

        // check for elements created or modified
        for (elementType element : elements) {
            elementKey = getElementKey(element);
            keySet.remove(elementKey);
            if (!this.cacheMemory.containsKey(elementKey)) {
                newElements.add(element);
                this.cacheMemory.put(elementKey, element);
            } else if (!equals(element, this.cacheMemory.get(elementKey))) {
                modifiedElementHandler(element);
                this.cacheMemory.put(elementKey, element);
            }
        }

        // try to batch new elements if more than one (or manually send one by one if batchedElementHandler return an error)
        if (newElements.size() <= 1 || !this.batchedElementHandler(newElements)) {
            for (elementType element : newElements) {
                newElementHandler(element);
            }
        }

        // delete elements that are no more in LiveData
        if (keySet.size() > 0) {
            for (keyType key : keySet) {
                deletedElementHandler(this.cacheMemory.get(key));
                this.cacheMemory.remove(key);
            }
        }
    }
}
