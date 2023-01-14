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

package io.olvid.engine.notification;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.metamanager.MetaManager;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ObvManager;


public class NotificationManager implements NotificationListeningDelegate, NotificationPostingDelegate, ObvManager {
    private long instanceCounter;
    private final HashMap<String, HashMap<Long, WeakReference<NotificationListener>>> listeners;
    private final ReentrantLock listenersLock;

    public NotificationManager(MetaManager metaManager) {
        this.instanceCounter = 0;
        this.listeners = new HashMap<>();
        this.listenersLock = new ReentrantLock();

        metaManager.registerImplementedDelegates(this);
    }

    @Override
    public void initialisationComplete() {
        // Nothing to do here
    }

    private synchronized long getInstanceNumber() {
        long instanceNumber = instanceCounter;
        instanceCounter++;
        return instanceNumber;
    }




    // region implement NotificationListeningDelegate

    @Override
    public long addListener(String notificationName, NotificationListener notificationListener) {
        listenersLock.lock();
        long listenerNumber = getInstanceNumber();
        HashMap<Long, WeakReference<NotificationListener>> notificationObservers = listeners.get(notificationName);
        if (notificationObservers == null) {
            notificationObservers = new HashMap<>();
            listeners.put(notificationName, notificationObservers);
        }
        WeakReference<NotificationListener> weakReference = new WeakReference<>(notificationListener);
        notificationObservers.put(listenerNumber, weakReference);
        listenersLock.unlock();
        return listenerNumber;
    }

    @Override
    public void removeListener(String notificationName, long notificationListenerNumber) {
        listenersLock.lock();
        HashMap<Long, WeakReference<NotificationListener>> notificationObservers = listeners.get(notificationName);
        if (notificationObservers != null) {
            notificationObservers.remove(notificationListenerNumber);
        }
        listenersLock.unlock();
    }

    // endregion



    // region implement NotificationPostingDelegate

    @Override
    public void postNotification(String notificationName, HashMap<String, Object> userInfo) {
        Logger.d("Posting notification with name " + notificationName);
        listenersLock.lock();
        HashMap<Long, WeakReference<NotificationListener>> notificationObservers = listeners.get(notificationName);
        if (notificationObservers != null) {
            notificationObservers = new HashMap<>(notificationObservers); // we clone the HashMap to make sure that, even outside the lock, we can iterate on the HashMap
            listenersLock.unlock();
            for (HashMap.Entry<Long, WeakReference<NotificationListener>> entry: notificationObservers.entrySet()) {
                NotificationListener listener = entry.getValue().get();
                if (listener == null) {
                    // remove the listener
                    removeListener(notificationName, entry.getKey());
                } else {
                    // call callback method
                    listener.callback(notificationName, userInfo);
                }
            }
        } else {
            listenersLock.unlock();
        }
    }

    // endregion
}
