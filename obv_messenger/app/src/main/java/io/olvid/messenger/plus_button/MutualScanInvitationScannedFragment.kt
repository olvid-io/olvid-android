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
package io.olvid.messenger.plus_button

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion

class MutualScanInvitationScannedFragment : Fragment(), OnClickListener,
    Observer<Discussion?> {
    lateinit var activity: AppCompatActivity
    val viewModel: PlusButtonViewModel by activityViewModels()

    var spinner: View? = null
    var message: TextView? = null
    var dismissButton: View? = null
    var discussButton: TextView? = null

    var contactShortDisplayName: String? = null

    var timeOutFired: Boolean = false
    var mutualScanFinishedListener: SimpleEngineNotificationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_plus_button_mutual_scan_invitation_scanned,
            container,
            false
        )
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

        val uri = viewModel.scannedUri
        if (uri == null) {
            activity.finish()
            return
        }

        val mutualScanUrl: ObvMutualScanUrl?
        val matcher = ObvMutualScanUrl.MUTUAL_SCAN_PATTERN.matcher(uri)
        mutualScanUrl = if (matcher.find()) {
            ObvMutualScanUrl.fromUrlRepresentation(uri)
        } else {
            null
        }
        if (mutualScanUrl == null || viewModel.currentIdentity == null) {
            activity.finish()
            return
        }

        contactShortDisplayName =
            StringUtils.removeCompanyFromDisplayName(mutualScanUrl.displayName)

        spinner = view.findViewById(R.id.mutual_scan_spinner)
        message = view.findViewById(R.id.mutual_scan_explanation_text_view)
        dismissButton = view.findViewById(R.id.dismiss_button)
        discussButton = view.findViewById(R.id.discuss_button)

        view.findViewById<View>(R.id.back_button).setOnClickListener(this)
        dismissButton?.setOnClickListener(this)


        val bytesOwnedIdentity = viewModel.currentIdentity?.bytesOwnedIdentity
        val bytesContactIdentity = mutualScanUrl.bytesIdentity

        if (bytesContactIdentity.contentEquals(bytesOwnedIdentity)) {
            displaySelfInvite()
        } else {
            if (bytesOwnedIdentity != null
                && AppSingleton.getEngine()
                    .verifyMutualScanSignedNonceUrl(bytesOwnedIdentity, mutualScanUrl)
            ) {
                App.runThread {
                    // check the discussion to start the correct listener
                    val discussion =
                        AppDatabase.getInstance().discussionDao()
                            .getByContactWithAnyStatus(bytesOwnedIdentity, bytesContactIdentity)

                    if (discussion == null || !discussion.isNormal) {
                        // not a contact yet or not a one-to-one contact --> listen to the discussion creation
                        val discussionLiveData =
                            AppDatabase.getInstance().discussionDao()
                                .getByContactLiveData(bytesOwnedIdentity, bytesContactIdentity)
                        Handler(Looper.getMainLooper()).post {
                            discussionLiveData.observe(
                                viewLifecycleOwner, this
                            )
                        }
                    } else {
                        // listen to protocol notification
                        mutualScanFinishedListener = object :
                            SimpleEngineNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED) {
                            override fun callback(userInfo: HashMap<String, Any>) {
                                val notificationBytesOwnedIdentity =
                                    userInfo[EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY] as ByteArray?
                                val notificationBytesContactIdentity =
                                    userInfo[EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY] as ByteArray?

                                if (bytesOwnedIdentity.contentEquals(notificationBytesOwnedIdentity) && bytesContactIdentity.contentEquals(
                                        notificationBytesContactIdentity
                                    )
                                ) {
                                    Handler(Looper.getMainLooper()).post {
                                        onChanged(
                                            discussion
                                        )
                                    }
                                }
                            }
                        }
                        AppSingleton.getEngine().addNotificationListener(
                            EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED,
                            mutualScanFinishedListener
                        )
                    }


                    // start the protocol
                    try {
                        AppSingleton.getEngine().startMutualScanTrustEstablishmentProtocol(
                            bytesOwnedIdentity,
                            bytesContactIdentity,
                            mutualScanUrl.signature
                        )
                    } catch (ignored: Exception) {
                        App.toast(
                            R.string.toast_message_failed_to_invite_contact,
                            Toast.LENGTH_SHORT
                        )
                        activity.finish()
                    }

                    // start a timeout to say the protocol was started
                    timeOutFired = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!timeOutFired) {
                            timeOutFired = true
                            viewModel.isDismissOnMutualScanFinished = false
                            spinner?.setVisibility(View.GONE)
                            message?.setText(
                                getString(
                                    R.string.text_explanation_mutual_scan_pending,
                                    contactShortDisplayName
                                )
                            )
                            message?.setVisibility(View.VISIBLE)
                            dismissButton?.setVisibility(View.VISIBLE)
                        }
                    }, 5000)
                }
            } else {
                displayBadSignature()
            }
        }
    }

    private fun displaySelfInvite() {
        spinner?.visibility = View.GONE
        message?.setText(R.string.text_explanation_warning_cannot_invite_yourself)
        message?.visibility = View.VISIBLE
        dismissButton?.visibility = View.VISIBLE
    }

    private fun displayBadSignature() {
        spinner?.visibility = View.GONE
        message?.text =
            getString(
                R.string.text_explanation_invalid_mutual_scan_qr_code,
                contactShortDisplayName
            )
        message?.visibility = View.VISIBLE
        dismissButton?.visibility = View.VISIBLE
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.back_button) {
            activity.onBackPressed()
        }
        if (id == R.id.dismiss_button) {
            activity.finish()
        }
    }

    override fun onDetach() {
        super.onDetach()
    }

    override fun onStop() {
        super.onStop()
        timeOutFired = true
        if (mutualScanFinishedListener != null) {
            AppSingleton.getEngine().removeNotificationListener(
                EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED,
                mutualScanFinishedListener
            )
        }
    }

    override fun onChanged(discussion: Discussion?) {
        if (discussion != null) {
            // this is the listener to switch to the correct discussion once contact addition is done
            if (timeOutFired) {
                message?.text =
                    getString(
                        R.string.text_explanation_mutual_scan_success,
                        contactShortDisplayName
                    )
                dismissButton?.visibility = View.GONE
                discussButton?.text =
                    getString(R.string.button_label_discuss_with, contactShortDisplayName)
                discussButton?.visibility = View.VISIBLE
                discussButton?.setOnClickListener { v: View? ->
                    App.openDiscussionActivity(activity, discussion.id)
                    activity.finish()
                }
            } else {
                App.openDiscussionActivity(activity, discussion.id)
                activity.finish()
            }
        }
    }
}
