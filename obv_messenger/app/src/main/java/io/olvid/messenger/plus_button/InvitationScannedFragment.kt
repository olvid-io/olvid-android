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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.OwnedIdentity

class InvitationScannedFragment : Fragment(), OnClickListener {
    private lateinit var activity: AppCompatActivity
    private val viewModel: PlusButtonViewModel by activityViewModels()

    private var contactInitialView: InitialView? = null
    private var contactNameTextView: TextView? = null
    private var inviteWarningTextView: TextView? = null
    private var cardView: View? = null
    private var mutualScanExplanationTextView: TextView? = null
    private var mutualScanQrCodeImageView: ImageView? = null
    private var inviteContactButton: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_plus_button_invitation_scanned, container, false)

        if (!viewModel.isDeepLinked) {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        viewModel.setMutualScanUrl(null, null)
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

        // set screen brightness to the max
        val params = activity.window.attributes
        if (params != null && activity.window != null) {
            params.screenBrightness = 1f
            activity.window.attributes = params
        }

        view.findViewById<View>(R.id.back_button).setOnClickListener(this)

        contactInitialView = view.findViewById(R.id.contact_initial_view)
        contactNameTextView = view.findViewById(R.id.contact_name_text_view)
        inviteWarningTextView = view.findViewById(R.id.invite_warning_text_view)
        inviteContactButton = view.findViewById(R.id.invite_contact_button)
        cardView = view.findViewById(R.id.mutual_scan_card_view)
        mutualScanExplanationTextView = view.findViewById(R.id.mutual_scan_explanation_text_view)
        mutualScanQrCodeImageView = view.findViewById(R.id.qr_code_image_view)

        view.findViewById<View>(R.id.mutual_scan_card_view).setOnClickListener(this)

        val uri = viewModel.scannedUri
        if (uri == null) {
            activity.finish()
            return
        }

        var contactUrlIdentity: ObvUrlIdentity? = null
        val matcher = ObvLinkActivity.INVITATION_PATTERN.matcher(uri)
        if (matcher.find()) {
            contactUrlIdentity = ObvUrlIdentity.fromUrlRepresentation(uri)
        }
        if (contactUrlIdentity == null || viewModel.currentIdentity == null) {
            activity.finish()
            return
        }

        val finalContactUrlIdentity: ObvUrlIdentity = contactUrlIdentity
        displayContact(viewModel.currentIdentity, finalContactUrlIdentity)
    }

    private fun displayContact(ownedIdentity: OwnedIdentity?, contactUrlIdentity: ObvUrlIdentity) {
        if (ownedIdentity == null) {
            return
        }

        if (ownedIdentity.bytesOwnedIdentity.contentEquals(contactUrlIdentity.bytesIdentity)) {
            viewModel.setMutualScanUrl(null, null)
            displaySelfInvite(ownedIdentity)
        } else {
            try {
                val identityDetails = ownedIdentity.getIdentityDetails()
                viewModel.setMutualScanUrl(
                    AppSingleton.getEngine().computeMutualScanSignedNonceUrl(
                        contactUrlIdentity.bytesIdentity,
                        ownedIdentity.bytesOwnedIdentity,
                        if ((identityDetails != null)) identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                            false
                        ) else ownedIdentity.displayName
                    ),
                    contactUrlIdentity.bytesIdentity
                )
                viewModel.isDismissOnMutualScanFinished = true
            } catch (e: Exception) {
                viewModel.setMutualScanUrl(null, null)
            }
            displayContact(ownedIdentity, contactUrlIdentity, viewModel.mutualScanUrl)
        }
    }

    private fun displayContact(
        ownedIdentity: OwnedIdentity,
        contactUrlIdentity: ObvUrlIdentity,
        mutualScanUrl: ObvMutualScanUrl?
    ) {
        val contactShortName =
            StringUtils.removeCompanyFromDisplayName(contactUrlIdentity.displayName)
        contactNameTextView!!.text = contactUrlIdentity.displayName
        val contactCacheInfo = ContactCacheSingleton.getContactCacheInfo(contactUrlIdentity.bytesIdentity)

        if (contactCacheInfo != null) {
            contactInitialView!!.setFromCache(contactUrlIdentity.bytesIdentity)

            inviteWarningTextView!!.visibility = View.VISIBLE
            inviteWarningTextView!!.setBackgroundResource(R.drawable.background_ok_message)
            inviteWarningTextView!!.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_ok_outline,
                0,
                0,
                0
            )
            if (contactCacheInfo.oneToOne) {
                inviteWarningTextView!!.text = activity.getString(
                    R.string.text_explanation_warning_mutual_scan_contact_already_known,
                    contactShortName
                )
            } else {
                inviteWarningTextView!!.text = activity.getString(
                    R.string.text_explanation_warning_mutual_scan_contact_already_known_not_one_to_one,
                    contactShortName
                )
            }
        } else {
            contactInitialView!!.setInitial(
                contactUrlIdentity.bytesIdentity,
                StringUtils.getInitial(contactUrlIdentity.displayName)
            )
            inviteWarningTextView!!.visibility = View.GONE
        }


        if (mutualScanUrl != null) {
            cardView!!.visibility = View.VISIBLE
            mutualScanExplanationTextView!!.text =
                activity.getString(R.string.text_explanation_mutual_scan, contactShortName)
            App.setQrCodeImage(mutualScanQrCodeImageView!!, mutualScanUrl.urlRepresentation)
        } else {
            cardView!!.visibility = View.GONE
        }

        inviteContactButton!!.setOnClickListener { v: View? ->
            try {
                AppSingleton.getEngine().startTrustEstablishmentProtocol(
                    contactUrlIdentity.bytesIdentity,
                    contactUrlIdentity.displayName,
                    ownedIdentity.bytesOwnedIdentity
                )
                activity.finish()
            } catch (e: Exception) {
                App.toast(
                    R.string.toast_message_failed_to_invite_contact,
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    private fun displaySelfInvite(ownedIdentity: OwnedIdentity) {
        contactInitialView!!.setOwnedIdentity(ownedIdentity)
        contactNameTextView!!.text = ownedIdentity.displayName

        inviteWarningTextView!!.visibility = View.VISIBLE
        inviteWarningTextView!!.setBackgroundResource(R.drawable.background_error_message)
        inviteWarningTextView!!.setCompoundDrawablesRelativeWithIntrinsicBounds(
            R.drawable.ic_error_outline,
            0,
            0,
            0
        )
        inviteWarningTextView!!.setText(R.string.text_explanation_warning_cannot_invite_yourself)

        cardView!!.visibility = View.GONE
        inviteContactButton!!.setOnClickListener(null)
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.back_button) {
            activity.onBackPressed()
        } else if (id == R.id.mutual_scan_card_view) {
            if (viewModel.mutualScanUrl != null) {
                viewModel.fullScreenQrCodeUrl = viewModel.mutualScanUrl!!.urlRepresentation
                try {
                    findNavController(v).navigate(R.id.action_open_full_screen_qr_code)
                } catch (e: Exception) {
                    // do nothing
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.isDismissOnMutualScanFinished = false

        // restore initial brightness
        val params = activity.window.attributes
        if (params != null && activity.window != null) {
            params.screenBrightness = -1.0f
            activity.window.attributes = params
        }
    }
}
