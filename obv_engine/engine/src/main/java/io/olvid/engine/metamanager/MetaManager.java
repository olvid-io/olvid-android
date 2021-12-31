/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.engine.metamanager;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;

public class MetaManager {
    private final HashMap<String, Object> registeredInterfaceImplementations;
    private final HashMap<String, ArrayList<ObvManager>> managersAwaitingInterfaceImplementations;
    private final Set<ObvManager> registeredManagers;
    private final Set<Object> registeredDelegates;
    private final ReentrantLock lockOnInterfaceImplementations;


    public MetaManager() {
        registeredInterfaceImplementations = new HashMap<>();
        managersAwaitingInterfaceImplementations = new HashMap<>();
        registeredManagers = Collections.newSetFromMap(Collections.synchronizedMap(new HashMap<>())); // Used to create a thread-safe Set
        registeredDelegates = Collections.newSetFromMap(Collections.synchronizedMap(new HashMap<>())); // Used to create a thread-safe Set
        lockOnInterfaceImplementations = new ReentrantLock();
    }

    public void initializationComplete() throws Exception {
        if (!managersAwaitingInterfaceImplementations.isEmpty()) {
            Logger.e("Called initializationComplete but some managers are still awaiting some delegates.");
            for (Map.Entry<String, ArrayList<ObvManager>> entry: managersAwaitingInterfaceImplementations.entrySet()) {
                Logger.e("Missing delegate for " + entry.getKey());
            }
            throw new Exception();
        }
        Logger.d("Initialisation complete. All managers have their requested delegates set.");
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // do nothing
            }
            for (ObvManager manager: registeredManagers) {
                manager.initialisationComplete();
            }
        }).start();
    }

    public void registerImplementedDelegates(Object delegatesImplementation) {
        registeredDelegates.add(delegatesImplementation);
        checkInterfaceImplementations(delegatesImplementation);
    }

    public void requestDelegate(ObvManager manager, Class<?> interfaceClass) {
        Logger.d("Manager " + manager.getClass() + " requesting delegate " + interfaceClass);
        String interfaceName = interfaceClass.getName();

        registeredManagers.add(manager);

        lockOnInterfaceImplementations.lock();
        Object delegate = registeredInterfaceImplementations.get(interfaceName);
        // first check whether this interface is already registered
        if (delegate != null) {
            Logger.d("A delegate of " + delegate.getClass() + " was already cached for " + interfaceName);
            setManagerDelegate(manager, delegate, interfaceName);
        } else {
            // the interface was never registered
            // check if any of the registered delegates implements it:
            for (Object registeredDelegate: registeredDelegates) {
                if (interfaceClass.isInstance(registeredDelegate)) {
                    Logger.d("Found " + registeredDelegate.getClass() + " implementing " + interfaceName);
                    registeredInterfaceImplementations.put(interfaceName, registeredDelegate);
                    setManagerDelegate(manager, registeredDelegate, interfaceName);
                    lockOnInterfaceImplementations.unlock();
                    return;
                }
            }
            // no registered delegate implements the interface, add the manager to the list of waiting managers
            Logger.d("No delegate found implementing " + interfaceName);
            ArrayList<ObvManager> waitingManagers = managersAwaitingInterfaceImplementations.get(interfaceName);
            if (waitingManagers == null) {
                waitingManagers = new ArrayList<>();
                managersAwaitingInterfaceImplementations.put(interfaceName, waitingManagers);
            }
            waitingManagers.add(manager);
        }
        lockOnInterfaceImplementations.unlock();
    }

    private void setManagerDelegate(ObvManager manager, Object delegate, String interfaceName) {
        try {
            Logger.d("Setting delegate " + delegate.getClass() + " as " + interfaceName + " for manager " + manager.getClass());
            Method method = manager.getClass().getMethod("setDelegate", Class.forName(interfaceName));
            method.invoke(manager, delegate);
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            Logger.e("ObvManager " + manager.getClass() + " requests a delegate of type " + interfaceName + " but does not implement the matching setDelegate method.");
            throw new RuntimeException();
        }
    }

    private void checkInterfaceImplementations(Object delegatesImplementation) {
        lockOnInterfaceImplementations.lock();
        // first check that this new delegatesImplementation does not implement any of the registered interface implementations
        for (String interfaceName: registeredInterfaceImplementations.keySet()) {
            try {
                if (Class.forName(interfaceName).isInstance(delegatesImplementation)) {
                    Logger.e("The MetaManager received two managers implementing " + interfaceName + ":\n  " + registeredInterfaceImplementations.get(interfaceName) + "\n  " + delegatesImplementation.getClass());
                    throw new RuntimeException();
                }
            } catch (ClassNotFoundException e ) {
                e.printStackTrace();
            }
        }

        // then, check all managers awaiting an interface implementation
        for (String interfaceName: managersAwaitingInterfaceImplementations.keySet().toArray(new String[0])) {
            try {
                if (Class.forName(interfaceName).isInstance(delegatesImplementation)) {
                    registeredInterfaceImplementations.put(interfaceName, delegatesImplementation);
                    for (ObvManager waitingManager: managersAwaitingInterfaceImplementations.get(interfaceName)) {
                        setManagerDelegate(waitingManager, delegatesImplementation, interfaceName);
                    }
                    managersAwaitingInterfaceImplementations.remove(interfaceName);
                }
            } catch (ClassNotFoundException e ) {
                e.printStackTrace();
            }
        }
        lockOnInterfaceImplementations.unlock();
    }

}
