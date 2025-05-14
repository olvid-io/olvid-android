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
package io.olvid.messenger.owneddetails

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.olvid.engine.datatypes.Identity
import io.olvid.engine.encoder.DecodingException
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto
import io.olvid.engine.engine.types.ObvDeviceManagementRequest
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.MuteNotificationDialog
import io.olvid.messenger.customClasses.MuteNotificationDialog.MuteType.PROFILE
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.DeleteOwnedIdentityAndEverythingRelatedToItTask
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask
import io.olvid.messenger.fragments.FullScreenImageFragment
import io.olvid.messenger.fragments.SubscriptionStatusFragment
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.plus_button.PlusButtonActivity
import io.olvid.messenger.settings.SettingsActivity.Companion.contactDisplayNameFormat
import io.olvid.messenger.settings.SettingsActivity.Companion.uppercaseLastName
import java.util.Locale

class OwnedIdentityDetailsActivity : LockableActivity(), OnClickListener {
    private val viewModel: OwnedIdentityDetailsActivityViewModel by viewModels()
    private var myIdInitialView: InitialView? = null
    private var myIdNameTextView: TextView? = null
    private var latestDetailsCardView: CardView? = null
    private var latestDetailsTextViews: LinearLayout? = null
    private var latestDetailsInitialView: InitialView? = null
    private var publishedDetailsTextViews: LinearLayout? = null
    private var publishedDetailsInitialView: InitialView? = null
    private var inactiveCardView: CardView? = null
    private var keycloakManaged = false

    private var deviceChangedEngineListener: EngineNotificationListener? = null

    private var latestDetails: JsonIdentityDetailsWithVersionAndPhoto? = null
    private val identityObserver: IdentityObserver by lazy { IdentityObserver() }
    private val ownedIdentityDetailsViewModel: OwnedIdentityDetailsViewModel by viewModels()
    private var primary700 = 0

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_owned_identity_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.elevation = 0f

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES

        findViewById<CoordinatorLayout>(R.id.root_coordinator)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                view.updateLayoutParams<MarginLayoutParams> {
                    updateMargins(bottom = insets.bottom)
                }
                view.updatePadding(top = insets.top)
                findViewById<ScrollView>(R.id.root_scrollview)?.updatePadding(
                    left = insets.left,
                    right = insets.right
                )
                windowInsets
            }
        }

        myIdInitialView = findViewById(R.id.myid_initial_view)
        myIdInitialView?.setOnClickListener(this)
        myIdNameTextView = findViewById(R.id.myid_name_text_view)

        inactiveCardView = findViewById(R.id.identity_inactive_card_view)
        val reactivateIdButton = findViewById<Button>(R.id.button_reactivate_identity)
        reactivateIdButton.setOnClickListener(this)

        latestDetailsCardView = findViewById(R.id.latest_details_cardview)
        latestDetailsTextViews = findViewById(R.id.latest_details_textviews)
        latestDetailsInitialView = findViewById(R.id.latest_details_initial_view)
        latestDetailsInitialView?.setOnClickListener(this)
        publishedDetailsTextViews = findViewById(R.id.published_details_textviews)
        publishedDetailsInitialView = findViewById(R.id.published_details_initial_view)
        publishedDetailsInitialView?.setOnClickListener(this)

        val publishButton = findViewById<Button>(R.id.button_publish)
        publishButton.setOnClickListener(this)
        val discardButton = findViewById<Button>(R.id.button_discard)
        discardButton.setOnClickListener(this)
        val addContactButton = findViewById<Button>(R.id.add_contact_button)
        addContactButton.setOnClickListener(this)

        val diffUtilCallback: ItemCallback<OwnedDevice> = object : ItemCallback<OwnedDevice>() {
            override fun areItemsTheSame(oldItem: OwnedDevice, newItem: OwnedDevice): Boolean {
                return oldItem.bytesDeviceUid.contentEquals(newItem.bytesDeviceUid)
            }

            override fun areContentsTheSame(oldItem: OwnedDevice, newItem: OwnedDevice): Boolean {
                return oldItem.displayName == newItem.displayName
                        && (oldItem.channelConfirmed == newItem.channelConfirmed)
                        && (oldItem.hasPreKey == newItem.hasPreKey)
                        && (oldItem.currentDevice == newItem.currentDevice)
                        && (oldItem.trusted == newItem.trusted)
                        && oldItem.lastRegistrationTimestamp == newItem.lastRegistrationTimestamp
                        && oldItem.expirationTimestamp == newItem.expirationTimestamp
            }
        }
        val deviceListAdapter: ListAdapter<OwnedDevice, OwnedDeviceViewHolder> =
            object : ListAdapter<OwnedDevice, OwnedDeviceViewHolder>(diffUtilCallback) {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): OwnedDeviceViewHolder {
                    return OwnedDeviceViewHolder(
                        layoutInflater.inflate(
                            R.layout.item_view_owned_device,
                            parent,
                            false
                        ), viewModel
                    )
                }

                override fun onBindViewHolder(holder: OwnedDeviceViewHolder, position: Int) {
                    holder.bind(getItem(position)!!, viewModel.ownedIdentityActive.value != false)
                }

                override fun onViewRecycled(holder: OwnedDeviceViewHolder) {
                    holder.unbind()
                }
            }

        val deviceListRecyclerView = findViewById<RecyclerView>(R.id.device_list_recycler_view)
        deviceListRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceListRecyclerView.adapter = deviceListAdapter
        viewModel.ownedDevicesLiveData.observe(
            this
        ) { list: List<OwnedDevice>? ->
            deviceListAdapter.submitList(
                list
            )
        }
        viewModel.ownedIdentityActive.observe(
            this
        ) { deviceListAdapter.notifyDataSetChanged() }

        val addDeviceButton = findViewById<Button>(R.id.add_device_button)
        addDeviceButton.setOnClickListener(this)

        val loadingSpinner = findViewById<View>(R.id.loading_spinner)
        viewModel.showRefreshSpinner.observe(
            this
        ) { refreshing: Boolean ->
            loadingSpinner.visibility =
                if (refreshing) View.VISIBLE else View.GONE
        }

        primary700 = ContextCompat.getColor(this, R.color.primary700)

        AppSingleton.getCurrentIdentityLiveData().observe(this, identityObserver)

        deviceChangedEngineListener = object :
            SimpleEngineNotificationListener(EngineNotifications.OWNED_IDENTITY_DEVICE_LIST_CHANGED) {
            override fun callback(userInfo: HashMap<String, Any>) {
                viewModel.hideRefreshSpinner()
            }
        }
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.OWNED_IDENTITY_DEVICE_LIST_CHANGED,
            deviceChangedEngineListener
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (deviceChangedEngineListener != null) {
            AppSingleton.getEngine().removeNotificationListener(
                EngineNotifications.OWNED_IDENTITY_DEVICE_LIST_CHANGED,
                deviceChangedEngineListener
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_owned_identity_details, menu)
        if (keycloakManaged) {
            menuInflater.inflate(R.menu.menu_owned_identity_details_keycloak, menu)
        }
        if (identityObserver.ownedIdentity != null) {
            if (identityObserver.ownedIdentity!!.shouldMuteNotifications()) {
                menuInflater.inflate(R.menu.menu_owned_identity_details_muted, menu)
            }
            if (identityObserver.ownedIdentity!!.isHidden) {
                menuInflater.inflate(R.menu.menu_owned_identity_details_hidden, menu)
                val neutralNotificationItem = menu.findItem(R.id.action_neutral_notification)
                neutralNotificationItem.isChecked = identityObserver.ownedIdentity!!.prefShowNeutralNotificationWhenHidden
            }
        }
        // make the delete profile item red
        val deleteItem = menu.findItem(R.id.action_delete_owned_identity)
        if (deleteItem != null) {
            val spannableString = SpannableString(deleteItem.title)
            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)),
                0,
                spannableString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            deleteItem.title = spannableString
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }

            R.id.action_unmute -> {
                if (identityObserver.ownedIdentity != null && identityObserver.ownedIdentity!!.shouldMuteNotifications()) {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unmute_notifications)
                        .setPositiveButton(
                            R.string.button_label_unmute_notifications
                        ) { _, _ ->
                            App.runThread {
                                identityObserver.ownedIdentity?.prefMuteNotifications = false
                                AppDatabase.getInstance().ownedIdentityDao()
                                    .updateMuteNotifications(
                                        identityObserver.ownedIdentity!!.bytesOwnedIdentity,
                                        identityObserver.ownedIdentity!!.prefMuteNotifications,
                                        null,
                                        identityObserver.ownedIdentity!!.prefMuteNotificationsExceptMentioned
                                    )
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)

                    if (identityObserver.ownedIdentity?.prefMuteNotificationsTimestamp == null) {
                        builder.setMessage(R.string.dialog_message_unmute_notifications)
                    } else {
                        builder.setMessage(
                            getString(
                                R.string.dialog_message_unmute_notifications_muted_until,
                                StringUtils.getLongNiceDateString(
                                    this,
                                    identityObserver.ownedIdentity!!.prefMuteNotificationsTimestamp!!
                                )
                            )
                        )
                    }
                    builder.create().show()
                }
                return true
            }

            R.id.action_mute -> {
                val muteNotificationDialog = MuteNotificationDialog(
                    this,
                    { muteExpirationTimestamp: Long?, _: Boolean, exceptMentioned: Boolean ->
                        App.runThread {
                            if (identityObserver.ownedIdentity != null) {
                                identityObserver.ownedIdentity!!.prefMuteNotifications = true
                                identityObserver.ownedIdentity!!.prefMuteNotificationsTimestamp =
                                    muteExpirationTimestamp
                                identityObserver.ownedIdentity!!.prefMuteNotificationsExceptMentioned =
                                    exceptMentioned
                                AppDatabase.getInstance().ownedIdentityDao()
                                    .updateMuteNotifications(
                                        identityObserver.ownedIdentity!!.bytesOwnedIdentity,
                                        identityObserver.ownedIdentity!!.prefMuteNotifications,
                                        identityObserver.ownedIdentity!!.prefMuteNotificationsTimestamp,
                                        identityObserver.ownedIdentity!!.prefMuteNotificationsExceptMentioned
                                    )
                            }
                        }
                    },
                    PROFILE,
                    identityObserver.ownedIdentity == null || identityObserver.ownedIdentity!!.prefMuteNotificationsExceptMentioned
                )

                muteNotificationDialog.show()
                return true
            }

            R.id.action_neutral_notification -> {
                if (identityObserver.ownedIdentity != null) {
                    if (identityObserver.ownedIdentity!!.prefShowNeutralNotificationWhenHidden) {
                        App.runThread {
                            AppDatabase.getInstance().ownedIdentityDao()
                                .updateShowNeutralNotificationWhenHidden(
                                    identityObserver.ownedIdentity!!.bytesOwnedIdentity, false
                                )
                        }
                    } else {
                        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_neutral_notification_when_hidden)
                            .setMessage(R.string.dialog_message_neutral_notification_when_hidden)
                            .setPositiveButton(
                                R.string.button_label_activate
                            ) { _, _ ->
                                App.runThread {
                                    AppDatabase.getInstance().ownedIdentityDao()
                                        .updateShowNeutralNotificationWhenHidden(
                                            identityObserver.ownedIdentity!!.bytesOwnedIdentity,
                                            true
                                        )
                                }
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                        builder.create().show()
                    }
                }
                return true
            }

            R.id.action_rename -> {

                identityObserver.ownedIdentity?.let { ownedIdentity ->
                    ownedIdentityDetailsViewModel.bytesOwnedIdentity =
                        ownedIdentity.bytesOwnedIdentity
                    latestDetails?.let {
                        ownedIdentityDetailsViewModel.setOwnedIdentityDetails(
                            it,
                            ownedIdentity.customDisplayName,
                            ownedIdentity.unlockPassword != null
                        )
                    }
                    ownedIdentityDetailsViewModel.detailsLocked = ownedIdentity.keycloakManaged
                    ownedIdentityDetailsViewModel.isIdentityInactive = ownedIdentity.active.not()
                    val dialogFragment =
                        EditOwnedIdentityDetailsDialogFragment()
                    dialogFragment.show(supportFragmentManager, "dialog")
                }
                return true
            }

            R.id.action_refresh_subscription_status -> {
                if (identityObserver.ownedIdentity == null) {
                    return true
                }
                AppSingleton.getEngine()
                    .recreateServerSession(identityObserver.ownedIdentity!!.bytesOwnedIdentity)
                return true
            }

            R.id.action_unbind_from_keycloak -> {
                identityObserver.ownedIdentity?.let { ownedId ->
                    val builder =
                        if (KeycloakManager.isOwnedIdentityTransferRestricted(ownedId.bytesOwnedIdentity)) {
                            SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_unbind_from_keycloak_restricted)
                                .setMessage(R.string.dialog_message_unbind_from_keycloak_restricted)
                                .setPositiveButton(R.string.button_label_ok, null)
                        } else {
                            SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_unbind_from_keycloak)
                                .setMessage(R.string.dialog_message_unbind_from_keycloak)
                                .setPositiveButton(R.string.button_label_ok) { _, _ ->
                                    KeycloakManager.getInstance().unregisterKeycloakManagedIdentity(
                                        identityObserver.ownedIdentity!!.bytesOwnedIdentity
                                    )
                                    AppSingleton.getEngine()
                                        .unbindOwnedIdentityFromKeycloak(identityObserver.ownedIdentity!!.bytesOwnedIdentity)
                                }
                                .setNegativeButton(R.string.button_label_cancel, null)
                        }
                    builder.create().show()
                }
                return true
            }

            R.id.action_delete_owned_identity -> {
                App.runThread {
                    val ownedIdentity = identityObserver.ownedIdentity ?: return@runThread
                    // first check if this is the last profile (or last unhidden profile)
                    val otherNotHiddenOwnedIdentityCount =
                        AppDatabase.getInstance().ownedIdentityDao()
                            .countNotHidden() - (if (ownedIdentity.isHidden) 0 else 1)
                    // also check if there are other devices
                    val hasOtherDevices = AppDatabase.getInstance().ownedDeviceDao()
                        .getAllSync(ownedIdentity.bytesOwnedIdentity).size > 1

                    val builder =
                        SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_profile)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setPositiveButton(
                                R.string.button_label_next
                            ) { _, _ ->
                                showDeleteProfileDialog(
                                    ownedIdentity,
                                    otherNotHiddenOwnedIdentityCount < 1,
                                    hasOtherDevices
                                )
                            }

                    if (hasOtherDevices) {
                        builder.setMessage(if ((otherNotHiddenOwnedIdentityCount >= 1)) R.string.dialog_message_delete_profile_multi else R.string.dialog_message_delete_last_profile_multi)
                    } else {
                        builder.setMessage(if ((otherNotHiddenOwnedIdentityCount >= 1)) R.string.dialog_message_delete_profile else R.string.dialog_message_delete_last_profile)
                    }
                    runOnUiThread { builder.create().show() }
                }
                return true
            }

            R.id.action_debug_information -> {
                val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                if (ownedIdentity != null) {
                    val sb = StringBuilder()
                    try {
                        val ownIdentity = Identity.of(ownedIdentity.bytesOwnedIdentity)
                        sb.append(getString(R.string.debug_label_server)).append(" ")
                        sb.append(ownIdentity.server).append("\n\n")
                    } catch (_: DecodingException) {
                    }
                    if (ownedIdentity.keycloakManaged) {
                        AppSingleton.getEngine().getOwnedIdentityKeycloakState(ownedIdentity.bytesOwnedIdentity)?.keycloakServer?.let {
                            sb.append(getString(R.string.debug_label_identity_provider)).append(" ")
                            sb.append(it).append("\n\n")
                        }
                    }
                    sb.append(getString(R.string.debug_label_identity_link)).append("\n")
                    sb.append(
                        ObvUrlIdentity(
                            ownedIdentity.bytesOwnedIdentity,
                            ownedIdentity.displayName
                        ).urlRepresentation
                    ).append("\n\n")
                    sb.append(getString(R.string.debug_label_capabilities)).append("\n")
                    sb.append(getString(R.string.bullet)).append(" ").append(
                        getString(
                            R.string.debug_label_capability_continuous_gathering,
                            ownedIdentity.capabilityWebrtcContinuousIce
                        )
                    ).append("\n")
                    sb.append(getString(R.string.bullet)).append(" ").append(
                        getString(
                            R.string.debug_label_capability_one_to_one_contacts,
                            ownedIdentity.capabilityOneToOneContacts
                        )
                    ).append("\n")
                    sb.append(getString(R.string.bullet)).append(" ").append(
                        getString(
                            R.string.debug_label_capability_groups_v2,
                            ownedIdentity.capabilityGroupsV2
                        )
                    ).append("\n")

                    val textView = TextView(this)
                    val sixteenDp = (16 * resources.displayMetrics.density).toInt()
                    textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp)
                    textView.setTextIsSelectable(true)
                    textView.autoLinkMask = Linkify.ALL
                    textView.movementMethod = LinkMovementMethod.getInstance()
                    textView.text = sb

                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.menu_action_debug_information)
                        .setView(textView)
                        .setPositiveButton(R.string.button_label_ok, null)
                    builder.create().show()
                }
                return true
            }

            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    private var deleteProfileEverywhere = true

    private fun showDeleteProfileDialog(
        ownedIdentity: OwnedIdentity,
        deleteAllHiddenOwnedIdentities: Boolean,
        hasOtherDevices: Boolean
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_delete_profile, null)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val deleteButton = dialogView.findViewById<Button>(R.id.delete_button)
        deleteButton.isEnabled = false
        @SuppressLint("UseSwitchCompatOrMaterialCode") val deleteEverywhereSwitch =
            dialogView.findViewById<Switch>(R.id.delete_profile_everywhere_switch)
        deleteEverywhereSwitch.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            deleteProfileEverywhere = checked
            deleteButton.setText(if (checked) R.string.button_label_delete_everywhere else R.string.button_label_delete)
        }
        val typeDeleteEditText = dialogView.findViewById<EditText>(R.id.type_delete_edit_text)
        typeDeleteEditText.addTextChangedListener(object : TextChangeListener() {
            val target: String = getString(R.string.text_delete_capitalized)

            override fun afterTextChanged(s: Editable) {
                deleteButton.isEnabled =
                    target == s.toString().uppercase(Locale.getDefault())
            }
        })
        val explanationTextView =
            dialogView.findViewById<TextView>(R.id.delete_dialog_confirmation_explanation)
        explanationTextView.movementMethod = ScrollingMovementMethod.getInstance()
        if (ownedIdentity.active) {
            if (hasOtherDevices) {
                explanationTextView.setText(R.string.explanation_delete_owned_identity_multi)
                deleteEverywhereSwitch.isChecked = false
                deleteProfileEverywhere = false
            } else {
                explanationTextView.setText(R.string.explanation_delete_owned_identity)
                deleteEverywhereSwitch.isChecked = true
                deleteProfileEverywhere = true
            }
            deleteEverywhereSwitch.isEnabled = true
        } else {
            explanationTextView.setText(R.string.explanation_delete_inactive_owned_identity)
            deleteEverywhereSwitch.isChecked = false
            deleteEverywhereSwitch.isEnabled = false
            deleteProfileEverywhere = false
        }

        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_delete_profile)
            .setView(dialogView)
        val dialog: Dialog = builder.create()
        cancelButton.setOnClickListener { dialog.dismiss() }
        deleteButton.setOnClickListener {
            App.runThread {
                val bytesOwnedIdentities: MutableList<ByteArray> =
                    ArrayList()
                bytesOwnedIdentities.add(ownedIdentity.bytesOwnedIdentity)
                if (deleteAllHiddenOwnedIdentities) {
                    val hiddenOwnedIdentities =
                        AppDatabase.getInstance().ownedIdentityDao().allHidden
                    for (hiddenOwnedIdentity in hiddenOwnedIdentities) {
                        bytesOwnedIdentities.add(hiddenOwnedIdentity.bytesOwnedIdentity)
                    }
                }
                try {
                    for (bytesOwnedIdentity in bytesOwnedIdentities) {
                        AppSingleton.getEngine().deleteOwnedIdentityAndNotifyContacts(
                            bytesOwnedIdentity,
                            deleteProfileEverywhere
                        )
                    }
                    runOnUiThread { dialog.dismiss() }
                    for (bytesOwnedIdentity in bytesOwnedIdentities) {
                        App.runThread(
                            DeleteOwnedIdentityAndEverythingRelatedToItTask(
                                bytesOwnedIdentity
                            )
                        )
                    }
                    finish()
                    App.toast(
                        R.string.toast_message_profile_deleted,
                        Toast.LENGTH_SHORT
                    )
                } catch (_: Exception) {
                    App.toast(
                        R.string.toast_message_something_went_wrong,
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }

        val window = dialog.window
        window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        dialog.show()
    }

    fun displayDetails(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) {
            finish()
            return
        }

        keycloakManaged = ownedIdentity.keycloakManaged
        invalidateOptionsMenu()

        myIdInitialView!!.setOwnedIdentity(ownedIdentity)
        myIdNameTextView!!.text = ownedIdentity.getCustomDisplayName()

        if (ownedIdentity.active) {
            inactiveCardView!!.visibility = View.GONE
        } else {
            inactiveCardView!!.visibility = View.VISIBLE
        }

        val subscriptionStatusFragment = SubscriptionStatusFragment.newInstance(
            ownedIdentity.bytesOwnedIdentity,
            ownedIdentity.getApiKeyStatus(),
            ownedIdentity.apiKeyExpirationTimestamp,
            ownedIdentity.getApiKeyPermissions(),
            false,
            !keycloakManaged,
            AppSingleton.getOtherProfileHasCallsPermission()
        )
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.subscription_status_placeholder, subscriptionStatusFragment)
        transaction.commit()

        kotlin.runCatching {
            val jsons = AppSingleton.getEngine()
                .getOwnedIdentityPublishedAndLatestDetails(ownedIdentity.bytesOwnedIdentity)
            if (jsons == null || jsons.isEmpty()) {
                return
            }
            publishedDetailsTextViews!!.removeAllViews()
            val publishedDetails = jsons[0].identityDetails
            val publishedFirstLine = publishedDetails.formatFirstAndLastName(
                contactDisplayNameFormat, uppercaseLastName
            )
            val publishedSecondLine = publishedDetails.formatPositionAndCompany(
                contactDisplayNameFormat
            )
            if (jsons[0].photoUrl != null) {
                publishedDetailsInitialView!!.setPhotoUrl(
                    ownedIdentity.bytesOwnedIdentity,
                    jsons[0].photoUrl
                )
            } else {
                publishedDetailsInitialView!!.setInitial(
                    ownedIdentity.bytesOwnedIdentity,
                    StringUtils.getInitial(publishedFirstLine)
                )
            }
            run {
                val tv = makeTextView()
                tv.text = publishedFirstLine
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                publishedDetailsTextViews!!.addView(tv)
            }
            if (!publishedSecondLine.isNullOrEmpty()) {
                val tv = makeTextView()
                tv.text = publishedSecondLine
                publishedDetailsTextViews!!.addView(tv)
            }
            if (publishedDetails.customFields != null) {
                val keys: MutableList<String> = ArrayList(publishedDetails.customFields.size)
                keys.addAll(publishedDetails.customFields.keys)
                keys.sort()
                for (key in keys) {
                    val tv = makeTextView()
                    val value = publishedDetails.customFields[key]
                    val spannableString = SpannableString(
                        getString(
                            R.string.format_identity_details_custom_field,
                            key,
                            value
                        )
                    )
                    spannableString.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        0,
                        key.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    tv.text = spannableString
                    publishedDetailsTextViews!!.addView(tv)
                }
            }

            // update OwnedIdentity photoUrl if not in sync
            if ((ownedIdentity.photoUrl == null && jsons[0].photoUrl != null) ||
                (ownedIdentity.photoUrl != null && ownedIdentity.photoUrl != jsons[0].photoUrl)
            ) {
                App.runThread {
                    ownedIdentity.photoUrl = jsons[0].photoUrl
                    AppDatabase.getInstance().ownedIdentityDao().updateIdentityDetailsAndPhoto(
                        ownedIdentity.bytesOwnedIdentity,
                        ownedIdentity.identityDetails,
                        ownedIdentity.displayName,
                        ownedIdentity.photoUrl
                    )
                }
            }

            if (jsons.size == 2) {
                latestDetails = jsons[1]
                latestDetailsCardView!!.visibility = View.VISIBLE
                latestDetailsTextViews!!.removeAllViews()
                val latestDetails = jsons[1].identityDetails
                val latestFirstLine = latestDetails.formatFirstAndLastName(
                    contactDisplayNameFormat,
                    uppercaseLastName
                )
                val latestSecondLine = latestDetails.formatPositionAndCompany(
                    contactDisplayNameFormat
                )
                if (jsons[1].photoUrl != null) {
                    latestDetailsInitialView!!.setPhotoUrl(
                        ownedIdentity.bytesOwnedIdentity,
                        jsons[1].photoUrl
                    )
                } else {
                    latestDetailsInitialView!!.setInitial(
                        ownedIdentity.bytesOwnedIdentity,
                        StringUtils.getInitial(latestFirstLine)
                    )
                }
                run {
                    val tv = makeTextView()
                    tv.text = latestFirstLine
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    if (latestFirstLine != publishedFirstLine) {
                        tv.setTypeface(tv.typeface, Typeface.BOLD)
                    }
                    latestDetailsTextViews!!.addView(tv)
                }
                if (!latestSecondLine.isNullOrEmpty()) {
                    val tv = makeTextView()
                    tv.text = latestSecondLine
                    if (latestSecondLine != publishedSecondLine) {
                        tv.setTypeface(tv.typeface, Typeface.BOLD)
                    }
                    latestDetailsTextViews!!.addView(tv)
                }
                if (latestDetails.customFields != null) {
                    val keys: MutableList<String> = ArrayList(latestDetails.customFields.size)
                    keys.addAll(latestDetails.customFields.keys)
                    keys.sort()
                    for (key in keys) {
                        val tv = makeTextView()
                        val value = latestDetails.customFields[key] ?: continue
                        val spannableString = SpannableString(
                            getString(
                                R.string.format_identity_details_custom_field,
                                key,
                                value
                            )
                        )
                        spannableString.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            0,
                            key.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        tv.text = spannableString
                        if (!(publishedDetails.customFields != null && value == publishedDetails.customFields[key])) {
                            tv.setTypeface(tv.typeface, Typeface.BOLD)
                        }
                        latestDetailsTextViews!!.addView(tv)
                    }
                }

                if (ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST
                    AppDatabase.getInstance().ownedIdentityDao().updateUnpublishedDetails(
                        ownedIdentity.bytesOwnedIdentity,
                        ownedIdentity.unpublishedDetails
                    )
                }
            } else {
                latestDetails = jsons[0]
                latestDetailsCardView!!.visibility = View.GONE

                if (ownedIdentity.unpublishedDetails != OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW
                    AppDatabase.getInstance().ownedIdentityDao().updateUnpublishedDetails(
                        ownedIdentity.bytesOwnedIdentity,
                        ownedIdentity.unpublishedDetails
                    )
                }
            }
        }
    }

    private fun makeTextView(): TextView {
        val tv: TextView = AppCompatTextView(this)
        tv.setTextColor(primary700)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.maxLines = 4
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, resources.getDimensionPixelSize(R.dimen.identity_details_margin), 0, 0)
        tv.layoutParams = params
        return tv
    }


    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val fullScreenImageFragment = supportFragmentManager.findFragmentByTag(
                FULL_SCREEN_IMAGE_FRAGMENT_TAG
            )
            if (fullScreenImageFragment != null) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            }
        }
        return super.dispatchTouchEvent(event)
    }


    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        val fullScreenImageFragment = supportFragmentManager.findFragmentByTag(
            FULL_SCREEN_IMAGE_FRAGMENT_TAG
        )
        if (fullScreenImageFragment != null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(0, R.anim.fade_out)
                .remove(fullScreenImageFragment)
                .commit()
        } else {
            if (isTaskRoot) {
                App.showMainActivityTab(this, MainActivity.DISCUSSIONS_TAB)
            }
            finish()
        }
    }

    override fun onClick(view: View) {
        val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return

        val id = view.id
        if (id == R.id.add_contact_button) {
            startActivity(Intent(this, PlusButtonActivity::class.java))
        } else if (id == R.id.button_publish) {
            AppSingleton.getEngine().publishLatestIdentityDetails(bytesOwnedIdentity)
        } else if (id == R.id.button_discard) {
            AppSingleton.getEngine().discardLatestIdentityDetails(bytesOwnedIdentity)
            reloadIdentity()
        } else if (id == R.id.button_reactivate_identity) {
            val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
            if (ownedIdentity != null) {
                App.openAppDialogIdentityDeactivated(ownedIdentity)
            }
        } else if (id == R.id.add_device_button) {
            App.startTransferFlowAsSource(this)
        } else if (view is InitialView) {
            val photoUrl = view.photoUrl
            if (photoUrl != null) {
                val fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl)
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, 0)
                    .replace(R.id.overlay, fullScreenImageFragment, FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                    .commit()
            }
        }
    }

    internal inner class IdentityObserver : Observer<OwnedIdentity?> {
        var ownedIdentity: OwnedIdentity? = null

        override fun onChanged(value: OwnedIdentity?) {
            if (this.ownedIdentity != null && (value == null || !value.bytesOwnedIdentity.contentEquals(
                    this.ownedIdentity!!.bytesOwnedIdentity
                ))
            ) {
                // the owned identity change --> leave the activity
                finish()
            } else {
                this.ownedIdentity = value
                reload()
            }
        }

        fun reload() {
            latestDetails = null
            displayDetails(ownedIdentity)
        }
    }

    fun reloadIdentity() {
        identityObserver.reload()
    }

    internal class OwnedDeviceViewHolder(
        itemView: View,
        val viewModel: OwnedIdentityDetailsActivityViewModel
    ) :
        ViewHolder(itemView) {
        private val deviceNameTextView: TextView = itemView.findViewById(R.id.device_name_text_view)
        private val deviceStatusTextView: TextView =
            itemView.findViewById(R.id.device_status_text_view)
        private val expirationTextView: TextView =
            itemView.findViewById(R.id.device_expiration_text_view)
        private val channelCreationGroup: ViewGroup =
            itemView.findViewById(R.id.establishing_channel_group)
        private val channelCreationDotsImageView: ImageView =
            itemView.findViewById(R.id.establishing_channel_image_view)
        val dots: ImageView = itemView.findViewById(R.id.button_dots)
        private val untrusted: ImageView
        private var ownedDevice: OwnedDevice? = null

        init {
            dots.setOnClickListener { view: View -> this.onClick(view) }
            untrusted = itemView.findViewById(R.id.untrusted)
        }

        fun bind(ownedDevice: OwnedDevice, currentDeviceIsActive: Boolean) {
            this.ownedDevice = ownedDevice
            deviceNameTextView.text =
                ownedDevice.getDisplayNameOrDeviceHexName(deviceNameTextView.context)

            if (ownedDevice.currentDevice) {
                deviceStatusTextView.visibility = View.VISIBLE
                deviceStatusTextView.setText(R.string.text_this_device)
            } else if (ownedDevice.lastRegistrationTimestamp != null) {
                deviceStatusTextView.visibility = View.VISIBLE
                deviceStatusTextView.text = deviceStatusTextView.context.getString(
                    R.string.text_last_online,
                    StringUtils.getLongNiceDateString(
                        deviceStatusTextView.context,
                        ownedDevice.lastRegistrationTimestamp!!
                    )
                )
            } else {
                deviceStatusTextView.visibility = View.GONE
            }

            if (ownedDevice.currentDevice && !currentDeviceIsActive) {
                expirationTextView.visibility = View.VISIBLE
                expirationTextView.setText(R.string.text_device_is_inactive)
                expirationTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_device_inactive,
                    0,
                    0,
                    0
                )
            } else if (ownedDevice.expirationTimestamp == null) {
                expirationTextView.visibility = View.GONE
            } else {
                expirationTextView.visibility = View.VISIBLE
                expirationTextView.text = deviceStatusTextView.context.getString(
                    R.string.text_deactivates_on,
                    StringUtils.getPreciseAbsoluteDateString(
                        deviceStatusTextView.context,
                        ownedDevice.expirationTimestamp!!,
                        expirationTextView.context.getString(R.string.text_date_time_separator)
                    )
                )
                expirationTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_device_expiration,
                    0,
                    0,
                    0
                )
            }

            if (ownedDevice.trusted || ownedDevice.currentDevice) {
                untrusted.visibility = View.GONE
            } else {
                untrusted.visibility = View.VISIBLE
            }

            if (!currentDeviceIsActive || ownedDevice.channelConfirmed || ownedDevice.currentDevice) {
                channelCreationGroup.visibility = View.GONE
                channelCreationDotsImageView.setImageDrawable(null)
            } else {
                channelCreationGroup.visibility = View.VISIBLE
                val animated = AnimatedVectorDrawableCompat.create(
                    channelCreationDotsImageView.context,
                    R.drawable.dots
                )
                if (animated != null) {
                    animated.registerAnimationCallback(object : AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable) {
                            Handler(Looper.getMainLooper()).post { animated.start() }
                        }
                    })
                    animated.start()
                }
                channelCreationDotsImageView.setImageDrawable(animated)
            }

            if (currentDeviceIsActive) {
                dots.visibility = View.VISIBLE
            } else {
                dots.visibility = View.GONE
            }
        }

        fun unbind() {
            ownedDevice = null
        }

        private fun onClick(view: View) {
            if (ownedDevice != null) {
                var order = 0
                val popupMenu = PopupMenu(view.context, view)

                if (!ownedDevice!!.currentDevice && !ownedDevice!!.trusted) {
                    popupMenu.menu.add(Menu.NONE, 6, order++, R.string.menu_action_trust_device)
                }

                if (ownedDevice!!.currentDevice) {
                    popupMenu.menu.add(Menu.NONE, 3, order++, R.string.menu_action_rename_device)
                } else {
                    popupMenu.menu.add(Menu.NONE, 4, order++, R.string.menu_action_rename_device)
                }

                if (ownedDevice!!.expirationTimestamp != null) {
                    popupMenu.menu.add(
                        Menu.NONE,
                        5,
                        order++,
                        R.string.menu_action_remove_expiration
                    )
                }


                if (ownedDevice!!.currentDevice) {
                    popupMenu.menu.add(
                        Menu.NONE,
                        0,
                        order++,
                        R.string.menu_action_refresh_device_list
                    )
                }

                if (!ownedDevice!!.currentDevice) {
                    popupMenu.menu.add(Menu.NONE, 1, order++, R.string.menu_action_recreate_channel)
                    val removeDeviceSpannableString =
                        SpannableString(view.context.getString(R.string.menu_action_remove_device))
                    removeDeviceSpannableString.setSpan(
                        ForegroundColorSpan(
                            ContextCompat.getColor(
                                view.context,
                                R.color.red
                            )
                        ), 0, removeDeviceSpannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    popupMenu.menu.add(Menu.NONE, 2, order++, removeDeviceSpannableString)
                }
                popupMenu.setOnMenuItemClickListener { popupItem: MenuItem ->
                    this.onMenuItemClick(
                        popupItem
                    )
                }
                popupMenu.show()
            }
        }

        private fun onMenuItemClick(popupItem: MenuItem): Boolean {
            if (ownedDevice == null) {
                return false
            }


            when (popupItem.itemId) {
                0 -> {
                    // refresh
                    if (ownedDevice!!.currentDevice) {
                        App.runThread {
                            OwnedDevicesSynchronisationWithEngineTask(ownedDevice!!.bytesOwnedIdentity).run()
                            AppSingleton.getEngine()
                                .refreshOwnedDeviceList(ownedDevice!!.bytesOwnedIdentity)
                        }
                    }
                }

                1 -> {
                    // recreate channel
                    if (!ownedDevice!!.currentDevice) {
                        AppSingleton.getEngine().recreateOwnedDeviceChannel(
                            ownedDevice!!.bytesOwnedIdentity,
                            ownedDevice!!.bytesDeviceUid
                        )
                    }
                }

                2 -> {
                    // delete
                    val builder =
                        SecureAlertDialogBuilder(itemView.context, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_remove_device)
                            .setMessage(R.string.dialog_message_remove_device)
                            .setPositiveButton(
                                R.string.button_label_remove,
                                (DialogInterface.OnClickListener { _, _ ->
                                    try {
                                        viewModel.showRefreshSpinner()
                                        AppSingleton.getEngine().processDeviceManagementRequest(
                                            ownedDevice!!.bytesOwnedIdentity,
                                            ObvDeviceManagementRequest.createDeactivateDeviceRequest(
                                                ownedDevice!!.bytesDeviceUid
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                })
                            )
                            .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                }

                3, 4 -> {
                    // rename device
                    val dialogView = LayoutInflater.from(itemView.context)
                        .inflate(R.layout.dialog_view_message_and_input, null)
                    val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
                    if (popupItem.itemId == 3) {
                        // current device
                        messageTextView.setText(R.string.dialog_message_rename_current_device)
                    } else {
                        // other device
                        messageTextView.setText(R.string.dialog_message_rename_other_device)
                    }
                    val textInputLayout =
                        dialogView.findViewById<TextInputLayout>(R.id.dialog_text_layout)
                    textInputLayout.setHint(R.string.hint_device_name)

                    val deviceNameEditText =
                        dialogView.findViewById<TextInputEditText>(R.id.dialog_edittext)
                    deviceNameEditText.setText(ownedDevice!!.displayName)
                    deviceNameEditText.inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS

                    val builder =
                        SecureAlertDialogBuilder(itemView.context, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_rename_device)
                            .setView(dialogView)
                            .setPositiveButton(R.string.button_label_ok) { _, _ ->
                                val nickname: CharSequence? = deviceNameEditText.text
                                if (nickname != null) {
                                    try {
                                        viewModel.showRefreshSpinner()
                                        AppSingleton.getEngine().processDeviceManagementRequest(
                                            ownedDevice!!.bytesOwnedIdentity,
                                            ObvDeviceManagementRequest.createSetNicknameRequest(
                                                ownedDevice!!.bytesDeviceUid, nickname.toString()
                                            )
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                    if (popupItem.itemId == 3) {
                        builder.setNeutralButton(R.string.button_label_default, null)
                    }

                    val dialog = builder.create()
                    dialog.setOnShowListener {
                        val ok =
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        if (ok != null) {
                            deviceNameEditText.addTextChangedListener(object :
                                TextChangeListener() {
                                override fun afterTextChanged(s: Editable) {
                                    ok.isEnabled = s.isEmpty().not()
                                }
                            })
                        }
                        val neutral =
                            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        neutral?.setOnClickListener {
                            deviceNameEditText.setText(
                                AppSingleton.DEFAULT_DEVICE_DISPLAY_NAME
                            )
                        }
                    }
                    dialog.show()
                }

                5 -> {
                    // set as unexpiring
                    if (ownedDevice!!.expirationTimestamp != null) {
                        App.runThread {
                            AppDatabase.getInstance()
                                .ownedIdentityDao()[ownedDevice!!.bytesOwnedIdentity]?.let { ownedIdentity ->
                                if (!ownedIdentity.hasMultiDeviceApiKeyPermission()) {
                                    val ownedDevices = AppDatabase.getInstance().ownedDeviceDao()
                                        .getAllSync(ownedIdentity.bytesOwnedIdentity)
                                    var currentlyNotExpiringDevice: OwnedDevice? =
                                        null
                                    for (ownedDevice in ownedDevices) {
                                        if (ownedDevice.expirationTimestamp == null) {
                                            currentlyNotExpiringDevice = ownedDevice
                                            break
                                        }
                                    }

                                    if (currentlyNotExpiringDevice != null) {
                                        val builder = SecureAlertDialogBuilder(
                                            itemView.context,
                                            R.style.CustomAlertDialog
                                        )
                                            .setTitle(R.string.dialog_title_set_unexpiring_device)
                                            .setMessage(
                                                itemView.context.getString(
                                                    R.string.dialog_message_set_unexpiring_device,
                                                    ownedDevice!!.getDisplayNameOrDeviceHexName(
                                                        itemView.context
                                                    ),
                                                    currentlyNotExpiringDevice.getDisplayNameOrDeviceHexName(
                                                        itemView.context
                                                    )
                                                )
                                            )
                                            .setPositiveButton(
                                                R.string.button_label_proceed,
                                                (DialogInterface.OnClickListener { _, _ ->
                                                    try {
                                                        viewModel.showRefreshSpinner()
                                                        AppSingleton.getEngine()
                                                            .processDeviceManagementRequest(
                                                                ownedDevice!!.bytesOwnedIdentity,
                                                                ObvDeviceManagementRequest.createSetUnexpiringDeviceRequest(
                                                                    ownedDevice!!.bytesDeviceUid
                                                                )
                                                            )
                                                    } catch (_: Exception) {
                                                    }
                                                })
                                            )
                                            .setNegativeButton(R.string.button_label_cancel, null)
                                        Handler(Looper.getMainLooper()).post {
                                            builder.create().show()
                                        }
                                        return@runThread
                                    }
                                }
                            }
                            try {
                                // if we reach this point, the user either has the multi-device permission, or has an expiration for all of his devices --> no need to show a confirmation dialog
                                viewModel.showRefreshSpinner()
                                AppSingleton.getEngine().processDeviceManagementRequest(
                                    ownedDevice!!.bytesOwnedIdentity,
                                    ObvDeviceManagementRequest.createSetUnexpiringDeviceRequest(
                                        ownedDevice!!.bytesDeviceUid
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                6 -> {
                    // trust
                    App.runThread {
                        AndroidNotificationManager.clearDeviceTrustNotification(ownedDevice!!.bytesDeviceUid)
                        AppDatabase.getInstance().ownedDeviceDao().updateTrusted(
                            ownedDevice!!.bytesOwnedIdentity,
                            ownedDevice!!.bytesDeviceUid,
                            true
                        )
                    }
                }

            }
            return true
        }
    }

    class OwnedIdentityDetailsActivityViewModel : ViewModel() {
        val showRefreshSpinner: MutableLiveData<Boolean> = MutableLiveData(false)
        val ownedIdentityActive: MutableLiveData<Boolean?> = MutableLiveData(null)
        val ownedDevicesLiveData: LiveData<List<OwnedDevice>> =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    if (ownedIdentityActive.value != true) {
                        ownedIdentityActive.postValue(true)
                    }
                    return@switchMap MutableLiveData<List<OwnedDevice>>(
                        emptyList<OwnedDevice>()
                    )
                }
                if (ownedIdentityActive.value != ownedIdentity.active) {
                    ownedIdentityActive.postValue(ownedIdentity.active)
                }
                AppDatabase.getInstance().ownedDeviceDao()
                    .getAllSorted(ownedIdentity.bytesOwnedIdentity)
            }

        fun showRefreshSpinner() {
            showRefreshSpinner.postValue(true)
        }

        fun hideRefreshSpinner() {
            showRefreshSpinner.postValue(false)
        }
    }

    companion object {
        const val FULL_SCREEN_IMAGE_FRAGMENT_TAG: String = "full_screen_image"
    }
}
