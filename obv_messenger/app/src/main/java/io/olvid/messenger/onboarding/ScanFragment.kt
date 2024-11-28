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
package io.olvid.messenger.onboarding

import android.animation.ObjectAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation.findNavController
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.fragments.QRCodeScannerFragment
import io.olvid.messenger.fragments.QRCodeScannerFragment.ResultHandler

class ScanFragment : Fragment(), OnClickListener,
    ResultHandler {
    private lateinit var activity: AppCompatActivity
    val viewModel: OnboardingViewModel by activityViewModels()
    var rootView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_scan, container, false)
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

        rootView = view
        view.findViewById<View>(R.id.back_button).setOnClickListener(this)
        view.findViewById<View>(R.id.more_button).setOnClickListener(this)

        val qrCodeScannerFragment = QRCodeScannerFragment()
        qrCodeScannerFragment.setResultHandler(this)

        view.findViewById<View>(R.id.switch_camera_button)
            .setOnClickListener { qrCodeScannerFragment.switchCamera() }

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.scanner_fragment_placeholder, qrCodeScannerFragment)
        transaction.commit()
    }

    override fun onClick(v: View) {
        if (v.id == R.id.back_button) {
            activity.onBackPressed()
        } else if (v.id == R.id.more_button) {
            val popup = PopupMenu(activity, v, Gravity.END)
            popup.inflate(R.menu.popup_more_onboarding)
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                if (menuItem.itemId == R.id.popup_action_import_from_clipboard) {
                    val clipboard =
                        App.getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                    if (clipboard != null) {
                        val clipData = clipboard.primaryClip
                        if ((clipData != null) && (clipData.itemCount > 0)) {
                            val text = clipData.getItemAt(0).text
                            if (text != null) {
                                val matcher =
                                    ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text)
                                if (matcher.find() && viewModel.parseScannedConfigurationUri(
                                        matcher.group(2)
                                    )
                                ) {
                                    viewModel.isDeepLinked = true
                                    if (viewModel.keycloakServer != null) {
                                        findNavController(v).navigate(R.id.action_keycloak_scanned)
                                    } else {
                                        findNavController(v).navigate(R.id.action_configuration_scanned)
                                    }
                                    return@setOnMenuItemClickListener true
                                }
                            }
                        }
                    }
                    App.toast(
                        R.string.toast_message_invalid_clipboard_data,
                        Toast.LENGTH_SHORT
                    )
                    return@setOnMenuItemClickListener true
                } else if (menuItem.itemId == R.id.popup_action_manual_configuration) {
                    findNavController(v).navigate(R.id.action_configuration_scanned)
                } else if (menuItem.itemId == R.id.popup_action_use_keycloak) {
                    findNavController(v).navigate(R.id.action_keycloak_scanned)
                }
                false
            }
            popup.show()
        }
    }

    override fun handleResult(text: String): Boolean {
        val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v?.vibrate(100)
        val matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text)
        if (matcher.find() && viewModel.parseScannedConfigurationUri(matcher.group(2))) {
            viewModel.isDeepLinked = true
            if (viewModel.keycloakServer != null) {
                activity.runOnUiThread { rootView?.let { findNavController(it).navigate(R.id.action_keycloak_scanned) } }
            } else {
                activity.runOnUiThread { rootView?.let { findNavController(it).navigate(R.id.action_configuration_scanned) } }
            }
            return true
        }

        App.toast(R.string.toast_message_unrecognized_url, Toast.LENGTH_SHORT, Gravity.BOTTOM)
        return false
    }
}
