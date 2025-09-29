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

package io.olvid.messenger.plus_button.scan

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.main.MainActivity.Companion.LINK_URI_INTENT_EXTRA
import io.olvid.messenger.plus_button.PlusButtonViewModel

const val SCAN_ONLY_EXTRA_KEY = "scan_only"

class ScanActivity : LockableActivity() {
    private val plusButtonViewModel: PlusButtonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scanOnly = intent.getBooleanExtra(SCAN_ONLY_EXTRA_KEY, false)
        plusButtonViewModel.currentIdentity = AppSingleton.getCurrentIdentityLiveData().value
        if (plusButtonViewModel.currentIdentity == null) {
            finish()
            return
        }
        intent.getStringExtra(LINK_URI_INTENT_EXTRA)?.let { linkUri ->
            plusButtonViewModel.scannedUri = linkUri
            plusButtonViewModel.isDeepLinked = true
            plusButtonViewModel.handleLink(this, linkUri)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(
                Color.Transparent.toArgb(),
                ContextCompat.getColor(this, R.color.blackOverlay)
            )
        )
        setContent {
            val mutualScanFinishedListener = remember {
                object : SimpleEngineNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED) {
                    override fun callback(userInfo: HashMap<String, Any>) {
                        plusButtonViewModel.mutualScanUrl?.let { mutualScanUrl ->
                            val signature =
                                userInfo[EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY] as ByteArray?
                            val bytesOwnedIdentity =
                                userInfo[EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY] as ByteArray?
                            val bytesContactIdentity =
                                userInfo[EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY] as ByteArray?

                            if (bytesOwnedIdentity != null
                                && bytesContactIdentity != null
                                && mutualScanUrl.bytesIdentity.contentEquals(bytesOwnedIdentity)
                                && plusButtonViewModel.mutualScanBytesContactIdentity.contentEquals(bytesContactIdentity)
                                && mutualScanUrl.signature.contentEquals(signature)
                            ) {
                                val contactLiveData = AppDatabase.getInstance().contactDao()
                                    .getAsync(bytesOwnedIdentity, bytesContactIdentity)
                                runOnUiThread {
                                    contactLiveData.observe(
                                        this@ScanActivity
                                    ) { contact: Contact? ->
                                        if (contact != null) {
                                            finish()
                                            App.openOneToOneDiscussionActivity(
                                                this@ScanActivity,
                                                bytesOwnedIdentity,
                                                bytesContactIdentity,
                                                true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DisposableEffect(Unit) {
                AppSingleton.getEngine().addNotificationListener(
                    EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED,
                    mutualScanFinishedListener
                )
                onDispose {
                    AppSingleton.getEngine().removeNotificationListener(
                        EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED,
                        mutualScanFinishedListener
                    )
                }
            }

            ScanScreen(
                onCancel = { finish() },
                scanOnly = scanOnly,
                plusButtonViewModel = plusButtonViewModel,
                activity = this
            )
        }
    }
}