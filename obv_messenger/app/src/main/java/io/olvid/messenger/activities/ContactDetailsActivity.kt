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
package io.olvid.messenger.activities

import android.animation.LayoutTransition
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.Identity
import io.olvid.engine.encoder.DecodingException
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.ObvDialog.Category
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.REVOKED
import io.olvid.engine.engine.types.identities.ObvTrustOrigin
import io.olvid.engine.engine.types.identities.ObvTrustOrigin.TYPE.DIRECT
import io.olvid.engine.engine.types.identities.ObvTrustOrigin.TYPE.GROUP
import io.olvid.engine.engine.types.identities.ObvTrustOrigin.TYPE.INTRODUCTION
import io.olvid.engine.engine.types.identities.ObvTrustOrigin.TYPE.KEYCLOAK
import io.olvid.engine.engine.types.identities.ObvTrustOrigin.TYPE.SERVER_GROUP_V2
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask
import io.olvid.messenger.fragments.FilteredDiscussionListFragment
import io.olvid.messenger.fragments.FullScreenImageFragment
import io.olvid.messenger.fragments.dialog.ContactIntroductionDialogFragment.Companion.newInstance
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.settings.SettingsActivity.Companion.contactDisplayNameFormat
import io.olvid.messenger.settings.SettingsActivity.Companion.uppercaseLastName
import io.olvid.messenger.viewModels.ContactDetailsViewModel
import io.olvid.messenger.viewModels.ContactDetailsViewModel.ContactAndInvitation
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion
import java.lang.ref.WeakReference

class ContactDetailsActivity : LockableActivity(), OnClickListener,
    EngineNotificationListener {
    private val contactDetailsViewModel: ContactDetailsViewModel by viewModels()

    private var mainConstraintLayout: ConstraintLayout? = null
    private val discussionButton: FloatingActionButton by lazy { findViewById(R.id.contact_discussion_button) }
    private val contactInitialView: InitialView by lazy { findViewById(R.id.contact_details_initial_view) }
    private var contactNameTextView: TextView? = null
    private var personalNoteTextView: TextView? = null
    private val trustOriginsListTextView: TextView by lazy { findViewById(R.id.contact_trust_origins_list) }
    private val exchangeDigitsButton: Button by lazy { findViewById(R.id.contact_trust_origin_exchange_digits_button) }
    private var revokedCardView: CardView? = null
    private var revokedExplanationTextView: TextView? = null
    private val unblockRevokedButton: Button by lazy { findViewById(R.id.button_contact_revoked_forcefully_unblock) }
    private val reblockRevokedButton: Button by lazy { findViewById(R.id.button_contact_revoked_forcefully_reblock) }
    private var notRecentlyOnlineCardView: CardView? = null
    private var noChannelCardView: CardView? = null
    private var noChannelSpinner: ImageView? = null
    private var notOneToOneCardView: CardView? = null
    private var notOneToOneTitleTextView: TextView? = null
    private var notOneToOneExplanationTextView: TextView? = null
    private val notOneToOneInviteButton: Button by lazy { findViewById(R.id.contact_not_one_to_one_invite_button) }
    private val notOneToOneRejectButton: Button by lazy { findViewById(R.id.contact_not_one_to_one_reject_button) }
    private var acceptUpdateCardView: CardView? = null
    private var trustedDetailsCardView: CardView? = null
    private val publishedDetailsTextViews: LinearLayout by lazy { findViewById(R.id.published_details_textviews) }
    private var trustedDetailsTextViews: LinearLayout? = null
    private val publishedDetailsInitialView: InitialView by lazy { findViewById(R.id.published_details_initial_view) }
    private val trustedDetailsInitialView: InitialView by lazy { findViewById(R.id.trusted_details_initial_view) }
    private var publishDetailsTitle: TextView? = null
    private val introduceButton: Button by lazy { findViewById(R.id.contact_introduce_button) }
    private var contactGroupDiscussionsFragment: FilteredDiscussionListFragment? = null
    private lateinit var publishedDetails: JsonIdentityDetails

    private var primary700 = 0
    private var registrationNumber: Long? = null

    private var animationsSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_contact_details)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        findViewById<CoordinatorLayout>(R.id.contact_details_coordinatorLayout)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                view.updatePadding(top = insets.top)
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    updateMargins(bottom = insets.bottom)
                }
                findViewById<ScrollView>(R.id.contact_details_scroll_view)?.updatePadding(
                    left = insets.left,
                    right = insets.right
                )
                WindowInsetsCompat.CONSUMED
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.elevation = 0f

        mainConstraintLayout = findViewById(R.id.contact_details_main_constraint_layout)

        discussionButton.setOnClickListener(this)

        revokedCardView = findViewById(R.id.contact_revoked_cardview)
        revokedExplanationTextView = findViewById(R.id.contact_revoked_explanation)
        unblockRevokedButton.setOnClickListener(this)
        reblockRevokedButton.setOnClickListener(this)

        notRecentlyOnlineCardView = findViewById(R.id.contact_not_recently_online_cardview)

        noChannelCardView = findViewById(R.id.contact_no_channel_cardview)
        val restartChannelButton = findViewById<Button>(R.id.contact_no_channel_restart_button)
        restartChannelButton.setOnClickListener(this)
        noChannelSpinner = findViewById(R.id.contact_no_channel_spinner)

        notOneToOneCardView = findViewById(R.id.contact_not_one_to_one_card)
        notOneToOneTitleTextView = findViewById(R.id.contact_not_one_to_one_header)
        notOneToOneExplanationTextView = findViewById(R.id.contact_not_one_to_one_explanation)
        notOneToOneInviteButton.setOnClickListener(this)
        notOneToOneRejectButton.setOnClickListener(this)


        contactInitialView.setOnClickListener(this)
        contactNameTextView = findViewById(R.id.contact_name_text_view)
        personalNoteTextView = findViewById(R.id.contact_personal_note_text_view)


        // detail cards
        acceptUpdateCardView = findViewById(R.id.contact_accept_update_cardview)
        val updateButton = findViewById<Button>(R.id.button_update)
        updateButton.setOnClickListener(this)


        publishDetailsTitle = findViewById(R.id.published_details_title)
        publishedDetailsInitialView.setOnClickListener(this)
        val shareButton = findViewById<Button>(R.id.contact_share_button)

        shareButton.setOnClickListener(this)
        introduceButton.setOnClickListener(this)
        introduceButton.isEnabled = false


        trustedDetailsCardView = findViewById(R.id.trusted_details_cardview)
        trustedDetailsTextViews = findViewById(R.id.trusted_details_textviews)
        trustedDetailsInitialView.setOnClickListener(this)

        val groupEmptyView = findViewById<View>(R.id.contact_group_list_empty_view)

        contactGroupDiscussionsFragment = FilteredDiscussionListFragment().apply {
            setBottomPadding(0)
            setEmptyView(groupEmptyView)
            setOnClickDelegate { view: View, searchableDiscussion: SearchableDiscussion ->
                App.openDiscussionActivity(
                    view.context,
                    searchableDiscussion.discussionId
                )
            }
        }

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.contact_group_list_placeholder, contactGroupDiscussionsFragment!!)
        transaction.commit()


        trustOriginsListTextView.movementMethod = LinkMovementMethod.getInstance()

        exchangeDigitsButton.setOnClickListener(this)

        primary700 = ContextCompat.getColor(this, R.color.primary700)

        registrationNumber = null
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.NEW_CONTACT_PHOTO, this)
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS,
            this
        )
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.NEW_CONTACT_PHOTO,
            this
        )
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS,
            this
        )
        if (contactDetailsViewModel.contactAndInvitation == null || contactDetailsViewModel.contactAndInvitation.value == null) {
            return
        }
        val contact = contactDetailsViewModel.contactAndInvitation
            .value!!.contact
        App.runThread {
            if (contact.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN) {
                contact.newPublishedDetails =
                    Contact.PUBLISHED_DETAILS_NEW_SEEN
                AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity,
                    contact.newPublishedDetails
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (contactDetailsViewModel.contactAndInvitation == null || contactDetailsViewModel.contactAndInvitation.value == null) {
            return true
        }
        val contact = contactDetailsViewModel.contactAndInvitation
            .value!!.contact
        if (contact.oneToOne) {
            menuInflater.inflate(R.menu.menu_contact_details, menu)
        } else {
            menuInflater.inflate(R.menu.menu_contact_details_not_one_to_one, menu)
        }
        if (contact.active) {
            menuInflater.inflate(R.menu.menu_contact_details_recreate_channels, menu)
            if (contact.hasChannelOrPreKey()) {
                menuInflater.inflate(R.menu.menu_contact_details_call, menu)
            }
        }
        val deleteItem = menu.findItem(R.id.action_delete_contact)
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


    private var wasNonNull: Boolean = false

    private fun displayDetails(contactAndInvitation: ContactAndInvitation?) {
        if (contactAndInvitation == null) {
            if (wasNonNull) {
                finish()
            }
            return
        }
        wasNonNull = true
        val contact = contactAndInvitation.contact
        val invitation = contactAndInvitation.invitation

        invalidateOptionsMenu()

        if (contact.oneToOne) {
            setTitle(R.string.activity_title_contact_details)
            introduceButton.visibility = View.VISIBLE
            notOneToOneCardView!!.visibility = View.GONE
            discussionButton.visibility = View.VISIBLE
        } else {
            setTitle(R.string.activity_title_user_details)
            introduceButton.visibility = View.GONE
            notOneToOneCardView!!.visibility = View.VISIBLE
            discussionButton.visibility = View.INVISIBLE
            if (invitation == null) {
                notOneToOneTitleTextView!!.setText(R.string.label_contact_not_one_to_one)
                notOneToOneExplanationTextView!!.text =
                    getString(
                        R.string.explanation_contact_not_one_to_one,
                        contact.getCustomDisplayName()
                    )
                notOneToOneInviteButton.visibility = if (contact.active) View.VISIBLE else View.GONE
                notOneToOneInviteButton.setText(R.string.button_label_invite)
                notOneToOneRejectButton.visibility = View.GONE
            } else if (invitation.categoryId == Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY) {
                notOneToOneTitleTextView!!.setText(R.string.invitation_status_one_to_one_invitation)
                notOneToOneExplanationTextView!!.text = getString(
                    R.string.invitation_status_description_one_to_one_invitation_sent,
                    contact.getCustomDisplayName()
                )
                notOneToOneInviteButton.visibility = View.GONE
                notOneToOneRejectButton.visibility = View.VISIBLE
                notOneToOneRejectButton.setText(R.string.button_label_abort)
            } else if (invitation.categoryId == Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                AndroidNotificationManager.clearInvitationNotification(invitation.dialogUuid)
                notOneToOneTitleTextView!!.setText(R.string.invitation_status_one_to_one_invitation)
                notOneToOneExplanationTextView!!.text = getString(
                    R.string.invitation_status_description_one_to_one_invitation,
                    contact.getCustomDisplayName()
                )
                notOneToOneInviteButton.visibility = View.VISIBLE
                notOneToOneInviteButton.setText(R.string.button_label_accept)
                notOneToOneRejectButton.visibility = View.VISIBLE
                notOneToOneRejectButton.setText(R.string.button_label_reject)
            }
        }

        val displayName = contact.getCustomDisplayName()
        contactInitialView.setContact(contact)
        contactNameTextView!!.text = displayName
        if (contact.personalNote != null) {
            personalNoteTextView!!.visibility = View.VISIBLE
            personalNoteTextView!!.text = contact.personalNote
        } else {
            personalNoteTextView!!.visibility = View.GONE
        }

        if (contact.shouldShowChannelCreationSpinner() && contact.active) {
            noChannelCardView!!.visibility = View.VISIBLE
            val animated = AnimatedVectorDrawableCompat.create(App.getContext(), R.drawable.dots)
            if (animated != null) {
                animated.registerAnimationCallback(object : AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable) {
                        Handler(Looper.getMainLooper()).post { animated.start() }
                    }
                })
                noChannelSpinner!!.setImageDrawable(animated)
                animated.start()
            }
        } else {
            noChannelCardView!!.visibility = View.GONE
            noChannelSpinner!!.setImageDrawable(null)
        }

        if (contact.recentlyOnline) {
            notRecentlyOnlineCardView!!.visibility = View.GONE
        } else {
            notRecentlyOnlineCardView!!.visibility = View.VISIBLE
        }

        if (contact.hasChannelOrPreKey()) {
            introduceButton.isEnabled = true
            notOneToOneInviteButton.isEnabled = true
        } else if (contact.keycloakManaged) {
            introduceButton.isEnabled = false
            notOneToOneInviteButton.isEnabled = true
        } else {
            introduceButton.isEnabled = false
            notOneToOneInviteButton.isEnabled = false
        }


        val reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(
            contact.bytesOwnedIdentity,
            contact.bytesContactIdentity
        )
        if (reasons != null && reasons.contains(REVOKED)) {
            revokedCardView!!.visibility = View.VISIBLE
            if (reasons.contains(FORCEFULLY_UNBLOCKED)) {
                revokedExplanationTextView!!.setText(R.string.explanation_contact_revoked_and_unblocked)
                reblockRevokedButton.visibility = View.VISIBLE
                unblockRevokedButton.visibility = View.GONE
            } else {
                revokedExplanationTextView!!.setText(R.string.explanation_contact_revoked)
                reblockRevokedButton.visibility = View.GONE
                unblockRevokedButton.visibility = View.VISIBLE
            }
        } else {
            revokedCardView!!.visibility = View.GONE
        }

        try {
            val jsons = AppSingleton.getEngine().getContactPublishedAndTrustedDetails(
                contact.bytesOwnedIdentity,
                contact.bytesContactIdentity
            )
            if (jsons.isNullOrEmpty()) {
                return
            }
            if (jsons.size == 1) {
                acceptUpdateCardView!!.visibility = View.GONE
                trustedDetailsCardView!!.visibility = View.GONE

                publishDetailsTitle!!.setText(R.string.label_olvid_card)
                publishDetailsTitle!!.background =
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.background_identity_title
                    )

                publishedDetailsTextViews.removeAllViews()
                publishedDetails = jsons[0].identityDetails
                val publishedFirstLine = publishedDetails.formatFirstAndLastName(
                    contactDisplayNameFormat, uppercaseLastName
                )
                val publishedSecondLine = publishedDetails.formatPositionAndCompany(
                    contactDisplayNameFormat
                )
                publishedDetailsInitialView.setInitial(
                    contact.bytesContactIdentity,
                    StringUtils.getInitial(publishedFirstLine)
                )
                if (jsons[0].photoUrl != null) {
                    publishedDetailsInitialView.setPhotoUrl(
                        contact.bytesContactIdentity,
                        jsons[0].photoUrl
                    )
                }

                run {
                    val tv = textView
                    tv.text = publishedFirstLine
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    publishedDetailsTextViews.addView(tv)
                }
                if (!publishedSecondLine.isNullOrEmpty()) {
                    val tv = textView
                    tv.text = publishedSecondLine
                    publishedDetailsTextViews.addView(tv)
                }
                if (publishedDetails.customFields != null) {
                    val keys: MutableList<String> =
                        ArrayList(publishedDetails.customFields.size)
                    keys.addAll(publishedDetails.customFields.keys)
                    keys.sort()
                    for (key in keys) {
                        val tv = textView
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
                        publishedDetailsTextViews.addView(tv)
                    }
                }

                App.runThread {
                    if (contact.newPublishedDetails != Contact.PUBLISHED_DETAILS_NOTHING_NEW) {
                        contact.newPublishedDetails =
                            Contact.PUBLISHED_DETAILS_NOTHING_NEW
                        AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity,
                            contact.newPublishedDetails
                        )
                    }
                    if ((contact.photoUrl == null && jsons[0].photoUrl != null) ||
                        (contact.photoUrl != null && contact.photoUrl != jsons[0].photoUrl)
                    ) {
                        contact.photoUrl = jsons[0].photoUrl
                        AppDatabase.getInstance().contactDao().updatePhotoUrl(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity,
                            contact.photoUrl
                        )
                        AppSingleton.updateCachedPhotoUrl(
                            contact.bytesContactIdentity,
                            contact.getCustomPhotoUrl()
                        )
                    }
                }
            } else {
                publishDetailsTitle!!.setText(R.string.label_olvid_card_published_update)
                publishDetailsTitle!!.background =
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.background_identity_title_new
                    )

                acceptUpdateCardView!!.visibility = View.VISIBLE
                trustedDetailsCardView!!.visibility = View.VISIBLE

                trustedDetailsTextViews!!.removeAllViews()
                publishedDetailsTextViews.removeAllViews()

                val trustedDetails = jsons[1].identityDetails
                val trustedFirstLine = trustedDetails.formatFirstAndLastName(
                    contactDisplayNameFormat, uppercaseLastName
                )
                val trustedSecondLine = trustedDetails.formatPositionAndCompany(
                    contactDisplayNameFormat
                )
                trustedDetailsInitialView.setInitial(
                    contact.bytesContactIdentity,
                    StringUtils.getInitial(trustedFirstLine)
                )
                if (jsons[1].photoUrl != null) {
                    trustedDetailsInitialView.setPhotoUrl(
                        contact.bytesContactIdentity,
                        jsons[1].photoUrl
                    )
                }

                run {
                    val tv = textView
                    tv.text = trustedFirstLine
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    trustedDetailsTextViews!!.addView(tv)
                }
                if (!trustedSecondLine.isNullOrEmpty()) {
                    val tv = textView
                    tv.text = trustedSecondLine
                    trustedDetailsTextViews!!.addView(tv)
                }
                if (trustedDetails.customFields != null) {
                    val keys: MutableList<String> = ArrayList(trustedDetails.customFields.size)
                    keys.addAll(trustedDetails.customFields.keys)
                    keys.sort()
                    for (key in keys) {
                        val tv = textView
                        val value = trustedDetails.customFields[key] ?: continue
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
                        trustedDetailsTextViews!!.addView(tv)
                    }
                }


                publishedDetails = jsons[0].identityDetails
                val publishedFirstLine = publishedDetails.formatFirstAndLastName(
                    contactDisplayNameFormat, uppercaseLastName
                )
                val publishedSecondLine = publishedDetails.formatPositionAndCompany(
                    contactDisplayNameFormat
                )
                publishedDetailsInitialView.setInitial(
                    contact.bytesContactIdentity,
                    StringUtils.getInitial(publishedFirstLine)
                )
                if (jsons[0].photoUrl != null) {
                    publishedDetailsInitialView.setPhotoUrl(
                        contact.bytesContactIdentity,
                        jsons[0].photoUrl
                    )
                }

                run {
                    val tv = textView
                    tv.text = publishedFirstLine
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    if (publishedFirstLine != trustedFirstLine) {
                        tv.setTypeface(tv.typeface, Typeface.BOLD)
                    }
                    publishedDetailsTextViews.addView(tv)
                }
                if (!publishedSecondLine.isNullOrEmpty()) {
                    val tv = textView
                    tv.text = publishedSecondLine
                    if (publishedSecondLine != trustedSecondLine) {
                        tv.setTypeface(tv.typeface, Typeface.BOLD)
                    }
                    publishedDetailsTextViews.addView(tv)
                }
                if (publishedDetails.customFields != null) {
                    val keys: MutableList<String> =
                        ArrayList(publishedDetails.customFields.size)
                    keys.addAll(publishedDetails.customFields.keys)
                    keys.sort()
                    for (key in keys) {
                        val tv = textView
                        val value = publishedDetails.customFields[key] ?: continue
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
                        if (!(trustedDetails.customFields != null && value == trustedDetails.customFields[key])) {
                            tv.setTypeface(tv.typeface, Typeface.BOLD)
                        }
                        publishedDetailsTextViews.addView(tv)
                    }
                }

                App.runThread {
                    if (contact.newPublishedDetails == Contact.PUBLISHED_DETAILS_NOTHING_NEW) {
                        contact.newPublishedDetails =
                            Contact.PUBLISHED_DETAILS_NEW_SEEN
                        AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity,
                            contact.newPublishedDetails
                        )
                    }
                    if ((contact.photoUrl == null && jsons[1].photoUrl != null) ||
                        (contact.photoUrl != null && contact.photoUrl != jsons[1].photoUrl)
                    ) {
                        contact.photoUrl = jsons[1].photoUrl
                        AppDatabase.getInstance().contactDao().updatePhotoUrl(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity,
                            contact.photoUrl
                        )
                        AppSingleton.updateCachedPhotoUrl(
                            contact.bytesContactIdentity,
                            contact.getCustomPhotoUrl()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        if (!animationsSet) {
            mainConstraintLayout!!.layoutTransition = LayoutTransition()
            animationsSet = true
        }
        App.runThread(
            DisplayTrustOriginsTask(
                trustOriginsListTextView,
                exchangeDigitsButton,
                contact
            )
        )
    }

    private val textView: TextView
        get() {
            val tv: TextView = AppCompatTextView(this)
            tv.setTextColor(primary700)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tv.maxLines = 4
            val params = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            params.setMargins(
                0,
                0,
                0,
                resources.getDimensionPixelSize(R.dimen.identity_details_margin)
            )
            tv.layoutParams = params
            return tv
        }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.NEW_CONTACT_PHOTO -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesContactIdentity =
                    userInfo[EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY] as ByteArray?
                val isTrusted =
                    userInfo[EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY] as Boolean?
                contactDetailsViewModel.contactAndInvitation?.value?.let { contactAndInvitation ->
                    if (isTrusted != null && !isTrusted
                        && contactAndInvitation.contact.bytesContactIdentity.contentEquals(
                            bytesContactIdentity
                        ) && contactAndInvitation.contact.bytesOwnedIdentity.contentEquals(
                            bytesOwnedIdentity
                        )
                    ) {
                        runOnUiThread { displayDetails(contactAndInvitation) }
                    }
                }
            }

            EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesContactIdentity =
                    userInfo[EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY] as ByteArray?

                contactDetailsViewModel.contactAndInvitation?.value?.let { contactAndInvitation ->
                    if (contactAndInvitation.contact.bytesContactIdentity.contentEquals(
                            bytesContactIdentity
                        ) && contactAndInvitation.contact.bytesOwnedIdentity.contentEquals(
                            bytesOwnedIdentity
                        )
                    ) {
                        runOnUiThread { displayDetails(contactAndInvitation) }
                    }
                }
            }
        }
    }

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        this.registrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return registrationNumber!!
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return registrationNumber != null
    }

    internal class DisplayTrustOriginsTask(
        textView: TextView?,
        exchangeDigitsButton: Button?,
        private val contact: Contact
    ) :
        Runnable {
        private val textViewWeakReference =
            WeakReference(textView)
        private val buttonWeakReference =
            WeakReference(exchangeDigitsButton)
        private val context: Context = App.getContext()

        override fun run() {
            if (contact.trustLevel < 4) {
                val button = buttonWeakReference.get()
                if (button != null) {
                    Handler(Looper.getMainLooper()).post {
                        button.text = button.context.getString(
                            R.string.button_label_exchange_digits_with_user,
                            contact.getCustomDisplayName()
                        )
                        button.visibility = View.VISIBLE
                        button.parent.requestLayout()
                    }
                }
            }

            try {
                val trustOrigins = AppSingleton.getEngine().getContactTrustOrigins(
                    contact.bytesOwnedIdentity, contact.bytesContactIdentity
                )
                val builder = SpannableStringBuilder()
                var first = true
                for (trustOrigin in trustOrigins) {
                    if (!first) {
                        builder.append("\n")
                    }
                    first = false
                    builder.append(
                        trustOriginToCharSequence(
                            trustOrigin,
                            contact.bytesOwnedIdentity
                        )
                    )
                }
                val textView = textViewWeakReference.get()
                if (textView != null) {
                    Handler(Looper.getMainLooper()).post {
                        textView.text =
                            builder
                    }
                }
            } catch (_: Exception) {
                val textView = textViewWeakReference.get()
                if (textView != null) {
                    Handler(Looper.getMainLooper()).post { textView.setText(R.string.message_error_trust_origin) }
                }
            }
        }

        private fun trustOriginToCharSequence(
            trustOrigin: ObvTrustOrigin,
            bytesOwnedIdentity: ByteArray
        ): CharSequence {
            when (trustOrigin.type) {
                DIRECT -> return context.getString(
                    R.string.trust_origin_direct_type,
                    StringUtils.getNiceDateString(context, trustOrigin.timestamp)
                )

                INTRODUCTION -> {
                    val text = context.getString(
                        R.string.trust_origin_introduction_type,
                        StringUtils.getNiceDateString(context, trustOrigin.timestamp)
                    )
                    var link =
                        if ((trustOrigin.mediatorOrGroupOwner.identityDetails == null)) null else SpannableString(
                            trustOrigin.mediatorOrGroupOwner.identityDetails.formatDisplayName(
                                contactDisplayNameFormat, uppercaseLastName
                            )
                        )
                    if (!link.isNullOrEmpty()) {
                        val clickableSpan: ClickableSpan = object : ClickableSpan() {
                            override fun onClick(view: View) {
                                App.openContactDetailsActivity(
                                    view.context,
                                    bytesOwnedIdentity,
                                    trustOrigin.mediatorOrGroupOwner.bytesIdentity
                                )
                            }
                        }
                        link.setSpan(
                            clickableSpan,
                            0,
                            link.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        link = SpannableString(context.getString(R.string.text_deleted_contact))
                        val styleSpan = StyleSpan(Typeface.ITALIC)
                        link.setSpan(styleSpan, 0, link.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    return TextUtils.concat(text, link)
                }

                GROUP -> {
                    val text = context.getString(
                        R.string.trust_origin_group_type,
                        StringUtils.getNiceDateString(context, trustOrigin.timestamp)
                    )
                    var link =
                        if ((trustOrigin.mediatorOrGroupOwner.identityDetails == null)) null else SpannableString(
                            trustOrigin.mediatorOrGroupOwner.identityDetails.formatDisplayName(
                                contactDisplayNameFormat, uppercaseLastName
                            )
                        )
                    if (!link.isNullOrEmpty()) {
                        val clickableSpan: ClickableSpan = object : ClickableSpan() {
                            override fun onClick(view: View) {
                                App.openContactDetailsActivity(
                                    view.context,
                                    bytesOwnedIdentity,
                                    trustOrigin.mediatorOrGroupOwner.bytesIdentity
                                )
                            }
                        }
                        link.setSpan(
                            clickableSpan,
                            0,
                            link.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        link = SpannableString(context.getString(R.string.text_deleted_contact))
                        val styleSpan = StyleSpan(Typeface.ITALIC)
                        link.setSpan(styleSpan, 0, link.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    return TextUtils.concat(text, link)
                }

                KEYCLOAK -> {
                    return context.getString(
                        R.string.trust_origin_keycloak_type,
                        trustOrigin.keycloakServer,
                        StringUtils.getNiceDateString(context, trustOrigin.timestamp)
                    )
                }

                SERVER_GROUP_V2 -> {
                    val text = context.getString(
                        R.string.trust_origin_group_v2_type,
                        StringUtils.getNiceDateString(context, trustOrigin.timestamp)
                    )
                    val group2 = AppDatabase.getInstance()
                        .group2Dao()[bytesOwnedIdentity, trustOrigin.bytesGroupIdentifier]
                    val link: SpannableString
                    if (group2 == null) {
                        link = SpannableString(context.getString(R.string.text_deleted_group))
                        val styleSpan = StyleSpan(Typeface.ITALIC)
                        link.setSpan(styleSpan, 0, link.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        val groupName = group2.truncatedCustomName
                        link = SpannableString(groupName)
                        val clickableSpan: ClickableSpan = object : ClickableSpan() {
                            override fun onClick(view: View) {
                                App.openGroupV2DetailsActivity(
                                    view.context,
                                    bytesOwnedIdentity,
                                    trustOrigin.bytesGroupIdentifier
                                )
                            }
                        }
                        link.setSpan(
                            clickableSpan,
                            0,
                            link.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    return TextUtils.concat(text, link)
                }

                else -> return context.getString(
                    R.string.trust_origin_unknown_type,
                    StringUtils.getNiceDateString(context, trustOrigin.timestamp)
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (!intent.hasExtra(CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA) || !intent.hasExtra(
                CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA
            )
        ) {
            finish()
            Logger.w("Missing contact identity in intent.")
            return
        }

        val contactBytesIdentity = intent.getByteArrayExtra(
            CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA
        )
        val contactBytesOwnedIdentity = intent.getByteArrayExtra(
            CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA
        )

        if (contactDetailsViewModel.contactAndInvitation != null) {
            contactDetailsViewModel.contactAndInvitation.removeObservers(this)
        }
        if (contactDetailsViewModel.groupDiscussions != null) {
            contactDetailsViewModel.groupDiscussions.removeObservers(this)
        }
        contactDetailsViewModel.setContactBytes(contactBytesOwnedIdentity, contactBytesIdentity)
        contactDetailsViewModel.contactAndInvitation.observe(
            this
        ) { contactAndInvitation: ContactAndInvitation? ->
            this.displayDetails(
                contactAndInvitation
            )
        }

        contactGroupDiscussionsFragment!!.setUnfilteredDiscussions(contactDetailsViewModel.groupDiscussions)
    }


    override fun onClick(view: View) {
        if (contactDetailsViewModel.contactAndInvitation == null) {
            return
        }
        val contactAndInvitation = contactDetailsViewModel.contactAndInvitation.value ?: return
        val contact = contactAndInvitation.contact

        val id = view.id
        if (id == R.id.contact_introduce_button) {
            if (contact.hasChannelOrPreKey()) {
                val contactIntroductionDialogFragment = newInstance(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity,
                    contact.getCustomDisplayName()
                )
                contactIntroductionDialogFragment.show(supportFragmentManager, "dialog")
            } else { // this should never happen as the button should be disabled when no channel exists
                App.toast(
                    R.string.toast_message_established_channel_required_for_introduction,
                    Toast.LENGTH_LONG
                )
            }
        } else if (id == R.id.contact_not_one_to_one_invite_button) {
            if (contactAndInvitation.invitation != null) {
                // this is an accept for an invitation
                if (contactAndInvitation.invitation.categoryId == Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                    try {
                        val obvDialog = contactAndInvitation.invitation.associatedDialog
                        obvDialog.setResponseToAcceptOneToOneInvitation(true)
                        AppSingleton.getEngine().respondToDialog(obvDialog)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                // this is an invite initiation
                try {
                    // if the contact has channels, invite them to one-to-one discussion
                    if (contact.hasChannelOrPreKey()) {
                        AppSingleton.getEngine().startOneToOneInvitationProtocol(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity
                        )
                    }

                    // if the contact is keycloak managed, also start a keycloak invitation protocol
                    if (contact.keycloakManaged) {
                        val jsonIdentityDetails = contact.getIdentityDetails()
                        if (jsonIdentityDetails != null && jsonIdentityDetails.signedUserDetails != null) {
                            AppSingleton.getEngine().addKeycloakContact(
                                contact.bytesOwnedIdentity,
                                contact.bytesContactIdentity,
                                jsonIdentityDetails.signedUserDetails
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else if (id == R.id.contact_not_one_to_one_reject_button) {
            if (contactAndInvitation.invitation != null) {
                if (contactAndInvitation.invitation.categoryId == Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                    // this is a reject invitation
                    try {
                        val obvDialog = contactAndInvitation.invitation.associatedDialog
                        obvDialog.setResponseToAcceptOneToOneInvitation(false)
                        AppSingleton.getEngine().respondToDialog(obvDialog)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (contactAndInvitation.invitation.categoryId == Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY) {
                    // this is an abort
                    try {
                        val obvDialog = contactAndInvitation.invitation.associatedDialog
                        obvDialog.setAbortOneToOneInvitationSent(true)
                        AppSingleton.getEngine().respondToDialog(obvDialog)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else if (id == R.id.contact_discussion_button) {
            App.openOneToOneDiscussionActivity(
                this,
                contact.bytesOwnedIdentity,
                contact.bytesContactIdentity,
                true
            )
        } else if (id == R.id.contact_share_button) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.setType("text/plain")
            val identityUrl = ObvUrlIdentity(
                contact.bytesContactIdentity,
                publishedDetails.formatDisplayName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                    false
                )
            ).urlRepresentation
                ?: return
            intent.putExtra(Intent.EXTRA_TEXT, identityUrl)
            startActivity(Intent.createChooser(intent, getString(R.string.title_sharing_chooser)))
        } else if (id == R.id.contact_no_channel_restart_button) {
            val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_restart_channel_establishment)
                .setMessage(R.string.dialog_message_restart_channel_establishment)
                .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                    try {
                        AppSingleton.getEngine().restartAllOngoingChannelEstablishmentProtocols(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity
                        )
                    } catch (_: Exception) {
                        App.toast(
                            R.string.toast_message_channel_restart_failed,
                            Toast.LENGTH_SHORT
                        )
                        return@setPositiveButton
                    }
                    App.toast(
                        R.string.toast_message_channel_restart_sucessful,
                        Toast.LENGTH_SHORT
                    )
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        } else if (id == R.id.button_update) {
            AppSingleton.getEngine().trustPublishedContactDetails(
                contact.bytesOwnedIdentity,
                contact.bytesContactIdentity
            )
        } else if (id == R.id.button_contact_revoked_forcefully_reblock) {
            if (!AppSingleton.getEngine().reBlockForcefullyUnblockedContact(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity
                )
            ) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT)
            }
        } else if (id == R.id.button_contact_revoked_forcefully_unblock) {
            if (!AppSingleton.getEngine().forcefullyUnblockContact(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity
                )
            ) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT)
            }
        } else if (id == R.id.contact_trust_origin_exchange_digits_button) {
            val builder: Builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            builder.setMessage(
                getString(
                    R.string.dialog_message_exchange_digits,
                    contact.getCustomDisplayName()
                )
            )
                .setTitle(R.string.dialog_title_exchange_digits)
                .setPositiveButton(
                    R.string.button_label_ok
                ) { _: DialogInterface?, _: Int ->
                    try {
                        AppSingleton.getEngine().startTrustEstablishmentProtocol(
                            contact.bytesContactIdentity,
                            contact.getCustomDisplayName(),
                            contact.bytesOwnedIdentity
                        )
                        App.openOneToOneDiscussionActivity(
                            view.context,
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity,
                            true
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
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
                App.showMainActivityTab(this, MainActivity.CONTACTS_TAB)
            }
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (itemId == R.id.action_call) {
            if (contactDetailsViewModel.contactAndInvitation == null || contactDetailsViewModel.contactAndInvitation.value == null) {
                return true
            }
            val contact = contactDetailsViewModel.contactAndInvitation.value!!.contact
            if (!contact.hasChannelOrPreKey()) {
                return true
            }

            App.startWebrtcCall(this, contact.bytesOwnedIdentity, contact.bytesContactIdentity)
            return true
        } else if (itemId == R.id.action_rename) {
            contactDetailsViewModel.contactAndInvitation?.value?.contact?.let { contact ->
                EditNameAndPhotoDialogFragment.newInstance(this, contact)
                    .show(supportFragmentManager, "dialog")
            }
            return true
        } else if (itemId == R.id.action_recreate_channels) {
            val contactAndInvitation = contactDetailsViewModel.contactAndInvitation.value
            if (contactAndInvitation != null) {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_recreate_channels)
                    .setMessage(R.string.dialog_message_recreate_channels)
                    .setPositiveButton(
                        R.string.button_label_ok
                    ) { _: DialogInterface?, _: Int ->
                        try {
                            AppSingleton.getEngine().recreateAllChannels(
                                contactAndInvitation.contact.bytesOwnedIdentity,
                                contactAndInvitation.contact.bytesContactIdentity
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                builder.create().show()
            }
            return true
        } else if (itemId == R.id.action_delete_contact) {
            val contactAndInvitation = contactDetailsViewModel.contactAndInvitation.value
            if (contactAndInvitation != null) {
                App.runThread(PromptToDeleteContactTask(
                    this,
                    contactAndInvitation.contact.bytesOwnedIdentity,
                    contactAndInvitation.contact.bytesContactIdentity
                ) { this.onBackPressed() })
            }
            return true
        } else if (itemId == R.id.action_debug_information) {
            val contactAndInvitation = contactDetailsViewModel.contactAndInvitation.value
            if (contactAndInvitation != null) {
                val contact = contactAndInvitation.contact
                val sb = StringBuilder()
                sb.append(getString(R.string.debug_label_number_of_channels_and_devices))
                    .append("\n")
                sb.append(contact.establishedChannelCount).append("+").append(contact.preKeyCount)
                    .append("/").append(contact.deviceCount).append("\n\n")
                try {
                    val contactIdentity = Identity.of(contact.bytesContactIdentity)
                    sb.append(getString(R.string.debug_label_server)).append(" ")
                    sb.append(contactIdentity.server).append("\n\n")
                } catch (_: DecodingException) {
                }
                sb.append(getString(R.string.debug_label_identity_link)).append("\n")
                sb.append(
                    ObvUrlIdentity(
                        contact.bytesContactIdentity,
                        contact.displayName
                    ).urlRepresentation
                ).append("\n\n")
                sb.append(getString(R.string.debug_label_capabilities)).append("\n")
                sb.append(getString(R.string.bullet)).append(" ").append(
                    getString(
                        R.string.debug_label_capability_continuous_gathering,
                        contact.capabilityWebrtcContinuousIce
                    )
                ).append("\n")
                sb.append(getString(R.string.bullet)).append(" ").append(
                    getString(
                        R.string.debug_label_capability_one_to_one_contacts,
                        contact.capabilityOneToOneContacts
                    )
                ).append("\n")
                sb.append(getString(R.string.bullet)).append(" ").append(
                    getString(
                        R.string.debug_label_capability_groups_v2,
                        contact.capabilityGroupsV2
                    )
                ).append("\n")

                val textView = TextView(this)
                val sixteenDp = (16 * resources.displayMetrics.density).toInt()
                textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp)
                textView.setTextIsSelectable(true)
                textView.autoLinkMask = Linkify.WEB_URLS
                textView.movementMethod = LinkMovementMethod.getInstance()
                textView.text = sb

                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.menu_action_debug_information)
                    .setView(textView)
                    .setPositiveButton(R.string.button_label_ok, null)
                builder.create().show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA: String = "contact_bytes_identity"
        const val CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA: String = "contact_bytes_owned_identity"

        const val FULL_SCREEN_IMAGE_FRAGMENT_TAG: String = "full_screen_image"
    }
}
