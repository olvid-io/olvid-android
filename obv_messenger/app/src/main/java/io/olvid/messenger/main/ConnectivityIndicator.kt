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

package io.olvid.messenger.main

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.settings.SettingsActivity
import java.util.Timer
import java.util.TimerTask

internal class ConnectivityIndicator(private val activity: Activity) : EngineNotificationListener, Observer<Int> {
    private val pingConnectivityDot: View? = activity.findViewById(R.id.ping_indicator_dot)
    private val pingConnectivityLine: View? = activity.findViewById(R.id.ping_indicator_line)
    private val pingConnectivityFull: View? = activity.findViewById(R.id.ping_indicator_full)
    private val pingConnectivityFullTextView: TextView?  = activity.findViewById(R.id.ping_indicator_full_text_view)
    private val pingConnectivityFullPingTextView: TextView? = activity.findViewById(R.id.ping_indicator_full_ping_text_view)
    private val pingRed = ContextCompat.getColor(activity, R.color.red)
    private val pingGolden = ContextCompat.getColor(activity, R.color.golden)
    private val pingGreen = ContextCompat.getColor(activity, R.color.green)

    private var registrationNumber: Long? = null
    private var websocketConnectionState = 0
    private var lastPing: Long = 0
    private var pingingStarted = false

    private val timer = Timer()
    private var activityInForeground = false
    private var lastConnectedTimestamp = 0L
    private var notConnectedTask: TimerTask? = null
    private var connectedTask: TimerTask? = null


    private var pingConnectivitySetting = SettingsActivity.PingConnectivityIndicator.NONE
    private var forceShowFullIndicator = false
    private var currentPingConnectivityIndicator = SettingsActivity.PingConnectivityIndicator.NONE



    private fun refresh() {
        onChanged(websocketConnectionState)
    }

    override fun onChanged(value: Int) {
        if (value != this.websocketConnectionState) {
            lastPing = 0
            if (value == 2 || this.websocketConnectionState == 2) {
                // we just lost or gained connection
                lastConnectedTimestamp = System.currentTimeMillis()
            }
        }

        this.websocketConnectionState = value

        if (pingingStarted.not() && computeConnectivityIndicator() != SettingsActivity.PingConnectivityIndicator.NONE) {
            pingingStarted = true
            Utils.startPinging()
        }

        // cancel any task that is no longer relevant
        if (value == 2 && lastPing != -1L) {
            notConnectedTask?.let {
                it.cancel()
                notConnectedTask = null
            }
            if (forceShowFullIndicator) {
                // in case we were forcing the full indicator, start the timer to hide it
                startTimerIfNeeded()
            }
        } else {
            connectedTask?.let {
                it.cancel()
                connectedTask = null
            }
            if (pingConnectivitySetting == SettingsActivity.PingConnectivityIndicator.NONE) {
                // in case we just lost connection, start the timer to hide it
                startTimerIfNeeded()
            }
        }

        // update what indicator is shown in case it changed
        showPingIndicator(computeConnectivityIndicator())

        val stateColor: Int = if (value == 0 || lastPing == -1L) {
            pingRed
        } else if (value == 1 || lastPing > 3000) {
            pingGolden
        } else {
            pingGreen
        }
        when (currentPingConnectivityIndicator) {
            SettingsActivity.PingConnectivityIndicator.NONE,
            SettingsActivity.PingConnectivityIndicator.NEVER -> {}
            SettingsActivity.PingConnectivityIndicator.DOT -> pingConnectivityDot?.setBackgroundColor(stateColor)
            SettingsActivity.PingConnectivityIndicator.LINE -> pingConnectivityLine?.setBackgroundColor(stateColor)
            SettingsActivity.PingConnectivityIndicator.FULL -> {
                pingConnectivityFull?.setBackgroundColor(stateColor)
                when (value) {
                    1 -> pingConnectivityFullTextView?.setText(
                        R.string.label_ping_connectivity_connecting
                    )

                    2 -> pingConnectivityFullTextView?.setText(R.string.label_ping_connectivity_connected)
                    0 -> pingConnectivityFullTextView?.setText(R.string.label_ping_connectivity_none)
                    else -> pingConnectivityFullTextView?.setText(R.string.label_ping_connectivity_none)
                }
                when (lastPing) {
                    -1L -> {
                        pingConnectivityFullPingTextView?.text =
                            activity.getString(R.string.label_over_max_ping_delay, 5)
                    }

                    0L -> {
                        pingConnectivityFullPingTextView?.text = "-"
                    }

                    else -> {
                        pingConnectivityFullPingTextView?.text =
                            activity.getString(R.string.label_ping_delay, lastPing)
                    }
                }
            }
        }
    }

    private fun showPingIndicator(pingConnectivityIndicator: SettingsActivity.PingConnectivityIndicator) {
        if (pingConnectivityIndicator == currentPingConnectivityIndicator) {
            return
        }
        currentPingConnectivityIndicator = pingConnectivityIndicator
        when (pingConnectivityIndicator) {
            SettingsActivity.PingConnectivityIndicator.NONE,
            SettingsActivity.PingConnectivityIndicator.NEVER -> {
                pingConnectivityDot?.visibility = View.GONE
                pingConnectivityLine?.visibility = View.GONE
                pingConnectivityFull?.visibility = View.GONE
            }

            SettingsActivity.PingConnectivityIndicator.DOT -> {
                pingConnectivityDot?.visibility = View.VISIBLE
                pingConnectivityLine?.visibility = View.GONE
                pingConnectivityFull?.visibility = View.GONE
            }

            SettingsActivity.PingConnectivityIndicator.LINE -> {
                pingConnectivityDot?.visibility = View.GONE
                pingConnectivityLine?.visibility = View.VISIBLE
                pingConnectivityFull?.visibility = View.GONE
            }

            SettingsActivity.PingConnectivityIndicator.FULL -> {
                pingConnectivityDot?.visibility = View.GONE
                pingConnectivityLine?.visibility = View.GONE
                pingConnectivityFull?.visibility = View.VISIBLE
                pingConnectivityFullPingTextView?.text = null
            }
        }
    }

    fun onPause() {
        activityInForeground = false
        stopListening()
    }

    fun onResume() {
        activityInForeground = true
        startListening()

        pingConnectivitySetting = SettingsActivity.Companion.pingConnectivityIndicator
        if (pingConnectivitySetting == SettingsActivity.PingConnectivityIndicator.NONE) {
            startTimerIfNeeded()
        } else {
            forceShowFullIndicator = false
        }

        refresh()
    }

    fun computeConnectivityIndicator() : SettingsActivity.PingConnectivityIndicator {
        return if (pingConnectivitySetting != SettingsActivity.PingConnectivityIndicator.NONE) {
            pingConnectivitySetting
        } else if (forceShowFullIndicator) {
            SettingsActivity.PingConnectivityIndicator.LINE
        } else {
            SettingsActivity.PingConnectivityIndicator.NONE
        }
    }


    fun startTimerIfNeeded() {
        if (forceShowFullIndicator.not()
            && notConnectedTask == null
            && (websocketConnectionState != 2 || lastPing == -1L)) {
            notConnectedTask = object : TimerTask() {
                override fun run() {
                    notConnectedTask = null
                    if (websocketConnectionState != 2 || lastPing == -1L) {
                        forceShowFullIndicator = true
                    }
                    if (activityInForeground) {
                        activity.runOnUiThread {
                            refresh()
                        }
                    }
                }
            }

            val millisSinceLastConnectedOrForeground = System.currentTimeMillis() - (App.latestAppForeground.coerceAtLeast(lastConnectedTimestamp))
            // show the indicator after 5 unconnected seconds, but never less than 1 seconds to give the websocket status livedata time to update
            val delay = (5_000L - millisSinceLastConnectedOrForeground).coerceAtLeast(1_000L)

            timer.schedule(
                notConnectedTask,
                delay
            )
        }
        if (forceShowFullIndicator
            && connectedTask == null
            && websocketConnectionState == 2
            && lastPing != -1L
            && lastConnectedTimestamp > App.latestAppForeground) {
            connectedTask = object : TimerTask() {
                override fun run() {
                    connectedTask = null
                    if (websocketConnectionState == 2) {
                        forceShowFullIndicator = false
                    }
                    if (activityInForeground) {
                        activity.runOnUiThread { refresh() }
                    }
                }
            }

            val millisSinceLastConnected = System.currentTimeMillis() - lastConnectedTimestamp
            // hide the indicator after 3 connected seconds
            val delay = (3_000L - millisSinceLastConnected).coerceAtLeast(0L)

            timer.schedule(
                connectedTask,
                delay
            )
        }
    }


    // region EngineListener for pings

    private fun startListening() {
        if (registrationNumber == null) {
            AppSingleton.getEngine()
                .addNotificationListener(EngineNotifications.PING_LOST, this)
            AppSingleton.getEngine()
                .addNotificationListener(EngineNotifications.PING_RECEIVED, this)
        }
    }

    private fun stopListening() {
        if (registrationNumber != null) {
            AppSingleton.getEngine()
                .removeNotificationListener(EngineNotifications.PING_LOST, this)
            AppSingleton.getEngine()
                .removeNotificationListener(EngineNotifications.PING_RECEIVED, this)
            registrationNumber = null
        }
    }


    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.PING_LOST -> {
                lastPing = -1
                activity.runOnUiThread { refresh() }
            }

            EngineNotifications.PING_RECEIVED -> {
                (userInfo[EngineNotifications.PING_RECEIVED_DELAY_KEY] as Long?)?.let {
                    lastPing = it
                    activity.runOnUiThread { refresh() }
                }
            }
        }
    }

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        this.registrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return registrationNumber ?: 0
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return registrationNumber != null
    }

    // endregion
}