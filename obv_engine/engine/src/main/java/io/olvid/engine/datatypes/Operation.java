/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.engine.datatypes;


import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;

public abstract class Operation {
    public interface OnFinishCallback {
        void onFinishCallback(Operation operation);
    }
    public interface OnCancelCallback {
        void onCancelCallback(Operation operation);
    }

    public static final int RFC_NULL = -1;
    private static final ReentrantLock globalLock = new ReentrantLock();
    private static final HashMap<String, HashSet<UID>> runningOperationUIDsByClass = new HashMap<>();

    private enum State {
        NOT_QUEUED,
        PENDING,
        READY,
        EXECUTING,
        FINISHED,
        CANCELLED
    }

    private final List<Operation> dependencies;
    private State state;
    private final ReentrantLock lockOnState;
    private long timestampOfLastExecution;
    private boolean cancelWasRequested;
    private Integer reasonForCancel;

    private final UID uid;
    private final OnFinishCallback onFinishCallback;
    private final OnCancelCallback onCancelCallback;

    public Operation() {
        this(null, null, null);
    }

    public Operation(UID uid, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        state = State.NOT_QUEUED;
        dependencies = new LinkedList<>();
        lockOnState = new ReentrantLock();
        timestampOfLastExecution = 0;
        cancelWasRequested = false;
        reasonForCancel = null;

        this.uid = uid;
        this.onFinishCallback = onFinishCallback;
        this.onCancelCallback = onCancelCallback;
    }

    public String toString() {
        return "Operation of type " + this.getClass().getName() + "(" + System.identityHashCode(this) + ")\n\tStatus: " + this.state;
    }

    public UID getUid() {
        return uid;
    }

    public boolean hasCancelledDependency() {
        for (Operation op: dependencies) {
            if (op.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllDependenciesFinished() {
        for (Operation op: dependencies) {
            if (! op.isFinished()) {
                return false;
            }
        }
        return true;
    }

    public void updateReadiness() {
        if (! isPending()) {
            return;
        }
        if (hasCancelledDependency()) {
            cancel(null);
            return;
        }
        if (areAllDependenciesFinished()) {
            setReady();
        }
    }

    public void setFinished() {
        lockOnState.lock();
        if (isStateChangeAuthorized(State.FINISHED)) {
            state = State.FINISHED;
            lockOnState.unlock();
            if (onFinishCallback != null) {
                onFinishCallback.onFinishCallback(this);
            }
            if (uid != null) {
                globalLock.lock();
                HashSet<UID> uids = runningOperationUIDsByClass.get(this.getClass().getName());
                if (uids != null) {
                    uids.remove(uid);
                }
                globalLock.unlock();
            }
        } else {
            lockOnState.unlock();
        }
    }

    public final void cancel(Integer reasonForCancel) {
        lockOnState.lock();
        if ((state != State.CANCELLED) && (state != State.FINISHED) && !cancelWasRequested) {
            cancelWasRequested = true;
            this.reasonForCancel = reasonForCancel;
            Logger.d("Cancel with RFC " + reasonForCancel + " requested for Operation of " + getClass());
        }
        lockOnState.unlock();
        doCancel();
    }

    public abstract void doCancel();

    public void processCancel() {
        lockOnState.lock();
        if ((state != State.CANCELLED) && (state != State.FINISHED) && cancelWasRequested) {
            state = State.CANCELLED;
            lockOnState.unlock();
            if (onCancelCallback != null) {
                onCancelCallback.onCancelCallback(this);
            }
            if (uid != null) {
                globalLock.lock();
                HashSet<UID> uids = runningOperationUIDsByClass.get(this.getClass().getName());
                if (uids != null) {
                    uids.remove(uid);
                }
                globalLock.unlock();
            }
            Logger.d("Processed cancel of Operation of " + this.getClass().toString());
        } else {
            lockOnState.unlock();
        }
    }

    public boolean areConditionsFulfilled() {
        boolean conditionsFulfilled = true;
        globalLock.lock();
        if (uid != null) {
            HashSet<UID> uids = runningOperationUIDsByClass.get(this.getClass().getName());
            if ((uids != null) && (uids.contains(uid))) {
                conditionsFulfilled = false;
            }
        }
        globalLock.unlock();
        return conditionsFulfilled;
    }

    public final void execute() {
        if (uid != null) {
            globalLock.lock();
            HashSet<UID> uids = runningOperationUIDsByClass.get(this.getClass().getName());
            if (uids == null) {
                uids = new HashSet<>();
                runningOperationUIDsByClass.put(this.getClass().getName(), uids);
            }
            uids.add(uid);
            globalLock.unlock();
        }
        setExecuting();
        doExecute();
    }

    public abstract void doExecute();


    public void addDependency(Operation operation) {
        for (Operation op: dependencies) {
            op.addDependency(operation);
        }
        dependencies.add(operation);
    }

    @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
    private boolean isStateChangeAuthorized(State newState) {
        switch (state) {
            case NOT_QUEUED:
                switch (newState) {
                    case NOT_QUEUED:
                    case PENDING:
                    case CANCELLED:
                        return true;
                    default:
                        return false;
                }
            case PENDING:
                switch (newState) {
                    case PENDING:
                    case READY:
                    case CANCELLED:
                        return true;
                    default:
                        return false;
                }
            case READY:
                switch (newState) {
                    case READY:
                    case EXECUTING:
                    case CANCELLED:
                        return true;
                    default:
                        return false;
                }
            case EXECUTING:
                switch (newState) {
                    case EXECUTING:
                    case FINISHED:
                    case CANCELLED:
                        return true;
                    default:
                        return false;
                }
            case FINISHED:
                return newState == State.FINISHED;
            case CANCELLED:
                return newState == State.CANCELLED;
        }
        return false;
    }

    public void setPending() {
        lockOnState.lock();
        if (isStateChangeAuthorized(State.PENDING)) {
            state = State.PENDING;
        }
        lockOnState.unlock();
    }

    public void setReady() {
        lockOnState.lock();
        if (isStateChangeAuthorized(State.READY)) {
            state = State.READY;
        }
        lockOnState.unlock();
    }

    public void setExecuting() {
        lockOnState.lock();
        if (isStateChangeAuthorized(State.EXECUTING)) {
            state = State.EXECUTING;
        }
        lockOnState.unlock();
    }

    public boolean wasQueued() {
        lockOnState.lock();
        boolean res = state != State.NOT_QUEUED;
        lockOnState.unlock();
        return res;
    }

    public boolean isPending() {
        lockOnState.lock();
        boolean res = state == State.PENDING;
        lockOnState.unlock();
        return res;
    }

    public boolean isExecuting() {
        lockOnState.lock();
        boolean res = state == State.EXECUTING;
        lockOnState.unlock();
        return res;
    }

    public boolean isReady() {
        lockOnState.lock();
        boolean res = state == State.READY;
        lockOnState.unlock();
        return res;
    }

    public boolean isFinished() {
        lockOnState.lock();
        boolean res = state == State.FINISHED;
        lockOnState.unlock();
        return res;
    }

    public boolean isCancelled() {
        lockOnState.lock();
        boolean res = state == State.CANCELLED;
        lockOnState.unlock();
        return res;
    }

    public long getTimestampOfLastExecution() {
        return timestampOfLastExecution;
    }
    public void setTimestampOfLastExecution(long timestampOfLastExecution) {
        this.timestampOfLastExecution = timestampOfLastExecution;
    }

    public List<Operation> getDependencies() {
        return dependencies;
    }

    public boolean cancelWasRequested() {
        return cancelWasRequested;
    }

    public Integer getReasonForCancel() {
        return reasonForCancel;
    }

    public boolean hasNoReasonForCancel() {
        return reasonForCancel == null;
    }
}
