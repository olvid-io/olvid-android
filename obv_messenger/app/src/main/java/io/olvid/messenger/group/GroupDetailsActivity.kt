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
package io.olvid.messenger.group

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.anim
import io.olvid.messenger.R.color
import io.olvid.messenger.R.dimen
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.id
import io.olvid.messenger.R.layout
import io.olvid.messenger.R.string
import io.olvid.messenger.R.style
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.EmptyRecyclerView
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.ContactGroupJoinDao.ContactAndTimestamp
import io.olvid.messenger.databases.dao.PendingGroupMemberDao.PendingGroupMemberAndContact
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.tasks.GroupCloningTasks
import io.olvid.messenger.fragments.FullScreenImageFragment
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.fragments.dialog.GroupMemberAdditionDialogFragment
import io.olvid.messenger.fragments.dialog.GroupMemberSuppressionDialogFragment
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment
import io.olvid.messenger.group.EditOwnedGroupDetailsDialogFragment.Companion.newInstance
import io.olvid.messenger.group.GroupDetailsActivity.GroupMembersAdapter.GroupMemberViewHolder
import io.olvid.messenger.group.GroupDetailsActivity.PendingGroupMembersAdapter.PendingGroupMemberViewHolder
import io.olvid.messenger.main.MainActivity

class GroupDetailsActivity : LockableActivity(), OnClickListener, EngineNotificationListener {
    private val groupDetailsViewModel: GroupDetailsViewModel by viewModels()
    private var mainConstraintLayout: ConstraintLayout? = null
    private val groupInitialView: InitialView by lazy { findViewById(id.group_details_initial_view) }
    private var groupNameTextView: TextView? = null
    private var groupOwnerTextView: TextView? = null
    private var groupPersonalNoteTextView: TextView? = null
    private var groupManagementButtons: ViewGroup? = null
    private var acceptUpdateCardView: CardView? = null
    private var secondDetailsCardView: CardView? = null
    private var firstDetailsTextViews: LinearLayout? = null
    private var secondDetailsTextViews: LinearLayout? = null
    private val firstDetailsInitialView: InitialView by lazy { findViewById(id.first_details_initial_view) }
    private val secondDetailsInitialView: InitialView by lazy { findViewById(id.second_details_initial_view) }
    private var firstDetailsTitle: TextView? = null
    private var secondDetailsTitle: TextView? = null
    private var firstDetailsButtons: LinearLayout? = null
    private var latestDetails: JsonGroupDetailsWithVersionAndPhoto? = null
    private lateinit var groupMembersAdapter: GroupMembersAdapter
    private lateinit var pendingGroupMembersAdapter: PendingGroupMembersAdapter
    private var primary700 = 0
    private var registrationNumber: Long? = null
    private val groupMembersHashMap = HashMap<BytesKey, Contact>()
    private var animationsSet = false
    private var groupIsOwned = false
    private var showEditDetails = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        setContentView(layout.activity_group_details)
        findViewById<CoordinatorLayout>(R.id.group_details_coordinatorLayout)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                view.updatePadding(top = insets.top)
                findViewById<ScrollView>(R.id.group_details_scroll_view)?.updatePadding(
                    left = insets.left,
                    right = insets.right
                )
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    updateMargins(bottom = insets.bottom)
                }
                WindowInsetsCompat.CONSUMED
            }
        }
        onBackPressed {
            val fullScreenImageFragment = supportFragmentManager.findFragmentByTag(
                FULL_SCREEN_IMAGE_FRAGMENT_TAG
            )
            if (fullScreenImageFragment != null) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(0, anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            } else {
                if (isTaskRoot) {
                    App.showMainActivityTab(this, MainActivity.GROUPS_TAB)
                }
                finish()
            }
        }
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        mainConstraintLayout = findViewById(id.group_details_main_constraint_layout)
        val discussionButton = findViewById<FloatingActionButton>(id.group_discussion_button)
        discussionButton.setOnClickListener(this)
        groupInitialView.setOnClickListener(this)
        groupNameTextView = findViewById(id.group_name_text_view)
        groupOwnerTextView = findViewById(id.group_owner_text_view)
        groupPersonalNoteTextView = findViewById(id.group_personal_note_text_view)
        groupManagementButtons = findViewById(id.group_management_buttons)
        val addMembersButton = findViewById<Button>(id.group_management_add_members_button)
        addMembersButton.setOnClickListener(this)
        val removeMembersButton = findViewById<Button>(id.group_management_remove_members_button)
        removeMembersButton.setOnClickListener(this)
        val cloneGroupButton = findViewById<Button>(id.clone_to_v2_button)
        cloneGroupButton.setOnClickListener(this)

        // detail cards
        acceptUpdateCardView = findViewById(id.group_accept_update_cardview)
        val updateButton = findViewById<Button>(id.button_update)
        updateButton.setOnClickListener(this)
        firstDetailsTitle = findViewById(id.first_details_title)
        firstDetailsTextViews = findViewById(id.first_details_textviews)
        firstDetailsInitialView.setOnClickListener(this)
        firstDetailsButtons = findViewById(id.first_details_buttons)
        secondDetailsCardView = findViewById(id.second_details_cardview)
        secondDetailsTitle = findViewById(id.second_details_title)
        secondDetailsTextViews = findViewById(id.second_details_textviews)
        secondDetailsInitialView.setOnClickListener(this)
        val publishButton = findViewById<Button>(id.button_publish)
        publishButton.setOnClickListener(this)
        val discardButton = findViewById<Button>(id.button_discard)
        discardButton.setOnClickListener(this)
        val groupMembersEmptyView = findViewById<TextView>(id.group_members_empty_view)
        val groupMembersRecyclerView =
            findViewById<EmptyRecyclerView>(id.group_members_recycler_view)
        val layoutManager = LinearLayoutManager(this)
        groupMembersRecyclerView.layoutManager = layoutManager
        groupMembersAdapter = GroupMembersAdapter()
        groupMembersRecyclerView.setEmptyView(groupMembersEmptyView)
        groupMembersRecyclerView.adapter = groupMembersAdapter
        groupMembersRecyclerView.addItemDecoration(ItemDecorationSimpleDivider(this, 68, 12))
        val pendingGroupMembersEmptyView =
            findViewById<TextView>(id.pending_group_members_empty_view)
        val pendingGroupMembersRecyclerView =
            findViewById<EmptyRecyclerView>(id.pending_group_members_recycler_view)
        val pendingLayoutManager = LinearLayoutManager(this)
        pendingGroupMembersRecyclerView.layoutManager = pendingLayoutManager
        pendingGroupMembersAdapter = PendingGroupMembersAdapter()
        pendingGroupMembersRecyclerView.setEmptyView(pendingGroupMembersEmptyView)
        pendingGroupMembersRecyclerView.adapter = pendingGroupMembersAdapter
        pendingGroupMembersRecyclerView.addItemDecoration(ItemDecorationSimpleDivider(this, 68, 12))
        primary700 = ContextCompat.getColor(this, color.primary700)
        registrationNumber = null
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_GROUP_PHOTO, this)
        AppSingleton.getEngine()
            .addNotificationListener(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS, this)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppSingleton.getEngine()
            .removeNotificationListener(EngineNotifications.NEW_GROUP_PHOTO, this)
        AppSingleton.getEngine()
            .removeNotificationListener(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS, this)
        groupDetailsViewModel.group?.value?.let { group ->
            App.runThread {
                if (group.newPublishedDetails == Group.PUBLISHED_DETAILS_NEW_UNSEEN) {
                    group.newPublishedDetails = Group.PUBLISHED_DETAILS_NEW_SEEN
                    AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid,
                        group.newPublishedDetails
                    )
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
        val bytesGroupOwnerAndUid = intent.getByteArrayExtra(
            BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA
        )
        showEditDetails = intent.getBooleanExtra(EDIT_DETAILS_INTENT_EXTRA, false)
        if (bytesOwnedIdentity == null || bytesGroupOwnerAndUid == null) {
            finish()
            Logger.w("Missing owned identity or group id in intent.")
            return
        }
        groupDetailsViewModel.group?.removeObservers(this)
        groupDetailsViewModel.groupMembers?.removeObservers(this)
        groupDetailsViewModel.pendingGroupMembers?.removeObservers(this)
        groupDetailsViewModel.setGroup(bytesOwnedIdentity, bytesGroupOwnerAndUid)
        groupDetailsViewModel.group?.observe(this) { group: Group? ->
            if (group != null) {
                groupMembersAdapter.showCrown(group.bytesGroupOwnerIdentity)
            }
            displayGroupDetails(group)
        }
        groupDetailsViewModel.groupMembers?.observe(this) { contacts: List<ContactAndTimestamp?>? ->
            displayGroupMembersDetails(contacts)
        }
        groupDetailsViewModel.pendingGroupMembers?.observe(this) { pendingGroupMembers: List<PendingGroupMemberAndContact?>? ->
            displayPendingGroupMembersDetails(pendingGroupMembers)
        }
        App.runThread {
            try {
                AppSingleton.getEngine()
                    .queryGroupOwnerForLatestGroupMembers(bytesGroupOwnerAndUid, bytesOwnedIdentity)
            } catch (e: Exception) {
                // nothing to do, an exception is thrown when you are the owner of the group and this is normal
            }
        }
    }

    private fun displayGroupDetails(group: Group?) {
        if (group == null) {
            finish()
            return
        }
        groupIsOwned = group.bytesGroupOwnerIdentity == null
        invalidateOptionsMenu()
        groupNameTextView!!.text = group.getCustomName()
        groupInitialView.setGroup(group)
        if (groupIsOwned) {
            groupOwnerTextView!!.visibility = View.GONE
            groupManagementButtons!!.visibility = View.VISIBLE
        } else {
            groupOwnerTextView!!.visibility = View.VISIBLE
            groupManagementButtons!!.visibility = View.GONE
            setGroupOwnerText(group.bytesGroupOwnerIdentity)
        }
        if (group.personalNote != null) {
            groupPersonalNoteTextView!!.visibility = View.VISIBLE
            groupPersonalNoteTextView!!.text = group.personalNote
        } else {
            groupPersonalNoteTextView!!.visibility = View.GONE
        }

        try {
            val jsons = AppSingleton.getEngine().getGroupPublishedAndLatestOrTrustedDetails(
                group.bytesOwnedIdentity,
                group.bytesGroupOwnerAndUid
            )
            if (jsons == null || jsons.isEmpty()) {
                return
            }
            val publishedDetails: JsonGroupDetailsWithVersionAndPhoto
            if (jsons.size == 1) {
                acceptUpdateCardView!!.visibility = View.GONE
                secondDetailsCardView!!.visibility = View.GONE
                firstDetailsButtons!!.visibility = View.GONE
                firstDetailsTitle!!.setText(string.label_group_card)
                firstDetailsTitle!!.background =
                    ContextCompat.getDrawable(this, drawable.background_identity_title)
                firstDetailsTextViews!!.removeAllViews()
                publishedDetails = jsons[0]
                latestDetails = publishedDetails
                if (publishedDetails.photoUrl != null) {
                    firstDetailsInitialView.setPhotoUrl(
                        group.bytesGroupOwnerAndUid,
                        publishedDetails.photoUrl
                    )
                } else {
                    firstDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid)
                }
                run {
                    val tv = this.textView
                    tv.text = publishedDetails.groupDetails.name
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    firstDetailsTextViews!!.addView(tv)
                }
                if (publishedDetails.groupDetails.description.isNullOrEmpty().not()) {
                    val tv = textView
                    tv.text = publishedDetails.groupDetails.description
                    firstDetailsTextViews!!.addView(tv)
                }
                App.runThread {
                    if (group.newPublishedDetails != Group.PUBLISHED_DETAILS_NOTHING_NEW) {
                        group.newPublishedDetails = Group.PUBLISHED_DETAILS_NOTHING_NEW
                        AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(
                            group.bytesOwnedIdentity,
                            group.bytesGroupOwnerAndUid,
                            group.newPublishedDetails
                        )
                    }
                    if (group.photoUrl == null && jsons[0].photoUrl != null || group.photoUrl != null && group.photoUrl != jsons[0].photoUrl) {
                        group.photoUrl = jsons[0].photoUrl
                        AppDatabase.getInstance().groupDao().updatePhotoUrl(
                            group.bytesOwnedIdentity,
                            group.bytesGroupOwnerAndUid,
                            group.photoUrl
                        )
                    }
                }
            } else {
                secondDetailsCardView!!.visibility = View.VISIBLE
                firstDetailsTextViews!!.removeAllViews()
                secondDetailsTextViews!!.removeAllViews()
                if (group.bytesGroupOwnerIdentity == null) {
                    firstDetailsTitle!!.setText(string.label_group_card_unpublished_draft)
                    firstDetailsTitle!!.background =
                        ContextCompat.getDrawable(this, drawable.background_identity_title_new)
                    secondDetailsTitle!!.setText(string.label_group_card_published)
                    acceptUpdateCardView!!.visibility = View.GONE
                    firstDetailsButtons!!.visibility = View.VISIBLE
                    publishedDetails = jsons[0]
                    if (publishedDetails.photoUrl != null) {
                        secondDetailsInitialView.setPhotoUrl(
                            group.bytesGroupOwnerAndUid,
                            publishedDetails.photoUrl
                        )
                    } else {
                        secondDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid)
                    }
                    run {
                        val tv = this.textView
                        tv.text = publishedDetails.groupDetails.name
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        secondDetailsTextViews!!.addView(tv)
                    }
                    if (publishedDetails.groupDetails.description.isNullOrEmpty().not()) {
                        val tv = textView
                        tv.text = publishedDetails.groupDetails.description
                        secondDetailsTextViews!!.addView(tv)
                    }
                    latestDetails = jsons[1]
                    if (latestDetails!!.photoUrl != null) {
                        firstDetailsInitialView.setPhotoUrl(
                            group.bytesGroupOwnerAndUid,
                            latestDetails!!.photoUrl
                        )
                    } else {
                        firstDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid)
                    }
                    run {
                        val tv = this.textView
                        tv.text = latestDetails!!.groupDetails.name
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        if (latestDetails!!.groupDetails.name != publishedDetails.groupDetails.name) {
                            tv.setTypeface(tv.typeface, Typeface.BOLD)
                        }
                        firstDetailsTextViews!!.addView(tv)
                    }
                    if (latestDetails!!.groupDetails.description.isNullOrEmpty().not()) {
                        val tv = textView
                        tv.text = latestDetails!!.groupDetails.description
                        if (latestDetails!!.groupDetails.description != publishedDetails.groupDetails.description) {
                            tv.setTypeface(tv.typeface, Typeface.BOLD)
                        }
                        firstDetailsTextViews!!.addView(tv)
                    }
                    App.runThread {
                        if (group.newPublishedDetails != Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW) {
                            group.newPublishedDetails = Group.PUBLISHED_DETAILS_UNPUBLISHED_NEW
                            AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(
                                group.bytesOwnedIdentity,
                                group.bytesGroupOwnerAndUid,
                                group.newPublishedDetails
                            )
                        }
                        if ((group.photoUrl == null && jsons[0].photoUrl != null || group.photoUrl != null) && group.photoUrl != jsons[0].photoUrl) {
                            group.photoUrl = jsons[0].photoUrl
                            AppDatabase.getInstance().groupDao().updatePhotoUrl(
                                group.bytesOwnedIdentity,
                                group.bytesGroupOwnerAndUid,
                                group.photoUrl
                            )
                        }
                    }
                } else {
                    firstDetailsTitle!!.setText(string.label_group_card_published_update)
                    firstDetailsTitle!!.background =
                        ContextCompat.getDrawable(this, drawable.background_identity_title_new)
                    secondDetailsTitle!!.setText(string.label_group_card)
                    acceptUpdateCardView!!.visibility = View.VISIBLE
                    firstDetailsButtons!!.visibility = View.GONE
                    val trustedDetails = jsons[1]
                    if (trustedDetails.photoUrl != null) {
                        secondDetailsInitialView.setPhotoUrl(
                            group.bytesGroupOwnerAndUid,
                            trustedDetails.photoUrl
                        )
                    } else {
                        secondDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid)
                    }
                    run {
                        val tv = this.textView
                        tv.text = trustedDetails.groupDetails.name
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        secondDetailsTextViews!!.addView(tv)
                    }
                    if (trustedDetails.groupDetails.description.isNullOrEmpty().not()) {
                        val tv = textView
                        tv.text = trustedDetails.groupDetails.description
                        secondDetailsTextViews!!.addView(tv)
                    }
                    publishedDetails = jsons[0]
                    if (publishedDetails.photoUrl != null) {
                        firstDetailsInitialView.setPhotoUrl(
                            group.bytesGroupOwnerAndUid,
                            publishedDetails.photoUrl
                        )
                    } else {
                        firstDetailsInitialView.setGroup(group.bytesGroupOwnerAndUid)
                    }
                    run {
                        val tv = this.textView
                        tv.text = publishedDetails.groupDetails.name
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        if (publishedDetails.groupDetails.name != trustedDetails.groupDetails.name) {
                            tv.setTypeface(tv.typeface, Typeface.BOLD)
                        }
                        firstDetailsTextViews!!.addView(tv)
                    }
                    if (publishedDetails.groupDetails.description.isNullOrEmpty().not()) {
                        val tv = textView
                        tv.text = publishedDetails.groupDetails.description
                        if (publishedDetails.groupDetails.description != trustedDetails.groupDetails.description) {
                            tv.setTypeface(tv.typeface, Typeface.BOLD)
                        }
                        firstDetailsTextViews!!.addView(tv)
                    }
                    App.runThread {
                        if (group.newPublishedDetails == Group.PUBLISHED_DETAILS_NOTHING_NEW) {
                            group.newPublishedDetails = Group.PUBLISHED_DETAILS_NEW_SEEN
                            AppDatabase.getInstance().groupDao().updatePublishedDetailsStatus(
                                group.bytesOwnedIdentity,
                                group.bytesGroupOwnerAndUid,
                                group.newPublishedDetails
                            )
                        }
                        if ((group.photoUrl == null && jsons[1].photoUrl != null || group.photoUrl != null) && group.photoUrl != jsons[1].photoUrl) {
                            group.photoUrl = jsons[1].photoUrl
                            AppDatabase.getInstance().groupDao().updatePhotoUrl(
                                group.bytesOwnedIdentity,
                                group.bytesGroupOwnerAndUid,
                                group.photoUrl
                            )
                        }
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
    }

    private val textView: TextView
        get() {
            val tv: TextView = AppCompatTextView(this)
            tv.setTextColor(primary700)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            params.setMargins(
                0,
                0,
                0,
                resources.getDimensionPixelSize(dimen.identity_details_margin)
            )
            tv.layoutParams = params
            return tv
        }

    private fun displayGroupMembersDetails(contacts: List<ContactAndTimestamp?>?) {
        groupMembersAdapter.setContacts(contacts)
        if (contacts != null) {
            groupMembersHashMap.clear()
            for (contact in contacts) {
                groupMembersHashMap[BytesKey(contact!!.contact.bytesContactIdentity)] =
                    contact.contact
            }
            groupDetailsViewModel.group?.value?.bytesGroupOwnerIdentity?.let {
                setGroupOwnerText(it)
            }
        }
    }

    private fun displayPendingGroupMembersDetails(pendingGroupMembers: List<PendingGroupMemberAndContact?>?) {
        pendingGroupMembersAdapter.setPendingGroupMembers(pendingGroupMembers)
    }

    private fun setGroupOwnerText(bytesGroupOwnerIdentity: ByteArray?) {
        val key = BytesKey(bytesGroupOwnerIdentity)
        if (groupMembersHashMap.containsKey(key)) {
            val groupOwner = groupMembersHashMap[key]
            if (groupOwner != null) {
                groupOwnerTextView!!.text =
                    getString(string.text_group_managed_by, groupOwner.getCustomDisplayName())
            }
        }
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.NEW_GROUP_PHOTO -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.NEW_GROUP_PHOTO_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupUid =
                    userInfo[EngineNotifications.NEW_GROUP_PHOTO_BYTES_GROUP_OWNER_AND_UID_KEY] as ByteArray?
                val isTrusted =
                    userInfo[EngineNotifications.NEW_GROUP_PHOTO_IS_TRUSTED_KEY] as Boolean
                val group = groupDetailsViewModel.group?.value
                if (!isTrusted && group != null && group.bytesGroupOwnerAndUid.contentEquals(
                        bytesGroupUid
                    )
                    && group.bytesOwnedIdentity.contentEquals(bytesOwnedIdentity)
                ) {
                    runOnUiThread { displayGroupDetails(group) }
                }
            }

            EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupUid =
                    userInfo[EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_GROUP_OWNER_AND_UID_KEY] as ByteArray?
                val group = groupDetailsViewModel.group?.value
                if (group != null && group.bytesGroupOwnerAndUid.contentEquals(bytesGroupUid)
                    && group.bytesOwnedIdentity.contentEquals(bytesOwnedIdentity)
                ) {
                    runOnUiThread { displayGroupDetails(group) }
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

    override fun onClick(view: View) {
        groupDetailsViewModel.group?.value?.let { group ->
            val id = view.id
            if (id == R.id.group_discussion_button) {
                App.openGroupDiscussionActivity(
                    this,
                    group.bytesOwnedIdentity,
                    group.bytesGroupOwnerAndUid
                )
            } else if (id == R.id.button_update) {
                AppSingleton.getEngine()
                    .trustPublishedGroupDetails(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid
                    )
            } else if (id == R.id.button_publish) {
                AppSingleton.getEngine()
                    .publishLatestGroupDetails(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid
                    )
            } else if (id == R.id.button_discard) {
                AppSingleton.getEngine()
                    .discardLatestGroupDetails(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid
                    )
                displayGroupDetails(group)
            } else if (id == R.id.group_management_add_members_button) {
                val groupMemberAdditionDialogFragment =
                    GroupMemberAdditionDialogFragment.newInstance(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid
                    )
                groupMemberAdditionDialogFragment.show(supportFragmentManager, "dialog")
            } else if (id == R.id.group_management_remove_members_button) {
                val groupMemberSuppressionDialogFragment =
                    GroupMemberSuppressionDialogFragment.newInstance(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid
                    )
                groupMemberSuppressionDialogFragment.show(supportFragmentManager, "dialog")
            } else if (id == R.id.clone_to_v2_button) {
                App.runThread {
                    val clonabilityOutput = GroupCloningTasks.getClonability(group)
                    Handler(Looper.getMainLooper()).post {
                        GroupCloningTasks.initiateGroupCloningOrWarnUser(
                            this,
                            clonabilityOutput
                        )
                    }
                }
            } else if (view is InitialView) {
                val photoUrl = view.photoUrl
                if (photoUrl != null) {
                    val fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl)
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(anim.fade_in, 0)
                        .replace(
                            R.id.overlay,
                            fullScreenImageFragment,
                            FULL_SCREEN_IMAGE_FRAGMENT_TAG
                        )
                        .commit()
                }
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
                    .setCustomAnimations(0, anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (groupIsOwned) {
            menuInflater.inflate(R.menu.menu_group_details_owned, menu)
            val deleteItem = menu.findItem(id.action_disband)
            if (deleteItem != null) {
                val spannableString = SpannableString(deleteItem.title)
                spannableString.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this,
                            color.red
                        )
                    ), 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                deleteItem.title = spannableString
            }
        } else {
            menuInflater.inflate(R.menu.menu_group_details_joined, menu)
            val deleteItem = menu.findItem(id.action_leave_group)
            if (deleteItem != null) {
                val spannableString = SpannableString(deleteItem.title)
                spannableString.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this,
                            color.red
                        )
                    ), 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                deleteItem.title = spannableString
            }
        }
        if (showEditDetails) {
            showEditDetails = false
            menu.performIdentifierAction(id.action_rename, 0)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            id.action_call -> {
                if (groupDetailsViewModel.group == null) {
                    return true
                }
                val group = groupDetailsViewModel.group?.value ?: return true
                val contactAndTimestamps = groupDetailsViewModel.groupMembers?.value
                if (contactAndTimestamps != null) {
                    val bytesContactIdentities = ArrayList<BytesKey>(contactAndTimestamps.size)
                    for (contactAndTimestamp in contactAndTimestamps) {
                        bytesContactIdentities.add(BytesKey(contactAndTimestamp.contact.bytesContactIdentity))
                    }
                    val multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid,
                        bytesContactIdentities
                    )
                    multiCallStartDialogFragment.show(supportFragmentManager, "dialog")
                }
                return true
            }
            id.action_rename -> {
                if (groupDetailsViewModel.group == null) {
                    return true
                }
                val group = groupDetailsViewModel.group?.value ?: return true
                if (group.bytesGroupOwnerIdentity == null) {
                    val dialogFragment = newInstance(
                        this,
                        group.bytesOwnedIdentity,
                        group.bytesGroupOwnerAndUid,
                        latestDetails!!,
                        group.personalNote
                    ) { val reGroup = AppDatabase.getInstance().groupDao()[group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid]
                        runOnUiThread { displayGroupDetails(reGroup) } }
                    dialogFragment.show(supportFragmentManager, "dialog")
                } else {
                    val editNameAndPhotoDialogFragment =
                        EditNameAndPhotoDialogFragment.newInstance(this, group)
                    editNameAndPhotoDialogFragment.show(supportFragmentManager, "dialog")
                }
                return true
            }
            id.action_disband -> {
                if (groupDetailsViewModel.group == null) {
                    return true
                }
                val group = groupDetailsViewModel.group?.value ?: return true
                if (groupIsOwned) {
                    val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                        .setTitle(string.dialog_title_disband_group)
                        .setMessage(
                            getString(
                                string.dialog_message_disband_group,
                                group.getCustomName()
                            )
                        )
                        .setPositiveButton(string.button_label_ok) { _, _ ->
                            if ((groupDetailsViewModel.groupMembers?.value == null || groupDetailsViewModel.groupMembers?.value?.size == 0) &&
                                (groupDetailsViewModel.pendingGroupMembers?.value == null || groupDetailsViewModel.pendingGroupMembers?.value?.size == 0)
                            ) {
                                // group is empty, just delete it
                                try {
                                    AppSingleton.getEngine().disbandGroup(
                                        group.bytesOwnedIdentity,
                                        group.bytesGroupOwnerAndUid
                                    )
                                    App.toast(string.toast_message_group_disbanded, Toast.LENGTH_SHORT)
                                    onBackPressedDispatcher.onBackPressed()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                // group is not empty, second confirmation
                                val confirmationBuilder = SecureAlertDialogBuilder(
                                    this@GroupDetailsActivity,
                                    style.CustomAlertDialog
                                )
                                    .setTitle(string.dialog_title_disband_group)
                                    .setMessage(
                                        getString(
                                            string.dialog_message_disband_non_empty_group_confirmation,
                                            group.getCustomName(),
                                            groupDetailsViewModel.groupMembers?.value?.size ?: 0,
                                            groupDetailsViewModel.pendingGroupMembers?.value?.size ?: 0
                                        )
                                    )
                                    .setPositiveButton(string.button_label_ok) { _, _ ->
                                        // delete group
                                        try {
                                            AppSingleton.getEngine().disbandGroup(
                                                group.bytesOwnedIdentity,
                                                group.bytesGroupOwnerAndUid
                                            )
                                            App.toast(
                                                string.toast_message_group_disbanded,
                                                Toast.LENGTH_SHORT
                                            )
                                            onBackPressedDispatcher.onBackPressed()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    .setNegativeButton(string.button_label_cancel, null)
                                confirmationBuilder.create().show()
                            }
                        }
                        .setNegativeButton(string.button_label_cancel, null)
                    builder.create().show()
                }
                return true
            }
            id.action_leave_group -> {
                if (groupDetailsViewModel.group == null) {
                    return true
                }
                val group = groupDetailsViewModel.group?.value ?: return true
                // you can only leave groups you do not own
                if (group.bytesGroupOwnerIdentity != null) {
                    val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                        .setTitle(string.dialog_title_leave_group)
                        .setMessage(getString(string.dialog_message_leave_group, group.getCustomName()))
                        .setPositiveButton(string.button_label_ok) { _, _ ->
                            try {
                                AppSingleton.getEngine()
                                    .leaveGroup(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid)
                                App.toast(string.toast_message_leaving_group, Toast.LENGTH_SHORT)
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .setNegativeButton(string.button_label_cancel, null)
                    builder.create().show()
                }
                return true
            }
            id.action_clone_group -> {
                val group = groupDetailsViewModel.group?.value ?: return true
                App.runThread {
                    val clonabilityOutput = GroupCloningTasks.getClonability(group)
                    Handler(Looper.getMainLooper()).post {
                        GroupCloningTasks.initiateGroupCloningOrWarnUser(
                            this,
                            clonabilityOutput
                        )
                    }
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    internal inner class GroupMembersAdapter : Adapter<GroupMemberViewHolder>() {
        private val inflater: LayoutInflater = LayoutInflater.from(this@GroupDetailsActivity)
        private var contacts: List<ContactAndTimestamp?>? = null
        private var byteGroupOwnerIdentity: ByteArray? = null

        @SuppressLint("NotifyDataSetChanged")
        fun setContacts(contacts: List<ContactAndTimestamp?>?) {
            this.contacts = contacts
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun showCrown(bytesGroupOwnerIdentity: ByteArray?) {
            byteGroupOwnerIdentity = bytesGroupOwnerIdentity
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupMemberViewHolder {
            val view = inflater.inflate(layout.item_view_group_member, parent, false)
            return GroupMemberViewHolder(view)
        }

        override fun onViewAttachedToWindow(holder: GroupMemberViewHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder.shouldAnimateChannelImageView) {
                val drawable = holder.groupMemberEstablishingChannelImageView.drawable
                if (drawable is Animatable) {
                    (drawable as Animatable).start()
                }
            }
        }

        override fun onBindViewHolder(holder: GroupMemberViewHolder, position: Int) {
            if (contacts != null) {
                val contact = contacts!![position]
                holder.groupMemberNameTextView.text = contact!!.contact.getCustomDisplayName()
                holder.groupMemberJoinTimestampTextView.text = getString(
                    string.text_joined_group,
                    StringUtils.getNiceDateString(this@GroupDetailsActivity, contact.timestamp)
                )
                holder.groupMemberInitialView.setContact(contact.contact)
                if (contact.contact.bytesContactIdentity.contentEquals(byteGroupOwnerIdentity)) {
                    holder.groupMemberOwnerCrownImageView.visibility = View.VISIBLE
                } else {
                    holder.groupMemberOwnerCrownImageView.visibility = View.GONE
                }
                when (contact.contact.newPublishedDetails) {
                    Contact.PUBLISHED_DETAILS_NOTHING_NEW -> holder.newPublishedDetailsGroup.visibility =
                        View.GONE

                    Contact.PUBLISHED_DETAILS_NEW_SEEN -> {
                        holder.newPublishedDetailsGroup.visibility = View.VISIBLE
                        holder.newUnseenPublishedDetailsDot.visibility = View.GONE
                    }

                    Contact.PUBLISHED_DETAILS_NEW_UNSEEN -> {
                        holder.newPublishedDetailsGroup.visibility = View.VISIBLE
                        holder.newUnseenPublishedDetailsDot.visibility = View.VISIBLE
                    }
                }
                if (contact.contact.shouldShowChannelCreationSpinner() && contact.contact.active) {
                    holder.shouldAnimateChannelImageView = true
                    holder.groupMemberEstablishingChannelGroup.visibility = View.VISIBLE
                    val animated =
                        AnimatedVectorDrawableCompat.create(App.getContext(), drawable.dots)
                    if (animated != null) {
                        animated.registerAnimationCallback(object : AnimationCallback() {
                            override fun onAnimationEnd(drawable: Drawable) {
                                Handler(Looper.getMainLooper()).post { animated.start() }
                            }
                        })
                        animated.start()
                    }
                    holder.groupMemberEstablishingChannelImageView.setImageDrawable(animated)
                } else {
                    holder.shouldAnimateChannelImageView = false
                    holder.groupMemberEstablishingChannelGroup.visibility = View.GONE
                    holder.groupMemberEstablishingChannelImageView.setImageDrawable(null)
                }
            }
        }

        override fun getItemCount(): Int {
            return if (contacts != null) {
                contacts!!.size
            } else 0
        }

        internal inner class GroupMemberViewHolder(itemView: View) : ViewHolder(itemView),
            OnClickListener {
            val groupMemberNameTextView: TextView
            val groupMemberJoinTimestampTextView: TextView
            val groupMemberInitialView: InitialView
            val groupMemberEstablishingChannelGroup: ViewGroup
            val groupMemberEstablishingChannelImageView: ImageView
            val groupMemberOwnerCrownImageView: ImageView
            val newPublishedDetailsGroup: ViewGroup
            val newUnseenPublishedDetailsDot: ImageView
            var shouldAnimateChannelImageView: Boolean

            init {
                itemView.setOnClickListener(this)
                shouldAnimateChannelImageView = false
                groupMemberNameTextView = itemView.findViewById(id.group_member_name_text_view)
                groupMemberJoinTimestampTextView =
                    itemView.findViewById(id.group_member_join_timestamp_text_view)
                groupMemberInitialView = itemView.findViewById(id.group_member_initial_view)
                groupMemberOwnerCrownImageView =
                    itemView.findViewById(id.group_member_owner_crown_image_view)
                groupMemberEstablishingChannelGroup =
                    itemView.findViewById(id.group_member_establishing_channel_group)
                groupMemberEstablishingChannelImageView =
                    itemView.findViewById(id.group_member_establishing_channel_image_view)
                newPublishedDetailsGroup = itemView.findViewById(id.new_published_details_group)
                newUnseenPublishedDetailsDot =
                    itemView.findViewById(id.new_unseen_published_details_dot)
            }

            override fun onClick(view: View) {
                val position = this.layoutPosition
                groupMemberClicked(contacts!![position]!!.contact)
            }
        }
    }

    private fun groupMemberClicked(contact: Contact) {
        App.openContactDetailsActivity(
            this,
            contact.bytesOwnedIdentity,
            contact.bytesContactIdentity
        )
    }

    internal inner class PendingGroupMembersAdapter : Adapter<PendingGroupMemberViewHolder>() {
        private val inflater: LayoutInflater = LayoutInflater.from(this@GroupDetailsActivity)
        private var pendingGroupMembers: List<PendingGroupMemberAndContact?>? = null

        @SuppressLint("NotifyDataSetChanged")
        fun setPendingGroupMembers(pendingGroupMembers: List<PendingGroupMemberAndContact?>?) {
            this.pendingGroupMembers = pendingGroupMembers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PendingGroupMemberViewHolder {
            val view = inflater.inflate(layout.item_view_pending_group_member, parent, false)
            return PendingGroupMemberViewHolder(view)
        }

        override fun onBindViewHolder(holder: PendingGroupMemberViewHolder, position: Int) {
            if (pendingGroupMembers != null) {
                val pendingGroupMember = pendingGroupMembers!![position]
                if (pendingGroupMember!!.contact == null) {
                    holder.pendingGroupMemberNameTextView.text =
                        pendingGroupMember.pendingGroupMember.displayName
                    holder.pendingGroupMemberInitialView.setInitial(
                        ByteArray(0), StringUtils.getInitial(
                            pendingGroupMember.pendingGroupMember.displayName
                        )
                    )
                } else {
                    holder.pendingGroupMemberNameTextView.text =
                        pendingGroupMember.contact.getCustomDisplayName()
                    holder.pendingGroupMemberInitialView.setContact(pendingGroupMember.contact)
                }
                holder.invitationDeclinedTextView.visibility =
                    if (pendingGroupMember.pendingGroupMember.declined) View.VISIBLE else View.GONE
            }
        }

        override fun getItemCount(): Int {
            return if (pendingGroupMembers != null) {
                pendingGroupMembers!!.size
            } else 0
        }

        internal inner class PendingGroupMemberViewHolder(itemView: View) : ViewHolder(itemView),
            OnClickListener {
            val pendingGroupMemberNameTextView: TextView
            val pendingGroupMemberInitialView: InitialView
            val invitationDeclinedTextView: TextView

            init {
                itemView.setOnClickListener(this)
                pendingGroupMemberNameTextView =
                    itemView.findViewById(id.pending_group_member_name_text_view)
                pendingGroupMemberInitialView =
                    itemView.findViewById(id.pending_group_member_initial_view)
                invitationDeclinedTextView = itemView.findViewById(id.invitation_declined_textview)
            }

            override fun onClick(view: View) {
                if (!groupIsOwned || groupDetailsViewModel.group?.value == null) {
                    return
                }
                val position = this.layoutPosition
                val pendingGroupMemberAndContact = pendingGroupMembers!![position]
                val builder = SecureAlertDialogBuilder(view.context, style.CustomAlertDialog)
                    .setTitle(string.dialog_title_group_reinvite)
                    .setMessage(
                        getString(
                            string.dialog_message_group_reinvite,
                            pendingGroupMemberAndContact!!.pendingGroupMember.displayName,
                            groupDetailsViewModel.group?.value?.getCustomName()
                        )
                    )
                    .setPositiveButton(string.button_label_ok) { _, _ ->
                        try {
                            AppSingleton.getEngine().reinvitePendingToGroup(
                                pendingGroupMemberAndContact.pendingGroupMember.bytesOwnedIdentity,
                                pendingGroupMemberAndContact.pendingGroupMember.bytesGroupOwnerAndUid,
                                pendingGroupMemberAndContact.pendingGroupMember.bytesIdentity
                            )
                            App.toast(string.toast_message_invite_sent, Toast.LENGTH_SHORT)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .setNegativeButton(string.button_label_cancel, null)
                builder.create().show()
            }
        }
    }

    companion object {
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity"
        const val BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA = "group_id"
        const val EDIT_DETAILS_INTENT_EXTRA = "edit_details"
        const val FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image"
    }
}