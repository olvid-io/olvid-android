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
package io.olvid.messenger.activities.storage_manager

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.text.format.Formatter
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.ActionMode.Callback
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.activities.storage_manager.StorageManagerViewModel.SortKey.DATE
import io.olvid.messenger.activities.storage_manager.StorageManagerViewModel.SortKey.NAME
import io.olvid.messenger.activities.storage_manager.StorageManagerViewModel.SortKey.SIZE
import io.olvid.messenger.activities.storage_manager.StorageManagerViewModel.SortOrder
import io.olvid.messenger.activities.storage_manager.StorageUsageLiveData.StorageUsage
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.EmptyRecyclerView
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.LockScreenOrNotActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.databases.tasks.SaveMultipleAttachmentsTask
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter
import io.olvid.messenger.settings.SettingsActivity.Companion.contactDisplayNameFormat
import io.olvid.messenger.settings.SettingsActivity.Companion.uppercaseLastName

class StorageManagerActivity : LockScreenOrNotActivity() {
    private val viewModel: StorageManagerViewModel by viewModels()
    private var audioAttachmentServiceBinding: AudioAttachmentServiceBinding? = null

    private var currentIdentityInitialView: InitialView? = null
    private var currentNameTextView: TextView? = null
    private var currentNameSecondLineTextView: TextView? = null
    private var currentIdentityMutedImageView: ImageView? = null

    private var anchor: View? = null
    private var adapter: OwnedIdentityListAdapter? = null
    private var popupWindow: PopupWindow? = null

    private val totalUsageTextView: TextView by lazy { findViewById(R.id.summary_usage_text_view) }
    private val usageBarLinearLayout: LinearLayout by lazy { findViewById(R.id.summary_usage_bar) }
    private var sizePhotosView: View? = null
    private var sizeVideosView: View? = null
    private var sizeAudioView: View? = null
    private var sizeOtherView: View? = null

    private lateinit var tabViews: Array<View>
    private val viewPager: ViewPager2 by lazy { findViewById(R.id.view_pager_container) }
    private var tabsPagerAdapter: TabViewPagerAdapter? = null

    private lateinit var actionModeCallback: Callback
    private var actionMode: ActionMode? = null

    private val saveSelectedAttachmentsLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { activityResult: ActivityResult? ->
        if (activityResult?.data == null || activityResult.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }
        val folderUri = activityResult.data!!.data
        if (StringUtils.validateUri(folderUri) && viewModel.selectedFyles.isNotEmpty()) {
            val selectedAttachments =
                ArrayList(
                    viewModel.selectedFyles
                )
            viewModel.clearSelectedFyles()
            App.runThread(
                SaveMultipleAttachmentsTask(
                    this,
                    folderUri,
                    selectedAttachments
                )
            )
        }
    }


    override fun notLockedOnCreate() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.olvid_gradient_dark)
        setContentView(R.layout.activity_storage_manager)

        try {
            audioAttachmentServiceBinding = AudioAttachmentServiceBinding(this)
        } catch (e: Exception) {
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.elevation = 0f

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES

        findViewById<CoordinatorLayout>(R.id.root_coordinator)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                view.updatePadding(top = insets.top)
                findViewById<ViewPager2>(R.id.view_pager_container)?.updatePadding(
                    left = insets.left,
                    right = insets.right
                )
                view.updateLayoutParams<MarginLayoutParams> {
                    updateMargins(bottom = insets.bottom)
                }
                WindowInsetsCompat.CONSUMED
            }
        }

        val backButton = findViewById<ImageView>(R.id.back_button)
        backButton.setOnClickListener { v: View? -> onBackPressed() }

        //////////////
        // current identity
        //////////////
        currentIdentityInitialView = findViewById(R.id.current_identity_initial_view)
        currentNameTextView = findViewById(R.id.current_identity_name_text_view)
        currentNameSecondLineTextView =
            findViewById(R.id.current_identity_name_second_line_text_view)
        currentIdentityMutedImageView = findViewById(R.id.current_identity_muted_marker_image_view)
        anchor = toolbar

        AppSingleton.getCurrentIdentityLiveData().observe(
            this
        ) { ownedIdentity: OwnedIdentity? ->
            this.bindOwnedIdentity(
                ownedIdentity
            )
        }

        val switchProfileButton = findViewById<TextView>(R.id.button_switch_profile)
        switchProfileButton.setOnClickListener { v: View? -> openSwitchProfilePopup() }
        switchProfileButton.setOnLongClickListener { v: View? ->
            OpenHiddenProfileDialog(
                this
            )
            true
        }

        adapter = OwnedIdentityListAdapter(
            layoutInflater
        ) { bytesOwnedIdentity: ByteArray? ->
            if (popupWindow != null) {
                popupWindow!!.dismiss()
            }
            AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null)
        }
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(
                ownedIdentity?.bytesOwnedIdentity ?: ByteArray(0)
            )
        }.observe(
            this,
            adapter!!
        )

        //////////////
        // storage usage bar
        //////////////
        usageBarLinearLayout.clipToOutline = true
        sizePhotosView = findViewById(R.id.size_photos)
        sizeVideosView = findViewById(R.id.size_videos)
        sizeAudioView = findViewById(R.id.size_audio)
        sizeOtherView = findViewById(R.id.size_other)

        val totalUsageLiveData =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap MutableLiveData<Long>(0L)
                }
                AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                    .getTotalUsage(ownedIdentity.bytesOwnedIdentity)
            }
        val photosUsageLiveData =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap MutableLiveData<Long>(0L)
                }
                AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                    .getMimeUsage(ownedIdentity.bytesOwnedIdentity, "image/%")
            }
        val videosUsageLiveData =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap MutableLiveData<Long>(0L)
                }
                AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                    .getMimeUsage(ownedIdentity.bytesOwnedIdentity, "video/%")
            }
        val audioUsageLiveData =
            AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@switchMap MutableLiveData<Long>(0L)
                }
                AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                    .getMimeUsage(ownedIdentity.bytesOwnedIdentity, "audio/%")
            }

        val storageUsageLiveData = StorageUsageLiveData(
            totalUsageLiveData,
            photosUsageLiveData,
            videosUsageLiveData,
            audioUsageLiveData
        )
        storageUsageLiveData.observe(
            this
        ) { storageUsage: StorageUsage? -> this.bindUsage(storageUsage) }

        //////////////
        // view poger
        //////////////
        tabViews = arrayOf(
            findViewById(R.id.tab_images_button),
            findViewById(R.id.tab_files_button),
            findViewById(R.id.tab_audio_button),
            findViewById(R.id.tab_all_button)
        )

        tabViews.forEach { view ->
            view.setOnClickListener { tabView: View ->
                this.tabClicked(
                    tabView
                )
            }
        }

        val pageChangeListener = StoragePageChangeListener(tabViews)

        tabsPagerAdapter = TabViewPagerAdapter(this, audioAttachmentServiceBinding!!)

        viewPager.setAdapter(tabsPagerAdapter)
        viewPager.registerOnPageChangeCallback(pageChangeListener)
        viewPager.setOffscreenPageLimit(3)

        //////////////
        // action mode
        //////////////
        actionModeCallback = object : Callback {
            private var inflater: MenuInflater? = null

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                inflater = mode.menuInflater
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                inflater!!.inflate(R.menu.action_menu_storate_manager, menu)
                return true
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == R.id.action_delete_attachments) {
                    val count = viewModel.selectedCountLiveData.value
                    if (count == null || count == 0) {
                        return true
                    }
                    val builder = SecureAlertDialogBuilder(
                        this@StorageManagerActivity,
                        R.style.CustomAlertDialog
                    )
                        .setTitle(R.string.dialog_title_confirm_deletion)
                        .setMessage(
                            resources.getQuantityString(
                                R.plurals.dialog_message_delete_attachments,
                                count,
                                count
                            )
                        )
                        .setPositiveButton(
                            R.string.button_label_ok
                        ) { dialog: DialogInterface?, which: Int ->
                            App.runThread {
                                val fylesToDelete: List<FyleAndStatus> =
                                    ArrayList(
                                        viewModel.selectedFyles
                                    )
                                viewModel.clearSelectedFyles()
                                for (fyleAndStatus in fylesToDelete) {
                                    DeleteAttachmentTask(fyleAndStatus).run()
                                }
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                } else if (item.itemId == R.id.action_save_attachments) {
                    val count = viewModel.selectedCountLiveData.value
                    if (count == null || count == 0) {
                        return true
                    }
                    val builder = SecureAlertDialogBuilder(
                        this@StorageManagerActivity,
                        R.style.CustomAlertDialog
                    )
                        .setTitle(R.string.dialog_title_save_selected_attachments)
                        .setMessage(
                            resources.getQuantityString(
                                R.plurals.dialog_message_save_selected_attachments,
                                count,
                                count
                            )
                        )
                        .setPositiveButton(R.string.button_label_ok) { dialog: DialogInterface?, which: Int ->
                            App.prepareForStartActivityForResult(this@StorageManagerActivity)
                            saveSelectedAttachmentsLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                } else if (item.itemId == R.id.action_select_all) {
                    val adapter = tabsPagerAdapter!!.getAdapter(viewPager.getCurrentItem())
                    if (adapter?.fyleAndOrigins != null) {
                        viewModel.selectAllFyles(adapter.fyleAndOrigins)
                        adapter.notifyDataSetChanged()
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                viewModel.clearSelectedFyles()
                actionMode = null
            }
        }


        viewModel.getSelectedCountLiveData().observe(
            this
        ) { selectedCount: Int? ->
            if (selectedCount != null && selectedCount > 0) {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
                if (actionMode != null) {
                    actionMode!!.title = resources.getQuantityString(
                        R.plurals.action_mode_title_storage,
                        selectedCount,
                        selectedCount
                    )
                }
            } else {
                if (actionMode != null) {
                    actionMode!!.finish()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_storage_manager, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (anchor != null) {
            val popupMenu = PopupMenu(this, anchor, Gravity.TOP or Gravity.END)
            popupMenu.inflate(R.menu.popup_storage_sort_order)
            val currentSortOrder = viewModel.currentSortOrder

            if (currentSortOrder.sortKey == SIZE && !currentSortOrder.ascending) {
                val menuItem = popupMenu.menu.findItem(R.id.popup_action_sort_size)
                menuItem?.setTitle(R.string.menu_action_sort_size_alt)
            } else if (currentSortOrder.sortKey == DATE && !currentSortOrder.ascending) {
                val menuItem = popupMenu.menu.findItem(R.id.popup_action_sort_date)
                menuItem?.setTitle(R.string.menu_action_sort_date_alt)
            } else if (currentSortOrder.sortKey == NAME && currentSortOrder.ascending) {
                val menuItem = popupMenu.menu.findItem(R.id.popup_action_sort_name)
                menuItem?.setTitle(R.string.menu_action_sort_name_alt)
            }
            popupMenu.setOnMenuItemClickListener { popupItem: MenuItem ->
                val id = popupItem.itemId
                if (id == R.id.popup_action_sort_size) {
                    viewModel.setSortOrder(
                        SortOrder(
                            SIZE,
                            currentSortOrder.sortKey == SIZE && !currentSortOrder.ascending
                        )
                    )
                } else if (id == R.id.popup_action_sort_date) {
                    viewModel.setSortOrder(
                        SortOrder(
                            DATE,
                            currentSortOrder.sortKey == DATE && !currentSortOrder.ascending
                        )
                    )
                } else if (id == R.id.popup_action_sort_name) {
                    viewModel.setSortOrder(
                        SortOrder(
                            NAME,
                            !(currentSortOrder.sortKey == NAME && currentSortOrder.ascending)
                        )
                    )
                }
                true
            }
            popupMenu.show()
        }
        return true
    }

    private fun bindOwnedIdentity(ownedIdentity: OwnedIdentity?) {
        if (currentIdentityInitialView == null || currentNameTextView == null || currentNameSecondLineTextView == null || currentIdentityMutedImageView == null) {
            return
        }

        if (ownedIdentity == null) {
            currentIdentityInitialView!!.setUnknown()
            currentIdentityMutedImageView!!.visibility = View.GONE
            return
        }

        if (ownedIdentity.customDisplayName != null) {
            currentNameTextView!!.text = ownedIdentity.customDisplayName
            val identityDetails = ownedIdentity.getIdentityDetails()
            currentNameSecondLineTextView!!.visibility = View.VISIBLE
            if (identityDetails != null) {
                currentNameSecondLineTextView!!.text = identityDetails.formatDisplayName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                    uppercaseLastName
                )
            } else {
                currentNameSecondLineTextView!!.text = ownedIdentity.displayName
            }
        } else {
            val identityDetails = ownedIdentity.getIdentityDetails()
            if (identityDetails != null) {
                currentNameTextView!!.text = identityDetails.formatFirstAndLastName(
                    contactDisplayNameFormat,
                    uppercaseLastName
                )

                val posComp = identityDetails.formatPositionAndCompany(contactDisplayNameFormat)
                if (posComp != null) {
                    currentNameSecondLineTextView!!.visibility = View.VISIBLE
                    currentNameSecondLineTextView!!.text = posComp
                } else {
                    currentNameSecondLineTextView!!.visibility = View.GONE
                }
            } else {
                currentNameTextView!!.text = ownedIdentity.displayName
                currentNameSecondLineTextView!!.visibility = View.GONE
                currentNameSecondLineTextView!!.text = null
            }
        }
        currentIdentityInitialView!!.setOwnedIdentity(ownedIdentity)
        if (ownedIdentity.shouldMuteNotifications()) {
            currentIdentityMutedImageView!!.visibility = View.VISIBLE
        } else {
            currentIdentityMutedImageView!!.visibility = View.GONE
        }
    }

    private fun openSwitchProfilePopup() {
        if (anchor == null || adapter == null) {
            return
        }
        val eightDp = (resources.displayMetrics.density * 8).toInt()
        @SuppressLint("InflateParams") val popupView =
            layoutInflater.inflate(R.layout.popup_switch_owned_identity, null)
        popupWindow = PopupWindow(
            popupView,
            anchor!!.width - 10 * eightDp,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow!!.elevation = 12f
        popupWindow!!.setBackgroundDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.background_half_rounded_dialog
            )
        )
        popupWindow!!.setOnDismissListener { popupWindow = null }

        val ownedIdentityListRecyclerView =
            popupView.findViewById<EmptyRecyclerView>(R.id.owned_identity_list_recycler_view)
        ownedIdentityListRecyclerView.layoutManager = LinearLayoutManager(this)
        ownedIdentityListRecyclerView.adapter = adapter
        ownedIdentityListRecyclerView.setEmptyView(popupView.findViewById(R.id.empty_view))

        popupWindow!!.animationStyle = R.style.FadeInAndOutAnimation
        popupWindow!!.showAsDropDown(anchor, 5 * eightDp, 0)
    }

    private fun bindUsage(storageUsage: StorageUsage?) {
        if (sizePhotosView == null || sizeVideosView == null || sizeAudioView == null || sizeOtherView == null) {
            return
        }

        if (storageUsage == null) {
            totalUsageTextView.text = "-"
            (sizePhotosView!!.layoutParams as LayoutParams).weight = 0f
            (sizeVideosView!!.layoutParams as LayoutParams).weight = 0f
            (sizeAudioView!!.layoutParams as LayoutParams).weight = 0f
            (sizeOtherView!!.layoutParams as LayoutParams).weight = 0f
        } else {
            totalUsageTextView.text =
                Formatter.formatShortFileSize(
                    this,
                    storageUsage.total
                )
            (sizePhotosView!!.layoutParams as LayoutParams).weight =
                storageUsage.photos.toFloat() / storageUsage.total
            (sizeVideosView!!.layoutParams as LayoutParams).weight =
                storageUsage.videos.toFloat() / storageUsage.total
            (sizeAudioView!!.layoutParams as LayoutParams).weight =
                storageUsage.audio.toFloat() / storageUsage.total
            (sizeOtherView!!.layoutParams as LayoutParams).weight =
                storageUsage.other.toFloat() / storageUsage.total
        }

        usageBarLinearLayout.requestLayout()
    }


    private fun tabClicked(tabView: View) {
        val id = tabView.id
        if (id == R.id.tab_images_button) {
            viewPager.currentItem = 0
        } else if (id == R.id.tab_files_button) {
            viewPager.currentItem = 1
        } else if (id == R.id.tab_audio_button) {
            viewPager.currentItem = 2
        } else if (id == R.id.tab_all_button) {
            viewPager.currentItem = 3
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (audioAttachmentServiceBinding != null) {
            audioAttachmentServiceBinding!!.release()
        }
    }

    private inner class StoragePageChangeListener(private val buttonViews: Array<View>) :
        OnPageChangeCallback() {
        private val inactiveColor =
            ContextCompat.getColor(this@StorageManagerActivity, R.color.greyTint)
        private val activeColor =
            ContextCompat.getColor(
                this@StorageManagerActivity,
                R.color.olvid_gradient_light
            )

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            for (i in buttonViews.indices) {
                if (i == position) {
                    var color = -0x1000000
                    color =
                        color or ((positionOffset * (inactiveColor and 0xff) + (1 - positionOffset) * (activeColor and 0xff)).toInt() and 0xff)
                    color =
                        color or ((positionOffset * (inactiveColor and 0xff00) + (1 - positionOffset) * (activeColor and 0xff00)).toInt() and 0xff00)
                    color =
                        color or ((positionOffset * (inactiveColor and 0xff0000) + (1 - positionOffset) * (activeColor and 0xff0000)).toInt() and 0xff0000)
                    if (buttonViews[i] is ImageView) {
                        (buttonViews[i] as ImageView).setColorFilter(color)
                    } else if (buttonViews[i] is TextView) {
                        (buttonViews[i] as TextView).setTextColor(color)
                    }
                } else if (i == position + 1) {
                    var color = -0x1000000
                    color =
                        color or ((positionOffset * (activeColor and 0xff) + (1 - positionOffset) * (inactiveColor and 0xff)).toInt() and 0xff)
                    color =
                        color or ((positionOffset * (activeColor and 0xff00) + (1 - positionOffset) * (inactiveColor and 0xff00)).toInt() and 0xff00)
                    color =
                        color or ((positionOffset * (activeColor and 0xff0000) + (1 - positionOffset) * (inactiveColor and 0xff0000)).toInt() and 0xff0000)
                    if (buttonViews[i] is ImageView) {
                        (buttonViews[i] as ImageView).setColorFilter(color)
                    } else if (buttonViews[i] is TextView) {
                        (buttonViews[i] as TextView).setTextColor(color)
                    }
                } else {
                    if (buttonViews[i] is ImageView) {
                        (buttonViews[i] as ImageView).setColorFilter(inactiveColor)
                    } else if (buttonViews[i] is TextView) {
                        (buttonViews[i] as TextView).setTextColor(inactiveColor)
                    }
                }
            }
        }

        override fun onPageSelected(position: Int) {
            viewModel.clearSelectedFyles()
        }
    }


    private class OpenHiddenProfileDialog(activity: FragmentActivity) :
        io.olvid.messenger.customClasses.OpenHiddenProfileDialog(activity) {
        override fun onHiddenIdentityPasswordEntered(
            dialog: AlertDialog,
            byteOwnedIdentity: ByteArray
        ) {
            dialog.dismiss()
            AppSingleton.getInstance().selectIdentity(byteOwnedIdentity, null)
        }
    }
}
