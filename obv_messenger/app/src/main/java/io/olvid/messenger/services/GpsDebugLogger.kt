/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.services

import android.icu.text.SimpleDateFormat
import android.location.Location
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.settings.SettingsActivity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.ArrayDeque
import java.util.Deque
import java.util.Locale


object GpsDebugLogger {
    const val GPS_DEBUG_LOG_FILE_NAME = "gps_debug.log"
    const val GPS_DEBUG_LOG_MAX_SIZE = 1_000_000L

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd@HH:mm:ss", Locale.ROOT)
    val queue: Deque<String> = ArrayDeque(500)

    var writingToFile = false

    fun logReceivedLocation(location: Location?) {
        if (!SettingsActivity.useGpsDebug) return
        if (location == null) return

        val logString = "${dateFormatter.format(location.time)} received location from provider ${location.provider}: (${location.latitude},${location.longitude}) (acc.: ${location.accuracy}m)"
        Logger.d("🗺️ $logString")

        val size: Int
        synchronized(queue) {
            queue.add(logString)
            size = queue.size
        }

        if (size > 20 && !writingToFile) {
            writeLogs()
        }
    }

    fun logGpsEvent(event: String) {
        val logString = "${dateFormatter.format(System.currentTimeMillis())} $event"
        Logger.d("🗺️ $logString")

        if (!SettingsActivity.useGpsDebug) {
            return
        }
        val size: Int
        synchronized(queue) {
            queue.add(logString)
            size = queue.size
        }

        if (size > 20 && !writingToFile) {
            writeLogs()
        }
    }

    private fun writeLogs(synchronous: Boolean = false) {
        writingToFile = true

        val logs = mutableListOf<String>()
        synchronized(queue) {
            logs.addAll(queue)
            queue.clear()
        }

        val block = object : Runnable {
            override fun run() {
                try {
                    val logFile = File(App.getContext().getNoBackupFilesDir(), GPS_DEBUG_LOG_FILE_NAME);
                    val append = !logFile.exists() || logFile.length() <= GPS_DEBUG_LOG_MAX_SIZE;

                    FileWriter(logFile, append).use { fileWriter ->
                        runCatching {
                            logs.forEach {
                                fileWriter.append(it)
                                fileWriter.append("\n")
                            }
                        }
                    }
                } finally {
                    writingToFile = false
                }
            }
        }

        if (synchronous) {
            block.run()
        } else {
            App.runThread(block)
        }
    }

    fun getLogsContent(): String {
        writeLogs(synchronous = true)
        val logFile = File(App.getContext().getNoBackupFilesDir(), GPS_DEBUG_LOG_FILE_NAME);
        FileReader(logFile).use { fileReader ->
            return fileReader.readText()
        }
    }
}