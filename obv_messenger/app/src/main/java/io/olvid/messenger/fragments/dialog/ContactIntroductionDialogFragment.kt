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
package io.olvid.messenger.fragments.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager.LayoutParams
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.DialogFragment
import io.olvid.messenger.R
import io.olvid.messenger.contact.ContactIntroductionScreen
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.settings.SettingsActivity

class ContactIntroductionDialogFragment : DialogFragment() {
    private val bytesOwnedIdentity by lazy { arguments?.getByteArray(BYTES_OWNED_IDENTITY_KEY) }
    private val bytesContactIdentity by lazy { arguments?.getByteArray(BYTES_CONTACT_IDENTITY_KEY) }
    private val displayName by lazy { arguments?.getString(DISPLAY_NAME_KEY) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .background(colorResource(R.color.almostWhite))
                        .fillMaxSize()
                ) {
                    OlvidTopAppBar(
                        titleText = stringResource(
                                R.string.dialog_title_introduce_contact,
                                displayName.orEmpty()
                            ),
                        onBackPressed = { dismiss() }
                    )
                    ContactIntroductionScreen(
                        bytesOwnedIdentity = bytesOwnedIdentity ?: ByteArray(0),
                        bytesContactIdentity = bytesContactIdentity ?: ByteArray(0),
                        displayName = displayName.orEmpty(),
                        onDone = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        private const val BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"
        private const val BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"
        private const val DISPLAY_NAME_KEY = "display_name"

        @JvmStatic
        fun newInstance(
            bytesOwnedIdentity: ByteArray?,
            bytesContactIdentityA: ByteArray?,
            displayNameA: String?
        ): ContactIntroductionDialogFragment {
            val fragment = ContactIntroductionDialogFragment()
            val args = Bundle()
            args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity)
            args.putByteArray(BYTES_CONTACT_IDENTITY_KEY, bytesContactIdentityA)
            args.putString(DISPLAY_NAME_KEY, displayNameA)
            fragment.arguments = args
            return fragment
        }
    }
}