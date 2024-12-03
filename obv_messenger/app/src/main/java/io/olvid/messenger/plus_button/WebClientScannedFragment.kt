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
package io.olvid.messenger.plus_button

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation.findNavController
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.services.UnifiedForegroundService.ServiceBinder
import io.olvid.messenger.services.UnifiedForegroundService.WebClientSubService
import io.olvid.messenger.settings.SettingsActivity.Companion.isWebclientUnlockRequired
import io.olvid.messenger.settings.SettingsActivity.Companion.useApplicationLockScreen
import io.olvid.messenger.webclient.WebClientManager.State.ERROR
import io.olvid.messenger.webclient.WebClientManager.State.FINISHING
import io.olvid.messenger.webclient.WebClientManager.State.WAITING_FOR_RECONNECTION

class WebClientScannedFragment : Fragment(), OnClickListener {
    private val serviceConnection = WebClientServiceConnection()
    private var eventBroadcastReceiver: EventBroadcastReceiver? = null


    private lateinit var activity: FragmentActivity
    val viewModel: PlusButtonViewModel by activityViewModels()

    var protocolInProgress: LinearLayout? = null
    var enterSASCode: LinearLayout? = null
    private var protocolSuccess: LinearLayout? = null
    var sasCodeEditText: EditText? = null
    var sasCodeError: TextView? = null

    private var scannedUri: String? = null

    var calculatedSasCode: MutableLiveData<String>? = null //code received from WebClientManager
    var isServiceClosed: MutableLiveData<Boolean>? = null //handle service closing during process

    var isBound: Boolean = false
    var webClientService: WebClientSubService? = null //retrieved from bounding to service
    private var timeOutCloseActivity: Handler? = null
    private var closeActivity: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity()
        calculatedSasCode = MutableLiveData()
        isServiceClosed = MutableLiveData()
        isBound = false

        eventBroadcastReceiver = EventBroadcastReceiver()
        val intentFilter = IntentFilter(WebClientSubService.READY_FOR_BIND_BROADCAST_ACTION)
        LocalBroadcastManager.getInstance(activity)
            .registerReceiver(eventBroadcastReceiver!!, intentFilter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_plus_button_webclient_scanned, container, false)

        if (!viewModel.isDeepLinked) {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        findNavController(rootView).popBackStack()
                    }
                })
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.top_bar)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.updatePadding(top = insets.top)
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = view.context.resources.getDimensionPixelSize(R.dimen.tab_bar_size) + insets.top
                }
                WindowInsetsCompat.CONSUMED
            }
        }

        this.sasCodeEditText = view.findViewById(R.id.sas_code)
        this.protocolInProgress = view.findViewById(R.id.wait_protocol_for_sas_step)
        this.enterSASCode = view.findViewById(R.id.enter_sas_code_step)
        this.protocolSuccess = view.findViewById(R.id.protocol_finished_successfully)
        this.sasCodeError = view.findViewById(R.id.error_sas_incorrect)

        val textWatcher: TextWatcher = object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                validateSasCode()
            }
        }

        sasCodeEditText?.addTextChangedListener(textWatcher)
        view.findViewById<View>(R.id.back_button).setOnClickListener(this)

        val uri = viewModel.scannedUri
        if (uri == null) {
            activity.finish()
            return
        }
        scannedUri = uri

        //start service and connect to web client. Service will run even after fragment (activity) closes.
        val connectIntent = Intent(activity, UnifiedForegroundService::class.java)
        connectIntent.putExtra(
            UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA,
            UnifiedForegroundService.SUB_SERVICE_WEB_CLIENT
        )
        connectIntent.setAction(WebClientSubService.ACTION_CONNECT)
        connectIntent.putExtra(WebClientSubService.CONNECTION_DATA_INTENT_EXTRA, scannedUri)
        activity.startService(connectIntent)
    }

    override fun onResume() {
        super.onResume()
        val window = activity.window
        if (window != null && sasCodeEditText != null && sasCodeEditText!!.isFocused) {
            window.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private inner class WebClientServiceConnection : ServiceConnection {
        var binder: ServiceBinder? = null

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service as ServiceBinder
            if (binder == null) {
                activity.onBackPressed()
                return
            }
            webClientService = binder!!.webClientService
            if (webClientService == null || webClientService?.manager == null) {
                activity.onBackPressed()
                return
            }
            isBound = true
            if (webClientService?.manager?.currentState == ERROR
                || webClientService?.manager?.currentState == FINISHING
            ) {
                unBind()
                activity.onBackPressed()
                return
            }

            // if webclient service is waiting for reconnection do not ask before erasing previous connection
            if (webClientService?.isAlreadyRunning() == true
                && WAITING_FOR_RECONNECTION == webClientService?.currentState
            ) {
                webClientService?.restartService()
                this.launchObservers()
            } else if (webClientService?.isAlreadyRunning() == true) {
                // if webclient was running ask for permission to erase previous connection
                val builder = SecureAlertDialogBuilder(
                    activity, R.style.CustomAlertDialog
                )
                    .setTitle(R.string.label_webclient_already_running)
                    .setMessage(R.string.label_webclient_restart_connection)
                    .setPositiveButton(R.string.button_label_ok) { dialog: DialogInterface, which: Int ->
                        (dialog as AlertDialog).setOnDismissListener(null)
                        webClientService?.restartService()
                        launchObservers()
                    }
                    .setOnDismissListener { dialog: DialogInterface? ->
                        unBind()
                        activity.onBackPressed()
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                builder.create().show()
                // if no webclient service was launched create it
            } else {
                this.launchObservers()
            }
        }

        fun launchObservers() {
            if (useApplicationLockScreen() && isWebclientUnlockRequired) {
                UnifiedForegroundService.lockApplication(
                    activity,
                    R.string.message_unlock_before_web_client
                )
            }

            //live data for sas : get notified when sas code is generated
            calculatedSasCode = webClientService?.sasCodeLiveData
            // Create the observer which updates the UI.
            val sasObserver =
                Observer<String> { computedSasCode: String? ->
                    if (computedSasCode != null && "" != computedSasCode) {
                        protocolInProgress!!.visibility = View.INVISIBLE
                        enterSASCode!!.visibility = View.VISIBLE
                        sasCodeError!!.visibility = View.INVISIBLE
                        sasCodeEditText!!.requestFocus()
                        val imm =
                            activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(sasCodeEditText, InputMethodManager.SHOW_IMPLICIT)
                    }
                }

            if (calculatedSasCode != null) {
                calculatedSasCode!!.observe(activity, sasObserver)
            }

            /**live data for service closing : get notified when service is closing due to an error */
            isServiceClosed = webClientService?.serviceClosingLiveData

            isServiceClosed?.observe(activity, object : Observer<Boolean?> {
                override fun onChanged(serviceClosed: Boolean?) {
                    if (serviceClosed == true) {
                        isServiceClosed?.removeObserver(this)
                        unBind()
                        activity.onBackPressed()
                    }
                }
            })

        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
            isBound = false
            activity.onBackPressed()
        }
    }

    fun validateSasCode() {
        if (webClientService == null) {
            return
        }
        val sas = sasCodeEditText!!.text.toString()
        val count = sasCodeEditText!!.text.length
        if (count == 4) {
            if (webClientService?.verifySasCode(sas) == true) { //WebClientManager also changes its state in the function called by verifySasCode
                enterSASCode!!.visibility = View.INVISIBLE
                protocolSuccess!!.visibility = View.VISIBLE
                //close activity after 3 seconds
                closeActivity = Runnable {
                    unBind()
                    activity.finish()
                }
                timeOutCloseActivity = Handler(Looper.getMainLooper())
                timeOutCloseActivity!!.postDelayed(closeActivity!!, 3000)

                val imm =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm?.hideSoftInputFromWindow(enterSASCode!!.windowToken, 0)
            } else {
                val shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake)
                sasCodeEditText!!.startAnimation(shakeAnimation)
                sasCodeEditText!!.setSelection(0, sasCodeEditText!!.text.length)
                sasCodeError!!.visibility = View.VISIBLE
            }
        }
    }

    override fun onClick(v: View) {
        if (v.id == R.id.back_button) {
            unBind()
            activity.onBackPressed()
        }
    }

    fun unBind() {
        if (isBound) {
            activity.unbindService(serviceConnection)
            isBound = false
            // manually call the onUnbind method in case unbinding from the UnifiedForegroundService is not enough (onUnbind called when all connections are unbound)
            webClientService?.onUnbind()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //remove callbacks in case the back button was pressed before delay
        if (timeOutCloseActivity != null) {
            timeOutCloseActivity!!.removeCallbacks(closeActivity!!)
            timeOutCloseActivity = null
        }
        if (eventBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(activity)
                .unregisterReceiver(eventBroadcastReceiver!!)
            eventBroadcastReceiver = null
        }
        unBind()
    }

    internal inner class EventBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null) {
                return
            }
            if (WebClientSubService.READY_FOR_BIND_BROADCAST_ACTION == intent.action) {
                //bind activity to service
                val bindIntent = Intent(activity, UnifiedForegroundService::class.java)
                bindIntent.putExtra(
                    UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA,
                    UnifiedForegroundService.SUB_SERVICE_WEB_CLIENT
                )
                activity.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)

                // unregister receiver as soon as one broadcast intent is received
                if (eventBroadcastReceiver != null) {
                    LocalBroadcastManager.getInstance(activity).unregisterReceiver(
                        eventBroadcastReceiver!!
                    )
                    eventBroadcastReceiver = null
                }
            }
        }
    }
}