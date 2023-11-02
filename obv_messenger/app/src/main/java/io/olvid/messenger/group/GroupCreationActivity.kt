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
package io.olvid.messenger.group

import android.os.Bundle
import android.text.InputType
import android.util.Pair
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.appcompat.widget.Toolbar
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.switchMap
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.google.android.material.tabs.TabLayout
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.containers.GroupV2.Permission
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.engine.engine.types.identities.ObvGroup
import io.olvid.engine.engine.types.identities.ObvGroupV2
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.fragments.FilteredContactListFragment
import io.olvid.messenger.group.GroupTypeModel.CustomGroup
import io.olvid.messenger.group.GroupTypeModel.PrivateGroup
import io.olvid.messenger.group.GroupTypeModel.SimpleGroup
import io.olvid.messenger.settings.SettingsActivity

class GroupCreationActivity : LockableActivity(), OnClickListener {
    private val viewPager: ViewPager by lazy { findViewById(R.id.group_creation_view_pager) }
    private val groupCreationViewModel: GroupCreationViewModel by viewModels()
    private val groupDetailsViewModel: OwnedGroupDetailsViewModel by viewModels()
    private val groupV2DetailsViewModel: GroupV2DetailsViewModel by viewModels()
    private val nextButton: Button by lazy { findViewById(R.id.button_next_tab) }
    private val previousButton: Button by lazy { findViewById(R.id.button_previous_tab) }
    private val confirmationButton: Button by lazy { findViewById(R.id.button_confirmation) }
    private var subtitleTextView: TextView? = null
    var contactsSelectionFragment: ContactsSelectionFragment? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupDetailsViewModel.valid.observe(this) { ready: Boolean? ->
            confirmationButton.isEnabled = ready ?: false
        }
        if (savedInstanceState == null) {
            groupDetailsViewModel.setBytesGroupOwnerAndUidOrIdentifier(ByteArray(0))

            // only look at intent when first creating the activity
            val intent = intent
            groupDetailsViewModel.setGroupV2(groupV2)
            if (intent.hasExtra(SERIALIZED_GROUP_DETAILS_INTENT_EXTRA)) {
                try {
                    val groupDetails = AppSingleton.getJsonObjectMapper().readValue(
                        intent.getStringExtra(SERIALIZED_GROUP_DETAILS_INTENT_EXTRA),
                        JsonGroupDetails::class.java
                    )
                    if (groupDetails.name.isNullOrEmpty().not()) {
                        groupDetailsViewModel.setGroupName(getString(R.string.text_copy_of_prefix) + groupDetails.name)
                    }
                    groupDetailsViewModel.groupDescription = groupDetails.description
                } catch (ignored: Exception) {
                }
            }
            if (intent.hasExtra(SERIALIZED_GROUP_TYPE_INTENT_EXTRA)) {
                try {
                    val groupType = AppSingleton.getJsonObjectMapper().readValue(
                        intent.getStringExtra(SERIALIZED_GROUP_TYPE_INTENT_EXTRA),
                        JsonGroupType::class.java
                    )
                    if (groupType != null) {
                        groupV2DetailsViewModel.setGroupType(groupType.toGroupCreationModel())
                        groupCreationViewModel.setIsCustomGroup(groupType.type == JsonGroupType.TYPE_CUSTOM)
                    }
                } catch (ignored: Exception) {
                }
            }
            if (intent.hasExtra(ABSOLUTE_PHOTO_URL_INTENT_EXTRA)) {
                groupDetailsViewModel.setAbsolutePhotoUrl(
                    intent.getStringExtra(
                        ABSOLUTE_PHOTO_URL_INTENT_EXTRA
                    )
                )
            }
            val preselectedContactAdminBytesKeys = intent.getParcelableArrayListExtra<BytesKey>(
                PRESELECTED_GROUP_ADMIN_MEMBERS_INTENT_EXTRA
            ) ?: arrayListOf<BytesKey>()
            val preselectedContactNonAdminBytesKeys =
                intent.getParcelableArrayListExtra<BytesKey>(PRESELECTED_GROUP_MEMBERS_INTENT_EXTRA)
                    ?: arrayListOf<BytesKey>()
            val preselectedContactBytesKeys = preselectedContactAdminBytesKeys + preselectedContactNonAdminBytesKeys
            if (preselectedContactBytesKeys.isNotEmpty()) {
                val admins : HashSet<Contact> = hashSetOf()
                    App.runThread {
                        val preselectedContacts: MutableList<Contact> = ArrayList()
                        for (bytesKey in preselectedContactBytesKeys) {
                            val contact = AppDatabase.getInstance()
                                .contactDao()[AppSingleton.getBytesCurrentIdentity(), bytesKey.bytes]
                            if (contact != null) {
                                preselectedContacts.add(contact)
                                if (preselectedContactAdminBytesKeys.contains(bytesKey)) {
                                    admins.add(contact)
                                }
                            }
                        }
                        if (preselectedContacts.isNotEmpty()) {
                            runOnUiThread {
                                groupCreationViewModel.admins.value = admins
                                groupCreationViewModel.selectedContacts = preselectedContacts
                                contactsSelectionFragment?.setInitiallySelectedContacts(
                                    preselectedContacts
                                )
                            }
                        }
                    }
            }
        }
        setContentView(R.layout.activity_group_creation)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        subtitleTextView = toolbar.findViewById(R.id.subtitle)
        val groupV2WarningMessage = findViewById<View>(R.id.group_v2_warning_message)
        groupCreationViewModel.showGroupV2WarningLiveData.observe(this) { showGroupV2WarningMessage: Boolean? ->
            groupV2WarningMessage.visibility =
                if (showGroupV2WarningMessage != null && showGroupV2WarningMessage) View.VISIBLE else View.GONE
        }
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowTitleEnabled(false)
        }
        groupCreationViewModel.subtitleLiveData.observe(this) { tabAndSelectedContactCount: Pair<Int?, Int?>? ->
            if (subtitleTextView == null || tabAndSelectedContactCount == null || tabAndSelectedContactCount.first == null || tabAndSelectedContactCount.second == null) {
                return@observe
            }
            when (tabAndSelectedContactCount.first) {
                CONTACTS_SELECTION_TAB -> if (tabAndSelectedContactCount.second == 0) {
                    subtitleTextView!!.text = getString(R.string.subtitle_select_group_members)
                } else {
                    subtitleTextView!!.text = resources.getQuantityString(
                        R.plurals.other_members_count,
                        tabAndSelectedContactCount.second!!,
                        tabAndSelectedContactCount.second
                    )
                }

                GROUP_NAME_TAB -> subtitleTextView!!.text =
                    getString(R.string.subtitle_choose_group_name)

                GROUP_SETTINGS_TAB -> subtitleTextView!!.text =
                    getString(R.string.label_group_custom_settings)
            }
        }

        val fragmentPagerAdapter: FragmentPagerAdapter = object : FragmentPagerAdapter(
            supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
        ) {
            override fun getItem(position: Int): Fragment {
                return when (position) {
                    CONTACTS_SELECTION_TAB -> ContactsSelectionFragment()
                    GROUP_NAME_TAB -> GroupNameFragment()
                    else -> GroupCustomSettingsPreferenceFragment(isGroupCreation = true)
                }
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val fragment = super.instantiateItem(container, position) as Fragment
                when (position) {
                    CONTACTS_SELECTION_TAB -> {
                        contactsSelectionFragment = fragment as ContactsSelectionFragment
                        contactsSelectionFragment!!.setGroupV2(groupDetailsViewModel.isGroupV2())
                        contactsSelectionFragment!!.setInitiallySelectedContacts(
                            groupCreationViewModel.selectedContacts
                        )
                    }

                    GROUP_NAME_TAB -> {}
                }
                return fragment
            }

            override fun getCount(): Int {
                return if (java.lang.Boolean.TRUE == groupCreationViewModel.isCustomGroup().value) 3 else 2
            }
        }
        viewPager.adapter = fragmentPagerAdapter
        viewPager.offscreenPageLimit = 2
        val tabLayout = findViewById<TabLayout>(R.id.group_creation_tab_dots)
        tabLayout.setupWithViewPager(viewPager, true)
        groupCreationViewModel.isCustomGroup().observe(this) { custom: Boolean ->
            fragmentPagerAdapter.notifyDataSetChanged()
            if (viewPager.currentItem == GROUP_NAME_TAB) {
                if (custom) {
                    nextButton.visibility = View.VISIBLE
                    confirmationButton.visibility = View.GONE
                } else {
                    nextButton.visibility = View.GONE
                    confirmationButton.visibility = View.VISIBLE
                }
            }
        }
        nextButton.setOnClickListener(this)
        previousButton.setOnClickListener(this)
        confirmationButton.setOnClickListener(this)
        val pageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageSelected(position: Int) {
                groupCreationViewModel.setSelectedTab(position)
                when (position) {
                    CONTACTS_SELECTION_TAB -> {
                        previousButton.visibility = View.GONE
                        nextButton.visibility = View.VISIBLE
                        confirmationButton.visibility = View.GONE
                    }

                    GROUP_NAME_TAB -> if (java.lang.Boolean.TRUE == groupCreationViewModel.isCustomGroup().value) {
                        previousButton.visibility = View.VISIBLE
                        nextButton.visibility = View.VISIBLE
                        confirmationButton.visibility = View.GONE
                    } else {
                        previousButton.visibility = View.VISIBLE
                        nextButton.visibility = View.GONE
                        confirmationButton.visibility = View.VISIBLE
                    }

                    GROUP_SETTINGS_TAB -> {
                        previousButton.visibility = View.VISIBLE
                        nextButton.visibility = View.GONE
                        confirmationButton.visibility = View.VISIBLE
                    }
                }
                invalidateOptionsMenu()
            }
        }
        viewPager.addOnPageChangeListener(pageChangeListener)
        pageChangeListener.onPageSelected(0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (viewPager.currentItem == CONTACTS_SELECTION_TAB) {
            menuInflater.inflate(R.menu.menu_group_creation_contact_selection, menu)
            val searchView = menu.findItem(R.id.action_search).actionView as SearchView?
            if (searchView != null) {
                searchView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        groupCreationViewModel.setSearchOpened(true)
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        groupCreationViewModel.setSearchOpened(false)
                    }
                })
                searchView.queryHint = getString(R.string.hint_search_contact_name)
                if (SettingsActivity.useKeyboardIncognitoMode()) {
                    searchView.imeOptions =
                        searchView.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                searchView.inputType =
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_FILTER
                searchView.setOnQueryTextListener(object : OnQueryTextListener {
                    val editText: EditText = AppCompatEditText(searchView.context)

                    init {
                        contactsSelectionFragment!!.setContactFilterEditText(editText)
                    }

                    override fun onQueryTextSubmit(query: String): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        editText.setText(newText)
                        return true
                    }
                })
            }
        }
        return true
    }

    override fun onBackPressed() {
        val position = viewPager.currentItem
        if (position > 0) {
            viewPager.currentItem = position - 1
        } else {
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.button_next_tab) {
            val position = viewPager.currentItem
            if (position < 2) {
                viewPager.currentItem = position + 1
            }
        } else if (id == R.id.button_previous_tab) {
            val position = viewPager.currentItem
            if (position > 0) {
                viewPager.currentItem = position - 1
            }
        } else if (id == R.id.button_confirmation) {
            if (groupCreationViewModel.selectedContacts.isNullOrEmpty()) {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_create_empty_group)
                    .setMessage(R.string.dialog_message_create_empty_group)
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        confirmationButton.isEnabled = false
                        initiateGroupCreationProtocol()
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                builder.create().show()
            } else {
                confirmationButton.isEnabled = false
                initiateGroupCreationProtocol()
            }
        }
    }

    private fun initiateGroupCreationProtocol() {
        if (groupDetailsViewModel.isGroupV2()) {
            val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return
            val selectedContacts = groupCreationViewModel.selectedContacts ?: emptyList()

            val groupAbsolutePhotoUrl = groupDetailsViewModel.getAbsolutePhotoUrl()
            val groupName = groupDetailsViewModel.getGroupName() ?: ""
            val groupDescription = groupDetailsViewModel.groupDescription?.trim()

            val jsonGroupDetails = JsonGroupDetails(groupName.trim(), groupDescription)
            val groupType = groupV2DetailsViewModel.getGroupTypeLiveData().value ?: SimpleGroup
            val otherGroupMembers = HashMap<ObvBytesKey, HashSet<Permission>>()
            for (contact in selectedContacts) {
                val permissions = groupV2DetailsViewModel.getPermissions(
                    groupType,
                    if (groupType is PrivateGroup)
                        false
                    else
                        groupCreationViewModel.admins.value?.find { it == contact } != null)
                otherGroupMembers[ObvBytesKey(contact.bytesContactIdentity)] = permissions
            }
            try {
                val serializedGroupDetails =
                    AppSingleton.getJsonObjectMapper().writeValueAsString(jsonGroupDetails)
                val serializedGroupType = AppSingleton.getJsonObjectMapper()
                    .writeValueAsString((groupType).toJsonGroupType())
                // set tmp ephemeral settings for just created group
                AppSingleton.setCreatedGroupEphemeralSettings(
                    JsonExpiration().takeIf { groupV2DetailsViewModel.getGroupTypeLiveData().value is CustomGroup }
                        ?.apply {
                            readOnce = groupCreationViewModel.settingsReadOnce
                            existenceDuration =
                                groupCreationViewModel.settingsExistenceDuration
                            visibilityDuration =
                                groupCreationViewModel.settingsVisibilityDuration
                        }
                )
                val ownPermissions = Permission.DEFAULT_ADMIN_PERMISSIONS.toHashSet().apply {
                    if (groupType is CustomGroup && groupType.remoteDeleteSetting == GroupTypeModel.RemoteDeleteSetting.NOBODY) this.remove(
                        Permission.REMOTE_DELETE_ANYTHING
                    )
                }
                AppSingleton.getEngine().startGroupV2CreationProtocol(
                    serializedGroupDetails,
                    groupAbsolutePhotoUrl,
                    bytesOwnedIdentity,
                    ownPermissions,
                    otherGroupMembers,
                    serializedGroupType
                )
                AppSingleton.getEngine().addNotificationListener(
                    EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
                    object : EngineNotificationListener {
                        private var registrationNumber: Long? = null
                        private val engineNotificationListener: EngineNotificationListener = this

                        init {
                            Thread {
                                try {
                                    Thread.sleep(3000)
                                } catch (e: InterruptedException) {
                                    Logger.i("Group creation listener timer interrupted")
                                }
                                AppSingleton.getEngine().removeNotificationListener(
                                    EngineNotifications.GROUP_CREATED,
                                    engineNotificationListener
                                )
                            }.start()
                        }

                        override fun callback(
                            notificationName: String,
                            userInfo: HashMap<String, Any>
                        ) {
                            AppSingleton.getEngine().removeNotificationListener(
                                EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
                                this
                            )
                            val group =
                                userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY] as ObvGroupV2?

                            group?.let {
                                runOnUiThread {
                                    App.openGroupV2DetailsActivity(
                                        this@GroupCreationActivity,
                                        group.bytesOwnedIdentity,
                                        group.groupIdentifier.bytes
                                    )
                                    finish()
                                }
                            }
                        }

                        override fun setEngineNotificationListenerRegistrationNumber(
                            registrationNumber: Long
                        ) {
                            this.registrationNumber = registrationNumber
                        }

                        override fun getEngineNotificationListenerRegistrationNumber(): Long {
                            return registrationNumber ?: 0
                        }

                        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                            return registrationNumber != null
                        }
                    })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return
            val selectedContacts = groupCreationViewModel.selectedContacts ?: emptyList()

            val groupAbsolutePhotoUrl = groupDetailsViewModel.getAbsolutePhotoUrl()
            val groupName = groupDetailsViewModel.getGroupName()
            val groupDescription = groupDetailsViewModel.groupDescription?.trim()
            if (groupName == null || groupName.trim().isEmpty()) {
                return
            }

            val jsonGroupDetailsWithVersionAndPhoto = JsonGroupDetailsWithVersionAndPhoto()
            jsonGroupDetailsWithVersionAndPhoto.version = 0
            jsonGroupDetailsWithVersionAndPhoto.groupDetails =
                JsonGroupDetails(groupName.trim(), groupDescription)
            val bytesContactIdentities = arrayOfNulls<ByteArray>(selectedContacts.size)
            for ((i, contact) in selectedContacts.withIndex()) {
                bytesContactIdentities[i] = contact.bytesContactIdentity
            }
            try {
                val serializedGroupDetailsWithVersionAndPhoto =
                    AppSingleton.getJsonObjectMapper()
                        .writeValueAsString(jsonGroupDetailsWithVersionAndPhoto)
                AppSingleton.getEngine().startGroupCreationProtocol(
                    serializedGroupDetailsWithVersionAndPhoto,
                    groupAbsolutePhotoUrl,
                    bytesOwnedIdentity,
                    bytesContactIdentities
                )
                AppSingleton.getEngine().addNotificationListener(
                    EngineNotifications.GROUP_CREATED,
                    object : EngineNotificationListener {
                        private var registrationNumber: Long? = null
                        private val engineNotificationListener: EngineNotificationListener =
                            this

                        init {
                            Thread {
                                try {
                                    Thread.sleep(3000)
                                } catch (e: InterruptedException) {
                                    Logger.i("Group creation listener timer interrupted")
                                }
                                AppSingleton.getEngine().removeNotificationListener(
                                    EngineNotifications.GROUP_CREATED,
                                    engineNotificationListener
                                )
                            }.start()
                        }

                        override fun callback(
                            notificationName: String,
                            userInfo: HashMap<String, Any>
                        ) {
                            AppSingleton.getEngine()
                                .removeNotificationListener(
                                    EngineNotifications.GROUP_CREATED,
                                    this
                                )
                            val group =
                                userInfo[EngineNotifications.GROUP_CREATED_GROUP_KEY] as ObvGroup?
                            if (group != null) {
                                runOnUiThread {
                                    App.openGroupDetailsActivity(
                                        this@GroupCreationActivity,
                                        group.bytesOwnedIdentity,
                                        group.bytesGroupOwnerAndUid
                                    )
                                    finish()
                                }
                            }
                        }

                        override fun setEngineNotificationListenerRegistrationNumber(
                            registrationNumber: Long
                        ) {
                            this.registrationNumber = registrationNumber
                        }

                        override fun getEngineNotificationListenerRegistrationNumber(): Long {
                            return registrationNumber ?: 0
                        }

                        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                            return registrationNumber != null
                        }
                    })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class ContactsSelectionFragment : Fragment() {
        private val filteredContactListFragment: FilteredContactListFragment by lazy { FilteredContactListFragment() }
        private var initiallySelectedContacts: List<Contact>? = null
        private var groupV2 = false
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (initiallySelectedContacts != null) {
                filteredContactListFragment.setInitiallySelectedContacts(
                    initiallySelectedContacts
                )
                initiallySelectedContacts = null
            }
            filteredContactListFragment.setSelectable(true)
            if (groupV2) {
                filteredContactListFragment.setUnfilteredContacts(
                    AppSingleton.getCurrentIdentityLiveData()
                        .switchMap { ownedIdentity: OwnedIdentity? ->
                            if (ownedIdentity == null) {
                                return@switchMap null
                            }
                            AppDatabase.getInstance().contactDao()
                                .getAllForOwnedIdentityWithChannelAndGroupV2Capability(
                                    ownedIdentity.bytesOwnedIdentity
                                )
                        })
            } else {
                filteredContactListFragment.setUnfilteredContacts(
                    AppSingleton.getCurrentIdentityLiveData()
                        .switchMap { ownedIdentity: OwnedIdentity? ->
                            if (ownedIdentity == null) {
                                return@switchMap null
                            }
                            AppDatabase.getInstance().contactDao()
                                .getAllForOwnedIdentityWithChannel(ownedIdentity.bytesOwnedIdentity)
                        })
            }
            filteredContactListFragment.setSelectedContactsObserver { contacts: List<Contact>? ->
                val activity = activity as GroupCreationActivity?
                if (activity != null) {
                    activity.groupCreationViewModel.selectedContacts = contacts
                }
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val rootView =
                inflater.inflate(
                    R.layout.fragment_group_creation_contact_selection,
                    container,
                    false
                )
            val transaction = childFragmentManager.beginTransaction()
            transaction.replace(
                R.id.filtered_contact_list_placeholder,
                filteredContactListFragment
            )
            transaction.commit()
            return rootView
        }

        fun setInitiallySelectedContacts(selectedContacts: List<Contact>?) {
            if (selectedContacts != null) {
                filteredContactListFragment.setInitiallySelectedContacts(selectedContacts)
            } else {
                initiallySelectedContacts = null
            }
        }

        fun setContactFilterEditText(contactNameFilter: EditText?) {
            filteredContactListFragment.setContactFilterEditText(contactNameFilter)
        }

        fun setGroupV2(groupV2: Boolean) {
            this.groupV2 = groupV2
        }
    }

    class GroupNameFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val rootView =
                inflater.inflate(R.layout.fragment_group_creation_group_name, container, false)

            val ownedGroupDetailsFragment = OwnedGroupDetailsFragment()
            ownedGroupDetailsFragment.setHidePersonalNote(true)
            ownedGroupDetailsFragment.setInitialGroupType(SimpleGroup)

            val transaction = childFragmentManager.beginTransaction()
            transaction.replace(
                R.id.fragment_group_details_placeholder,
                ownedGroupDetailsFragment
            )
            transaction.commit()
            return rootView
        }
    }

    companion object {
        // this boolean controls whether groups are created in v2 format or not
        const val groupV2 = true
        const val CONTACTS_SELECTION_TAB = 0
        const val GROUP_NAME_TAB = 1
        const val GROUP_SETTINGS_TAB = 2
        const val ABSOLUTE_PHOTO_URL_INTENT_EXTRA =
            "photo_url" // String with absolute path to photo
        const val SERIALIZED_GROUP_DETAILS_INTENT_EXTRA =
            "serialized_group_details" // String with serialized JsonGroupDetails
        const val SERIALIZED_GROUP_TYPE_INTENT_EXTRA =
            "serialized_group_type" // String with serialized JsonGroupType
        const val PRESELECTED_GROUP_MEMBERS_INTENT_EXTRA =
            "preselected_group_members" // Array of BytesKey
        const val PRESELECTED_GROUP_ADMIN_MEMBERS_INTENT_EXTRA =
            "preselected_group_admin_members" // Array of BytesKey
    }
}