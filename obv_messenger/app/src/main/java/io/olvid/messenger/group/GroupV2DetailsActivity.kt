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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
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
import android.widget.Button
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.Callback
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.containers.GroupV2.Identifier
import io.olvid.engine.encoder.DecodingException
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvGroupV2
import io.olvid.engine.engine.types.identities.ObvGroupV2.ObvGroupV2DetailsAndPhotos
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
import io.olvid.messenger.databases.dao.Group2MemberDao.Group2MemberOrPending
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.tasks.GroupCloningTasks
import io.olvid.messenger.fragments.FullScreenImageFragment
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment
import io.olvid.messenger.group.EditOwnedGroupDetailsDialogFragment.Companion.newInstanceV2
import io.olvid.messenger.group.GroupTypeModel.GroupType.PRIVATE
import io.olvid.messenger.group.GroupTypeModel.GroupType.SIMPLE
import io.olvid.messenger.group.GroupV2DetailsActivity.GroupMembersAdapter.GroupMemberViewHolder
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.settings.SettingsActivity

class GroupV2DetailsActivity : LockableActivity(), EngineNotificationListener, OnClickListener {
    private val groupDetailsViewModel: GroupV2DetailsViewModel by viewModels()
    private var mainConstraintLayout: ConstraintLayout? = null
    private var groupMembersListLinearLayout: LinearLayout? = null
    private var updateInProgressCard: CardView? = null
    private var updateInProgressTitleTextView: TextView? = null
    private var groupAdminImageView: ImageView? = null
    private val groupInitialView: InitialView by lazy { findViewById(id.initial_view) }
    private var groupNameTextView: TextView? = null
    private var groupPersonalNoteTextView: TextView? = null
    private var acceptUpdateGroup: View? = null
    private var secondDetailsCardView: CardView? = null
    private val firstDetailsInitialView: InitialView by lazy { findViewById(id.first_details_initial_view) }
    private val firstDetailsTextViews: LinearLayout by lazy { findViewById(id.first_details_textviews) }
    private var firstDetailsTitle: TextView? = null
    private val secondDetailsInitialView: InitialView by lazy { findViewById(id.second_details_initial_view) }
    private var secondDetailsTextViews: LinearLayout? = null
    private val editGroupButton: Button by lazy { findViewById(id.button_edit_group) }
    private val saveButton: Button by lazy { findViewById(id.button_save) }
    private val discardButton: Button by lazy { findViewById(id.button_discard) }
    private val addGroupMembersButton: Button by lazy { findViewById(id.button_add_members) }
    private val discussionButton: FloatingActionButton by lazy { findViewById(id.group_discussion_button) }
    private var publishingOpacityMask: View? = null
    private var primary700 = 0
    private var showEditDetails = false
    private var animationsSet = false
    private var groupAdmin = false
    private var keycloakGroup = false
    private var editingMembers = false
    private var updateInProgress = Group2.UPDATE_NONE
    private var publishedDetails: JsonGroupDetails? = null
    private var publishedPhotoUrl: String? = null
    private lateinit var groupMembersAdapter : GroupMembersAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_group_v2_details)
        onBackPressed {
            val fullScreenImageFragment = supportFragmentManager.findFragmentByTag(
                FULL_SCREEN_IMAGE_FRAGMENT_TAG
            )
            if (fullScreenImageFragment != null) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(0, anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            } else if (editingMembers) {
                if (!groupDetailsViewModel.discardGroupEdits()) {
                    finish()
                }
            } else {
                finish()
            }
        }
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        mainConstraintLayout = findViewById(id.group_details_main_constraint_layout)
        groupMembersListLinearLayout = findViewById(id.group_members_list)
        updateInProgressCard = findViewById(id.update_in_progress_card)
        updateInProgressTitleTextView = findViewById(id.update_in_progress_title)
        discussionButton.setOnClickListener(this)
        publishingOpacityMask = findViewById(id.publishing_opacity_mask)
        groupAdminImageView = findViewById(id.admin_indicator_image_view)
        groupInitialView.setOnClickListener(this)
        groupNameTextView = findViewById(id.group_name_text_view)
        groupPersonalNoteTextView = findViewById(id.group_personal_note_text_view)

        // detail cards
        acceptUpdateGroup = findViewById(id.update_details_group)
        val updateButton = findViewById<Button>(id.button_update)
        updateButton.setOnClickListener(this)
        firstDetailsTitle = findViewById(id.first_details_title)
        firstDetailsInitialView.setOnClickListener(this)
        secondDetailsCardView = findViewById(id.second_details_cardview)
        secondDetailsTextViews = findViewById(id.second_details_textviews)
        secondDetailsInitialView.setOnClickListener(this)
        editGroupButton.setOnClickListener(this)
        discardButton.setOnClickListener(this)
        saveButton.setOnClickListener(this)
        addGroupMembersButton.setOnClickListener(this)
        val groupMembersEmptyView = findViewById<TextView>(id.group_members_empty_view)
        val groupMembersRecyclerView =
            findViewById<EmptyRecyclerView>(id.group_members_recycler_view)
        val layoutManager = LinearLayoutManager(this)
        groupMembersRecyclerView.layoutManager = layoutManager
        groupMembersRecyclerView.setEmptyView(groupMembersEmptyView)
        groupMembersAdapter = GroupMembersAdapter()
        groupMembersRecyclerView.adapter = groupMembersAdapter
        groupMembersRecyclerView.addItemDecoration(ItemDecorationSimpleDivider(this, 68, 12))
        ItemTouchHelper(SwipeCallback()).attachToRecyclerView(groupMembersRecyclerView)
        primary700 = ContextCompat.getColor(this, color.primary700)
        registrationNumber = null
        for (notification in NOTIFICATIONS_TO_LISTEN_TO) {
            AppSingleton.getEngine().addNotificationListener(notification, this)
        }
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        for (notification in NOTIFICATIONS_TO_LISTEN_TO) {
            AppSingleton.getEngine().removeNotificationListener(notification, this)
        }
        val group = groupDetailsViewModel.group.value
        App.runThread {
            if (group != null && group.newPublishedDetails == Group2.PUBLISHED_DETAILS_NEW_UNSEEN) {
                group.newPublishedDetails = Group2.PUBLISHED_DETAILS_NEW_SEEN
                AppDatabase.getInstance().group2Dao().updateNewPublishedDetails(
                    group.bytesOwnedIdentity,
                    group.bytesGroupIdentifier,
                    group.newPublishedDetails
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
        val bytesGroupIdentifier = intent.getByteArrayExtra(BYTES_GROUP_IDENTIFIER_INTENT_EXTRA)
        showEditDetails = intent.getBooleanExtra(EDIT_DETAILS_INTENT_EXTRA, false)
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            finish()
            Logger.w("GroupV2DetailsActivity Missing owned identity or group identifier in intent.")
            return
        }
        groupDetailsViewModel.setGroup(bytesOwnedIdentity, bytesGroupIdentifier)
        groupDetailsViewModel.group.observe(this) { group: Group2? -> displayGroup(group) }
        groupDetailsViewModel.groupMembers.observe(this, groupMembersAdapter)
        groupDetailsViewModel.isEditingGroupMembersLiveData().observe(this) { editingMembers: Boolean? ->
            editingMembersChanged(
                editingMembers
            )
        }
        fetchEngineGroupCards()
    }

    private fun displayGroup(group: Group2?) {
        if (group == null) {
            finish()
            return
        }
        // everytime the group is updated refresh its initial group type from the engine
        groupDetailsViewModel.initialGroupType = try {
            AppSingleton.getJsonObjectMapper().readValue(
                AppSingleton.getEngine().getGroupV2JsonType(group.bytesOwnedIdentity, group.bytesGroupIdentifier),
                JsonGroupType::class.java
            ).toGroupCreationModel()
        } catch (_: Exception) {
            null
        }

        if (updateInProgress != group.updateInProgress) {
            updateInProgress = group.updateInProgress
            if (updateInProgress != Group2.UPDATE_NONE) {
                if (editingMembers) {
                    publishingOpacityMask!!.visibility = View.VISIBLE
                } else {
                    publishingOpacityMask!!.visibility = View.GONE
                }
                editGroupButton.isEnabled = false
                saveButton.isEnabled = false
                discardButton.isEnabled = false
                addGroupMembersButton.isEnabled = false
                updateInProgressCard!!.visibility = View.VISIBLE
                if (updateInProgress == Group2.UPDATE_SYNCING) {
                    updateInProgressTitleTextView!!.setText(string.label_group_update_in_progress_title)
                } else {
                    updateInProgressTitleTextView!!.setText(string.label_group_update_in_progress_title_for_creation)
                }
            } else {
                publishingOpacityMask!!.visibility = View.GONE
                editGroupButton.isEnabled = true
                saveButton.isEnabled = true
                discardButton.isEnabled = true
                addGroupMembersButton.isEnabled = true
                updateInProgressCard!!.visibility = View.GONE
            }
        }
        if (groupAdmin != group.ownPermissionAdmin || keycloakGroup != group.keycloakManaged) {
            groupAdmin = group.ownPermissionAdmin
            keycloakGroup = group.keycloakManaged
            invalidateOptionsMenu()
        }
        groupInitialView.setGroup2(group)
        val name = group.truncatedCustomName
        if (name.isEmpty()) {
            val spannableString = SpannableString(getString(string.text_unnamed_group))
            spannableString.setSpan(
                StyleSpan(Typeface.ITALIC),
                0,
                spannableString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            groupNameTextView!!.text = spannableString
        } else {
            groupNameTextView!!.text = name
        }
        if (group.personalNote != null) {
            groupPersonalNoteTextView!!.visibility = View.VISIBLE
            groupPersonalNoteTextView!!.text = group.personalNote
        } else {
            groupPersonalNoteTextView!!.visibility = View.GONE
        }
        if (groupAdmin) {
            groupAdminImageView!!.visibility = View.VISIBLE
            if (editingMembers) {
                editGroupButton.visibility = View.GONE
                addGroupMembersButton.visibility = View.VISIBLE
                saveButton.visibility = View.VISIBLE
                discardButton.visibility = View.VISIBLE
            } else {
                editGroupButton.visibility = View.VISIBLE
                addGroupMembersButton.visibility = View.GONE
                saveButton.visibility = View.GONE
                discardButton.visibility = View.GONE
            }
        } else {
            groupAdminImageView!!.visibility = View.GONE
            if (editingMembers) {
                // if we lose admin while editing, discard all changes
                onClick(discardButton)
            }
            editGroupButton.visibility = View.GONE
            addGroupMembersButton.visibility = View.GONE
            saveButton.visibility = View.GONE
            discardButton.visibility = View.GONE
        }
    }

    private fun editingMembersChanged(editingMembers: Boolean?) {
        this.editingMembers = editingMembers != null && editingMembers
        if (this.editingMembers) {
            discussionButton.hide()
            if (updateInProgress != Group2.UPDATE_NONE) {
                publishingOpacityMask!!.visibility = View.VISIBLE
            } else {
                publishingOpacityMask!!.visibility = View.GONE
            }
            editGroupButton.visibility = View.GONE
            addGroupMembersButton.visibility = View.VISIBLE
            saveButton.visibility = View.VISIBLE
            discardButton.visibility = View.VISIBLE
        } else {
            discussionButton.show()
            publishingOpacityMask!!.visibility = View.GONE
            if (groupAdmin) {
                editGroupButton.visibility = View.VISIBLE
            } else {
                editGroupButton.visibility = View.GONE
            }
            addGroupMembersButton.visibility = View.GONE
            saveButton.visibility = View.GONE
            discardButton.visibility = View.GONE
        }
    }

    private fun fetchEngineGroupCards() {
        val bytesOwnedIdentity = groupDetailsViewModel.bytesOwnedIdentity
        val bytesGroupIdentifier = groupDetailsViewModel.bytesGroupIdentifier
        App.runThread {
            val detailsAndPhotos: ObvGroupV2DetailsAndPhotos? = AppSingleton.getEngine()
                .getGroupV2DetailsAndPhotos(bytesOwnedIdentity, bytesGroupIdentifier)
            if (detailsAndPhotos != null) {
                runOnUiThread { displayEngineGroupCards(detailsAndPhotos) }
            }
        }
    }

    private fun displayEngineGroupCards(detailsAndPhotos: ObvGroupV2DetailsAndPhotos) {
        val bytesGroupIdentifier = groupDetailsViewModel.bytesGroupIdentifier
        if (detailsAndPhotos.serializedPublishedDetails == null) {
            acceptUpdateGroup!!.visibility = View.GONE
            secondDetailsCardView!!.visibility = View.GONE
            firstDetailsTitle!!.setText(string.label_group_card)
            firstDetailsTitle!!.background =
                ContextCompat.getDrawable(this, drawable.background_identity_title)
            firstDetailsTextViews.removeAllViews()
            try {
                val groupDetails = AppSingleton.getJsonObjectMapper().readValue(
                    detailsAndPhotos.serializedGroupDetails,
                    JsonGroupDetails::class.java
                )
                publishedDetails = groupDetails
                publishedPhotoUrl = detailsAndPhotos.nullIfEmptyPhotoUrl
                if (publishedPhotoUrl != null) {
                    firstDetailsInitialView.setPhotoUrl(bytesGroupIdentifier, publishedPhotoUrl)
                } else {
                    firstDetailsInitialView.setGroup(bytesGroupIdentifier)
                }
                run {
                    val tv = this.textView
                    if (groupDetails.name.isNullOrEmpty().not()) {
                        tv.text = groupDetails.name
                    } else {
                        val spannableString = SpannableString(getString(string.text_unnamed_group))
                        spannableString.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            0,
                            spannableString.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        tv.text = spannableString
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    firstDetailsTextViews.addView(tv)
                }
                if (groupDetails.description.isNullOrEmpty().not()) {
                    val tv = textView
                    tv.text = groupDetails.description
                    firstDetailsTextViews.addView(tv)
                }
            } catch (e: Exception) {
                firstDetailsTextViews.removeAllViews()
                firstDetailsInitialView.setUnknown()
                val tv = textView
                val spannableString =
                    SpannableString(getString(string.text_unable_to_display_contact_name))
                spannableString.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    spannableString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tv.text = spannableString
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                firstDetailsTextViews.addView(tv)
            }
        } else {
            acceptUpdateGroup!!.visibility = View.VISIBLE
            secondDetailsCardView!!.visibility = View.VISIBLE
            firstDetailsTitle!!.setText(string.label_group_card_published_update)
            firstDetailsTitle!!.background =
                ContextCompat.getDrawable(this, drawable.background_identity_title_new)
            firstDetailsTextViews.removeAllViews()
            secondDetailsTextViews!!.removeAllViews()
            try {
                val publishedGroupDetails = AppSingleton.getJsonObjectMapper().readValue(
                    detailsAndPhotos.serializedPublishedDetails,
                    JsonGroupDetails::class.java
                )
                publishedDetails = publishedGroupDetails
                publishedPhotoUrl = detailsAndPhotos.nullIfEmptyPublishedPhotoUrl
                if (publishedPhotoUrl != null) {
                    firstDetailsInitialView.setPhotoUrl(bytesGroupIdentifier, publishedPhotoUrl)
                } else {
                    firstDetailsInitialView.setGroup(bytesGroupIdentifier)
                }
                run {
                    val tv = this.textView
                    if (publishedGroupDetails.name.isNullOrEmpty().not()) {
                        tv.text = publishedGroupDetails.name
                    } else {
                        val spannableString = SpannableString(getString(string.text_unnamed_group))
                        spannableString.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            0,
                            spannableString.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        tv.text = spannableString
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    firstDetailsTextViews.addView(tv)
                }
                if (publishedGroupDetails.description.isNullOrEmpty().not()) {
                    val tv = textView
                    tv.text = publishedGroupDetails.description
                    firstDetailsTextViews.addView(tv)
                }
            } catch (e: Exception) {
                firstDetailsTextViews.removeAllViews()
                firstDetailsInitialView.setUnknown()
                val tv = textView
                val spannableString =
                    SpannableString(getString(string.text_unable_to_display_contact_name))
                spannableString.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    spannableString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tv.text = spannableString
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                firstDetailsTextViews.addView(tv)
            }
            try {
                val groupDetails = AppSingleton.getJsonObjectMapper().readValue(
                    detailsAndPhotos.serializedGroupDetails,
                    JsonGroupDetails::class.java
                )
                if (detailsAndPhotos.nullIfEmptyPhotoUrl != null) {
                    secondDetailsInitialView.setPhotoUrl(
                        bytesGroupIdentifier,
                        detailsAndPhotos.nullIfEmptyPhotoUrl
                    )
                } else {
                    secondDetailsInitialView.setGroup(bytesGroupIdentifier)
                }
                run {
                    val tv = this.textView
                    if (groupDetails.name != null && groupDetails.name.isNotEmpty()) {
                        tv.text = groupDetails.name
                    } else {
                        val spannableString = SpannableString(getString(string.text_unnamed_group))
                        spannableString.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            0,
                            spannableString.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        tv.text = spannableString
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    secondDetailsTextViews!!.addView(tv)
                }
                if (groupDetails.description != null && groupDetails.description.isNotEmpty()) {
                    val tv = textView
                    tv.text = groupDetails.description
                    secondDetailsTextViews!!.addView(tv)
                }
            } catch (e: Exception) {
                secondDetailsTextViews!!.removeAllViews()
                secondDetailsInitialView.setUnknown()
                val tv = textView
                val spannableString =
                    SpannableString(getString(string.text_unable_to_display_contact_name))
                spannableString.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    spannableString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tv.text = spannableString
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                secondDetailsTextViews!!.addView(tv)
            }
        }
        if (!animationsSet) {
            Handler(Looper.getMainLooper()).postDelayed({
                val layoutTransition = LayoutTransition()
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
                mainConstraintLayout!!.layoutTransition = layoutTransition
                val layoutTransitionMembers = LayoutTransition()
                groupMembersListLinearLayout!!.layoutTransition = layoutTransitionMembers
            }, 100)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (groupAdmin) {
            menuInflater.inflate(R.menu.menu_group_details_owned_v2, menu)
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
        } else if (keycloakGroup) {
            menuInflater.inflate(R.menu.menu_group_details_keycloak, menu)
        } else {
            menuInflater.inflate(R.menu.menu_group_details_joined_v2, menu)
        }
        val leaveItem = menu.findItem(id.action_leave_group)
        if (leaveItem != null) {
            val spannableString = SpannableString(leaveItem.title)
            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, color.red)),
                0,
                spannableString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            leaveItem.title = spannableString
        }
        if (groupAdmin && showEditDetails) {
            showEditDetails = false
            menu.performIdentifierAction(id.action_rename, 0)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        } else if (itemId == id.action_call) {
            val group = groupDetailsViewModel.group.value ?: return true
            App.runThread {
                val contacts = AppDatabase.getInstance().group2MemberDao()
                    .getGroupMemberContactsSync(
                        group.bytesOwnedIdentity,
                        group.bytesGroupIdentifier
                    )
                if (contacts != null) {
                    val bytesContactIdentities = ArrayList<BytesKey>()
                    for (contact in contacts) {
                        bytesContactIdentities.add(BytesKey(contact.bytesContactIdentity))
                    }
                    val multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(
                        group.bytesOwnedIdentity,
                        group.bytesGroupIdentifier,
                        bytesContactIdentities
                    )
                    Handler(Looper.getMainLooper()).post {
                        multiCallStartDialogFragment.show(
                            supportFragmentManager, "dialog"
                        )
                    }
                }
            }
            return true
        } else if (itemId == id.action_rename) {
            val group = groupDetailsViewModel.group.value ?: return true
            if (group.ownPermissionAdmin) {
                val initialGroupType = groupDetailsViewModel.initialGroupType ?: groupDetailsViewModel.inferGroupType(groupDetailsViewModel.groupMembers.value ?: emptyList())
                groupDetailsViewModel.setGroupType(initialGroupType.clone())

                val dialogFragment = newInstanceV2(
                    this,
                    group.bytesOwnedIdentity,
                    group.bytesGroupIdentifier,
                    publishedDetails!!,
                    publishedPhotoUrl,
                    group.personalNote
                ) { fetchEngineGroupCards() }
                dialogFragment.show(supportFragmentManager, "dialog")
            } else {
                val editNameAndPhotoDialogFragment =
                    EditNameAndPhotoDialogFragment.newInstance(this, group)
                editNameAndPhotoDialogFragment.show(supportFragmentManager, "dialog")
            }
            return true
        } else if (itemId == id.action_disband) {
            val group = groupDetailsViewModel.group.value ?: return true
            if (group.ownPermissionAdmin) {
                val groupName: String = group.getCustomName().ifEmpty {
                    getString(string.text_unnamed_group)
                }
                val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                    .setTitle(string.dialog_title_disband_group)
                    .setMessage(getString(string.dialog_message_disband_group, groupName))
                    .setPositiveButton(string.button_label_ok) { _, _ ->
                        if (groupDetailsViewModel.groupMembers.value .isNullOrEmpty()) {
                            // group is empty, just delete it
                            try {
                                AppSingleton.getEngine().disbandGroupV2(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupIdentifier
                                )
                                App.toast(string.toast_message_group_disbanded, Toast.LENGTH_SHORT)
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            // group is not empty, second confirmation
                            val confirmationBuilder =
                                SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                                    .setTitle(string.dialog_title_disband_group)
                                    .setMessage(
                                        getString(
                                            string.dialog_message_disband_non_empty_group_v2_confirmation,
                                            groupName,
                                            groupDetailsViewModel.groupMembers.value?.size ?: 0
                                        )
                                    )
                                    .setPositiveButton(string.button_label_ok) { _, _ ->
                                        // disband group
                                        try {
                                            AppSingleton.getEngine().disbandGroupV2(
                                                group.bytesOwnedIdentity,
                                                group.bytesGroupIdentifier
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
        } else if (itemId == id.action_leave_group) {
            val group = groupDetailsViewModel.group.value ?: return true
            App.runThread {
                if (group.ownPermissionAdmin && group.updateInProgress != Group2.UPDATE_SYNCING) {
                    // check you are not the only admin (among members, pending members could decline)
                    // only do this check if group update is not in progress: sometimes you can get locked in update and there is no way to leave/disband the group
                    var otherAdmin = false
                    val group2Members = AppDatabase.getInstance().group2MemberDao()
                        .getGroupMembers(group.bytesOwnedIdentity, group.bytesGroupIdentifier)
                    for (group2Member in group2Members) {
                        if (group2Member.permissionAdmin) {
                            otherAdmin = true
                            break
                        }
                    }
                    if (!otherAdmin) {
                        // you are the only admin --> cannot leave the group
                        // check if there is a pending admin to change the error message
                        var pendingAdmin = false
                        val group2PendingMembers =
                            AppDatabase.getInstance().group2PendingMemberDao()
                                .getGroupPendingMembers(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupIdentifier
                                )
                        for (group2Member in group2PendingMembers) {
                            if (group2Member.permissionAdmin) {
                                pendingAdmin = true
                                break
                            }
                        }
                        val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                            .setTitle(string.dialog_title_unable_to_leave_group)
                            .setPositiveButton(string.button_label_ok, null)
                        if (pendingAdmin) {
                            builder.setMessage(string.dialog_message_unable_to_leave_group_pending_admin)
                        } else {
                            builder.setMessage(string.dialog_message_unable_to_leave_group)
                        }
                        runOnUiThread { builder.create().show() }
                        return@runThread
                    }
                }
                val groupName: String = group.getCustomName().ifEmpty {
                    getString(string.text_unnamed_group)
                }
                val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                    .setTitle(string.dialog_title_leave_group)
                    .setMessage(getString(string.dialog_message_leave_group, groupName))
                    .setPositiveButton(string.button_label_ok) { _, _ ->
                        try {
                            AppSingleton.getEngine()
                                .leaveGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier)
                            App.toast(string.toast_message_leaving_group_v2, Toast.LENGTH_SHORT)
                            onBackPressedDispatcher.onBackPressed()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .setNegativeButton(string.button_label_cancel, null)
                runOnUiThread { builder.create().show() }
            }
            return true
        } else if (itemId == id.action_sync_group) {
            val group = groupDetailsViewModel.group.value ?: return true
            try {
                if (group.keycloakManaged) {
                    KeycloakManager.forceSyncManagedIdentity(group.bytesOwnedIdentity)
                } else {
                    AppSingleton.getEngine()
                        .reDownloadGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier)
                }
            } catch (ignored: Exception) {
            }
            return true
        } else if (itemId == id.action_clone_group) {
            val group = groupDetailsViewModel.group.value ?: return true
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
        } else if (itemId == id.action_debug_information) {
            val group2 = groupDetailsViewModel.group.value
            if (group2 != null) {
                val sb = StringBuilder()
                sb.append(getString(string.debug_label_number_of_members_and_invited)).append(" ")
                sb.append(groupDetailsViewModel.membersCount).append("/")
                    .append(groupDetailsViewModel.membersAndPendingCount).append("\n")
                try {
                    val version = AppSingleton.getEngine()
                        .getGroupV2Version(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier)
                    sb.append(getString(string.debug_label_group_version)).append(" ")
                        .append(version).append("\n\n")
                } catch (ignored: Exception) {
                }
                try {
                    val groupIdentifier = Identifier.of(group2.bytesGroupIdentifier)
                    when (groupIdentifier.category) {
                        Identifier.CATEGORY_SERVER -> {
                            sb.append(getString(string.debug_label_group_type)).append(" ").append(
                                getString(
                                    string.debug_label_group_type_user_managed
                                )
                            ).append("\n")
                        }

                        Identifier.CATEGORY_KEYCLOAK -> {
                            sb.append(getString(string.debug_label_group_type)).append(" ").append(
                                getString(
                                    string.debug_label_group_type_keycloak
                                )
                            ).append("\n")
                        }
                    }
                    sb.append(getString(string.debug_label_server)).append(" ")
                    sb.append(groupIdentifier.serverUrl).append("\n\n")
                } catch (ignored: DecodingException) {
                }
                val textView = TextView(this)
                val sixteenDp = (16 * resources.displayMetrics.density).toInt()
                textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp)
                textView.setTextIsSelectable(true)
                textView.autoLinkMask = Linkify.WEB_URLS
                textView.movementMethod = LinkMovementMethod.getInstance()
                textView.text = sb
                val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                    .setTitle(string.menu_action_debug_information)
                    .setView(textView)
                    .setPositiveButton(string.button_label_ok, null)
                builder.create().show()
            }
        } else if (itemId == id.action_invite_all_members) {
            val group = groupDetailsViewModel.group.value ?: return true
            if (group.updateInProgress == Group2.UPDATE_NONE) {
                groupDetailsViewModel.groupMembers.value?.mapNotNull { group2MemberOrPending ->
                    group2MemberOrPending.contact?.let { contact ->
                        if ((contact.establishedChannelCount > 0 || contact.keycloakManaged) && contact.oneToOne.not()) {
                            contact
                        } else {
                            null
                        }
                    }
                } ?. let { contacts ->
                    if (contacts.isEmpty()) {
                        App.toast(string.toast_message_no_member_can_be_invited, Toast.LENGTH_SHORT)
                    } else {
                        val builder = SecureAlertDialogBuilder(this, style.CustomAlertDialog)
                            .setTitle(string.dialog_title_invite_all_group_members)
                            .setMessage(resources.getQuantityString(R.plurals.dialog_message_invite_all_group_members, contacts.size, contacts.size))
                            .setPositiveButton(string.button_label_proceed) { _, _ ->
                                contacts.forEach { contact ->
                                    if (contact.establishedChannelCount > 0) {
                                        AppSingleton.getEngine().startOneToOneInvitationProtocol(
                                            contact.bytesOwnedIdentity,
                                            contact.bytesContactIdentity
                                        )
                                    }
                                    if (contact.keycloakManaged) {
                                        try {
                                            val jsonIdentityDetails = contact.getIdentityDetails()
                                            if (jsonIdentityDetails != null && jsonIdentityDetails.signedUserDetails != null) {
                                                AppSingleton.getEngine().addKeycloakContact(
                                                    contact.bytesOwnedIdentity,
                                                    contact.bytesContactIdentity,
                                                    jsonIdentityDetails.signedUserDetails
                                                )
                                            }
                                        } catch (e : Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                            .setNegativeButton(string.button_label_cancel, null)
                        builder.create().show()
                    }
                }
            }
            return true
        }
        return false
    }

    override fun onClick(view: View) {
        val group = groupDetailsViewModel.group.value ?: return
        val id = view.id
        if (id == R.id.group_discussion_button) {
            App.openGroupV2DiscussionActivity(
                this,
                group.bytesOwnedIdentity,
                group.bytesGroupIdentifier
            )
        } else if (id == R.id.button_update) {
            try {
                AppSingleton.getEngine().trustGroupV2PublishedDetails(
                    group.bytesOwnedIdentity,
                    group.bytesGroupIdentifier
                )
            } catch (e: Exception) {
                App.toast(string.toast_message_error_retry, Toast.LENGTH_SHORT)
            }
        } else if (id == R.id.button_add_members) {
            if (editingMembers) {
                val addedMembers = ArrayList(groupDetailsViewModel.changeSet.membersAdded.keys)
                val removedMembers = ArrayList(groupDetailsViewModel.changeSet.membersRemoved)
                val groupMemberAdditionDialogFragment =
                    GroupV2MemberAdditionDialogFragment.newInstance(
                        group.bytesOwnedIdentity,
                        group.bytesGroupIdentifier,
                        addedMembers,
                        removedMembers
                    )
                groupMemberAdditionDialogFragment.show(supportFragmentManager, "dialog")
            }
        } else if (id == R.id.button_edit_group) {
            val initialGroupType = groupDetailsViewModel.initialGroupType ?: groupDetailsViewModel.inferGroupType(groupDetailsViewModel.groupMembers.value ?: emptyList())
            groupDetailsViewModel.setGroupType(initialGroupType.clone())
            groupDetailsViewModel.startEditingMembers()
        } else if (id == R.id.button_discard) {
            groupDetailsViewModel.discardGroupEdits()
        } else if (id == R.id.button_save) {
            groupDetailsViewModel.publishGroupEdits()
        } else if (view is InitialView) {
            val photoUrl = view.photoUrl
            if (photoUrl != null) {
                val fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl)
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(anim.fade_in, 0)
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
                    .setCustomAnimations(0, anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    internal inner class GroupMembersAdapter : Adapter<GroupMemberViewHolder>(),
        Observer<List<Group2MemberOrPending>?> {
        private val inflater: LayoutInflater = LayoutInflater.from(this@GroupV2DetailsActivity)
        private var groupMembers: List<Group2MemberOrPending>? = null
        private var bytesContactIdentityBeingBound: ByteArray? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupMemberViewHolder {
            return GroupMemberViewHolder(
                inflater.inflate(
                    layout.item_view_group_v2_member,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: GroupMemberViewHolder, position: Int) {
            // never called
        }

        override fun onBindViewHolder(
            holder: GroupMemberViewHolder,
            position: Int,
            payloads: List<Any>
        ) {
            if (groupMembers == null) {
                return
            }
            var changesMask = -1
            if (payloads.isNotEmpty() && payloads[0] is Int) {
                changesMask = payloads[0] as Int
            }
            val group2Member = groupMembers!![position]
            holder.bytesContactIdentity = group2Member.bytesContactIdentity
            holder.contact = group2Member.contact
            bytesContactIdentityBeingBound = group2Member.bytesContactIdentity
            if (changesMask and Companion.CONTACT_CHANGE_MASK != 0) {
                val identityDetails: JsonIdentityDetails? = try {
                    AppSingleton.getJsonObjectMapper()
                        .readValue(group2Member.identityDetails, JsonIdentityDetails::class.java)
                } catch (e: Exception) {
                    null
                }
                if (group2Member.contact != null) {
                    holder.initialView.setContact(group2Member.contact)
                } else {
                    if (identityDetails != null) {
                        holder.initialView.setInitial(
                            group2Member.bytesContactIdentity,
                            StringUtils.getInitial(
                                identityDetails.formatDisplayName(
                                    SettingsActivity.getContactDisplayNameFormat(),
                                    SettingsActivity.getUppercaseLastName()
                                )
                            )
                        )
                    } else {
                        holder.initialView.setUnknown()
                    }
                    holder.initialView.setKeycloakCertified(keycloakGroup)
                    holder.initialView.setLocked(false)
                    holder.initialView.setInactive(false)
                    holder.initialView.setNullTrustLevel()
                }
                if (group2Member.contact != null && group2Member.contact.customDisplayName != null) {
                    holder.contactNameTextView.text = group2Member.contact.customDisplayName
                    if (identityDetails == null) {
                        holder.contactNameTextView.maxLines = 2
                        holder.contactNameSecondLineTextView.visibility = View.GONE
                    } else {
                        holder.contactNameTextView.maxLines = 1
                        holder.contactNameSecondLineTextView.visibility = View.VISIBLE
                        holder.contactNameSecondLineTextView.text =
                            identityDetails.formatDisplayName(
                                SettingsActivity.getContactDisplayNameFormat(),
                                SettingsActivity.getUppercaseLastName()
                            )
                    }
                } else {
                    if (identityDetails == null) {
                        val spannableString =
                            SpannableString(getString(string.text_unable_to_display_contact_name))
                        spannableString.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            0,
                            spannableString.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        holder.contactNameTextView.text = spannableString
                        holder.contactNameTextView.maxLines = 2
                        holder.contactNameSecondLineTextView.visibility = View.GONE
                    } else {
                        holder.contactNameTextView.text = identityDetails.formatFirstAndLastName(
                            SettingsActivity.getContactDisplayNameFormat(),
                            SettingsActivity.getUppercaseLastName()
                        )
                        val secondLine =
                            identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat())
                        if (secondLine == null) {
                            holder.contactNameTextView.maxLines = 2
                            holder.contactNameSecondLineTextView.visibility = View.GONE
                        } else {
                            holder.contactNameTextView.maxLines = 1
                            holder.contactNameSecondLineTextView.visibility = View.VISIBLE
                            holder.contactNameSecondLineTextView.text = secondLine
                        }
                    }
                }
            }
            if (group2Member.permissionAdmin) {
                holder.adminIndicatorImageView.visibility = View.VISIBLE
            } else {
                holder.adminIndicatorImageView.visibility = View.GONE
            }
            if (editingMembers) {
                holder.deleteButton.visibility = View.VISIBLE
                holder.adminGroup.visibility = View.VISIBLE
                if (groupDetailsViewModel.getGroupTypeLiveData().value?.type  == SIMPLE) {
                    holder.adminSwitch.visibility = View.GONE
                } else {
                    holder.adminSwitch.visibility = View.VISIBLE
                }
                holder.adminSwitch.isChecked = group2Member.permissionAdmin
                if (group2Member.permissionAdmin) {
                    holder.adminLabel.setText(string.label_admin)
                } else {
                    if (group2Member.permissionSendMessage) {
                        holder.adminLabel.setText(string.label_not_admin)
                    } else {
                        holder.adminLabel.setText(string.label_read_only_short)
                    }
                }
            } else {
                holder.deleteButton.visibility = View.GONE
                holder.adminSwitch.visibility = View.GONE
                if (group2Member.permissionAdmin) {
                    holder.adminGroup.visibility = View.VISIBLE
                    if (group2Member.pending) {
                        holder.adminLabel.setText(string.label_pending_admin)
                    } else {
                        holder.adminLabel.setText(string.label_admin)
                    }
                } else {
                    if (group2Member.pending) {
                        holder.adminGroup.visibility = View.VISIBLE
                        holder.adminLabel.setText(string.label_pending)
                    } else if (!group2Member.permissionSendMessage) {
                        holder.adminGroup.visibility = View.VISIBLE
                        holder.adminLabel.setText(string.label_read_only)
                    } else {
                        holder.adminGroup.visibility = View.GONE
                    }
                }
            }
            bytesContactIdentityBeingBound = null
        }

        override fun onViewRecycled(holder: GroupMemberViewHolder) {
            super.onViewRecycled(holder)
            holder.bytesContactIdentity = null
            holder.contact = null
        }

        override fun getItemCount(): Int {
            return if (groupMembers == null) {
                0
            } else groupMembers!!.size
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onChanged(value: List<Group2MemberOrPending>?) {
            if (this.groupMembers == null || value == null) {
                this.groupMembers = value
                notifyDataSetChanged()
            } else {
                val result = DiffUtil.calculateDiff(object : Callback() {
                    private val oldList = this@GroupMembersAdapter.groupMembers!!
                    private val newList = value
                    override fun getOldListSize(): Int {
                        return oldList.size
                    }

                    override fun getNewListSize(): Int {
                        return newList?.size ?: 0
                    }

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        val oldItem = oldList[oldItemPosition]
                        val newItem = newList?.get(newItemPosition)
                        return oldItem.bytesContactIdentity.contentEquals(newItem?.bytesContactIdentity)
                    }

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return false
                    }

                    override fun getChangePayload(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Any {
                        val oldItem = oldList[oldItemPosition]
                        val newItem = newList?.get(newItemPosition)
                        var changesMask = 0
                        if (oldItem.contact !== newItem?.contact) {
                            changesMask = changesMask or Companion.CONTACT_CHANGE_MASK
                        }
                        if (oldItem.identityDetails != newItem?.identityDetails) {
                            changesMask = changesMask or Companion.CONTACT_CHANGE_MASK
                        }
                        return changesMask
                    }
                })
                this.groupMembers = value
                result.dispatchUpdatesTo(this)
            }
        }

        internal inner class GroupMemberViewHolder(itemView: View) : ViewHolder(itemView),
            OnClickListener, OnCheckedChangeListener {
            val initialView: InitialView
            val adminIndicatorImageView: ImageView
            val contactNameTextView: TextView
            val contactNameSecondLineTextView: TextView
            val adminGroup: View
            val adminLabel: TextView

            @SuppressLint("UseSwitchCompatOrMaterialCode")
            val adminSwitch: Switch

            @SuppressLint("UseSwitchCompatOrMaterialCode")
            val deleteButton: ImageView
            var bytesContactIdentity: ByteArray? = null
            var contact: Contact? = null

            init {
                itemView.setOnClickListener(this)
                initialView = itemView.findViewById(id.initial_view)
                adminIndicatorImageView = itemView.findViewById(id.admin_indicator_image_view)
                contactNameTextView = itemView.findViewById(id.contact_name_text_view)
                contactNameSecondLineTextView =
                    itemView.findViewById(id.contact_name_second_line_text_view)
                adminGroup = itemView.findViewById(id.group_admin_group)
                adminLabel = itemView.findViewById(id.group_admin_label)
                adminSwitch = itemView.findViewById(id.group_admin_switch)
                adminSwitch.setOnCheckedChangeListener(this)
                deleteButton = itemView.findViewById(id.delete_button)
                deleteButton.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                if (v.id == id.delete_button) {
                    if (bytesContactIdentity != null) {
                        groupDetailsViewModel.memberRemoved(bytesContactIdentity)
                    }
                } else if (!editingMembers) {
                    if (bytesContactIdentity != null) {
                        if (contact != null) {
                            if (contact!!.oneToOne) {
                                App.openOneToOneDiscussionActivity(
                                    this@GroupV2DetailsActivity,
                                    contact!!.bytesOwnedIdentity,
                                    contact!!.bytesContactIdentity,
                                    false
                                )
                            } else {
                                App.openContactDetailsActivity(
                                    this@GroupV2DetailsActivity,
                                    contact!!.bytesOwnedIdentity,
                                    contact!!.bytesContactIdentity
                                )
                            }
                        }
                    }
                }
            }

            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (bytesContactIdentity == null || bytesContactIdentityBeingBound.contentEquals(
                        bytesContactIdentity
                    )
                ) {
                    // ignore check changes during onBindViewHolder
                    return
                }
                if (buttonView.id == id.group_admin_switch) {
                    groupDetailsViewModel.permissionChanged(bytesContactIdentity, isChecked)
//                    if (isChecked) {
//                        adminLabel.setText(string.label_admin)
//                    } else {
//                        adminLabel.setText(string.label_not_admin)
//                    }
                }
            }
        }
    }

    private inner class SwipeCallback :
        SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private val redPaint: Paint = Paint()

        init {
            redPaint.color =
                ContextCompat.getColor(this@GroupV2DetailsActivity, color.red)
        }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
            return if (!groupAdmin || !editingMembers) {
                0
            } else super.getMovementFlags(recyclerView, viewHolder)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            target: ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
            if (viewHolder is GroupMemberViewHolder) {
                groupDetailsViewModel.memberRemoved(viewHolder.bytesContactIdentity)
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            c.save()
            c.drawPaint(redPaint)
            c.restore()
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    // region EngineNotificationListener
    private var registrationNumber: Long? = null
    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        this.registrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return registrationNumber!!
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return registrationNumber != null
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.GROUP_V2_CREATED_OR_UPDATED -> {
                val groupV2 =
                    userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY] as ObvGroupV2?
                if (groupV2 != null && groupV2.bytesOwnedIdentity.contentEquals(
                        groupDetailsViewModel.bytesOwnedIdentity
                    )
                    && groupV2.groupIdentifier.bytes.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                ) {
                    runOnUiThread { displayEngineGroupCards(groupV2.detailsAndPhotos) }
                }
            }

            EngineNotifications.GROUP_V2_PHOTO_CHANGED -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                if (bytesOwnedIdentity.contentEquals(groupDetailsViewModel.bytesOwnedIdentity)
                    && bytesGroupIdentifier.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                ) {
                    fetchEngineGroupCards()
                }
            }

            EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                val updating =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY] as Boolean?
                if (bytesOwnedIdentity.contentEquals(groupDetailsViewModel.bytesOwnedIdentity)
                    && bytesGroupIdentifier.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                ) {
                    if (updating != null && !updating) {
                        groupDetailsViewModel.publicationFinished()
                    }
                }
            }

            EngineNotifications.GROUP_V2_UPDATE_FAILED -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_FAILED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                val error =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_FAILED_ERROR_KEY] as Boolean?
                if (bytesOwnedIdentity.contentEquals(groupDetailsViewModel.bytesOwnedIdentity)
                    && bytesGroupIdentifier.contentEquals(groupDetailsViewModel.bytesGroupIdentifier)
                ) {
                    groupDetailsViewModel.publicationFinished()
                    if (error != null && error) {
                        App.toast(string.toast_message_unable_to_update_group, Toast.LENGTH_LONG)
                    }
                }
            }
        }
    } // endregion

    companion object {
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity"
        const val BYTES_GROUP_IDENTIFIER_INTENT_EXTRA = "group_identifier"
        const val EDIT_DETAILS_INTENT_EXTRA = "edit_details"
        const val FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image"
        private const val CONTACT_CHANGE_MASK = 1
        val NOTIFICATIONS_TO_LISTEN_TO = arrayOf(
            EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
            EngineNotifications.GROUP_V2_PHOTO_CHANGED,
            EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED,
            EngineNotifications.GROUP_V2_UPDATE_FAILED
        )
    }
}

