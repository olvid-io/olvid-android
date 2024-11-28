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
package io.olvid.messenger.settings

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences.Editor
import android.content.res.Configuration
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.Constants
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.billing.BillingUtils
import io.olvid.messenger.customClasses.LocationShareQuality
import io.olvid.messenger.customClasses.LocationShareQuality.QUALITY_BALANCED
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.firebase.ObvFirebaseMessagingService
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.main.Utils
import io.olvid.messenger.services.BackupCloudProviderService.CloudProviderConfiguration
import io.olvid.messenger.settings.SettingsActivity.AutoJoinGroupsCategory.CONTACTS
import io.olvid.messenger.settings.SettingsActivity.AutoJoinGroupsCategory.EVERYONE
import io.olvid.messenger.settings.SettingsActivity.AutoJoinGroupsCategory.NOBODY
import io.olvid.messenger.settings.SettingsActivity.BlockUntrustedCertificate.ALWAYS
import io.olvid.messenger.settings.SettingsActivity.BlockUntrustedCertificate.ISSUER_CHANGED
import io.olvid.messenger.settings.SettingsActivity.BlockUntrustedCertificate.NEVER
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.BASIC
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.MAPS
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.OSM
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.DOT
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.FULL
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.LINE
import io.olvid.messenger.settings.SettingsActivity.PingConnectivityIndicator.NONE
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SettingsActivity : LockableActivity(), OnPreferenceStartFragmentCallback {
    enum class AutoJoinGroupsCategory {
        EVERYONE,
        CONTACTS,
        NOBODY;

        val stringValue: String
            get() {
                return when (this) {
                    NOBODY -> "nobody"
                    EVERYONE -> "everyone"
                    CONTACTS -> "contacts"
                    else -> "contacts"
                }
            }
    }

    enum class PingConnectivityIndicator {
        NONE,
        DOT,
        LINE,
        FULL,
    }

    enum class BlockUntrustedCertificate {
        ALWAYS,
        ISSUER_CHANGED,
        NEVER,
    }

    enum class LocationIntegrationEnum {
        NONE,  // used if user did not yet chose an integration
        OSM,
        MAPS,
        BASIC,
        CUSTOM_OSM;

        val string: String?
            get() {
                return when (this) {
                    OSM -> PREF_VALUE_LOCATION_INTEGRATION_OSM
                    MAPS -> PREF_VALUE_LOCATION_INTEGRATION_MAPS
                    BASIC -> PREF_VALUE_LOCATION_INTEGRATION_BASIC
                    CUSTOM_OSM -> PREF_VALUE_LOCATION_INTEGRATION_CUSTOM_OSM
                    NONE -> null
                    else -> null
                }
            }
    }

    // ACTIVITY VARIABLES
    private var headersPreferenceFragment: HeadersPreferenceFragment? = null


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

        if (savedInstanceState == null) {
            headersPreferenceFragment = HeadersPreferenceFragment()

            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, headersPreferenceFragment!!)
                .commit()

            val intent: Intent? = intent
            if (intent != null && intent.hasExtra(SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA)) {
                val prefKey: String? = intent.getStringExtra(
                    SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA
                )
                if (prefKey != null) {
                    Handler(Looper.getMainLooper()).apply {
                        postDelayed({
                            if (headersPreferenceFragment != null) {
                                val preference: Preference? =
                                    headersPreferenceFragment!!.findPreference(
                                        prefKey
                                    )
                                if (preference != null) {
                                    val position: Int = preference.order
                                    val recyclerView: RecyclerView? =
                                        headersPreferenceFragment!!.listView
                                    if (recyclerView != null) {
                                        val viewHolder: ViewHolder? =
                                            recyclerView.findViewHolderForAdapterPosition(position)
                                        if (viewHolder != null) {
                                            viewHolder.itemView.isPressed = true
                                            postDelayed({
                                                onPreferenceStartFragment(
                                                    headersPreferenceFragment!!,
                                                    preference
                                                )
                                            }, 600)
                                        } else {
                                            recyclerView.scrollToPosition(position)
                                            postDelayed({
                                                val reViewHolder: ViewHolder? =
                                                    recyclerView.findViewHolderForAdapterPosition(
                                                        position
                                                    )
                                                if (reViewHolder != null) {
                                                    reViewHolder.itemView.isPressed = true
                                                }
                                            }, 100)
                                            postDelayed({
                                                onPreferenceStartFragment(
                                                    headersPreferenceFragment!!,
                                                    preference
                                                )
                                            }, 700)
                                        }
                                    }
                                }
                            }
                        }, 400)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId: Int = item.itemId
        if (itemId == R.id.action_about) {
            val builder: Builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            val extraFeatures: MutableList<String> = ArrayList()
            if (betaFeaturesEnabled) {
                extraFeatures.add("beta")
            }
            val uptime: String = Utils.getUptime(this)
            builder.setTitle(R.string.dialog_title_about_olvid)
                .setPositiveButton(R.string.button_label_ok, null)
            val sb: StringBuilder = StringBuilder()
            if (extraFeatures.isEmpty() || VERSION.SDK_INT < VERSION_CODES.O) {
                sb.append(
                    getString(
                        R.string.dialog_message_about_olvid,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        Constants.SERVER_API_VERSION,
                        Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION,
                        AppDatabase.DB_SCHEMA_VERSION,
                        uptime
                    )
                )
            } else {
                val features: String = java.lang.String.join(
                    getString(R.string.text_contact_names_separator),
                    extraFeatures
                )
                sb.append(
                    getString(
                        R.string.dialog_message_about_olvid_extra_features,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        Constants.SERVER_API_VERSION,
                        Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION,
                        AppDatabase.DB_SCHEMA_VERSION,
                        features,
                        uptime
                    )
                )
            }
            @Suppress("KotlinConstantConditions")
            if (BuildConfig.USE_FIREBASE_LIB && GoogleServicesUtils.googleServicesAvailable(
                    this
                ) && !disablePushNotifications()
            ) {
                sb.append("\n")
                val date: CharSequence
                if (ObvFirebaseMessagingService.getLastPushNotificationTimestamp() == null) {
                    date = "-"
                } else {
                    date = StringUtils.getLongNiceDateString(
                        this,
                        ObvFirebaseMessagingService.getLastPushNotificationTimestamp()
                    )
                }
                sb.append(getString(R.string.dialog_message_about_last_push_notification, date))

                sb.append("\n")
                sb.append(
                    getString(
                        R.string.dialog_message_about_deprioritized_push_notification,
                        ObvFirebaseMessagingService.getDeprioritizedMessageCount(),
                        ObvFirebaseMessagingService.getHighPriorityMessageCount()
                    )
                )
            }

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.USE_GOOGLE_LIBS) {
                sb.append("\n\n")

                val link: String = getString(R.string.activity_title_open_source_licenses)
                sb.append(link)
                val spannableString = SpannableString(sb)
                spannableString.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            GoogleServicesUtils.openOssMenuActivity(this@SettingsActivity)
                        }
                    },
                    spannableString.length - link.length,
                    spannableString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setMessage(spannableString)
            } else {
                builder.setMessage(sb)
            }
            val dialog: Dialog = builder.create()

            dialog.setOnShowListener { dialog1: DialogInterface? ->
                if (dialog1 is AlertDialog) {
                    val messageTextView: TextView? = dialog1.findViewById(android.R.id.message)
                    if (messageTextView != null) {
                        messageTextView.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            }
            dialog.show()

            return true
        } else if (itemId == R.id.action_check_update) {
            val appPackageName: String = packageName
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$appPackageName")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                        )
                    )
                } catch (ee: Exception) {
                    ee.printStackTrace()
                }
            }
            return true
        } else if (itemId == R.id.action_help_faq) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://olvid.io/faq/")))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
        } else if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class HeadersPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_header, rootKey)

            val activity: FragmentActivity = requireActivity()

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.USE_BILLING_LIB) {
                BillingUtils.loadSubscriptionSettingsHeader(activity, preferenceScreen)
            }

            run {
                App.runThread {
                    if (AppDatabase.getInstance().actionShortcutConfigurationDao().countAll() > 0) {
                        Handler(Looper.getMainLooper()).post {
                            val actionShortcutPreference =
                                Preference(activity)
                            actionShortcutPreference.widgetLayoutResource =
                                R.layout.preference_widget_header_chevron
                            actionShortcutPreference.setIcon(R.drawable.ic_pref_widget)
                            actionShortcutPreference.setTitle(R.string.pref_title_widgets)
                            actionShortcutPreference.fragment = WidgetListFragment::class.java.name
                            preferenceScreen.addPreference(actionShortcutPreference)
                        }
                    }
                }
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.dialogBackground))
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val prefFragmentName: String = pref.fragment ?: return false
        val fragment: Fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader, prefFragmentName
        )

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


    override fun onBackPressed() {
        setTitle(R.string.activity_title_settings)
        super.onBackPressed()
    }


    companion object {
        const val SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA: String = "sub_setting"
        const val PREF_HEADER_KEY_BACKUP: String = "pref_header_key_backup"
        const val PREF_HEADER_KEY_NOTIFICATIONS: String = "pref_header_key_notifications"

        //    const val PREF_HEADER_KEY_PRIVACY: String = "pref_header_key_privacy";
        const val PREF_HEADER_KEY_LOCK_SCREEN: String = "pref_header_key_lock_screen"
        //    const val PREF_HEADER_KEY_LOCATION: String = "pref_header_key_location"

        const val ACTIVITY_RECREATE_REQUIRED_ACTION: String = "activity_recreate_required_action"


        // NON-USER VISIBLE SETTINGS
        const val PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP: String =
            "pref_key_last_available_space_warning_timestamp"
        const val PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED: String =
            "pref_key_first_call_audio_permission_requested"
        const val PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED: String =
            "pref_key_first_call_bluetooth_permission_requested"
        const val PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER: String =
            "pref_key_compose_message_icon_preferred_order"

        const val PREF_KEY_PREFERRED_REACTIONS: String = "pref_key_preferred_reactions"
        val PREF_KEY_PREFERRED_REACTIONS_DEFAULT: Array<String> =
            arrayOf("❤️", "👍", "👎", "😂", "😮", "😢")


        // BETA
        const val PREF_KEY_ENABLE_BETA_FEATURES: String = "pref_key_enable_beta_features"
        const val PREF_KEY_ENABLE_BETA_FEATURES_DEFAULT: Boolean = false

        const val PREF_KEY_SCALED_TURN_REGION: String = "pref_key_scaled_turn_region"
        const val PREF_KEY_SCALED_TURN_REGION_DEFAULT: String = "global"

        const val PREF_KEY_EXPORT_APP_DATABASES: String = "pref_key_export_app_databases"
        const val PREF_KEY_STORAGE_EXPLORER: String = "pref_key_storage_explorer"


        // NOTIFICATIONS
        val VIBRATION_PATTERN_1_SHORT: LongArray = longArrayOf(0, 100, 2900)
        val VIBRATION_PATTERN_2_SHORT: LongArray = longArrayOf(0, 100, 50, 100, 2750)
        val VIBRATION_PATTERN_3_SHORT: LongArray = longArrayOf(0, 100, 50, 100, 50, 100, 2600)
        val VIBRATION_PATTERN_1_LONG: LongArray = longArrayOf(0, 250, 2750)
        val VIBRATION_PATTERN_2_LONG: LongArray = longArrayOf(0, 250, 150, 250, 2350)
        val VIBRATION_PATTERN_3_LONG: LongArray = longArrayOf(0, 250, 150, 250, 150, 250, 1950)

        const val PREF_KEY_MESSAGE_VIBRATION_PATTERN: String = "pref_key_message_vibration_pattern"
        const val PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT: String = "1"

        const val PREF_KEY_MESSAGE_RINGTONE: String = "pref_key_message_ringtone"
        @JvmField
        val PREF_KEY_MESSAGE_RINGTONE_DEFAULT: String =
            Settings.System.DEFAULT_NOTIFICATION_URI.toString()

        const val PREF_KEY_MESSAGE_LED_COLOR: String = "pref_key_message_led_color"
        const val PREF_KEY_MESSAGE_LED_COLOR_DEFAULT: String = "#2f65f5"

        const val PREF_KEY_CALL_VIBRATION_PATTERN: String = "pref_key_call_vibration_pattern"
        const val PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT: String = "20"

        const val PREF_KEY_CALL_RINGTONE: String = "pref_key_call_ringtone"
        @JvmField
        val PREF_KEY_CALL_RINGTONE_DEFAULT: String = Settings.System.DEFAULT_RINGTONE_URI.toString()

        const val PREF_KEY_CALL_USE_FLASH: String = "pref_key_call_use_flash"
        const val PREF_KEY_CALL_USE_FLASH_DEFAULT: Boolean = false

        const val PREF_KEY_PERMANENT_FOREGROUND_SERVICE: String =
            "pref_key_permanent_foreground_service"
        const val PREF_KEY_PERMANENT_FOREGROUND_SERVICE_DEFAULT: Boolean = false


        // CUSTOMIZATION
        const val PREF_KEY_APP_LANGUAGE: String = "pref_key_app_language"
        const val PREF_KEY_DARK_MODE: String = "pref_key_dark_mode"
        const val PREF_KEY_DARK_MODE_DEFAULT: Boolean = false
        const val PREF_KEY_DARK_MODE_API29: String = "pref_key_dark_mode_api29"
        const val PREF_KEY_DARK_MODE_DEFAULT_API29: String = "Auto"

        const val PREF_KEY_FONT_SCALE: String = "pref_key_font_scale"
        const val PREF_KEY_FONT_SCALE_DEFAULT: String = "1.0"

        const val PREF_KEY_APP_ICON: String = "pref_key_app_icon"

        const val PREF_KEY_SCREEN_SCALE: String = "pref_key_screen_scale"
        const val PREF_KEY_SCREEN_SCALE_DEFAULT: String = "1.0"

        const val PREF_KEY_USE_SYSTEM_EMOJIS: String = "pref_key_use_system_emojis"
        const val PREF_KEY_USE_SYSTEM_EMOJIS_DEFAULT: Boolean = false

        const val PREF_KEY_USE_INTERNAL_IMAGE_VIEWER: String = "pref_key_use_internal_image_viewer"
        const val PREF_KEY_USE_INTERNAL_IMAGE_VIEWER_DEFAULT: Boolean = true

        const val PREF_KEY_SORT_CONTACTS_BY_LAST_NAME: String =
            "pref_key_sort_contacts_by_last_name"
        const val PREF_KEY_SORT_CONTACTS_BY_LAST_NAME_DEFAULT: Boolean = false

        const val PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT: String =
            "pref_key_contact_display_name_format"
        const val PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT_DEFAULT: String = "%f %l"

        const val PREF_KEY_SOMETIMES_SHOW_FIRST_NAME_ONLY: String =
            "pref_key_sometimes_show_first_name_only"
        const val PREF_KEY_SOMETIMES_SHOW_FIRST_NAME_ONLY_DEFAULT: Boolean = true

        const val PREF_KEY_UPPERCASE_LAST_NAME: String = "pref_key_uppercase_last_name"
        const val PREF_KEY_UPPERCASE_LAST_NAME_DEFAULT: Boolean = false


        // PRIVACY
        const val PREF_KEY_LAST_READ_RECEIPT_PROMPT_ANSWER_TIMESTAMP: String =
            "pref_key_last_read_receipt_prompt_answer_timestamp"
        const val PREF_KEY_READ_RECEIPT: String = "pref_key_send_read_receipt"
        const val PREF_KEY_READ_RECEIPT_DEFAULT: Boolean = false

        const val PREF_KEY_HIDE_NOTIFICATION_CONTENTS: String =
            "pref_key_hide_notification_contents"
        const val PREF_KEY_HIDE_NOTIFICATION_CONTENTS_DEFAULT: Boolean = false

        const val PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS: String =
            "pref_key_disable_notification_suggestions"
        const val PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS_DEFAULT: Boolean = true

        const val PREF_KEY_EXPOSE_RECENT_DISCUSSIONS: String = "pref_key_expose_recent_discussions"
        const val PREF_KEY_EXPOSE_RECENT_DISCUSSIONS_DEFAULT: Boolean = false

        const val PREF_KEY_KEYBOARD_INCOGNITO_MODE: String = "pref_key_keyboard_incognito_mode"
        const val PREF_KEY_KEYBOARD_INCOGNITO_MODE_DEFAULT: Boolean = true

        const val PREF_KEY_PREVENT_SCREEN_CAPTURE: String = "pref_key_prevent_screen_capture"
        const val PREF_KEY_PREVENT_SCREEN_CAPTURE_DEFAULT: Boolean = true

        const val PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY: String =
            "pref_key_hidden_profile_close_policy"
        const val HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK: Int = 1
        const val HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING: Int = 2
        const val HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND: Int = 3

        const val PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY: String =
            "pref_key_hidden_profile_close_policy_background_grace_delay"
        const val PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_DEFAULT: Int = -1
        @JvmField
        val PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_VALUES: IntArray =
            intArrayOf(0, 10, 30, 60, 120, 300)


        const val PREF_KEY_DISABLE_PUSH_NOTIFICATIONS: String =
            "pref_key_disable_push_notifications"
        const val PREF_KEY_DISABLE_PUSH_NOTIFICATIONS_DEFAULT: Boolean = false

        const val PREF_KEY_PERMANENT_WEBSOCKET: String = "pref_key_permanent_websocket"
        @Suppress("KotlinConstantConditions")
        val PREF_KEY_PERMANENT_WEBSOCKET_DEFAULT: Boolean = !BuildConfig.USE_FIREBASE_LIB

        // CONTACTS & GROUPS
        const val PREF_KEY_AUTO_JOIN_GROUPS: String = "pref_key_auto_join_groups"
        const val PREF_KEY_AUTO_JOIN_GROUPS_DEFAULT: String = "contacts"

        const val PREF_KEY_HIDE_GROUP_MEMBER_CHANGES: String = "pref_key_hide_group_member_changes"
        const val PREF_KEY_HIDE_GROUP_MEMBER_CHANGES_DEFAULT: Boolean = false

        const val PREF_KEY_SHOW_TRUST_LEVELS: String = "pref_key_show_trust_levels"
        const val PREF_KEY_SHOW_TRUST_LEVELS_DEFAULT: Boolean = false

        // LOCK SCREEN
        const val PREF_KEY_USE_LOCK_SCREEN: String = "pref_key_use_lock_screen"
        const val PREF_KEY_USE_LOCK_SCREEN_DEFAULT: Boolean = false

        const val PREF_KEY_UNLOCK_BIOMETRY: String = "pref_key_unlock_biometry"
        const val PREF_KEY_UNLOCK_BIOMETRY_DEFAULT: Boolean = true

        const val PREF_KEY_LOCK_GRACE_TIME: String = "pref_key_lock_grace_time"
        const val PREF_KEY_LOCK_GRACE_TIME_DEFAULT: String = "30"

        const val PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES: String =
            "pref_key_unlock_failed_wipe_messages"
        const val PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES_DEFAULT: Boolean = false

        const val PREF_KEY_USE_EMERGENCY_PIN: String = "pref_key_use_emergency_pin"
        const val PREF_KEY_USE_EMERGENCY_PIN_DEFAULT: Boolean = false

        const val PREF_KEY_KEEP_LOCK_SERVICE_OPEN: String = "pref_key_keep_lock_service_open"
        const val PREF_KEY_KEEP_LOCK_SERVICE_OPEN_DEFAULT: Boolean = true

        const val PREF_KEY_KEEP_LOCK_SCREEN_NEUTRAL: String = "pref_key_keep_lock_screen_neutral"
        const val PREF_KEY_KEEP_LOCK_SCREEN_NEUTRAL_DEFAULT: Boolean = false

        const val PREF_KEY_PIN_IS_A_PASSWORD: String = "pref_key_pin_is_a_password"
        const val PREF_KEY_PIN_IS_A_PASSWORD_DEFAULT: Boolean = false

        const val PREF_KEY_PIN_HASH: String = "pref_key_pin_hash"
        const val PREF_KEY_PLAIN_PIN: String = "pref_key_plain_pin"
        const val PREF_KEY_EMERGENCY_PIN_HASH: String = "pref_key_emergency_pin_hash"
        const val PREF_KEY_EMERGENCY_PLAIN_PIN: String = "pref_key_emergency_plain_pin"

        const val PREF_KEY_RESET_PIN: String = "pref_key_reset_pin"


        // BACKUPS
        const val PREF_KEY_RELOAD_BACKUP_CONFIGURATION: String =
            "pref_key_reload_backup_configuration"
        const val PREF_KEY_GENERATE_NEW_BACKUP_KEY: String = "pref_key_generate_new_backup_key"

        const val PREF_KEY_MANUAL_BACKUP: String = "pref_key_manual_backup"

        const val PREF_KEY_ENABLE_AUTOMATIC_BACKUP: String = "pref_key_enable_automatic_backup"
        const val PREF_KEY_ENABLE_AUTOMATIC_BACKUP_DEFAULT: Boolean = false
        const val PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID: String =
            "pref_key_automatic_backup_device_unique_id"
        const val PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION: String =
            "pref_key_automatic_backup_configuration"
        const val PREF_KEY_MDM_AUTOMATIC_BACKUP: String = "pref_key_mdm_automatic_backup"
        const val PREF_KEY_MDM_WEBDAV_KEY_ESCROW_PUBLIC_KEY: String =
            "pref_key_mdm_webdav_key_escrow_public_key"

        const val PREF_KEY_MANAGE_CLOUD_BACKUPS: String = "pref_key_manage_cloud_backups"

        // CAMERA SETTINGS
        const val PREF_KEY_CAMERA_RESOLUTION: String = "pref_key_camera_resolution"
        const val PREF_KEY_CAMERA_RESOLUTION_DEFAULT: String = "-1"

        const val PREF_KEY_REMOVE_METADATA: String = "pref_key_remove_metadata"
        const val PREF_KEY_REMOVE_METADATA_DEFAULT: Boolean = false

        // CALL SETTINGS
        const val PREF_KEY_VIDEO_RESOLUTION: String = "pref_key_video_resolution"
        const val PREF_KEY_VIDEO_RESOLUTION_DEFAULT: Int = 720

        const val PREF_KEY_HARDWARE_NOISE_SUPPRESSOR: String = "pref_key_hardware_noise_suppressor"
        const val PREF_KEY_HARDWARE_NOISE_SUPPRESSOR_DEFAULT: Boolean = false

        const val PREF_KEY_HARDWARE_ECHO_CANCELER: String = "pref_key_hardware_echo_canceler"
        const val PREF_KEY_HARDWARE_ECHO_CANCELER_DEFAULT: Boolean = false

        const val PREF_KEY_LOW_BANDWIDTH_CALLS: String = "pref_key_low_bandwidth_calls"
        const val PREF_KEY_LOW_BANDWIDTH_CALLS_DEFAULT: Boolean = false


        // OTHER SETTINGS
        const val PREF_KEY_SEND_WITH_HARDWARE_ENTER: String = "pref_key_send_with_hardware_enter"
        const val PREF_KEY_SEND_WITH_HARDWARE_ENTER_DEFAULT: Boolean = false

        const val PREF_KEY_SENDING_FOREGROUND_SERVICE: String =
            "pref_key_sending_foreground_service"
        const val PREF_KEY_SENDING_FOREGROUND_SERVICE_DEFAULT: Boolean = true

        const val PREF_KEY_QR_CORRECTION_LEVEL: String = "pref_key_qr_correction_level"
        const val PREF_KEY_QR_CORRECTION_LEVEL_DEFAULT: String = "M"

        const val PREF_KEY_RESET_DIALOGS: String = "pref_key_reset_dialogs"

        const val PREF_KEY_PING_CONNECTIVITY_INDICATOR: String =
            "pref_key_ping_connectivity_indicator"
        const val PREF_KEY_PING_CONNECTIVITY_INDICATOR_DEFAULT: String = "null"

        const val PREF_KEY_SHARE_APP_VERSION: String = "pref_key_share_app_version"
        const val PREF_KEY_SHARE_APP_VERSION_DEFAULT: Boolean = true

        const val PREF_KEY_NOTIFY_CERTIFICATE_CHANGE: String = "pref_key_notify_certificate_change"
        const val PREF_KEY_NOTIFY_CERTIFICATE_CHANGE_DEFAULT: Boolean = false

        const val PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE: String =
            "pref_key_block_untrusted_certificate"
        val PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE_DEFAULT: BlockUntrustedCertificate = ISSUER_CHANGED

        const val PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS: String =
            "pref_key_no_notify_certificate_change_for_previews"
        const val PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS_DEFAULT: Boolean = false
        //        const val USER_DIALOG_HIDE_GOOGLE_APIS: String = "user_dialog_hide_google_apis"
        const val USER_DIALOG_HIDE_OPEN_EXTERNAL_APP: String = "user_dialog_hide_open_external_app"
        const val USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION: String =
            "user_dialog_hide_forward_message_explanation"
        const val USER_DIALOG_HIDE_OPEN_EXTERNAL_APP_LOCATION: String =
            "user_dialog_hide_open_external_app_location"
        const val USER_DIALOG_HIDE_ADD_DEVICE_EXPLANATION: String =
            "user_dialog_hide_add_device_explanation"

        const val PREF_KEY_DEBUG_LOG_LEVEL: String = "pref_key_debug_log_level"
        const val PREF_KEY_DEBUG_LOG_LEVEL_DEFAULT: Boolean = false

        const val PREF_KEY_USE_LEGACY_ZXING_SCANNER: String = "pref_key_use_legacy_zxing_scanner"
        const val PREF_KEY_USE_LEGACY_ZXING_SCANNER_DEFAULT: Boolean = false


        const val PREF_KEY_PREFERRED_KEYCLOAK_BROWSER: String =
            "pref_key_preferred_keycloak_browser"

        private const val PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER: String =
            "pref_key_use_speaker_output_for_media_player"
        private const val PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER_DEFAULT: Boolean = true
        private const val PREF_KEY_PLAYBACK_SPEED_FOR_MEDIA_PLAYER: String =
            "pref_key_playback_speed_for_media_player"
        private const val PREF_KEY_PLAYBACK_SPEED_FOR_MEDIA_PLAYER_DEFAULT: Float = 1f

        // SEND & RECEIVE MESSAGES
        const val PREF_KEY_AUTODOWNLOAD_SIZE: String = "pref_key_autodownload_size"
        const val PREF_KEY_AUTODOWNLOAD_SIZE_DEFAULT: String = "10000000"


        const val PREF_KEY_LINK_PREVIEW_OUTBOUND: String = "pref_key_link_preview_outbound"
        const val PREF_KEY_LINK_PREVIEW_OUTBOUND_DEFAULT: Boolean = true
        const val PREF_KEY_LINK_PREVIEW_INBOUND: String = "pref_key_link_preview_inbound"
        const val PREF_KEY_LINK_PREVIEW_INBOUND_DEFAULT: Boolean = false
        const val PREF_KEY_NO_TRUNCATE_TRAILING_LINK: String = "pref_key_no_truncate_trailing_link"
        const val PREF_KEY_NO_TRUNCATE_TRAILING_LINK_DEFAULT: Boolean = false


        const val PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND: String =
            "pref_key_auto_open_limited_visibility_inbound"
        const val PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_DEFAULT: Boolean = false

        const val PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES: String =
            "pref_key_retain_wiped_outbound_messages"
        const val PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES_DEFAULT: Boolean = false

        const val PREF_KEY_RETAIN_REMOTE_DELETED_MESSAGES: String =
            "pref_key_retain_remote_deleted_messages"
        const val PREF_KEY_RETAIN_REMOTE_DELETED_MESSAGES_DEFAULT: Boolean = true
        const val PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT: String =
            "pref_key_default_discussion_retention_count" // number of messages to keep
        const val PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT_DEFAULT: String =
            "" // keep everything by default

        const val PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION: String =
            "pref_key_default_discussion_retention_duration" // duration in seconds
        const val PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION_DEFAULT: String =
            "null" // no expiration

        const val PREF_KEY_CATEGORY_DEFAULT_EPHEMERAL_SETTINGS: String =
            "pref_key_category_default_ephemeral_settings"

        const val PREF_KEY_DEFAULT_READ_ONCE: String = "pref_key_default_read_once"
        const val PREF_KEY_DEFAULT_READ_ONCE_DEFAULT: Boolean = false

        const val PREF_KEY_DEFAULT_VISIBILITY_DURATION: String =
            "pref_key_default_visibility_duration"
        const val PREF_KEY_DEFAULT_VISIBILITY_DURATION_DEFAULT: String = "null"

        const val PREF_KEY_DEFAULT_EXISTENCE_DURATION: String =
            "pref_key_default_existence_duration"
        const val PREF_KEY_DEFAULT_EXISTENCE_DURATION_DEFAULT: String = "null"


        // WEBCLIENT
        const val PREF_KEY_LANGUAGE_WEBCLIENT: String = "pref_key_language_webclient"
        const val PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT: String = "AppDefault"

        const val PREF_KEY_THEME_WEBCLIENT: String = "pref_key_theme_webclient"
        const val PREF_KEY_THEME_WEBCLIENT_DEFAULT: String = "BrowserDefault"

        const val PREF_KEY_SEND_ON_ENTER_WEBCLIENT: String = "pref_key_send_on_enter_webclient"
        const val PREF_KEY_SEND_ON_ENTER_WEBCLIENT_DEFAULT: Boolean =
            true //send message when hitting enter by default

        const val PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER: String =
            "pref_key_notification_show_on_browser"
        const val PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER_DEFAULT: Boolean =
            true //notification sound on by default

        const val PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT: String =
            "pref_key_notification_sound_webclient"
        const val PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT_DEFAULT: Boolean =
            true //notification sound on by default

        const val PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE: String =
            "pref_key_keep_webclient_alive_after_close"
        const val PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE_DEFAULT: Boolean = true

        const val PREF_KEY_SHOW_ERROR_NOTIFICATIONS: String = "pref_key_show_error_notifications"
        const val PREF_KEY_SHOW_ERROR_NOTIFICATIONS_DEFAULT: Boolean = true

        const val PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY: String =
            "pref_key_notifications_for_messages_after_inactivity"
        const val PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY_DEFAULT: Boolean = true

        const val PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT: String =
            "pref_key_require_unlock_before_connecting_to_webclient"
        const val PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT_DEFAULT: Boolean = true


        // LOCATION
        const val PREF_KEY_LOCATION_INTEGRATION: String = "pref_key_location_integration"
        const val PREF_VALUE_LOCATION_INTEGRATION_OSM: String = "osm"
        const val PREF_VALUE_LOCATION_INTEGRATION_MAPS: String = "maps"
        const val PREF_VALUE_LOCATION_INTEGRATION_BASIC: String = "basic"
        const val PREF_VALUE_LOCATION_INTEGRATION_CUSTOM_OSM: String = "custom_osm"

        const val PREF_KEY_LOCATION_CUSTOM_OSM_SERVER: String =
            "pref_key_location_custom_osm_server"

        const val PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION: String =
            "pref_key_location_share_duration"
        const val PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT: Long = 3600000L

        //    static final String PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL = "pref_key_location_share_interval";
        //    static final long PREF_KEY_LOCATION_DEFAULT_SHARE_INTERVAL_DEFAULT = 60_000L;
        const val PREF_KEY_LOCATION_DEFAULT_SHARE_QUALITY: String =
            "pref_key_location_default_share_quality"
        val PREF_KEY_LOCATION_DEFAULT_SHARE_QUALITY_DEFAULT: LocationShareQuality = QUALITY_BALANCED

        const val PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS: String =
            "pref_key_location_hide_error_notifications"
        const val PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS_DEFAULT: Boolean = false

        const val PREF_KEY_LOCATION_LAST_OSM_STYLE_ID: String =
            "pref_key_location_last_osm_style_id"
        const val PREF_KEY_LOCATION_LAST_GOOGLE_MAP_TYPE: String =
            "pref_key_location_last_google_map_type"

        const val PREF_KEY_LOCATION_DISABLE_ADDRESS_LOOKUP: String =
            "pref_key_location_disable_address_lookup"
        const val PREF_KEY_LOCATION_DISABLE_ADDRESS_LOOKUP_DEFAULT: Boolean = false

        const val PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER: String =
            "pref_key_location_use_custom_address_server"
        const val PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER_DEFAULT: Boolean = false
        const val PREF_KEY_LOCATION_CUSTOM_ADDRESS_SERVER: String =
            "pref_key_location_custom_address_server"

        private fun useDarkMode(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_DARK_MODE, PREF_KEY_DARK_MODE_DEFAULT
            )
        }

        private fun useDarkModeApi29(): String {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                PREF_KEY_DARK_MODE_API29, PREF_KEY_DARK_MODE_DEFAULT_API29
            )!!
        }

        @JvmStatic
        fun setDefaultNightMode() {
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                when (useDarkModeApi29()) {
                    "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "Auto" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            } else {
                if (useDarkMode()) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
        }

        @JvmStatic
        var forcedDarkMode: Boolean?
            get() {
                return if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                    when (useDarkModeApi29()) {
                        "Dark" -> true
                        "Light" -> false
                        "Auto" -> null
                        else -> null
                    }
                } else {
                    if (useDarkMode()) true else null
                }
            }
            set(forceDarkMode) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                    if (forceDarkMode!!) {
                        editor.putString(
                            PREF_KEY_DARK_MODE_API29,
                            "Dark"
                        )
                    } else {
                        editor.putString(
                            PREF_KEY_DARK_MODE_API29,
                            "Light"
                        )
                    }
                } else {
                    editor.putBoolean(
                        PREF_KEY_DARK_MODE,
                        forceDarkMode!!
                    )
                }
                editor.apply()
            }

        val fontScale: Float
            get() {
                val scaleString: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_FONT_SCALE,
                            PREF_KEY_FONT_SCALE_DEFAULT
                        )!!
                try {
                    return scaleString.toFloat()
                } catch (_: Exception) {
                }
                return 1.0f
            }

        @JvmStatic
        val screenScale: Float
            get() {
                val scaleString: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_SCREEN_SCALE,
                            PREF_KEY_SCREEN_SCALE_DEFAULT
                        )!!
                try {
                    return scaleString.toFloat()
                } catch (_: Exception) {
                }
                return 1.0f
            }

        @JvmStatic
        fun overrideContextScales(baseContext: Context): Context {
            val newContext: Context
            val customFontScale: Float = fontScale
            val customScreenScale: Float = screenScale
            if (customFontScale != 1.0f || customScreenScale != 1.0f) {
                val baseConfiguration: Configuration = baseContext.resources.configuration
                val configuration = Configuration()
                configuration.fontScale =
                    baseContext.resources.configuration.fontScale * customFontScale
                configuration.densityDpi =
                    (baseContext.resources.configuration.densityDpi * customScreenScale).toInt()
                configuration.screenWidthDp =
                    (baseConfiguration.screenWidthDp / customScreenScale).toInt()
                configuration.screenHeightDp =
                    (baseConfiguration.screenHeightDp / customScreenScale).toInt()
                configuration.smallestScreenWidthDp =
                    (baseConfiguration.smallestScreenWidthDp / customScreenScale).toInt()
                newContext = baseContext.createConfigurationContext(configuration)
            } else {
                newContext = baseContext
            }
            return newContext
        }


        @JvmStatic
        var lastAvailableSpaceWarningTimestamp: Long
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getLong(
                        PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP,
                        0L
                    )
            }
            set(timestamp) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putLong(
                    PREF_KEY_LAST_AVAILABLE_SPACE_WARNING_TIMESTAMP,
                    timestamp
                )
                editor.apply()
            }


        fun wasFirstCallAudioPermissionRequested(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED, false
            )
        }

        fun setFirstCallAudioPermissionRequested(requested: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED, requested)
            editor.apply()
        }

        fun wasFirstCallBluetoothPermissionRequested(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED, false
            )
        }

        fun setFirstCallBluetoothPermissionRequested(requested: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED, requested)
            editor.apply()
        }

        @JvmStatic
        fun useSystemEmojis(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_USE_SYSTEM_EMOJIS, PREF_KEY_USE_SYSTEM_EMOJIS_DEFAULT
            )
        }

        @JvmStatic
        fun setUseSystemEmojis(useSystemEmojis: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_USE_SYSTEM_EMOJIS, useSystemEmojis)
            editor.apply()
        }


        @JvmStatic
        fun useInternalImageViewer(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_USE_INTERNAL_IMAGE_VIEWER, PREF_KEY_USE_INTERNAL_IMAGE_VIEWER_DEFAULT
            )
        }

        @JvmStatic
        fun setUseInternalImageViewer(useInternalImageViewer: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_USE_INTERNAL_IMAGE_VIEWER, useInternalImageViewer)
            editor.apply()
        }

        @JvmStatic
        var sortContactsByLastName: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_SORT_CONTACTS_BY_LAST_NAME,
                        PREF_KEY_SORT_CONTACTS_BY_LAST_NAME_DEFAULT
                    )
            }
            set(sort) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_SORT_CONTACTS_BY_LAST_NAME,
                    sort
                )
                editor.apply()
            }

        @JvmStatic
        var contactDisplayNameFormat: String
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(
                        PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT,
                        PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT_DEFAULT
                    ) ?: PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT_DEFAULT
            }
            set(format) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT,
                    format
                )
                editor.apply()
            }

        @JvmStatic
        val allowContactFirstName: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_SOMETIMES_SHOW_FIRST_NAME_ONLY,
                        PREF_KEY_SOMETIMES_SHOW_FIRST_NAME_ONLY_DEFAULT
                    )
            }

        @JvmStatic
        var uppercaseLastName: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_UPPERCASE_LAST_NAME,
                        PREF_KEY_UPPERCASE_LAST_NAME_DEFAULT
                    )
            }
            set(uppercaseLastName) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_UPPERCASE_LAST_NAME,
                    uppercaseLastName
                )
                editor.apply()
            }

        @JvmStatic
        var autoDownloadSize: Long
            get() {
                val downloadSizeString: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_AUTODOWNLOAD_SIZE,
                            PREF_KEY_AUTODOWNLOAD_SIZE_DEFAULT
                        )!!
                return downloadSizeString.toLong()
            }
            set(autoDownloadSize) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_AUTODOWNLOAD_SIZE,
                    autoDownloadSize.toString()
                )
                editor.apply()
            }

        @JvmStatic
        fun isLinkPreviewInbound(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                PREF_KEY_LINK_PREVIEW_INBOUND, PREF_KEY_LINK_PREVIEW_INBOUND_DEFAULT
            )
        }

        @JvmStatic
        var isLinkPreviewOutbound: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_LINK_PREVIEW_OUTBOUND,
                        PREF_KEY_LINK_PREVIEW_OUTBOUND_DEFAULT
                    )
            }
            set(defaultLinkPreviewOutbound) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_LINK_PREVIEW_OUTBOUND,
                    defaultLinkPreviewOutbound
                )
                editor.apply()
            }

        @JvmStatic
        fun truncateMessageBodyTrailingLinks(): Boolean {
            // careful, there is a negation here!
            return !PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_NO_TRUNCATE_TRAILING_LINK, PREF_KEY_NO_TRUNCATE_TRAILING_LINK_DEFAULT
            )
        }


        @JvmStatic
        fun setLinkPreviewInbound(defaultLinkPreviewInbound: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_LINK_PREVIEW_INBOUND, defaultLinkPreviewInbound)
            editor.apply()
        }

        @JvmStatic
        var lastReadReceiptPromptAnswerTimestamp: Long
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getLong(
                        PREF_KEY_LAST_READ_RECEIPT_PROMPT_ANSWER_TIMESTAMP,
                        0
                    )
            }
            set(timestampOrMinusOne) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putLong(
                    PREF_KEY_LAST_READ_RECEIPT_PROMPT_ANSWER_TIMESTAMP,
                    timestampOrMinusOne
                )
                editor.apply()
            }

        @JvmStatic
        var defaultSendReadReceipt: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_READ_RECEIPT,
                        PREF_KEY_READ_RECEIPT_DEFAULT
                    )
            }
            set(defaultSendReadReceipt) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_READ_RECEIPT,
                    defaultSendReadReceipt
                )
                editor.apply()
            }

        val sendWithHardwareEnter: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_SEND_WITH_HARDWARE_ENTER,
                        PREF_KEY_SEND_WITH_HARDWARE_ENTER_DEFAULT
                    )
            }

        @JvmStatic
        fun useSendingForegroundService(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_SENDING_FOREGROUND_SERVICE, PREF_KEY_SENDING_FOREGROUND_SERVICE_DEFAULT
            )
        }

        @JvmStatic
        fun setSendingForegroundService(enabled: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_SENDING_FOREGROUND_SERVICE, enabled)
            editor.apply()
        }

        @JvmStatic
        val pingConnectivityIndicator: PingConnectivityIndicator
            get() {
                val pingConnectivityIndicator: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_PING_CONNECTIVITY_INDICATOR,
                            PREF_KEY_PING_CONNECTIVITY_INDICATOR_DEFAULT
                        )!!
                when (pingConnectivityIndicator) {
                    "dot" -> return DOT
                    "line" -> return LINE
                    "full" -> return FULL
                }
                return NONE
            }

        @JvmStatic
        fun setPingConnectivityIndicator(indicatorString: String) {
            val pingConnectivityIndicatorString: String? = when (indicatorString) {
                "dot", "line", "full" -> indicatorString
                else -> null
            }
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            if (pingConnectivityIndicatorString == null) {
                editor.remove(PREF_KEY_PING_CONNECTIVITY_INDICATOR)
            } else {
                editor.putString(
                    PREF_KEY_PING_CONNECTIVITY_INDICATOR,
                    pingConnectivityIndicatorString
                )
            }
            editor.apply()
        }

        @JvmStatic
        var qrCorrectionLevel: String
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(
                        PREF_KEY_QR_CORRECTION_LEVEL,
                        PREF_KEY_QR_CORRECTION_LEVEL_DEFAULT
                    ) ?: PREF_KEY_QR_CORRECTION_LEVEL_DEFAULT
            }
            set(level) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(PREF_KEY_QR_CORRECTION_LEVEL, level)
                editor.apply()
            }

        @JvmStatic
        fun useDebugLogLevel(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_DEBUG_LOG_LEVEL, PREF_KEY_DEBUG_LOG_LEVEL_DEFAULT
            )
        }

        @JvmStatic
        fun setUseDebugLogLevel(useDebugLogLevel: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_DEBUG_LOG_LEVEL, useDebugLogLevel)
            editor.apply()
        }

        @JvmStatic
        var defaultAutoOpenLimitedVisibilityInboundMessages: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND,
                        PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND_DEFAULT
                    )
            }
            set(autoOpen) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_AUTO_OPEN_LIMITED_VISIBILITY_INBOUND,
                    autoOpen
                )
                editor.apply()
            }

        @JvmStatic
        var defaultRetainWipedOutboundMessages: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES,
                        PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES_DEFAULT
                    )
            }
            set(retain) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_RETAIN_WIPED_OUTBOUND_MESSAGES,
                    retain
                )
                editor.apply()
            }

        @JvmStatic
        val retainRemoteDeletedMessages: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_RETAIN_REMOTE_DELETED_MESSAGES,
                        PREF_KEY_RETAIN_REMOTE_DELETED_MESSAGES_DEFAULT
                    )
            }

        @JvmStatic
        var defaultDiscussionRetentionCount: Long?
            get() {
                try {
                    val retentionCountString: String =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .getString(
                                PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT,
                                PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT_DEFAULT
                            )!!
                    if (retentionCountString.isEmpty()) {
                        return null
                    }
                    val count: Long = retentionCountString.toLong()
                    return if (count == 0L) null else count
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }
            set(count) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (count == 0L) {
                    editor.remove(PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT)
                } else {
                    editor.putString(
                        PREF_KEY_DEFAULT_DISCUSSION_RETENTION_COUNT,
                        count.toString()
                    )
                }
                editor.apply()
            }

        @JvmStatic
        var defaultDiscussionRetentionDuration: Long?
            get() {
                try {
                    val retentionDurationString: String =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .getString(
                                PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION,
                                PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION_DEFAULT
                            )!!
                    if ("null" == retentionDurationString) {
                        return null
                    }
                    return retentionDurationString.toLong()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }
            set(duration) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_DEFAULT_DISCUSSION_RETENTION_DURATION,
                    duration.toString()
                )
                editor.apply()
            }

        @JvmStatic
        var defaultDiscussionReadOnce: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_DEFAULT_READ_ONCE,
                        PREF_KEY_DEFAULT_READ_ONCE_DEFAULT
                    )
            }
            set(readOnce) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(PREF_KEY_DEFAULT_READ_ONCE, readOnce)
                editor.apply()
            }

        @JvmStatic
        var defaultDiscussionVisibilityDuration: Long?
            get() {
                try {
                    val visibilityDurationString: String =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .getString(
                                PREF_KEY_DEFAULT_VISIBILITY_DURATION,
                                PREF_KEY_DEFAULT_VISIBILITY_DURATION_DEFAULT
                            )!!
                    if ("null" == visibilityDurationString) {
                        return null
                    }
                    return visibilityDurationString.toLong()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }
            set(duration) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (duration == null) {
                    editor.remove(PREF_KEY_DEFAULT_VISIBILITY_DURATION)
                } else {
                    editor.putString(
                        PREF_KEY_DEFAULT_VISIBILITY_DURATION,
                        duration.toString()
                    )
                }
                editor.apply()
            }

        @JvmStatic
        var defaultDiscussionExistenceDuration: Long?
            get() {
                try {
                    val existenceDurationString: String =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .getString(
                                PREF_KEY_DEFAULT_EXISTENCE_DURATION,
                                PREF_KEY_DEFAULT_EXISTENCE_DURATION_DEFAULT
                            )!!
                    if ("null" == existenceDurationString) {
                        return null
                    }
                    return existenceDurationString.toLong()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }
            set(duration) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (duration == null) {
                    editor.remove(PREF_KEY_DEFAULT_EXISTENCE_DURATION)
                } else {
                    editor.putString(
                        PREF_KEY_DEFAULT_EXISTENCE_DURATION,
                        duration.toString()
                    )
                }
                editor.apply()
            }

        @JvmStatic
        var betaFeaturesEnabled: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_ENABLE_BETA_FEATURES,
                        PREF_KEY_ENABLE_BETA_FEATURES_DEFAULT
                    )
            }
            set(betaFeaturesEnabled) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_ENABLE_BETA_FEATURES,
                    betaFeaturesEnabled
                )
                editor.apply()
            }

        @JvmStatic
        val scaledTurn: String?
            get() {
                val value: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_SCALED_TURN_REGION,
                            PREF_KEY_SCALED_TURN_REGION_DEFAULT
                        )!!
                if (PREF_KEY_SCALED_TURN_REGION_DEFAULT == value) {
                    return null
                }
                return value
            }

        @JvmStatic
        fun resetScaledTurn() {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.remove(PREF_KEY_SCALED_TURN_REGION)
            editor.apply()
        }


        @JvmStatic
        var autoJoinGroups: AutoJoinGroupsCategory
            get() {
                val stringValue: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_AUTO_JOIN_GROUPS,
                            PREF_KEY_AUTO_JOIN_GROUPS_DEFAULT
                        )!!
                return getAutoJoinGroupsFromString(stringValue)
            }
            set(autoJoinGroups) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
                editor.putString(
                    PREF_KEY_AUTO_JOIN_GROUPS,
                    autoJoinGroups.stringValue
                )
                editor.apply()
            }

        @JvmStatic
        fun getAutoJoinGroupsFromString(stringValue: String?): AutoJoinGroupsCategory {
            if (stringValue != null) {
                when (stringValue) {
                    "nobody" -> return NOBODY
                    "everyone" -> return EVERYONE
                    "contacts" -> {}
                    else -> {}
                }
            }
            return CONTACTS
        }

        var hideGroupMemberChanges: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_HIDE_GROUP_MEMBER_CHANGES,
                        PREF_KEY_HIDE_GROUP_MEMBER_CHANGES_DEFAULT
                    )
            }
            set(hide) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_HIDE_GROUP_MEMBER_CHANGES,
                    hide
                )
                editor.apply()
            }

        @JvmStatic
        fun showTrustLevels(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_SHOW_TRUST_LEVELS, PREF_KEY_SHOW_TRUST_LEVELS_DEFAULT
            )
        }

        @JvmStatic
        fun setShowTrustLevels(show: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_SHOW_TRUST_LEVELS, show)
            editor.apply()
        }

        @JvmStatic
        fun useApplicationLockScreen(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_USE_LOCK_SCREEN, PREF_KEY_USE_LOCK_SCREEN_DEFAULT
            )
        }

        @JvmStatic
        fun useEmergencyPIN(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_USE_EMERGENCY_PIN, PREF_KEY_USE_EMERGENCY_PIN_DEFAULT
            )
        }

        @JvmStatic
        fun wipeMessagesOnUnlockFails(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES, PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES_DEFAULT
            )
        }

        @JvmStatic
        fun setWipeMessagesOnUnlockFails(wipe: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_UNLOCK_FAILED_WIPE_MESSAGES, wipe)
            editor.apply()
        }

        @JvmStatic
        var isNotificationContentHidden: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_HIDE_NOTIFICATION_CONTENTS,
                        PREF_KEY_HIDE_NOTIFICATION_CONTENTS_DEFAULT
                    )
            }
            set(contentHidden) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_HIDE_NOTIFICATION_CONTENTS,
                    contentHidden
                )
                editor.apply()
            }

        @JvmStatic
        var isNotificationSuggestionAllowed: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS,
                        PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS_DEFAULT
                    )
            }
            set(suggestionsAllowed) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_DISABLE_NOTIFICATION_SUGGESTIONS,
                    suggestionsAllowed
                )
                editor.apply()
            }

        @JvmStatic
        fun exposeRecentDiscussions(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_EXPOSE_RECENT_DISCUSSIONS, PREF_KEY_EXPOSE_RECENT_DISCUSSIONS_DEFAULT
            )
        }

        @JvmStatic
        fun setExposeRecentDiscussions(exposeRecentDiscussions: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_EXPOSE_RECENT_DISCUSSIONS, exposeRecentDiscussions)
            editor.apply()
        }

        @JvmStatic
        fun useKeyboardIncognitoMode(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_KEYBOARD_INCOGNITO_MODE, PREF_KEY_KEYBOARD_INCOGNITO_MODE_DEFAULT
            )
        }

        @JvmStatic
        fun setUseKeyboardIncognitoMode(useKeyboardIncognitoMode: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_KEYBOARD_INCOGNITO_MODE, useKeyboardIncognitoMode)
            editor.apply()
        }

        @JvmStatic
        fun preventScreenCapture(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_PREVENT_SCREEN_CAPTURE, PREF_KEY_PREVENT_SCREEN_CAPTURE_DEFAULT
            )
        }

        @JvmStatic
        fun setPreventScreenCapture(preventScreenCapture: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_PREVENT_SCREEN_CAPTURE, preventScreenCapture)
            editor.apply()
        }

        @JvmStatic
        val hiddenProfileClosePolicy: Int
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getInt(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY, -1)
            }

        @JvmStatic
        val hiddenProfileClosePolicyBackgroundGraceDelay: Int
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getInt(
                        PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY,
                        PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY_DEFAULT
                    )
            }

        @JvmStatic
        fun getHiddenProfileClosePolicyBackgroundGraceDelayLabels(context: Context): Array<String> {
            return arrayOf(
                context.getString(R.string.text_close_profile_instantly),
                context.getString(R.string.text_after_10s),
                context.getString(R.string.text_after_30s),
                context.getString(R.string.text_after_1m),
                context.getString(R.string.text_after_2m),
                context.getString(R.string.text_after_5m),
            )
        }

        @JvmStatic
        val isHiddenProfileClosePolicyDefined: Boolean
            get() {
                return hiddenProfileClosePolicy != -1
            }

        @JvmStatic
        fun setHiddenProfileClosePolicy(policy: Int, backgroundGraceDelay: Int) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putInt(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY, policy)
            if (policy == HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND) {
                editor.putInt(
                    PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY,
                    backgroundGraceDelay
                )
            } else {
                editor.remove(PREF_KEY_HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND_GRACE_DELAY)
            }
            editor.apply()
        }

        val videoSendResolution: Int
            get() {
                val resolution: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(PREF_KEY_VIDEO_RESOLUTION, null)
                if (resolution != null) {
                    try {
                        return resolution.toInt()
                    } catch (_: Exception) {
                    }
                }
                return PREF_KEY_VIDEO_RESOLUTION_DEFAULT
            }

        fun useHardwareNoiseSuppressor(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_HARDWARE_NOISE_SUPPRESSOR, PREF_KEY_HARDWARE_NOISE_SUPPRESSOR_DEFAULT
            )
        }

        fun useHardwareEchoCanceler(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_HARDWARE_ECHO_CANCELER, PREF_KEY_HARDWARE_ECHO_CANCELER_DEFAULT
            )
        }

        @JvmStatic
        fun useLowBandwidthInCalls(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_LOW_BANDWIDTH_CALLS, PREF_KEY_LOW_BANDWIDTH_CALLS_DEFAULT
            )
        }

        @JvmStatic
        fun setUseLowBandwidthInCalls(useLowBandwidthInCalls: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_LOW_BANDWIDTH_CALLS, useLowBandwidthInCalls)
            editor.apply()
        }

        @JvmStatic
        fun useLegacyZxingScanner(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_USE_LEGACY_ZXING_SCANNER, PREF_KEY_USE_LEGACY_ZXING_SCANNER_DEFAULT
            )
        }

        @JvmStatic
        fun usePermanentWebSocket(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_PERMANENT_WEBSOCKET, PREF_KEY_PERMANENT_WEBSOCKET_DEFAULT
            )
        }

        @JvmStatic
        fun setUsePermanentWebSocket(userPermanentWebSocket: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_PERMANENT_WEBSOCKET, userPermanentWebSocket)
            editor.apply()
        }

        @JvmStatic
        fun shareAppVersion(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_SHARE_APP_VERSION, PREF_KEY_SHARE_APP_VERSION_DEFAULT
            )
        }

        @JvmStatic
        fun setShareAppVersion(shareAppVersion: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_SHARE_APP_VERSION, shareAppVersion)
            editor.apply()
        }

        @JvmStatic
        fun notifyCertificateChange(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_NOTIFY_CERTIFICATE_CHANGE, PREF_KEY_NOTIFY_CERTIFICATE_CHANGE_DEFAULT
            )
        }

        @JvmStatic
        fun setNotifyCertificateChange(notifyCertificateChange: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_NOTIFY_CERTIFICATE_CHANGE, notifyCertificateChange)
            editor.apply()
        }

        @JvmStatic
        val blockUntrustedCertificate: BlockUntrustedCertificate
            get() {
                // never block a connection for API < 24 as the shutdownInput/shutdownOutput technique we use is not supported yet
                if (VERSION.SDK_INT < VERSION_CODES.N) {
                    return NEVER
                }
                val value: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE,
                            null
                        )
                if (value != null) {
                    when (value) {
                        "always" -> return ALWAYS
                        "never" -> return NEVER
                        "issuer" -> return ISSUER_CHANGED
                    }
                }
                return PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE_DEFAULT
            }

        @JvmStatic
        fun setBlockUntrustedCertificate(untrustedCertificateCategoryString: String) {
            val untrustedCertificateCategory: String? = when (untrustedCertificateCategoryString) {
                "always", "issuer", "never" -> untrustedCertificateCategoryString
                else -> null
            }
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            if (untrustedCertificateCategory == null) {
                editor.remove(PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE)
            } else {
                editor.putString(PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE, untrustedCertificateCategory)
            }
            editor.apply()
        }

        @JvmStatic
        var noNotifyCertificateChangeForPreviews: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS,
                        PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS_DEFAULT
                    )
            }
            set(doNotNotify) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS,
                    doNotNotify
                )
                editor.apply()
            }

        @JvmStatic
        fun disablePushNotifications(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_DISABLE_PUSH_NOTIFICATIONS, PREF_KEY_DISABLE_PUSH_NOTIFICATIONS_DEFAULT
            )
        }

        @JvmStatic
        fun setDisablePushNotifications(disablePushNotifications: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_DISABLE_PUSH_NOTIFICATIONS, disablePushNotifications)
            editor.apply()
        }


        const val PIN_SALT_LENGTH: Int = 8

        @JvmStatic
        fun savePIN(pin: String?, pinIsAPassword: Boolean) {
            val salt = ByteArray(PIN_SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            val hash: ByteArray? = computePINHash(pin, salt)
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            if (hash == null) {
                Logger.w("Error computing PIN hash, storing it in clear...")
                editor.putString(PREF_KEY_PLAIN_PIN, pin)
                editor.remove(PREF_KEY_PIN_HASH)
            } else {
                editor.remove(PREF_KEY_PLAIN_PIN)
                val saltAndHash = ByteArray(PIN_SALT_LENGTH + hash.size)
                System.arraycopy(salt, 0, saltAndHash, 0, PIN_SALT_LENGTH)
                System.arraycopy(hash, 0, saltAndHash, PIN_SALT_LENGTH, hash.size)
                val base64SaltAndHash: String =
                    Base64.encodeToString(saltAndHash, Base64.NO_PADDING or Base64.NO_WRAP)
                editor.putString(PREF_KEY_PIN_HASH, base64SaltAndHash)
            }
            editor.putBoolean(PREF_KEY_PIN_IS_A_PASSWORD, pinIsAPassword)
            editor.apply()
        }

        @JvmStatic
        fun saveEmergencyPIN(pin: String?) {
            val salt = ByteArray(PIN_SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            val hash: ByteArray? = computePINHash(pin, salt)
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            if (hash == null) {
                Logger.w("Error computing PIN hash, storing it in clear...")
                editor.putString(PREF_KEY_EMERGENCY_PLAIN_PIN, pin)
                editor.remove(PREF_KEY_EMERGENCY_PIN_HASH)
            } else {
                editor.remove(PREF_KEY_EMERGENCY_PLAIN_PIN)
                val saltAndHash = ByteArray(PIN_SALT_LENGTH + hash.size)
                System.arraycopy(salt, 0, saltAndHash, 0, PIN_SALT_LENGTH)
                System.arraycopy(hash, 0, saltAndHash, PIN_SALT_LENGTH, hash.size)
                val base64SaltAndHash: String =
                    Base64.encodeToString(saltAndHash, Base64.NO_PADDING or Base64.NO_WRAP)
                editor.putString(PREF_KEY_EMERGENCY_PIN_HASH, base64SaltAndHash)
            }
            editor.apply()
        }

        @JvmStatic
        fun verifyPIN(pin: String): Boolean {
            val plainPIN: String? =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                    PREF_KEY_PLAIN_PIN, null
                )
            val hashedPIN: String? =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                    PREF_KEY_PIN_HASH, null
                )
            if (plainPIN == null && hashedPIN == null) {
                return true
            } else if (plainPIN != null) {
                return plainPIN == pin
            } else {
                val saltAndHash: ByteArray =
                    Base64.decode(hashedPIN, Base64.NO_PADDING or Base64.NO_WRAP)
                val salt: ByteArray = saltAndHash.copyOfRange(0, PIN_SALT_LENGTH)
                val hash: ByteArray = saltAndHash.copyOfRange(PIN_SALT_LENGTH, saltAndHash.size)
                val hashCheck: ByteArray? = computePINHash(pin, salt)
                return (hashCheck != null) && hash.contentEquals(hashCheck)
            }
        }

        @JvmStatic
        fun verifyEmergencyPIN(pin: String): Boolean {
            val plainPIN: String? =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                    PREF_KEY_EMERGENCY_PLAIN_PIN, null
                )
            val hashedPIN: String? =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                    PREF_KEY_EMERGENCY_PIN_HASH, null
                )
            if (plainPIN == null && hashedPIN == null) {
                return true
            } else if (plainPIN != null) {
                return plainPIN == pin
            } else {
                val saltAndHash: ByteArray =
                    Base64.decode(hashedPIN, Base64.NO_PADDING or Base64.NO_WRAP)
                val salt: ByteArray = saltAndHash.copyOfRange(0, PIN_SALT_LENGTH)
                val hash: ByteArray = saltAndHash.copyOfRange(PIN_SALT_LENGTH, saltAndHash.size)
                val hashCheck: ByteArray? = computePINHash(pin, salt)
                return (hashCheck != null) && hash.contentEquals(hashCheck)
            }
        }

        @JvmStatic
        fun clearPIN() {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.remove(PREF_KEY_PLAIN_PIN)
            editor.remove(PREF_KEY_PIN_HASH)
            editor.apply()
            clearEmergencyPIN()
        }

        @JvmStatic
        fun clearEmergencyPIN() {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_USE_EMERGENCY_PIN, false)
            editor.remove(PREF_KEY_EMERGENCY_PLAIN_PIN)
            editor.remove(PREF_KEY_EMERGENCY_PIN_HASH)
            editor.apply()
        }

        @JvmStatic
        fun computePINHash(pin: String?, salt: ByteArray?): ByteArray? {
            if (pin.isNullOrEmpty()) {
                return null
            }
            val keySpec = PBEKeySpec(pin.toCharArray(), salt, 1000, 160)
            try {
                val skf: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                return skf.generateSecret(keySpec).encoded
            } catch (_: NoSuchAlgorithmException) {
                return null
            } catch (_: InvalidKeySpecException) {
                return null
            }
        }

        @JvmStatic
        val isPINAPassword: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_PIN_IS_A_PASSWORD,
                        PREF_KEY_PIN_IS_A_PASSWORD_DEFAULT
                    )
            }

        @JvmStatic
        fun useBiometryToUnlock(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_UNLOCK_BIOMETRY, PREF_KEY_UNLOCK_BIOMETRY_DEFAULT
            )
        }

        @JvmStatic
        fun setUseBiometryToUnlock(useBiometry: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_UNLOCK_BIOMETRY, useBiometry)
            editor.apply()
        }

        @JvmStatic
        var lockGraceTime: Int
            get() {
                val timeString: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_LOCK_GRACE_TIME,
                            PREF_KEY_LOCK_GRACE_TIME_DEFAULT
                        )!!
                return timeString.toInt()
            }
            set(graceTime) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_LOCK_GRACE_TIME,
                    graceTime.toString()
                )
                editor.apply()
            }

        @JvmStatic
        fun keepLockServiceOpen(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_KEEP_LOCK_SERVICE_OPEN, PREF_KEY_KEEP_LOCK_SERVICE_OPEN_DEFAULT
            )
        }

        @JvmStatic
        fun setKeepLockServiceOpen(keepOpen: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_KEEP_LOCK_SERVICE_OPEN, keepOpen)
            editor.apply()
        }

        @JvmStatic
        fun lockScreenNeutral(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_KEEP_LOCK_SCREEN_NEUTRAL, PREF_KEY_KEEP_LOCK_SCREEN_NEUTRAL_DEFAULT
            )
        }

        @JvmStatic
        fun setUseAutomaticBackup(useAutomaticBackup: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_ENABLE_AUTOMATIC_BACKUP, useAutomaticBackup)
            editor.apply()
        }

        @JvmStatic
        fun useAutomaticBackup(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_ENABLE_AUTOMATIC_BACKUP, PREF_KEY_ENABLE_AUTOMATIC_BACKUP_DEFAULT
            )
        }

        @JvmStatic
        var isMdmAutomaticBackup: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(PREF_KEY_MDM_AUTOMATIC_BACKUP, false)
            }
            set(mdmAutomaticBackup) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (mdmAutomaticBackup) {
                    editor.putBoolean(
                        PREF_KEY_MDM_AUTOMATIC_BACKUP,
                        true
                    )
                } else {
                    editor.remove(PREF_KEY_MDM_AUTOMATIC_BACKUP)
                }
                editor.apply()
            }

        @JvmStatic
        var mdmWebdavKeyEscrowPublicKey: String?
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(
                        PREF_KEY_MDM_WEBDAV_KEY_ESCROW_PUBLIC_KEY,
                        null
                    )
            }
            set(publicKeyPem) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_MDM_WEBDAV_KEY_ESCROW_PUBLIC_KEY,
                    publicKeyPem
                )
                editor.apply()
            }

        @JvmStatic
        val automaticBackupDeviceUniqueId: String
            get() {
                var deviceUniqueId: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID,
                            null
                        )
                if (deviceUniqueId == null) {
                    deviceUniqueId = UUID.randomUUID().toString()
                    val editor: Editor =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .edit()
                    editor.putString(
                        PREF_KEY_AUTOMATIC_BACKUP_DEVICE_UNIQUE_ID,
                        deviceUniqueId
                    )
                    editor.apply()
                }
                return deviceUniqueId
            }

        // this method is used when migrating to the new serialized backup configuration
        @JvmStatic
        fun migrateAutomaticBackupAccount(): String? {
            val account: String? = PreferenceManager.getDefaultSharedPreferences(App.getContext())
                .getString("pref_key_automatic_backup_account", null)
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.remove("pref_key_automatic_backup_account")
            editor.apply()
            return account
        }

        @JvmStatic
        @set:Throws(Exception::class)
        var automaticBackupConfiguration: CloudProviderConfiguration?
            get() {
                val serializedConfiguration: String? =
                    PreferenceManager.getDefaultSharedPreferences(
                        App.getContext()
                    ).getString(
                        PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION, null
                    )
                if (serializedConfiguration != null) {
                    try {
                        return AppSingleton.getJsonObjectMapper().readValue(
                            serializedConfiguration,
                            CloudProviderConfiguration::class.java
                        )
                    } catch (_: Exception) {
                    }
                }
                return null
            }
            set(configuration) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (configuration == null) {
                    editor.remove(PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION)
                } else {
                    editor.putString(
                        PREF_KEY_AUTOMATIC_BACKUP_CONFIGURATION,
                        AppSingleton.getJsonObjectMapper().writeValueAsString(configuration)
                    )
                }
                editor.apply()
            }

        @JvmStatic
        val messageRingtone: Uri
            get() {
                return Uri.parse(
                    PreferenceManager.getDefaultSharedPreferences(
                        App.getContext()
                    ).getString(
                        PREF_KEY_MESSAGE_RINGTONE,
                        PREF_KEY_MESSAGE_RINGTONE_DEFAULT
                    )
                )
            }

        @JvmStatic
        fun setMessageRingtone(uri: String?) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putString(PREF_KEY_MESSAGE_RINGTONE, uri)
            editor.apply()
        }

        @JvmStatic
        val messageVibrationPattern: LongArray
            get() {
                val pattern: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_MESSAGE_VIBRATION_PATTERN,
                            PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT
                        )!!
                return intToVibrationPattern(pattern.toInt())
            }

        @JvmStatic
        var messageVibrationPatternRaw: String
            get() {
                val pattern: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_MESSAGE_VIBRATION_PATTERN,
                            PREF_KEY_MESSAGE_VIBRATION_PATTERN_DEFAULT
                        )!!
                return pattern
            }
            set(pattern) {
                var pattern: String = pattern
                try {
                    val `val`: Int = pattern.toInt()
                    if (intToVibrationPattern(`val`).isEmpty()) {
                        pattern = "0"
                    }
                    val editor: Editor =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .edit()
                    editor.putString(
                        PREF_KEY_MESSAGE_VIBRATION_PATTERN,
                        pattern
                    )
                    editor.apply()
                } catch (_: Exception) {
                }
            }

        @JvmStatic
        fun intToVibrationPattern(patternIndex: Int): LongArray {
            return when (patternIndex) {
                1 -> VIBRATION_PATTERN_1_SHORT
                2 -> VIBRATION_PATTERN_2_SHORT
                3 -> VIBRATION_PATTERN_3_SHORT
                10 -> VIBRATION_PATTERN_1_LONG
                20 -> VIBRATION_PATTERN_2_LONG
                30 -> VIBRATION_PATTERN_3_LONG
                0 -> LongArray(0)
                else -> LongArray(0)
            }
        }

        @JvmStatic
        var messageLedColor: String?
            get() {
                val color: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_MESSAGE_LED_COLOR,
                            PREF_KEY_MESSAGE_LED_COLOR_DEFAULT
                        )!!
                if ("" == color) {
                    return null
                }
                return color
            }
            set(color) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (color == null) {
                    editor.putString(PREF_KEY_MESSAGE_LED_COLOR, "")
                } else {
                    editor.putString(PREF_KEY_MESSAGE_LED_COLOR, color)
                }
                editor.apply()
            }

        @JvmStatic
        val callRingtone: Uri
            get() {
                return Uri.parse(
                    PreferenceManager.getDefaultSharedPreferences(
                        App.getContext()
                    ).getString(
                        PREF_KEY_CALL_RINGTONE,
                        PREF_KEY_CALL_RINGTONE_DEFAULT
                    )
                )
            }

        @JvmStatic
        fun setCallRingtone(uri: String?) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putString(PREF_KEY_CALL_RINGTONE, uri)
            editor.apply()
        }

        val callVibrationPattern: LongArray
            get() {
                val pattern: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_CALL_VIBRATION_PATTERN,
                            PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT
                        )!!
                return intToVibrationPattern(pattern.toInt())
            }

        @JvmStatic
        var callVibrationPatternRaw: String
            get() {
                val pattern: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_CALL_VIBRATION_PATTERN,
                            PREF_KEY_CALL_VIBRATION_PATTERN_DEFAULT
                        )!!
                return pattern
            }
            set(pattern) {
                var pattern: String = pattern
                try {
                    val `val`: Int = pattern.toInt()
                    if (intToVibrationPattern(`val`).isEmpty()) {
                        pattern = "0"
                    }
                    val editor: Editor =
                        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                            .edit()
                    editor.putString(
                        PREF_KEY_CALL_VIBRATION_PATTERN,
                        pattern
                    )
                    editor.apply()
                } catch (_: Exception) {
                }
            }

        @JvmStatic
        fun useFlashOnIncomingCall(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_CALL_USE_FLASH, PREF_KEY_CALL_USE_FLASH_DEFAULT
            )
        }

        @JvmStatic
        fun setUseFlashOnIncomingCall(useFlash: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_CALL_USE_FLASH, useFlash)
            editor.apply()
        }

        @JvmStatic
        fun usePermanentForegroundService(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_PERMANENT_FOREGROUND_SERVICE, PREF_KEY_PERMANENT_FOREGROUND_SERVICE_DEFAULT
            )
        }

        @JvmStatic
        fun setUsePermanentForegroundService(value: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_PERMANENT_FOREGROUND_SERVICE, value)
            editor.apply()
        }

        @JvmStatic
        var cameraResolution: Int
            get() {
                val qualityString: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_CAMERA_RESOLUTION,
                            PREF_KEY_CAMERA_RESOLUTION_DEFAULT
                        )!!
                return qualityString.toInt()
            }
            set(resolution) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_CAMERA_RESOLUTION,
                    resolution.toString()
                )
                editor.apply()
            }

        @JvmStatic
        var metadataRemovalPreference: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_REMOVE_METADATA,
                        PREF_KEY_REMOVE_METADATA_DEFAULT
                    )
            }
            set(remove) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(PREF_KEY_REMOVE_METADATA, remove)
                editor.apply()
            }

        @JvmStatic
        var webclientLanguage: String?
            get() {
                var language: String =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_LANGUAGE_WEBCLIENT,
                            PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT
                        )!!
                if ("" == language) {
                    language = PREF_KEY_LANGUAGE_WEBCLIENT_DEFAULT
                }
                return language
            }
            set(language) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (language == null || "" == language) {
                    editor.remove(PREF_KEY_LANGUAGE_WEBCLIENT)
                } else {
                    editor.putString(
                        PREF_KEY_LANGUAGE_WEBCLIENT,
                        language
                    )
                }
                editor.apply()
            }

        @JvmStatic
        fun gWebclientTheme(): String {
            var theme: String =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                    PREF_KEY_THEME_WEBCLIENT, PREF_KEY_THEME_WEBCLIENT_DEFAULT
                )!!
            if ("" == theme) {
                theme = PREF_KEY_THEME_WEBCLIENT_DEFAULT
            }
            return theme
        }

        @JvmStatic
        fun setThemeWebclient(theme: String?) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            if (theme == null) {
                editor.remove(PREF_KEY_THEME_WEBCLIENT)
            } else {
                when (theme) {
                    "dark", "light", "BrowserDefault", "AppDefault" -> editor.putString(
                        PREF_KEY_THEME_WEBCLIENT, theme
                    )

                    else -> editor.remove(PREF_KEY_THEME_WEBCLIENT)
                }
            }
            editor.apply()
        }

        @JvmStatic
        var webclientSendOnEnter: Boolean?
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_SEND_ON_ENTER_WEBCLIENT,
                        PREF_KEY_SEND_ON_ENTER_WEBCLIENT_DEFAULT
                    )
            }
            set(send) {
                if (send == null) {
                    return
                }
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(PREF_KEY_SEND_ON_ENTER_WEBCLIENT, send)
                editor.apply()
            }

        @JvmStatic
        fun showWebclientNotificationsInBrowser(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER, PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER_DEFAULT
            )
        }

        @JvmStatic
        fun setShowWebclientNotificationsInBrowser(show: Boolean?) {
            if (show == null) {
                return
            }
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_NOTIFICATION_SHOW_ON_BROWSER, show)
            editor.apply()
        }

        @JvmStatic
        fun playWebclientNotificationsSoundInBrowser(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT, PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT_DEFAULT
            )
        }


        @JvmStatic
        fun setPlayWebclientNotificationsSoundInBrowser(notifications: Boolean?) {
            if (notifications == null) {
                return
            }
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_NOTIFICATION_SOUND_WEBCLIENT, notifications)
            editor.apply()
        }

        @JvmStatic
        fun keepWebclientAliveAfterClose(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE,
                PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE_DEFAULT
            )
        }

        @JvmStatic
        fun setKeepWebclientAliveAfterClose(keepAlive: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_KEEP_WEBCLIENT_ALIVE_AFTER_CLOSE, keepAlive)
            editor.apply()
        }

        @JvmStatic
        fun showWebclientErrorNotifications(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_SHOW_ERROR_NOTIFICATIONS, PREF_KEY_SHOW_ERROR_NOTIFICATIONS_DEFAULT
            )
        }

        @JvmStatic
        fun setShowWebclientErrorNotifications(show: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_SHOW_ERROR_NOTIFICATIONS, show)
            editor.apply()
        }

        @JvmStatic
        fun webclientNotifyAfterInactivity(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY,
                PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY_DEFAULT
            )
        }

        @JvmStatic
        fun setWebclientNotifyAfterInactivity(notify: Boolean) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            editor.putBoolean(PREF_KEY_NOTIFICATION_FOR_MESSAGES_AFTER_INACTIVITY, notify)
            editor.apply()
        }

        @JvmStatic
        var isWebclientUnlockRequired: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT,
                        PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT_DEFAULT
                    )
            }
            set(unlockRequired) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_REQUIRE_UNLOCK_BEFORE_CONNECTING_TO_WEBCLIENT,
                    unlockRequired
                )
                editor.apply()
            }


        @JvmStatic
        var preferredKeycloakBrowser: String?
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(PREF_KEY_PREFERRED_KEYCLOAK_BROWSER, null)
            }
            set(browser) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (browser != null) {
                    editor.putString(
                        PREF_KEY_PREFERRED_KEYCLOAK_BROWSER,
                        browser
                    )
                } else {
                    editor.remove(PREF_KEY_PREFERRED_KEYCLOAK_BROWSER)
                }
                editor.apply()
            }

        @JvmStatic
        var useSpeakerOutputForMediaPlayer: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER,
                        PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER_DEFAULT
                    )
            }
            set(useSpeakerOutput) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_USE_SPEAKER_OUTPUT_FOR_MEDIA_PLAYER,
                    useSpeakerOutput
                )
                editor.apply()
            }

        @JvmStatic
        var playbackSpeedForMediaPlayer: Float
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getFloat(
                        PREF_KEY_PLAYBACK_SPEED_FOR_MEDIA_PLAYER,
                        PREF_KEY_PLAYBACK_SPEED_FOR_MEDIA_PLAYER_DEFAULT
                    )
            }
            set(playbackSpeed) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putFloat(
                    PREF_KEY_PLAYBACK_SPEED_FOR_MEDIA_PLAYER,
                    playbackSpeed
                )
                editor.apply()
            }

        @JvmStatic
        var composeMessageIconPreferredOrder: MutableList<Int>?
            get() {
                val preferredOrderString: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER,
                            null
                        )
                try {
                    if (preferredOrderString != null) {
                        val parts: Array<String> =
                            preferredOrderString.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        val preferredIcons: MutableList<Int> =
                            ArrayList(parts.size)
                        for (part: String in parts) {
                            preferredIcons.add(part.toInt())
                        }
                        return preferredIcons
                    }
                } catch (_: Exception) {
                }
                return null
            }
            set(icons) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (icons == null) {
                    editor.remove(PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER)
                } else {
                    val iconSb: StringBuilder = StringBuilder()
                    for (icon: Int in icons) {
                        if (iconSb.isNotEmpty()) {
                            iconSb.append(",")
                        }
                        iconSb.append(icon)
                    }
                    editor.putString(
                        PREF_KEY_COMPOSE_MESSAGE_ICON_PREFERRED_ORDER,
                        iconSb.toString()
                    )
                }
                editor.apply()
            }

        @JvmStatic
        var preferredReactions: MutableList<String>
            get() {
                val preferredReactions: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(PREF_KEY_PREFERRED_REACTIONS, null)
                try {
                    if (preferredReactions != null) {
                        val reactions: Array<String> =
                            preferredReactions.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        return ArrayList(
                            mutableListOf(
                                *reactions
                            )
                        ) // build a new ArrayList to make the list mutable
                    }
                } catch (_: Exception) {
                }
                return ArrayList(mutableListOf(*PREF_KEY_PREFERRED_REACTIONS_DEFAULT))
            }
            set(reactions) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                val sb: StringBuilder = StringBuilder()
                for (reaction: String? in reactions) {
                    if (sb.isNotEmpty()) {
                        sb.append(",")
                    }
                    sb.append(reaction)
                }
                editor.putString(
                    PREF_KEY_PREFERRED_REACTIONS,
                    sb.toString()
                )
                editor.apply()
            }

        @JvmStatic
        val locationIntegration: LocationIntegrationEnum
            get() {
                val integrationString: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(PREF_KEY_LOCATION_INTEGRATION, null)

                if (integrationString == null) {
                    return LocationIntegrationEnum.NONE
                }

                when (integrationString) {
                    PREF_VALUE_LOCATION_INTEGRATION_OSM -> {
                        return OSM
                    }

                    PREF_VALUE_LOCATION_INTEGRATION_MAPS -> {
                        return MAPS
                    }

                    PREF_VALUE_LOCATION_INTEGRATION_BASIC -> {
                        return BASIC
                    }

                    PREF_VALUE_LOCATION_INTEGRATION_CUSTOM_OSM -> {
                        return CUSTOM_OSM
                    }

                    else -> {
                        return LocationIntegrationEnum.NONE
                    }
                }
            }


        @JvmStatic
        fun setLocationIntegration(integrationString: String?, customOsmServerUrl: String?) {
            val editor: Editor =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).edit()
            if (integrationString == null) {
                editor.remove(PREF_KEY_LOCATION_INTEGRATION)
            } else {
                when (integrationString) {
                    PREF_VALUE_LOCATION_INTEGRATION_MAPS, PREF_VALUE_LOCATION_INTEGRATION_BASIC, PREF_VALUE_LOCATION_INTEGRATION_OSM -> {
                        editor.putString(PREF_KEY_LOCATION_INTEGRATION, integrationString)
                    }

                    PREF_VALUE_LOCATION_INTEGRATION_CUSTOM_OSM -> {
                        if (customOsmServerUrl != null) {
                            editor.putString(PREF_KEY_LOCATION_INTEGRATION, integrationString)
                            editor.putString(
                                PREF_KEY_LOCATION_CUSTOM_OSM_SERVER,
                                customOsmServerUrl
                            )
                        }
                    }

                    else -> {
                        editor.remove(PREF_KEY_LOCATION_INTEGRATION)
                    }
                }
            }
            editor.apply()
        }

        @JvmStatic
        var locationLastOsmStyleId: String?
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(PREF_KEY_LOCATION_LAST_OSM_STYLE_ID, null)
            }
            set(id) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(PREF_KEY_LOCATION_LAST_OSM_STYLE_ID, id)
                editor.apply()
            }

        @JvmStatic
        var locationLastGoogleMapType: Int
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getInt(
                        PREF_KEY_LOCATION_LAST_GOOGLE_MAP_TYPE,
                        1
                    ) // 1 is GoogleMap.TYPE_NORMAL
            }
            set(mapType) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putInt(
                    PREF_KEY_LOCATION_LAST_GOOGLE_MAP_TYPE,
                    mapType
                )
                editor.apply()
            }

        @JvmStatic
        val locationCustomOsmServerUrl: String?
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(PREF_KEY_LOCATION_CUSTOM_OSM_SERVER, null)
            }

        @JvmStatic
        var locationDefaultSharingDurationValue: Long
            get() {
                val durationString: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION,
                            null
                        )
                if (durationString != null) {
                    try {
                        return durationString.toLong()
                    } catch (_: NumberFormatException) {
                    }
                }
                return PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT
            }
            set(duration) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION,
                    duration.toString()
                )
                editor.apply()
            }

        @JvmStatic
        fun getLocationDefaultSharingDurationLongString(context: Context): CharSequence {
            var duration: String? =
                PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(
                    PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION, null
                )

            // default value not set
            if (duration == null) {
                duration = PREF_KEY_LOCATION_DEFAULT_SHARE_DURATION_DEFAULT.toString()
            }

            val valuesArray: Array<String> =
                context.resources.getStringArray(R.array.share_location_duration_values)
            val longStringArray: Array<CharSequence> =
                context.resources.getTextArray(R.array.share_location_duration_long_strings)

            val index: Int = listOf(*valuesArray).indexOf(duration)
            if (index >= 0 && index < longStringArray.size) {
                return longStringArray[index]
            }

            // fallback mechanism
            if (longStringArray.isEmpty()) {
                return ""
            }
            return longStringArray[0]
        }

        @JvmStatic
        var locationDefaultShareQuality: LocationShareQuality
            get() {
                val qualityString: String? =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_LOCATION_DEFAULT_SHARE_QUALITY,
                            null
                        )
                if (qualityString != null) {
                    try {
                        return LocationShareQuality.fromValue(qualityString.toInt())
                    } catch (_: Exception) {
                    }
                }
                return PREF_KEY_LOCATION_DEFAULT_SHARE_QUALITY_DEFAULT
            }
            set(quality) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putString(
                    PREF_KEY_LOCATION_DEFAULT_SHARE_QUALITY,
                    quality.value.toString()
                )
                editor.apply()
            }


        @JvmStatic
        var locationDisableAddressLookup: Boolean
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getBoolean(
                        PREF_KEY_LOCATION_DISABLE_ADDRESS_LOOKUP,
                        PREF_KEY_LOCATION_DISABLE_ADDRESS_LOOKUP_DEFAULT
                    )
            }
            set(disabled) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                editor.putBoolean(
                    PREF_KEY_LOCATION_DISABLE_ADDRESS_LOOKUP,
                    disabled
                )
                editor.apply()
            }

        @JvmStatic
        var locationCustomAddressServer: String?
            get() {
                if (PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getBoolean(
                            PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER,
                            PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER_DEFAULT
                        )
                ) {
                    return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .getString(
                            PREF_KEY_LOCATION_CUSTOM_ADDRESS_SERVER,
                            null
                        )
                }
                return null
            }
            // set to null to disable the use of custom server, but do not wipe the previously entered server
            set(serverUrl) {
                val editor: Editor =
                    PreferenceManager.getDefaultSharedPreferences(App.getContext())
                        .edit()
                if (serverUrl == null) {
                    editor.putBoolean(
                        PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER,
                        false
                    )
                } else {
                    editor.putBoolean(
                        PREF_KEY_LOCATION_USE_CUSTOM_ADDRESS_SERVER,
                        true
                    )
                    editor.putString(
                        PREF_KEY_LOCATION_CUSTOM_ADDRESS_SERVER,
                        serverUrl
                    )
                }
                editor.apply()
            }

        val locationCustomAddressServerEvenIfDisabled: String?
            get() {
                return PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString(
                        PREF_KEY_LOCATION_CUSTOM_ADDRESS_SERVER,
                        null
                    )
            }

        @JvmStatic
        fun hideLocationErrorsNotifications(): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(App.getContext()).getBoolean(
                PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS,
                PREF_KEY_LOCATION_HIDE_ERROR_NOTIFICATIONS_DEFAULT
            )
        }
    }
}
