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

package io.olvid.messenger

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.common.base.Objects
import io.olvid.engine.datatypes.EtaEstimator.SpeedAndEta


object FyleProgressSingleton {

    private val ongoingProgresses: MutableMap<FyleAndMessageIds, MutableLiveData<ProgressStatus>> = mutableMapOf()
    private val listenedToProgresses: MutableMap<FyleAndMessageIds, MutableLiveData<ProgressStatus>> = mutableMapOf()

    fun getProgress(fyleId: Long, messageId: Long) : LiveData<ProgressStatus> {
        val fyleAndMessageIds = FyleAndMessageIds(fyleId, messageId)
        synchronized(FyleProgressSingleton) {
            return ongoingProgresses[fyleAndMessageIds]
                ?: listenedToProgresses[fyleAndMessageIds]
                ?: ProgressLiveData(fyleAndMessageIds)
        }
    }

    fun updateProgress(fyleId: Long, messageId: Long, progress: Float, speedAndEta: SpeedAndEta?) {
        publishProgress(fyleId, messageId, ProgressStatus.InProgress(progress, speedAndEta))
    }

    fun finishProgress(fyleId: Long, messageId: Long) {
        publishProgress(fyleId, messageId, ProgressStatus.Finished)
    }

    private fun publishProgress(fyleId: Long, messageId: Long, progressStatus: ProgressStatus) {
        val fyleAndMessageIds = FyleAndMessageIds(fyleId, messageId)
        when (progressStatus) {
            ProgressStatus.Unknown -> return
            ProgressStatus.Finished -> {
                synchronized(FyleProgressSingleton) {
                    // if this progress was ongoing, remove it
                    val ongoingProgress = ongoingProgresses.remove(fyleAndMessageIds)
                    if (ongoingProgress != null) {
                        if (ongoingProgress.hasObservers()) {
                            // only add the progress to listenedToProgresses if there is indeed some listener
                            listenedToProgresses[fyleAndMessageIds] = ongoingProgress
                            ongoingProgress.postValue(progressStatus)
                        }
                        return
                    }

                    listenedToProgresses[fyleAndMessageIds]?.postValue(progressStatus)
                }
            }
            is ProgressStatus.InProgress -> {
                synchronized(FyleProgressSingleton) {
                    // if a progress already exits, remove it of listenedToProgresses or get it from ongoingProgresses
                    val progress = listenedToProgresses.remove(fyleAndMessageIds)
                        ?: ongoingProgresses[fyleAndMessageIds]
                        ?: ProgressLiveData(fyleAndMessageIds)
                    ongoingProgresses[fyleAndMessageIds] = progress
                    progress.postValue(
                        (progress.takeIf { progressStatus.speedAndEta == null }?.value as? ProgressStatus.InProgress)?.speedAndEta?.let {
                            ProgressStatus.InProgress(progressStatus.progress, it)
                        } ?: progressStatus
                    )
                }
            }
        }
    }

    class ProgressLiveData internal constructor(private val fyleAndMessageIds: FyleAndMessageIds) : MutableLiveData<ProgressStatus>(ProgressStatus.Unknown) {
        override fun observe(owner: LifecycleOwner, observer: Observer<in ProgressStatus>) {
            // when adding a first observer, if not already in ongoingProgresses map, add it to listenedToProgresses
            synchronized(FyleProgressSingleton) {
                if (!hasObservers()) {
                    if (!ongoingProgresses.containsKey(fyleAndMessageIds)) {
                        listenedToProgresses[fyleAndMessageIds] = this
                    }
                }
            }

            super.observe(owner, observer)
        }

        override fun removeObserver(observer: Observer<in ProgressStatus>) {
            super.removeObserver(observer)

            // after removing the last observer, remove the LiveData from listenedToProgresses
            synchronized(FyleProgressSingleton) {
                if (!hasObservers()) {
                    listenedToProgresses.remove(fyleAndMessageIds)
                }
            }
        }
    }
}

internal data class FyleAndMessageIds(val fyleId: Long, val messageId: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FyleAndMessageIds) return false

        return (fyleId == other.fyleId) && (messageId == other.messageId)
    }

    override fun hashCode(): Int {
        return 31 * fyleId.hashCode() + messageId.hashCode()
    }
}

sealed class ProgressStatus {
    data object Unknown : ProgressStatus()
    class InProgress(val progress: Float, val speedAndEta: SpeedAndEta?) : ProgressStatus()
    data object Finished : ProgressStatus()
}