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
package io.olvid.messenger.customClasses

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.discussion.message.attachments.PdfViewerScreen
import io.olvid.messenger.settings.SettingsActivity.Companion.preventScreenCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PdfViewerDialog : DialogFragment() {
    companion object {
        private const val MESSAGE_ID_KEY = "message_id"
        private const val FYLE_ID_KEY = "fyle_id"

        @JvmStatic
        fun newInstance(fyleAndStatus: FyleAndStatus) = PdfViewerDialog().apply {
            arguments = bundleOf(
                MESSAGE_ID_KEY to fyleAndStatus.fyleMessageJoinWithStatus.messageId,
                FYLE_ID_KEY to fyleAndStatus.fyleMessageJoinWithStatus.fyleId,
            )
        }
    }

    private var fyleAndStatus: FyleAndStatus? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val messageId = requireArguments().getLong(MESSAGE_ID_KEY)
        val fyleId = requireArguments().getLong(FYLE_ID_KEY)
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndStatus(messageId, fyleId)
            }
            if (loaded == null) {
                dismissAllowingStateLoss()
            } else {
                fyleAndStatus = loaded
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.AppTheme_DialogFullScreen
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            if (preventScreenCapture()) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
            // without an explicit soft input mode the dialog window falls back to adjustPan,
            // panning the whole document when the search keyboard opens; go edge-to-edge so
            // the IME is delivered as insets and the window (and zoom math) stays stable
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.setDecorFitsSystemWindows(window, false)
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
        return ComposeView(inflater.context).apply {
            consumeWindowInsets = false
            setContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorResource(R.color.lightGrey)
                ) {
                    fyleAndStatus?.let {
                        PdfViewerScreen(Modifier.fillMaxSize(), pdfFyleAndStatus = it, onClose = { dismiss() } )
                    }
                }
            }
        }
    }
}
