/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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
package io.olvid.messenger.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult.ActionPerformed
import androidx.compose.material.SnackbarResult.Dismissed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.switchMap
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.ObvBase64
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.dimen
import io.olvid.messenger.R.id
import io.olvid.messenger.activities.ContactDetailsActivity
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.activities.storage_manager.StorageManagerActivity
import io.olvid.messenger.billing.BillingUtils
import io.olvid.messenger.customClasses.ConfigurationPojo
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.OpenHiddenProfileDialog
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.fragments.dialog.DiscussionSearchDialogFragment
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment.OnOwnedIdentitySelectedListener
import io.olvid.messenger.main.calls.CallLogFragment
import io.olvid.messenger.main.contacts.ContactListFragment
import io.olvid.messenger.main.discussions.DiscussionListFragment
import io.olvid.messenger.main.groups.GroupListFragment
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.onboarding.OnboardingActivity
import io.olvid.messenger.onboarding.flow.OnboardingFlowActivity
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity
import io.olvid.messenger.plus_button.PlusButtonActivity
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.DOT
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.FULL
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.LINE
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.NONE
import io.olvid.messenger.troubleshooting.TroubleshootingActivity
import io.olvid.messenger.troubleshooting.TroubleshootingDataStore
import io.olvid.messenger.troubleshooting.shouldShowTroubleshootingSnackbar
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : LockableActivity(), OnClickListener {
    private var root: CoordinatorLayout? = null
    private val ownInitialView: InitialView by lazy { findViewById(R.id.owned_identity_initial_view) }
    private var pingConnectivityDot: View? = null
    private var pingConnectivityLine: View? = null
    private var pingConnectivityFull: View? = null
    private var pingConnectivityFullTextView: TextView? = null
    private var pingConnectivityFullPingTextView: TextView? = null
    private var tabsPagerAdapter: TabsPagerAdapter? = null
    private val viewPager: ViewPager2 by lazy { findViewById(R.id.view_pager_container) }
    private var mainActivityPageChangeListener: MainActivityPageChangeListener? = null
    private val pingListener: PingListener by lazy { PingListener() }
    private var pingRed = 0
    private var pingGolden = 0
    private var pingGreen = 0
    internal var contactListFragment: ContactListFragment? = null
    private lateinit var tabImageViews: Array<ImageView?>

    @JvmField
    var requestNotificationPermission = registerForActivityResult(
        RequestPermission()
    ) {}

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launch { TroubleshootingDataStore(this@MainActivity).load() }
        App.runThread {
            val identityCount: Int = try {
                AppSingleton.getEngine().ownedIdentities.size
            } catch (e: Exception) {
                // if we fail to query ownIdentity count from Engine, fallback to querying on App side
                AppDatabase.getInstance().ownedIdentityDao().countAll()
            }
            if (identityCount == 0) {
                val onboardingIntent =
                    Intent(applicationContext, OnboardingFlowActivity::class.java)
                startActivity(onboardingIntent)
                finish()
            }
        }
        try {
            installSplashScreen()
        } catch (e: Exception) {
            setTheme(R.style.AppTheme_NoActionBar)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root_coordinator)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(false)
        val snackBarContainer = findViewById<ComposeView>(R.id.snackbar_container)
        snackBarContainer.setContent {
            val snackbarState = remember {
                SnackbarHostState()
            }
            LaunchedEffect(Unit) {
                if (savedInstanceState == null && shouldShowTroubleshootingSnackbar()) {
                    snackbarState.showSnackbar(
                        message = getString(R.string.snackbar_go_to_troubleshooting_message),
                        actionLabel = getString(R.string.snackbar_go_to_troubleshooting_button),
                        duration = SnackbarDuration.Long
                    ).let { snackbarResult ->
                        when (snackbarResult) {
                            Dismissed -> {}
                            ActionPerformed -> {
                                startActivity(
                                    Intent(
                                        this@MainActivity,
                                        TroubleshootingActivity::class.java
                                    )
                                )
                            }
                        }
                    }
                }
            }
            AppCompatTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    SnackbarHost(
                        hostState = snackbarState, modifier = Modifier
                            .widthIn(max = 400.dp)
                            .align(Alignment.BottomCenter)
                            .padding(vertical = 80.dp)
                    )
                }
            }
        }
        val gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            var scrolled = false
            override fun onDown(e: MotionEvent): Boolean {
                scrolled = false
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                OwnIdentitySelectorPopupWindow(this@MainActivity, ownInitialView).open()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scrolled) {
                    return false
                }
                scrolled = true
                AppSingleton.getInstance().selectNextIdentity(distanceX > 0)
                return true
            }
        })
        ownInitialView.setOnTouchListener { _, event: MotionEvent? ->
            gestureDetector.onTouchEvent(
                event!!
            )
        }
        pingConnectivityDot = findViewById(id.ping_indicator_dot)
        pingConnectivityLine = findViewById(id.ping_indicator_line)
        pingConnectivityFull = findViewById(id.ping_indicator_full)
        pingConnectivityFullTextView = findViewById(id.ping_indicator_full_text_view)
        pingConnectivityFullPingTextView = findViewById(id.ping_indicator_full_ping_text_view)
        pingRed = ContextCompat.getColor(this, color.red)
        pingGolden = ContextCompat.getColor(this, color.golden)
        pingGreen = ContextCompat.getColor(this, color.green)
        AppSingleton.getWebsocketConnectivityStateLiveData().observe(this, pingListener)
        tabsPagerAdapter = TabsPagerAdapter(
            this,
            findViewById(id.tab_discussions_notification_dot),
            findViewById(id.tab_contacts_notification_dot),
            findViewById(id.tab_groups_notification_dot),
            findViewById(id.tab_calls_notification_dot)
        )
        tabImageViews = arrayOfNulls(4)
        tabImageViews[0] = findViewById(id.tab_discussions_button)
        tabImageViews[1] = findViewById(id.tab_contacts_button)
        tabImageViews[2] = findViewById(id.tab_groups_button)
        tabImageViews[3] = findViewById(id.tab_calls_button)
        for (imageView in tabImageViews) {
            imageView?.setOnClickListener(this)
        }
        mainActivityPageChangeListener = MainActivityPageChangeListener(tabImageViews)
        viewPager.adapter = tabsPagerAdapter
        viewPager.isUserInputEnabled= false
        viewPager.registerOnPageChangeCallback(mainActivityPageChangeListener!!)
        viewPager.setPageTransformer(MarginPageTransformer(resources.getDimensionPixelSize(dimen.main_activity_page_margin)))
        viewPager.offscreenPageLimit = 3
        val addContactButton = findViewById<ImageView>(id.tab_plus_button)
        addContactButton.setOnClickListener(this)
        val focusHugger = findViewById<View>(id.focus_hugger)
        focusHugger.requestFocus()

        // observe owned Identity (for initial view)
        val ownedIdentityMutedImageView =
            findViewById<ImageView>(id.owned_identity_muted_marker_image_view)
        AppSingleton.getCurrentIdentityLiveData().observe(this) { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                App.runThread {
                    if (AppDatabase.getInstance().ownedIdentityDao().countAll() == 0) {
                        val onboardingIntent =
                            Intent(applicationContext, OnboardingFlowActivity::class.java)
                        startActivity(onboardingIntent)
                        finish()
                    }
                }
                ownInitialView.setUnknown()
                ownedIdentityMutedImageView.visibility = View.GONE
                return@observe
            }
            ownInitialView.setOwnedIdentity(ownedIdentity)
            if (ownedIdentity.shouldMuteNotifications()) {
                ownedIdentityMutedImageView.visibility = View.VISIBLE
            } else {
                ownedIdentityMutedImageView.visibility = View.GONE
            }
        }

        if (BuildConfig.USE_BILLING_LIB) {
            BillingUtils.initializeBillingClient(baseContext)
        }

        // observe unread messages
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().messageDao()
                .hasUnreadMessagesOrDiscussionsOrInvitations(ownedIdentity.bytesOwnedIdentity)
        }.observe(this) { unreadMessages: Boolean? ->
            if (unreadMessages != null && unreadMessages) {
                tabsPagerAdapter!!.showNotificationDot(DISCUSSIONS_TAB)
            } else {
                tabsPagerAdapter!!.hideNotificationDot(DISCUSSIONS_TAB)
            }
        }
        val unreadMarker = findViewById<ImageView>(id.owned_identity_unread_marker_image_view)
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().ownedIdentityDao()
                .otherNotHiddenOwnedIdentityHasMessageOrInvitation(ownedIdentity.bytesOwnedIdentity)
        }.observe(this) { hasUnread: Boolean? ->
            if (hasUnread != null && hasUnread) {
                unreadMarker.visibility = View.VISIBLE
            } else {
                unreadMarker.visibility = View.GONE
            }
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean(
                ALREADY_CREATED_BUNDLE_EXTRA,
                false
            )
        ) {
            intent = null
        } else {
            UnifiedForegroundService.finishAndRemoveExtraTasks(this)
        }
        handleIntent(intent)

        // check notifications permissions
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ALREADY_CREATED_BUNDLE_EXTRA, true)
    }

    private fun handleIntent(intent: Intent?) {
        AndroidNotificationManager.clearNeutralNotification()
        if (intent == null) {
            return
        }
        val bytesOwnedIdentityToSelect: ByteArray?
        val hexBytesOwnedIdentityToSelect = intent.getStringExtra(
            HEX_STRING_BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA
        )
        bytesOwnedIdentityToSelect = if (hexBytesOwnedIdentityToSelect != null) {
            try {
                Logger.fromHexString(hexBytesOwnedIdentityToSelect)
            } catch (e: Exception) {
                null
            }
        } else {
            intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA)
        }
        if (bytesOwnedIdentityToSelect != null && !Arrays.equals(
                bytesOwnedIdentityToSelect,
                AppSingleton.getBytesCurrentIdentity()
            )
        ) {
            // if a profile switch is required, execute only once the identity is switched
            intent.removeExtra(HEX_STRING_BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA)
            intent.removeExtra(BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA)

            // check if identity is hidden before selecting it
            val finalBytesOwnedIdentityToSelect: ByteArray = bytesOwnedIdentityToSelect
            App.runThread {
                val ownedIdentity =
                    AppDatabase.getInstance().ownedIdentityDao()[finalBytesOwnedIdentityToSelect]
                if (ownedIdentity.isHidden) {
                    runOnUiThread {
                        object : OpenHiddenProfileDialog(this) {
                            override fun onHiddenIdentityPasswordEntered(
                                dialog: AlertDialog,
                                byteOwnedIdentity: ByteArray
                            ) {
                                if (finalBytesOwnedIdentityToSelect.contentEquals(byteOwnedIdentity)
                                ) {
                                    dialog.dismiss()
                                    AppSingleton.getInstance()
                                        .selectIdentity(finalBytesOwnedIdentityToSelect) {
                                            executeIntentAction(intent)
                                        }
                                }
                            }
                        }
                    }
                } else {
                    AppSingleton.getInstance()
                        .selectIdentity(finalBytesOwnedIdentityToSelect) {
                            executeIntentAction(intent)
                        }
                }
            }
        } else {
            // otherwise execute intent action instantly
            executeIntentAction(intent)
        }
        val ownedIdentityRequiringAuthentication = intent.getByteArrayExtra(
            KEYCLOAK_AUTHENTICATION_NEEDED_EXTRA
        )
        if (ownedIdentityRequiringAuthentication != null) {
            AppSingleton.getInstance()
                .selectIdentity(ownedIdentityRequiringAuthentication) {
                    KeycloakManager.forceSelfTestAndReauthentication(
                        ownedIdentityRequiringAuthentication
                    )
                }
        }
        if (intent.hasExtra(BLOCKED_CERTIFICATE_ID_EXTRA)) {
            val blockedCertificateId = intent.getLongExtra(BLOCKED_CERTIFICATE_ID_EXTRA, -1)
            val lastTrustedCertificateId =
                intent.getLongExtra(LAST_TRUSTED_CERTIFICATE_ID_EXTRA, -1)
            if (blockedCertificateId != -1L) {
                App.openAppDialogCertificateChanged(
                    blockedCertificateId,
                    if (lastTrustedCertificateId == -1L) null else lastTrustedCertificateId
                )
            }
        }
        val tabToShow = intent.getIntExtra(TAB_TO_SHOW_INTENT_EXTRA, -1)
        if (tabToShow != -1) {
            intent.removeExtra(TAB_TO_SHOW_INTENT_EXTRA)
            viewPager.currentItem = tabToShow
        }
    }

    private fun executeIntentAction(intent: Intent) {
        if (intent.action == null) {
            return
        }
        when (intent.action) {
            FORWARD_ACTION -> {
                val forwardTo = intent.getStringExtra(FORWARD_TO_INTENT_EXTRA)
                if (forwardTo != null && ALLOWED_FORWARD_TO_VALUES.contains(forwardTo)) {
                    val forwardIntent = Intent()
                    forwardIntent.setClassName(this, forwardTo)
                    if (intent.extras != null) {
                        forwardIntent.putExtras(intent.extras!!)
                    }
                    startActivity(forwardIntent)
                }
            }

            LINK_ACTION -> {

                // first detect the type of link to show an appropriate dialog
                val uri = intent.getStringExtra(LINK_URI_INTENT_EXTRA)
                if (uri != null) {
                    val configurationMatcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri)
                    if (configurationMatcher.find()) {
                        try {
                            // detect if it is a licence or a keycloak
                            val configurationPojo = AppSingleton.getJsonObjectMapper().readValue(
                                ObvBase64.decode(configurationMatcher.group(2)),
                                ConfigurationPojo::class.java
                            )
                            val dialogTitleResourceId: Int =
                                if (configurationPojo.keycloak != null) {
                                    // offer to chose a profile or create a new one for profile binding
                                    R.string.dialog_title_chose_profile_keycloak_configuration
                                } else {
                                    // offer to chose a profile or create a new one for licence activation or configuration
                                    R.string.dialog_title_chose_profile_configuration
                                }
                            val ownedIdentitySelectionDialogFragment =
                                OwnedIdentitySelectionDialogFragment.newInstance(
                                    this,
                                    dialogTitleResourceId,
                                    object : OnOwnedIdentitySelectedListener {
                                        override fun onOwnedIdentitySelected(bytesOwnedIdentity: ByteArray) {
                                            AppSingleton.getInstance()
                                                .selectIdentity(bytesOwnedIdentity) {
                                                    val plusIntent = Intent(
                                                        this@MainActivity,
                                                        PlusButtonActivity::class.java
                                                    )
                                                    plusIntent.putExtra(
                                                        PlusButtonActivity.LINK_URI_INTENT_EXTRA,
                                                        uri
                                                    )
                                                    startActivity(plusIntent)
                                                }
                                        }

                                        override fun onNewProfileCreationSelected() {
                                            val onboardingIntent = Intent(
                                                this@MainActivity,
                                                OnboardingActivity::class.java
                                            )
                                                .putExtra(
                                                    PlusButtonActivity.LINK_URI_INTENT_EXTRA,
                                                    uri
                                                )
                                            startActivity(onboardingIntent)
                                        }
                                    })
                            ownedIdentitySelectionDialogFragment.setShowAddProfileButtonAsOpenHiddenProfile(
                                false
                            )
                            ownedIdentitySelectionDialogFragment.show(
                                supportFragmentManager,
                                "ownedIdentitySelectionDialogFragment"
                            )
                        } catch (e: Exception) {
                            // nothing to do
                            e.printStackTrace()
                        }
                    } else if (ObvLinkActivity.INVITATION_PATTERN.matcher(uri).find()
                        || ObvLinkActivity.MUTUAL_SCAN_PATTERN.matcher(uri).find()
                    ) {
                        // offer to chose a profile
                        val ownedIdentitySelectionDialogFragment =
                            OwnedIdentitySelectionDialogFragment.newInstance(
                                this,
                                R.string.dialog_title_chose_profile_invitation,
                                object : OnOwnedIdentitySelectedListener {
                                    override fun onOwnedIdentitySelected(bytesOwnedIdentity: ByteArray) {
                                        AppSingleton.getInstance()
                                            .selectIdentity(bytesOwnedIdentity) {
                                                val plusIntent = Intent(
                                                    this@MainActivity,
                                                    PlusButtonActivity::class.java
                                                )
                                                plusIntent.putExtra(
                                                    PlusButtonActivity.LINK_URI_INTENT_EXTRA,
                                                    uri
                                                )
                                                startActivity(plusIntent)
                                            }
                                    }

                                    override fun onNewProfileCreationSelected() {
                                        val onboardingIntent = Intent(
                                            this@MainActivity,
                                            OnboardingActivity::class.java
                                        )
                                            .putExtra(OnboardingActivity.LINK_URI_INTENT_EXTRA, uri)
                                        startActivity(onboardingIntent)
                                    }
                                })
                        ownedIdentitySelectionDialogFragment.setShowAddProfileButtonAsOpenHiddenProfile(
                            true
                        )
                        ownedIdentitySelectionDialogFragment.show(
                            supportFragmentManager,
                            "ownedIdentitySelectionDialogFragment"
                        )
                    } else if (ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(uri).find()) {
                        // offer to chose a profile
                        val ownedIdentitySelectionDialogFragment =
                            OwnedIdentitySelectionDialogFragment.newInstance(
                                this,
                                R.string.dialog_title_chose_profile_web_client,
                                object : OnOwnedIdentitySelectedListener {
                                    override fun onOwnedIdentitySelected(bytesOwnedIdentity: ByteArray) {
                                        AppSingleton.getInstance()
                                            .selectIdentity(bytesOwnedIdentity) {
                                                val plusIntent = Intent(
                                                    this@MainActivity,
                                                    PlusButtonActivity::class.java
                                                )
                                                    .putExtra(
                                                        PlusButtonActivity.LINK_URI_INTENT_EXTRA,
                                                        uri
                                                    )
                                                startActivity(plusIntent)
                                            }
                                    }

                                    override fun onNewProfileCreationSelected() {
                                        val onboardingIntent = Intent(
                                            this@MainActivity,
                                            OnboardingActivity::class.java
                                        )
                                            .putExtra(OnboardingActivity.PROFILE_CREATION, true)

                                        startActivity(onboardingIntent)
                                        App.toast(
                                            R.string.toast_message_create_new_profile_then_reopen_this_link,
                                            Toast.LENGTH_SHORT
                                        )
                                    }
                                })
                        ownedIdentitySelectionDialogFragment.setShowAddProfileButtonAsOpenHiddenProfile(
                            true
                        )
                        ownedIdentitySelectionDialogFragment.show(
                            supportFragmentManager,
                            "ownedIdentitySelectionDialogFragment"
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tabsPagerAdapter = null
        contactListFragment = null
        Utils.dialogShowing = false
    }

    inner class TabsPagerAdapter internal constructor(
        activity: FragmentActivity,
        private vararg val notificationDots: View
    ) : FragmentStateAdapter(
        activity
    ) {

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                CONTACTS_TAB -> {
                    ContactListFragment()
                }

                GROUPS_TAB -> {
                    GroupListFragment()
                }

                CALLS_TAB -> {
                    CallLogFragment()
                }

                DISCUSSIONS_TAB -> {
                    DiscussionListFragment()
                }

                else -> {
                    DiscussionListFragment()
                }
            }
        }

        override fun getItemCount(): Int {
            return 4
        }

        fun showNotificationDot(position: Int) {
            if (position < 0 || position >= itemCount) {
                return
            }
            notificationDots[position].visibility = View.VISIBLE
        }

        fun hideNotificationDot(position: Int) {
            if (position < 0 || position >= itemCount) {
                return
            }
            notificationDots[position].visibility = View.GONE
        }
    }

    private var previousPingConnectivityIndicator = NONE
    private fun showPingIndicator(pingConnectivityIndicator: PingConnectivityIndicator) {
        if (pingConnectivityIndicator == previousPingConnectivityIndicator) {
            return
        }
        previousPingConnectivityIndicator = pingConnectivityIndicator
        when (pingConnectivityIndicator) {
            NONE -> {
                pingConnectivityDot!!.visibility = View.GONE
                pingConnectivityLine!!.visibility = View.GONE
                pingConnectivityFull!!.visibility = View.GONE
            }

            DOT -> {
                pingConnectivityDot!!.visibility = View.VISIBLE
                pingConnectivityLine!!.visibility = View.GONE
                pingConnectivityFull!!.visibility = View.GONE
            }

            LINE -> {
                pingConnectivityDot!!.visibility = View.GONE
                pingConnectivityLine!!.visibility = View.VISIBLE
                pingConnectivityFull!!.visibility = View.GONE
            }

            FULL -> {
                pingConnectivityDot!!.visibility = View.GONE
                pingConnectivityLine!!.visibility = View.GONE
                pingConnectivityFull!!.visibility = View.VISIBLE
                pingConnectivityFullPingTextView?.text = null
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Utils.stopPinging()
        if (previousPingConnectivityIndicator != NONE) {
            AppSingleton.getEngine()
                .removeNotificationListener(EngineNotifications.PING_LOST, pingListener)
            AppSingleton.getEngine()
                .removeNotificationListener(EngineNotifications.PING_RECEIVED, pingListener)
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivityPageChangeListener!!.onPageSelected(viewPager.currentItem)
        val pingSetting = SettingsActivity.getPingConnectivityIndicator()
        if (previousPingConnectivityIndicator != NONE || pingSetting != NONE) {
            showPingIndicator(pingSetting)
            if (pingSetting != NONE) {
                pingListener.refresh()
                Utils.startPinging()
                AppSingleton.getEngine()
                    .addNotificationListener(EngineNotifications.PING_LOST, pingListener)
                AppSingleton.getEngine()
                    .addNotificationListener(EngineNotifications.PING_RECEIVED, pingListener)
            }
        }
    }

    override fun onBackPressed() {
        if (!moveTaskToBack(true)) {
            finishAndRemoveTask()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        when (viewPager.currentItem) {
            CONTACTS_TAB -> {
                menuInflater.inflate(R.menu.menu_main_contact_list, menu)
                val searchView = menu.findItem(R.id.action_search).actionView as SearchView?
                if (searchView != null) {
                    searchView.queryHint = getString(R.string.hint_search_contact_name)
                    searchView.inputType =
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_FILTER
                    if (SettingsActivity.useKeyboardIncognitoMode()) {
                        searchView.imeOptions =
                            searchView.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
                    searchView.setOnSearchClickListener(OnClickListener {
                        if (contactListFragment != null) {
                            contactListFragment!!.bindToSearchView(searchView)
                        }
                    })
                }
            }

            DISCUSSIONS_TAB -> {
                menuInflater.inflate(R.menu.menu_main_discussion_list, menu)
            }

            CALLS_TAB -> {
                menuInflater.inflate(R.menu.menu_call_log, menu)
                return true
            }
        }
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            return true
        } else if (itemId == R.id.action_storage_management) {
            startActivity(Intent(this, StorageManagerActivity::class.java))
        } else if (itemId == R.id.action_troubleshooting) {
            startActivity(Intent(this, TroubleshootingActivity::class.java))
        } else if (itemId == R.id.action_backup_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra(
                SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA,
                SettingsActivity.PREF_HEADER_KEY_BACKUP
            )
            startActivity(intent)
            return true
        } else if (itemId == R.id.action_search) {
            if (viewPager.currentItem == DISCUSSIONS_TAB) {
                val discussionSearchDialogFragment = DiscussionSearchDialogFragment.newInstance()
                discussionSearchDialogFragment.show(
                    supportFragmentManager,
                    "discussion_search_dialog_fragment"
                )
                return true
            }
        } else if (itemId == R.id.action_clear_log) {
            if (viewPager.currentItem == CALLS_TAB) {
                val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity()
                if (bytesOwnedIdentity != null) {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_clear_call_log)
                        .setMessage(R.string.dialog_message_clear_call_log)
                        .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                            App.runThread {
                                AppDatabase.getInstance().callLogItemDao()
                                    .deleteAll(bytesOwnedIdentity)
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.tab_plus_button -> {
                startActivity(Intent(this, PlusButtonActivity::class.java))
            }

            R.id.tab_discussions_button -> {
                viewPager.currentItem = DISCUSSIONS_TAB
            }

            R.id.tab_contacts_button -> {
                viewPager.currentItem = CONTACTS_TAB
            }

            R.id.tab_groups_button -> {
                viewPager.currentItem = GROUPS_TAB
            }

            R.id.tab_calls_button -> {
                viewPager.currentItem = CALLS_TAB
            }
        }
    }

    internal inner class PingListener : EngineNotificationListener, Observer<Int> {
        private var registrationNumber: Long? = null
        private var websocketConnectionState = 0
        private var lastPing: Long = 0
        fun refresh() {
            onChanged(websocketConnectionState)
        }

        override fun onChanged(value: Int) {
            if (value != this.websocketConnectionState) {
                lastPing = 0
            }
            this.websocketConnectionState = value
            val stateColor: Int = if (value == 0 || lastPing == -1L) {
                pingRed
            } else if (value == 1 || lastPing > 3000) {
                pingGolden
            } else {
                pingGreen
            }
            when (previousPingConnectivityIndicator) {
                NONE -> {}
                DOT -> pingConnectivityDot!!.setBackgroundColor(stateColor)
                LINE -> pingConnectivityLine!!.setBackgroundColor(stateColor)
                FULL -> {
                    pingConnectivityFull!!.setBackgroundColor(stateColor)
                    when (value) {
                        1 -> pingConnectivityFullTextView!!.setText(
                            R.string.label_ping_connectivity_connecting
                        )

                        2 -> pingConnectivityFullTextView!!.setText(R.string.label_ping_connectivity_connected)
                        0 -> pingConnectivityFullTextView!!.setText(R.string.label_ping_connectivity_none)
                        else -> pingConnectivityFullTextView!!.setText(R.string.label_ping_connectivity_none)
                    }
                    when (lastPing) {
                        -1L -> {
                            pingConnectivityFullPingTextView!!.text =
                                getString(R.string.label_over_max_ping_delay, 5)
                        }

                        0L -> {
                            pingConnectivityFullPingTextView!!.text = "-"
                        }

                        else -> {
                            pingConnectivityFullPingTextView!!.text =
                                getString(R.string.label_ping_delay, lastPing)
                        }
                    }
                }
            }
        }

        override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
            if (previousPingConnectivityIndicator != FULL) {
                return
            }
            when (notificationName) {
                EngineNotifications.PING_LOST -> {
                    lastPing = -1
                    runOnUiThread { refresh() }
                }

                EngineNotifications.PING_RECEIVED -> {
                    (userInfo[EngineNotifications.PING_RECEIVED_DELAY_KEY] as Long?)?.let {
                        lastPing = it
                        runOnUiThread { refresh() }
                    }
                }
            }
        }

        override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
            this.registrationNumber = registrationNumber
        }

        override fun getEngineNotificationListenerRegistrationNumber(): Long {
            return registrationNumber ?: 0
        }

        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
            return registrationNumber != null
        }
    }

    internal inner class MainActivityPageChangeListener(private val imageViews: Array<ImageView?>) :
        OnPageChangeCallback() {
        private val imm: InputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        private val inactiveColor: Int = ContextCompat.getColor(this@MainActivity, R.color.greyTint)
        private val activeColor: Int =
            ContextCompat.getColor(this@MainActivity, R.color.olvid_gradient_light)
        private var currentPosition = -1

        override fun onPageSelected(position: Int) {
            imm.hideSoftInputFromWindow(viewPager.windowToken, 0)

            // Update the menu as it depends on the tab
            if (currentPosition != position) {
                invalidateOptionsMenu()
            }
            if (position == CALLS_TAB) {
                AndroidNotificationManager.clearAllMissedCallNotifications()
            }
            currentPosition = position
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            for (i in imageViews.indices) {
                when (i) {
                    position -> {
                        var color = -0x1000000
                        color =
                            color or ((positionOffset * (inactiveColor and 0xff) + (1 - positionOffset) * (activeColor and 0xff)).toInt() and 0xff)
                        color =
                            color or ((positionOffset * (inactiveColor and 0xff00) + (1 - positionOffset) * (activeColor and 0xff00)).toInt() and 0xff00)
                        color =
                            color or ((positionOffset * (inactiveColor and 0xff0000) + (1 - positionOffset) * (activeColor and 0xff0000)).toInt() and 0xff0000)
                        imageViews[i]!!.setColorFilter(color)
                    }

                    position + 1 -> {
                        var color = -0x1000000
                        color =
                            color or ((positionOffset * (activeColor and 0xff) + (1 - positionOffset) * (inactiveColor and 0xff)).toInt() and 0xff)
                        color =
                            color or ((positionOffset * (activeColor and 0xff00) + (1 - positionOffset) * (inactiveColor and 0xff00)).toInt() and 0xff00)
                        color =
                            color or ((positionOffset * (activeColor and 0xff0000) + (1 - positionOffset) * (inactiveColor and 0xff0000)).toInt() and 0xff0000)
                        imageViews[i]!!.setColorFilter(color)
                    }

                    else -> {
                        imageViews[i]!!.setColorFilter(inactiveColor)
                    }
                }
            }
        }

        override fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val LINK_ACTION = "link_action"
        const val LINK_URI_INTENT_EXTRA = "link_uri"
        const val FORWARD_ACTION = "forward_action"
        const val FORWARD_TO_INTENT_EXTRA = "forward_to"
        const val TAB_TO_SHOW_INTENT_EXTRA = "tab_to_show"
        const val ALREADY_CREATED_BUNDLE_EXTRA = "already_created"
        const val KEYCLOAK_AUTHENTICATION_NEEDED_EXTRA = "keycloak_authentication_needed"
        const val BLOCKED_CERTIFICATE_ID_EXTRA = "blocked_certificate_id"
        const val LAST_TRUSTED_CERTIFICATE_ID_EXTRA = "last_trusted_certificate_id"
        const val BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA = "owned_identity"
        const val HEX_STRING_BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA = "hex_owned_identity"
        private val ALLOWED_FORWARD_TO_VALUES: Set<String> = HashSet(
            listOf(
                DiscussionActivity::class.java.name,
                OwnedIdentityDetailsActivity::class.java.name,
                PlusButtonActivity::class.java.name,
                OnboardingActivity::class.java.name,
                ContactDetailsActivity::class.java.name,
                SettingsActivity::class.java.name
            )
        )
        const val DISCUSSIONS_TAB = 0
        const val CONTACTS_TAB = 1
        const val GROUPS_TAB = 2
        const val CALLS_TAB = 3
    }
}