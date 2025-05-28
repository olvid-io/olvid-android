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

import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.navigation.fragment.NavHostFragment
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity

class PlusButtonActivity : LockableActivity(), EngineNotificationListener {
    private val plusButtonViewModel: PlusButtonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        plusButtonViewModel.currentIdentity = AppSingleton.getCurrentIdentityLiveData().value

        if (plusButtonViewModel.currentIdentity == null) {
            finish()
            return
        }

        AppSingleton.getCurrentIdentityLiveData().observe(
            this
        ) { ownedIdentity: OwnedIdentity ->
            if (ownedIdentity == plusButtonViewModel.currentIdentity) {
                plusButtonViewModel.currentIdentity = ownedIdentity
            } else {
                finish()
            }
        }

        setContentView(R.layout.activity_plus_button)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        findViewById<ConstraintLayout>(R.id.activity_plus_button_constraint_layout)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    updateMargins(bottom = insets.bottom)
                }
                windowInsets
            }
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?

        val intent = intent
        if (navHostFragment != null && intent.hasExtra(LINK_URI_INTENT_EXTRA)) {
            val uri = intent.getStringExtra(LINK_URI_INTENT_EXTRA)
            if (uri != null) {
                if (ObvLinkActivity.MUTUAL_SCAN_PATTERN.matcher(uri).find()) { // first check for mutual scan as INVITATION_PATTERN includes MUTUAL_SCAN_PATTERN
                    plusButtonViewModel.scannedUri = uri
                    plusButtonViewModel.isDeepLinked = true
                    navHostFragment.navController.popBackStack()
                    navHostFragment.navController.navigate(R.id.mutual_scan_invitation_scanned)
                } else if (ObvLinkActivity.INVITATION_PATTERN.matcher(uri).find()) {
                    plusButtonViewModel.scannedUri = uri
                    plusButtonViewModel.isDeepLinked = true
                    navHostFragment.navController.popBackStack()
                    navHostFragment.navController.navigate(R.id.invitation_scanned)
                } else if (ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri).find()) {
                    plusButtonViewModel.scannedUri = uri
                    plusButtonViewModel.isDeepLinked = true
                    navHostFragment.navController.popBackStack()
                    navHostFragment.navController.navigate(R.id.configuration_scanned)
                } else if (ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(uri).find()) {
                    plusButtonViewModel.scannedUri = uri
                    plusButtonViewModel.isDeepLinked = true
                    navHostFragment.navController.popBackStack()
                    navHostFragment.navController.navigate(R.id.webclient_scanned)
                }
            }
        }

        engineNotificationRegistrationNumber = null
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED,
            this
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED,
            this
        )
    }


    private var engineNotificationRegistrationNumber: Long? = null

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        if (EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED == notificationName) {
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
                            this
                        ) { contact: Contact? ->
                            if (contact != null) {
                                finish()
                                App.openOneToOneDiscussionActivity(
                                    this,
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

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        engineNotificationRegistrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return engineNotificationRegistrationNumber ?: -1
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return engineNotificationRegistrationNumber != null
    }

    companion object {
        const val LINK_URI_INTENT_EXTRA: String = "link_uri"
    }
}
