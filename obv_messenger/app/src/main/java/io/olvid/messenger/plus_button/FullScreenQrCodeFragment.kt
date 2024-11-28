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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.olvid.messenger.App
import io.olvid.messenger.R


class FullScreenQrCodeFragment : Fragment() {
    private val viewModel : PlusButtonViewModel by activityViewModels()
    private lateinit var activity: AppCompatActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plus_button_full_screen_qr_code, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnClickListener { activity.onBackPressed() }
        view.findViewById<View>(R.id.back_button)?.apply {
            setOnClickListener { activity.onBackPressed() }
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.updateLayoutParams<MarginLayoutParams> {
                    updateMargins(top = insets.top)
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

        App.setQrCodeImage(
            view.findViewById(R.id.qr_code_image_view),
            viewModel.fullScreenQrCodeUrl ?: ""
        )
    }

    override fun onStop() {
        super.onStop()
        // restore initial screen brightness
        val params = activity.window.attributes
        if (params != null && activity.window != null) {
            params.screenBrightness = -1.0f
            activity.window.attributes = params
        }
    }
}
