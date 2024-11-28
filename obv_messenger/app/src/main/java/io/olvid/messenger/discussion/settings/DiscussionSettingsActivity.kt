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
package io.olvid.messenger.discussion.settings

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.discussion.compose.EphemeralViewModel

class DiscussionSettingsActivity : LockableActivity(), OnPreferenceStartFragmentCallback {
    private val discussionSettingsViewModel: DiscussionSettingsViewModel by viewModels()
    private val ephemeralViewModel: EphemeralViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.elevation = 0f

        setContentView(R.layout.activity_settings)

        findViewById<CoordinatorLayout>(R.id.root_coordinator)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                view.updatePadding(top = insets.top, bottom = insets.bottom)
                windowInsets
            }
        }

        discussionSettingsViewModel.discussionCustomization.observe(
            this
        ) { discussionCustomization: DiscussionCustomization? ->
            discussionSettingsViewModel.notifySettingsChangedListeners(discussionCustomization)
            // if viewModel has not recorded any modifications, update its content
            discussionSettingsViewModel.updateNotificationsFromCustomization(
                discussionCustomization
            )
        }
        discussionSettingsViewModel.nonAdminGroupDiscussionLiveData.observe(
            this
        ) { discussionSettingsViewModel.notifyLockedOrNonGroupAdminChanged() }
        discussionSettingsViewModel.discussionLockedLiveData.observe(
            this
        ) { discussionSettingsViewModel.notifyLockedOrNonGroupAdminChanged() }

        if (savedInstanceState == null) {
            val settingsFragment = DiscussionSettingsHeadersFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, settingsFragment).commit()

            val intent = intent
            if (intent != null && intent.hasExtra(DISCUSSION_ID_INTENT_EXTRA)) {
                val discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1)
                discussionSettingsViewModel.discussionId = discussionId

                val prefKey = intent.getStringExtra(SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA)
                if (prefKey != null) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({
                        val preference = settingsFragment.findPreference<Preference>(prefKey)
                        if (preference != null && preference.isEnabled) {
                            val position =
                                preference.order - (if (discussionSettingsViewModel.isLocked) 0 else 1)
                            val recyclerView = settingsFragment.listView
                            if (recyclerView != null) {
                                val viewHolder =
                                    recyclerView.findViewHolderForAdapterPosition(position)
                                if (viewHolder != null) {
                                    viewHolder.itemView.isPressed = true
                                    handler.postDelayed({
                                        onPreferenceStartFragment(
                                            settingsFragment,
                                            preference
                                        )
                                    }, 600)
                                } else {
                                    recyclerView.scrollToPosition(position)
                                    handler.postDelayed({
                                        val reViewHolder =
                                            recyclerView.findViewHolderForAdapterPosition(position)
                                        if (reViewHolder != null) {
                                            reViewHolder.itemView.isPressed = true
                                        }
                                    }, 100)
                                    handler.postDelayed({
                                        onPreferenceStartFragment(
                                            settingsFragment,
                                            preference
                                        )
                                    }, 700)
                                }
                            }
                        }
                    }, 400)
                }
            } else {
                finish()
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onBackPressed() {
        setTitle(R.string.activity_title_discussion_settings)
        if (discussionSettingsViewModel.isMessageNotificationModified) {
            discussionSettingsViewModel.saveCustomMessageNotification()
        }
        if (java.lang.Boolean.TRUE == ephemeralViewModel.getDefaultsLoaded().value
            && java.lang.Boolean.TRUE == ephemeralViewModel.getSettingsModified().value
            && !discussionSettingsViewModel.isLocked
            && !discussionSettingsViewModel.isNonAdminGroupDiscussion
        ) {
            val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_shared_ephemeral_settings_modified)
                .setMessage(R.string.dialog_message_shared_ephemeral_settings_modified)
                .setNegativeButton(R.string.button_label_discard) { dialog: DialogInterface?, which: Int ->
                    ephemeralViewModel.reset()
                    super.onBackPressed()
                }
                .setPositiveButton(R.string.button_label_update) { dialog: DialogInterface?, which: Int ->
                    discussionSettingsViewModel.saveEphemeralSettingsAndNotifyPeers(
                        ephemeralViewModel.getReadOnce(),
                        ephemeralViewModel.getVisibility(),
                        ephemeralViewModel.getExistence()
                    )
                    ephemeralViewModel.discardDefaults()
                    super.onBackPressed()
                }
            builder.create().show()
        } else {
            super.onBackPressed()
        }
    }


    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val prefFragmentName = pref.fragment ?: return false
        val fragment: Fragment
        try {
            fragment =
                supportFragmentManager.fragmentFactory.instantiate(classLoader, prefFragmentName)
        } catch (e: Exception) {
            return false
        }

        try {
            // Replace the existing Fragment with the new Fragment
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // set the activity title
        title = pref.title
        return true
    }

    companion object {
        const val DISCUSSION_ID_INTENT_EXTRA: String = "discussion_id"
        const val SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA: String =
            "sub_setting_pref_key_to_open_intent_extra"

        const val PREF_KEY_DISCUSSION_CATEGORY_LOCKED_EXPLANATION: String =
            "pref_key_discussion_category_locked_explanation"
        const val PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS: String =
            "pref_key_discussion_category_shared_ephemeral_settings"
        const val PREF_KEY_DISCUSSION_CATEGORY_RETENTION_POLICY: String =
            "pref_key_discussion_category_retention_policy"
        const val PREF_KEY_DISCUSSION_CATEGORY_SEND_RECEIVE: String =
            "pref_key_discussion_category_send_receive"

        const val PREF_KEY_DISCUSSION_PIN: String = "pref_key_discussion_pin"
        const val PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS: String =
            "pref_key_discussion_mute_notifications"

        const val PREF_KEY_DISCUSSION_COLOR: String = "pref_key_discussion_color"
        const val PREF_KEY_DISCUSSION_BACKGROUND_IMAGE: String =
            "pref_key_discussion_background_image"

        const val PREF_KEY_DISCUSSION_MESSAGE_CUSTOM_NOTIFICATION: String =
            "pref_key_discussion_message_custom_notification"
        const val PREF_KEY_DISCUSSION_MESSAGE_VIBRATION_PATTERN: String =
            "pref_key_discussion_message_vibration_pattern"
        const val PREF_KEY_DISCUSSION_MESSAGE_RINGTONE: String =
            "pref_key_discussion_message_ringtone"
        const val PREF_KEY_DISCUSSION_MESSAGE_LED_COLOR: String =
            "pref_key_discussion_message_led_color"

        const val PREF_KEY_DISCUSSION_CALL_CUSTOM_NOTIFICATION: String =
            "pref_key_discussion_call_custom_notification"
        const val PREF_KEY_DISCUSSION_CALL_VIBRATION_PATTERN: String =
            "pref_key_discussion_call_vibration_pattern"
        const val PREF_KEY_DISCUSSION_CALL_RINGTONE: String = "pref_key_discussion_call_ringtone"
        const val PREF_KEY_DISCUSSION_CALL_USE_FLASH: String = "pref_key_discussion_call_use_flash"

        const val PREF_KEY_DISCUSSION_READ_RECEIPT: String = "pref_key_discussion_read_receipt"
        const val PREF_KEY_DISCUSSION_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND: String =
            "pref_key_discussion_auto_open_limited_visibility_inbound"
        const val PREF_KEY_DISCUSSION_RETAIN_WIPED_OUTBOUND_MESSAGES: String =
            "pref_key_discussion_retain_wiped_outbound_messages"

        const val PREF_KEY_DISCUSSION_RETENTION_COUNT: String =
            "pref_key_discussion_retention_count"
        const val PREF_KEY_DISCUSSION_RETENTION_DURATION: String =
            "pref_key_discussion_retention_duration"

        const val PREF_KEY_COMPOSE_EPHEMERAL_SETTINGS: String =
            "pref_key_compose_ephemeral_settings"
    }
}
