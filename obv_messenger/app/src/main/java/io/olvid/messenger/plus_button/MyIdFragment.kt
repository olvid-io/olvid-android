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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.databases.entity.OwnedIdentity

class MyIdFragment : Fragment(), OnClickListener {
    private lateinit var activity: AppCompatActivity
    private val viewModel: PlusButtonViewModel by activityViewModels()
    private var identityInitialView: InitialView? = null
    private var identityTextView: TextView? = null
    private var qrCodeImageView: ImageView? = null

    private var scanButton: Button? = null
    private var keycloakSearchButton: Button? = null

    private lateinit var urlIdentity: ObvUrlIdentity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_plus_button_my_id, container, false)
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

        view.findViewById<View>(R.id.back_button).setOnClickListener(this)
        identityInitialView = view.findViewById(R.id.myid_initial_view)
        identityTextView = view.findViewById(R.id.myid_name_text_view)
        val myIdCardView = view.findViewById<CardView>(R.id.my_id_card_view)
        qrCodeImageView = view.findViewById(R.id.qr_code_image_view)
        val shareButton = view.findViewById<Button>(R.id.share_button)
        scanButton = view.findViewById(R.id.scan_button)
        val moreButton = view.findViewById<ImageView>(R.id.more_button)
        keycloakSearchButton = view.findViewById(R.id.button_keycloak_search)

        myIdCardView.setOnClickListener(this)
        shareButton.setOnClickListener(this)
        scanButton?.setOnClickListener(this)
        moreButton.setOnClickListener(this)
        keycloakSearchButton?.setOnClickListener(this)

        displayIdentity(viewModel.currentIdentity)
    }

    private fun displayIdentity(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) {
            identityInitialView?.setUnknown()
            identityTextView?.text = null
            qrCodeImageView?.setImageResource(R.drawable.ic_broken_image)
            scanButton?.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_camera,
                0,
                R.drawable.empty,
                0
            )
            keycloakSearchButton?.visibility = View.GONE
        } else {
            if (ownedIdentity.keycloakManaged) {
                scanButton?.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_camera,
                    0,
                    0,
                    0
                )
                keycloakSearchButton?.visibility = View.VISIBLE
            } else {
                scanButton?.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_camera,
                    0,
                    R.drawable.empty,
                    0
                )
                keycloakSearchButton?.visibility = View.GONE
            }
            identityInitialView?.setOwnedIdentity(ownedIdentity)
            identityTextView?.text = ownedIdentity.displayName

            val identityDetails = ownedIdentity.getIdentityDetails()
            urlIdentity = if (identityDetails != null) {
                ObvUrlIdentity(
                    ownedIdentity.bytesOwnedIdentity,
                    identityDetails.formatDisplayName(
                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                        false
                    )
                )
            } else {
                ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName)
            }
            qrCodeImageView?.let { App.setQrCodeImage(it, urlIdentity.urlRepresentation) }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.back_button -> {
                activity.onBackPressed()
            }
            R.id.my_id_card_view -> {
                viewModel.fullScreenQrCodeUrl = urlIdentity.urlRepresentation
                try {
                    findNavController(v).navigate(R.id.action_open_full_screen_qr_code)
                } catch (e: Exception) {
                    // do nothing
                }
            }
            R.id.scan_button -> {
                try {
                    findNavController(v).navigate(R.id.action_scan)
                } catch (e: Exception) {
                    // do nothing
                }
            }
            R.id.share_button -> {
                val intent = Intent(Intent.ACTION_SEND)
                intent.setType("text/plain")
                val ownedIdentity = viewModel.currentIdentity ?: return
                val urlIdentity: ObvUrlIdentity
                val inviteName: String
                val identityDetails = ownedIdentity.getIdentityDetails()
                if (identityDetails != null) {
                    urlIdentity = ObvUrlIdentity(
                        ownedIdentity.bytesOwnedIdentity,
                        identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                            false
                        )
                    )
                    inviteName = identityDetails.formatDisplayName(
                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                        false
                    )
                } else {
                    urlIdentity =
                        ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName)
                    inviteName = ownedIdentity.displayName
                }
                intent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.message_user_invitation_subject, inviteName)
                )
                intent.putExtra(
                    Intent.EXTRA_TEXT,
                    getString(
                        R.string.message_user_invitation,
                        inviteName,
                        urlIdentity.urlRepresentation
                    )
                )
                startActivity(Intent.createChooser(intent, getString(R.string.title_invite_chooser)))
                activity.finish()
            }
            R.id.more_button -> {
                val popup = PopupMenu(activity, v, Gravity.END)
                popup.inflate(R.menu.popup_more_add_contact)
                popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                    if (menuItem.itemId == R.id.popup_action_import_from_clipboard) {
                        (App.getContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.let {  clipboard ->
                            val clipData = clipboard.primaryClip
                            if ((clipData != null) && (clipData.itemCount > 0)) {
                                val textChars = clipData.getItemAt(0).text
                                if (textChars != null) {
                                    val matcher =
                                        ObvLinkActivity.ANY_PATTERN.matcher(textChars)
                                    if (matcher.find()) {
                                        val text = textChars.toString()
                                        if (ObvLinkActivity.INVITATION_PATTERN.matcher(text).find()) {
                                            viewModel.scannedUri = text
                                            viewModel.isDeepLinked = true
                                            findNavController(v).navigate(R.id.invitation_scanned)
                                            return@setOnMenuItemClickListener true
                                        } else if (ObvLinkActivity.MUTUAL_SCAN_PATTERN.matcher(text)
                                                .find()
                                        ) {
                                            viewModel.scannedUri = text
                                            viewModel.isDeepLinked = true
                                            findNavController(v).navigate(R.id.mutual_scan_invitation_scanned)
                                            return@setOnMenuItemClickListener true
                                        } else if (ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text)
                                                .find()
                                        ) {
                                            viewModel.scannedUri = text
                                            viewModel.isDeepLinked = true
                                            findNavController(v).navigate(R.id.configuration_scanned)
                                            return@setOnMenuItemClickListener true
                                        } else if (ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(text)
                                                .find()
                                        ) {
                                            viewModel.scannedUri = text
                                            viewModel.isDeepLinked = true
                                            findNavController(v).navigate(R.id.webclient_scanned)
                                            return@setOnMenuItemClickListener true
                                        }
                                    }
                                }
                            }
                        }
                        App.toast(
                            R.string.toast_message_invalid_clipboard_data,
                            Toast.LENGTH_SHORT
                        )
                        return@setOnMenuItemClickListener true
                    }
                    false
                }
                popup.show()
            }
            R.id.button_keycloak_search -> {
                try {
                    findNavController(v).navigate(R.id.action_keycloak_search)
                } catch (e: Exception) {
                    // do nothing
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // set screen brightness to the max
        val params = activity.window.attributes
        if (params != null && activity.window != null) {
            params.screenBrightness = 1f
            activity.window.attributes = params
        }
    }

    override fun onStop() {
        super.onStop()
        // restore initial brightness
        val params = activity.window.attributes
        if (params != null && activity.window != null) {
            params.screenBrightness = -1.0f
            activity.window.attributes = params
        }
    }
}
