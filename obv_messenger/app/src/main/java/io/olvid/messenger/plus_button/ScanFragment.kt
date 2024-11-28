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
import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation.findNavController
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.fragments.QRCodeScannerFragment
import io.olvid.messenger.fragments.QRCodeScannerFragment.ResultHandler

class ScanFragment : Fragment(), OnClickListener,
    ResultHandler {
    var activity: AppCompatActivity? = null
    val viewModel: PlusButtonViewModel by activityViewModels()
    lateinit var rootView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plus_button_scan, container, false)
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
        view.findViewById<Button>(R.id.show_my_id_button).setOnClickListener(this)

        val qrCodeScannerFragment = QRCodeScannerFragment()
        qrCodeScannerFragment.setResultHandler(this)

        view.findViewById<View>(R.id.switch_camera_button)
            .setOnClickListener { v: View? -> qrCodeScannerFragment.switchCamera() }

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.scanner_fragment_placeholder, qrCodeScannerFragment)
        transaction.commit()
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.back_button || id == R.id.show_my_id_button) {
            activity?.onBackPressed()
        }
    }

    override fun handleResult(text: String): Boolean {
        (activity?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(100)
        var matcher = ObvLinkActivity.INVITATION_PATTERN.matcher(text)
        if (matcher.find()) {
            viewModel.scannedUri = matcher.group(0)
            Handler(Looper.getMainLooper()).post {
                findNavController(
                    rootView
                ).navigate(R.id.action_scanned_invitation)
            }
            return true
        }

        matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text)
        if (matcher.find()) {
            viewModel.scannedUri = matcher.group(0)
            Handler(Looper.getMainLooper()).post {
                findNavController(
                    rootView
                ).navigate(R.id.action_scanned_configuration)
            }
            return true
        }

        matcher = ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(text)
        if (matcher.find()) {
            viewModel.scannedUri = matcher.group(0)
            Handler(Looper.getMainLooper()).post {
                findNavController(
                    rootView
                ).navigate(R.id.action_scanned_webclient)
            }
            return true
        }

        matcher = ObvMutualScanUrl.MUTUAL_SCAN_PATTERN.matcher(text)
        if (matcher.find()) {
            viewModel.scannedUri = matcher.group(0)
            Handler(Looper.getMainLooper()).post {
                findNavController(
                    rootView
                ).navigate(R.id.action_scanned_mutual_scan_invitation)
            }
            return true
        }

        App.toast(R.string.toast_message_unrecognized_url, Toast.LENGTH_SHORT, Gravity.BOTTOM)
        return false
    }
}
