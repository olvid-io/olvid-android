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
package io.olvid.messenger.discussion.compose

import android.Manifest.permission
import android.R.attr
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils.TruncateAt.END
import android.text.method.ArrowKeyMovementMethod
import android.util.TypedValue
import android.view.ActionMode
import android.view.ActionMode.Callback
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLayoutChangeListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupMenu.OnMenuItemClickListener
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat.SRC_IN
import androidx.core.view.doOnLayout
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.customClasses.DraftAttachmentAdapter
import io.olvid.messenger.customClasses.DraftAttachmentAdapter.AttachmentSpaceItemDecoration
import io.olvid.messenger.customClasses.EmptyRecyclerView
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.InitialView.Companion.getTextColor
import io.olvid.messenger.customClasses.JpegUtils
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog
import io.olvid.messenger.customClasses.MAX_BITMAP_SIZE
import io.olvid.messenger.customClasses.MarkdownBold
import io.olvid.messenger.customClasses.MarkdownCode
import io.olvid.messenger.customClasses.MarkdownHeading
import io.olvid.messenger.customClasses.MarkdownItalic
import io.olvid.messenger.customClasses.MarkdownListItem
import io.olvid.messenger.customClasses.MarkdownOrderedListItem
import io.olvid.messenger.customClasses.MarkdownQuote
import io.olvid.messenger.customClasses.MarkdownStrikeThrough
import io.olvid.messenger.customClasses.MessageAttachmentAdapter.AttachmentLongClickListener
import io.olvid.messenger.customClasses.MessageAttachmentAdapter.Visibility
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.customClasses.insertMarkdown
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask
import io.olvid.messenger.databases.tasks.ClearDraftReplyTask
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.databases.tasks.PostMessageInDiscussionTask
import io.olvid.messenger.databases.tasks.SaveDraftTask
import io.olvid.messenger.databases.tasks.SetDraftJsonExpirationTask
import io.olvid.messenger.databases.tasks.UpdateMessageBodyTask
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.DiscussionActivity.DiscussionDelegate
import io.olvid.messenger.discussion.DiscussionOwnedIdentityPopupWindow
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.Utils
import io.olvid.messenger.discussion.compose.VoiceMessageRecorder.RequestAudioPermissionDelegate
import io.olvid.messenger.discussion.compose.emoji.EmojiKeyboardFragment
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.discussion.location.SendLocationBasicDialogFragment
import io.olvid.messenger.discussion.location.SendLocationMapDialogFragment
import io.olvid.messenger.discussion.mention.MentionUrlSpan
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.discussion.mention.MentionViewModel.MentionStatus
import io.olvid.messenger.discussion.mention.MentionViewModel.MentionStatus.End
import io.olvid.messenger.discussion.mention.MentionViewModel.MentionStatus.Filter
import io.olvid.messenger.discussion.mention.MentionViewModel.MentionStatus.None
import io.olvid.messenger.fragments.FilteredContactListFragment
import io.olvid.messenger.fragments.FilteredContactListFragment.FilteredContactListOnClickDelegate
import io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.BASIC
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.MAPS
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.NONE
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.OSM
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

val View.screenLocation
    get(): IntArray {
        val point = IntArray(2)
        getLocationInWindow(point)
        return point
    }

class ComposeMessageFragment : Fragment(R.layout.fragment_discussion_compose), OnClickListener,
    AttachmentLongClickListener,
    OnMenuItemClickListener {
    private val discussionViewModel: DiscussionViewModel by activityViewModels { FACTORY }
    private val composeMessageViewModel: ComposeMessageViewModel by activityViewModels { FACTORY }
    private val linkPreviewViewModel: LinkPreviewViewModel by activityViewModels { FACTORY }
    private val mentionViewModel: MentionViewModel by activityViewModels { FACTORY }
    private val ephemeralViewModel: EphemeralViewModel by activityViewModels { FACTORY }
    private var audioAttachmentServiceBinding: AudioAttachmentServiceBinding? = null
    private var ownedIdentityInitialView: InitialView? = null
    var discussionDelegate: DiscussionDelegate? = null
    private var voiceMessageRecorder: VoiceMessageRecorder? = null
    private var newMessageEditText: DiscussionInputEditText? = null
    private var sendButton: ImageButton? = null
    private var hasCamera = false
    private var animateLayoutChanges = false
    private var attachStuffPlus: ImageView? = null
    private lateinit var attachStuffPlusGoldenDot: ImageView
    private lateinit var attachIconsGroup: LinearLayout
    private var directAttachVoiceMessageImageView: ImageView? = null
    private var composeMessageEditGroup: ViewGroup? = null
    private var composeMessageEditBody: TextView? = null
    private var composeMessageReplyGroup: ViewGroup? = null
    private var composeMessageReplyMessageId: Long = 0
    private var composeMessageReplySenderName: TextView? = null
    private var composeMessageReplyBody: TextView? = null
    private var composeMessageReplyAttachmentCount: TextView? = null
    private var composeMessageLinkPreviewGroup: ConstraintLayout? = null
    private var composeMessageLinkPreviewTitle: TextView? = null
    private var composeMessageLinkPreviewDescription: TextView? = null
    private var composeMessageLinkPreviewImage: ImageView? = null
    private var newMessageAttachmentAdapter: DraftAttachmentAdapter? = null
    private var mentionCandidatesSpacer: View? = null
    private val mentionCandidatesFragment by lazy { FilteredContactListFragment() }
    private lateinit var popupMenu: ComposeView
    private val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (ComposeMessageViewModel::class.java.isAssignableFrom(modelClass)) {
                try {
                    return modelClass.getConstructor(LiveData::class.java, LiveData::class.java)
                        .newInstance(
                            discussionViewModel.discussionIdLiveData,
                            discussionViewModel.discussionCustomization
                        )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return try {
                modelClass.getDeclaredConstructor().newInstance()
            } catch (e: java.lang.InstantiationException) {
                throw RuntimeException("Cannot create an instance of $modelClass", e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException("Cannot create an instance of $modelClass", e)
            }
        }
    }
    private var requestPermissionForPictureLauncher: ActivityResultLauncher<String>? = null
    private var requestPermissionForVideoLauncher: ActivityResultLauncher<String>? = null
    private var requestPermissionForAudioLauncher: ActivityResultLauncher<String>? = null
    private var requestPermissionForAudioAfterRationaleLauncher: ActivityResultLauncher<String>? =
        null
    private var attachFileLauncher: ActivityResultLauncher<Intent>? = null
    private var takePictureLauncher: ActivityResultLauncher<Intent>? = null
    private var takeVideoLauncher: ActivityResultLauncher<Intent>? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        requestPermissionForPictureLauncher = registerForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                takePicture()
            } else {
                App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT)
            }
        }
        requestPermissionForVideoLauncher = registerForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                takeVideo()
            } else {
                App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT)
            }
        }
        requestPermissionForAudioLauncher = registerForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                voiceMessageRecorder?.setRecordPermission(true)
                voiceMessageRecorder?.startRecord()
            } else {
                val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_voice_message_explanation)
                    .setMessage(R.string.dialog_message_voice_message_explanation_blocked)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setNeutralButton(R.string.button_label_app_settings) { _, _ ->
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                builder.create().show()
            }
        }
        requestPermissionForAudioAfterRationaleLauncher =
            registerForActivityResult(
                RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    voiceMessageRecorder?.setRecordPermission(true)
                    voiceMessageRecorder?.startRecord()
                } else {
                    App.toast(R.string.toast_message_audio_permission_denied, Toast.LENGTH_SHORT)
                }
            }
        attachFileLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { activityResult: ActivityResult? ->
            if (activityResult == null || activityResult.data == null || activityResult.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            val dataUri = activityResult.data!!.data
            val discussionId =
                discussionViewModel.discussionId ?: return@registerForActivityResult
            if (dataUri != null) {
                if (StringUtils.validateUri(dataUri)) {
                    App.runThread(AddFyleToDraftFromUriTask(dataUri, discussionId))
                }
            } else {
                val clipData = activityResult.data!!.clipData
                if (clipData != null) {
                    val uris: MutableSet<Uri> = HashSet()
                    // Samsung Android 7.0 bug --> different files may return the same uri!
                    for (i in 0 until clipData.itemCount) {
                        val item = clipData.getItemAt(i)
                        val uri = item.uri
                        uris.add(uri)
                    }
                    if (uris.size != clipData.itemCount) {
                        App.toast(
                            R.string.toast_message_android_bug_attach_duplicate_uri,
                            Toast.LENGTH_LONG
                        )
                    }
                    for (uri in uris) {
                        if (StringUtils.validateUri(uri)) {
                            App.runThread(AddFyleToDraftFromUriTask(uri, discussionId))
                        }
                    }
                }
            }
        }
        takePictureLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { activityResult: ActivityResult? ->
            if (activityResult == null || activityResult.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            val photoUri = composeMessageViewModel.photoOrVideoUri
            val photoFile = composeMessageViewModel.photoOrVideoFile
            val discussionId = discussionViewModel.discussionId
            if (photoUri != null && photoFile != null && discussionId != null) {
                App.runThread {
                    val cameraResolutionSetting = SettingsActivity.getCameraResolution()
                    if (cameraResolutionSetting != -1) {
                        JpegUtils.resize(photoFile, cameraResolutionSetting)
                    }
                    AddFyleToDraftFromUriTask(photoUri, photoFile, discussionId).run()
                }
            }
        }
        takeVideoLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { activityResult: ActivityResult? ->
            if (activityResult == null || activityResult.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            val videoUri = composeMessageViewModel.photoOrVideoUri
            val videoFile = composeMessageViewModel.photoOrVideoFile
            val discussionId = discussionViewModel.discussionId
            if (videoUri != null && videoFile != null) {
                App.runThread(AddFyleToDraftFromUriTask(videoUri, videoFile, discussionId))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        newMessageEditText = view.findViewById(R.id.compose_message_edit_text)
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            newMessageEditText?.imeOptions =
                newMessageEditText!!.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        newMessageEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(editable: Editable) {
                composeMessageViewModel.setNewMessageText(editable)
                hasText = true && editable.isNotEmpty()
                mentionViewModel.updateMentions(editable, newMessageEditText!!.selectionEnd)
                if (hasText) {
                    editable.formatMarkdown(
                        ContextCompat.getColor(
                            newMessageEditText!!.context,
                            R.color.olvid_gradient_contrasted
                        )
                    )
                    if (!setShowAttachIcons(show = false, preserveOldSelection = false)) {
                        updateComposeAreaLayout()
                    }
                } else {
                    updateComposeAreaLayout()
                }
                if (SettingsActivity.isLinkPreviewOutbound() && !isEditMode()) {
                    linkPreviewViewModel.findLinkPreview(
                        editable.toString(),
                        MAX_BITMAP_SIZE,
                        MAX_BITMAP_SIZE
                    )
                }
            }
        })
        newMessageEditText?.setOnSelectionChangeListener { editable: Editable?, start: Int, end: Int ->
            if (start == end) {
                mentionViewModel.updateMentions(editable, start)
            }
        }
        mentionViewModel.mentionsStatus.observe(viewLifecycleOwner) { mentionStatus: MentionStatus? ->
            if (newMessageEditText == null) {
                return@observe
            }
            if (mentionStatus is None) {
                // we no longer insert cancelled mentions --> this is already taken care of by the new mention detection technique
                hideMentionPicker()
            } else if (mentionStatus is Filter) {
                showMentionPicker()
                mentionCandidatesFragment.setFilter(mentionStatus.text)
            } else if (mentionStatus is End) {
                hideMentionPicker()
                val mention = mentionStatus.mention
                val contact = mentionStatus.contact
                val color = getTextColor(
                    newMessageEditText!!.context,
                    mention.userIdentifier,
                    AppSingleton.getContactCustomHue(mention.userIdentifier)
                )
                if (newMessageEditText?.text != null) {
                    var mentionText: String = try {
                        val identityDetails = AppSingleton.getJsonObjectMapper()
                            .readValue(contact.identityDetails, JsonIdentityDetails::class.java)
                        // use a standardized FIRST_LAST displayName for mentions, independent of the user's setting
                        "@" + identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                            false
                        )
                    } catch (e: Exception) {
                        // as a fallback, use the precomputed displayName
                        "@" + contact.displayName
                    }
                    mentionText += "\ufeff"
                    val editable = newMessageEditText!!.text
                    if (editable!!.length > mention.rangeEnd && editable[mention.rangeEnd] == ' ') {
                        // if there is already a space after our mention, remove it to avoid pointless double spaces
                        editable.replace(mention.rangeEnd, mention.rangeEnd + 1, "")
                    }
                    editable.replace(mention.rangeStart, mention.rangeEnd, "$mentionText ")
                    mention.rangeEnd = mention.rangeStart + mentionText.length
                    editable.setSpan(
                        MentionUrlSpan(
                            mention.userIdentifier,
                            mentionText.length,
                            color,
                            null
                        ),
                        mention.rangeStart,
                        mention.rangeStart + mentionText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    mentionViewModel.updateMentions(newMessageEditText!!.text, -2)
                    updateComposeAreaLayout()
                }
            }
        }
        mentionCandidatesSpacer = view.findViewById(R.id.mention_candidates_spacer)
        mentionCandidatesFragment.disableEmptyView()
        mentionCandidatesFragment.removeBottomPadding()
        mentionCandidatesFragment.setUnfilteredContacts(discussionViewModel.mentionCandidatesLiveData)
        mentionCandidatesFragment.disableAnimations()
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.mention_candidates_placeholder, mentionCandidatesFragment)
        transaction.hide(mentionCandidatesFragment)
        transaction.commit()
        mentionCandidatesFragment.setOnClickDelegate(object : FilteredContactListOnClickDelegate {
            override fun contactClicked(view: View, contact: Contact) {
                mentionViewModel.validateMention(contact)
            }

            override fun contactLongClicked(view: View, contact: Contact) {}
        })
        newMessageEditText?.setOnClickListener {
            setShowAttachIcons(
                show = false,
                preserveOldSelection = true
            )
        }
        newMessageEditText?.requestFocus()
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        newMessageEditText?.setImeContentCommittedHandler { contentUri: Uri?, fileName: String?, mimeType: String?, callMeWhenDone: Runnable? ->
            if (composeMessageViewModel.trimmedNewMessageText != null) {
                val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                    composeMessageViewModel.rawNewMessageText ?: "", mentionViewModel.mentions
                )
                SaveDraftTask(
                    discussionViewModel.discussionId,
                    trimAndMentions.first,
                    composeMessageViewModel.getDraftMessage().value,
                    trimAndMentions.second
                ).run()
            }
            AddFyleToDraftFromUriTask(
                contentUri!!,
                fileName,
                mimeType,
                discussionViewModel.discussionId
            ).run()
            callMeWhenDone?.run()
        }
        newMessageEditText?.setOnEditorActionListener(OnEditorActionListener { v: TextView, actionId: Int, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSendAction()
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                return@OnEditorActionListener true
            }
            false
        })
        if (SettingsActivity.getSendWithHardwareEnter()) {
            newMessageEditText?.setOnKeyListener(View.OnKeyListener { _: View?, keyCode: Int, event: KeyEvent ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed) {
                    onSendAction()
                    return@OnKeyListener true
                }
                false
            })
        }
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            newMessageEditText?.customSelectionActionModeCallback = object : Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(Menu.FIRST, 1111, 1, R.string.label_selection_formatting)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    // Possible improvement: don't show markdown menu if not relevant:
                    // - selection crosses already present inline markdown
                    // - selection is inside a code block or inline code
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId == 1111) {
                        val popupMenu = PopupMenu(activity, newMessageEditText)
                        popupMenu.inflate(R.menu.action_menu_text_selection)
                        popupMenu.setOnMenuItemClickListener { menuItem: MenuItem ->
                            when (menuItem.itemId) {
                                R.id.action_text_selection_bold -> {
                                    newMessageEditText?.insertMarkdown(MarkdownBold())
                                }

                                R.id.action_text_selection_italic -> {
                                    newMessageEditText?.insertMarkdown(MarkdownItalic())
                                }

                                R.id.action_text_selection_strikethrough -> {
                                    newMessageEditText?.insertMarkdown(MarkdownStrikeThrough())
                                }

                                R.id.action_text_selection_heading -> {
                                    return@setOnMenuItemClickListener false
                                }

                                R.id.action_text_selection_heading_1 -> {
                                    newMessageEditText?.insertMarkdown(MarkdownHeading(1))
                                }

                                R.id.action_text_selection_heading_2 -> {
                                    newMessageEditText?.insertMarkdown(MarkdownHeading(2))
                                }

                                R.id.action_text_selection_heading_3 -> {
                                    newMessageEditText?.insertMarkdown(MarkdownHeading(3))
                                }

                                R.id.action_text_selection_heading_4 -> {
                                    newMessageEditText?.insertMarkdown(MarkdownHeading(4))
                                }

                                R.id.action_text_selection_heading_5 -> {
                                    newMessageEditText?.insertMarkdown(MarkdownHeading(5))
                                }

                                R.id.action_text_selection_list -> {
                                    return@setOnMenuItemClickListener false
                                }

                                R.id.action_text_selection_list_bullet -> {
                                    newMessageEditText?.insertMarkdown(MarkdownListItem())
                                }

                                R.id.action_text_selection_list_ordered -> {
                                    newMessageEditText?.insertMarkdown(MarkdownOrderedListItem())
                                }

                                R.id.action_text_selection_quote -> {
                                    newMessageEditText?.insertMarkdown(MarkdownQuote())
                                }

                                R.id.action_text_selection_code -> {
                                    newMessageEditText?.insertMarkdown(MarkdownCode())
                                }

                                else -> {
                                    newMessageEditText?.insertMarkdown(null)
                                }
                            }
                            mode.finish()
                            true
                        }
                        popupMenu.show()
                        return true
                    }
                    return false
                }

                override fun onDestroyActionMode(mode: ActionMode) {}
            }
        }
        ownedIdentityInitialView = view.findViewById(R.id.owned_identity_initial_view)
        AppSingleton.getCurrentIdentityLiveData()
            .observe(viewLifecycleOwner) { ownedIdentity: OwnedIdentity? ->
                if (ownedIdentity == null) {
                    return@observe
                }
                ownedIdentityInitialView?.setOwnedIdentity(ownedIdentity)
            }
        ownedIdentityInitialView?.setOnClickListener { v: View ->
            val discussion = discussionViewModel.discussion.value
            if (discussion != null) {
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                val discussionOwnedIdentityPopupWindow = DiscussionOwnedIdentityPopupWindow(
                    requireActivity(),
                    ownedIdentityInitialView!!,
                    discussion.discussionType,
                    discussion.bytesDiscussionIdentifier
                )
                Handler(Looper.getMainLooper()).postDelayed(
                    { discussionOwnedIdentityPopupWindow.open() },
                    100
                )
            }
        }
        view.findViewById<View>(R.id.white_bottom_mask)
            .addOnLayoutChangeListener(composeMessageSizeChangeListener)
        sendButton = view.findViewById(R.id.compose_message_send_button)
        sendButton?.setOnClickListener(this)
        hasCamera =
            context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ?: false
        attachStuffPlus = view.findViewById(R.id.attach_stuff_plus)
        attachStuffPlus?.setOnClickListener(this)
        attachStuffPlusGoldenDot = view.findViewById(R.id.golden_dot)
        val voiceRecorderView = view.findViewById<ComposeView>(R.id.voice_recorder)
        voiceRecorderView.setContent {
            voiceMessageRecorder?.let {
                AppCompatTheme {
                    Box {
                        AnimatedVisibility(
                            visible = it.opened,
                            enter = slideInHorizontally { it / 2 }) {
                            SoundWave(voiceMessageRecorder?.soundWave ?: SampleAndTicker()) {
                                voiceMessageRecorder?.stopRecord(
                                    discard = false
                                )
                            }
                        }
                    }
                }
            }
        }
        voiceRecorderView.setOnClickListener(this)
        voiceMessageRecorder =
            VoiceMessageRecorder(
                activity = requireActivity(),
                requestAudioPermissionDelegate = object : RequestAudioPermissionDelegate {
                    override fun requestAudioPermission(rationaleWasShown: Boolean) {
                        if (rationaleWasShown) {
                            requestPermissionForAudioAfterRationaleLauncher?.launch(permission.RECORD_AUDIO)
                        } else {
                            requestPermissionForAudioLauncher?.launch(permission.RECORD_AUDIO)
                        }
                    }
                }
            )
        directAttachVoiceMessageImageView =
            view.findViewById(R.id.direct_attach_voice_message_button)
        directAttachVoiceMessageImageView?.setOnTouchListener(voiceMessageRecorder)

        attachIconsGroup = view.findViewById(R.id.attach_icons_group)
        popupMenu = view.findViewById(R.id.popup_menu)
        popupMenu.setContent {
            AppCompatTheme {
                EphemeralSettingsGroup(
                    ephemeralViewModel = ephemeralViewModel,
                    expanded = composeMessageViewModel.openEphemeralSettings
                ) {
                    if (ephemeralViewModel.getValid()
                            .value == true
                    ) {
                        val jsonExpiration = JsonExpiration()
                        if (ephemeralViewModel.getReadOnce()) {
                            jsonExpiration.setReadOnce(true)
                        }
                        jsonExpiration.setVisibilityDuration(ephemeralViewModel.getVisibility())
                        jsonExpiration.setExistenceDuration(ephemeralViewModel.getExistence())
                        App.runThread(
                            SetDraftJsonExpirationTask(
                                discussionViewModel.discussionId,
                                jsonExpiration
                            )
                        )
                    }
                    composeMessageViewModel.openEphemeralSettings = false
                }
            }
        }

        val composeMessageCard =
            view.findViewById<ViewGroup>(R.id.compose_message_card)
        composeMessageEditGroup = view.findViewById(R.id.compose_message_edit_group)
        composeMessageEditBody = view.findViewById(R.id.compose_message_edit_body)
        val composeMessageEditClear =
            view.findViewById<ImageView>(R.id.compose_message_edit_clear)
        composeMessageEditClear.setOnClickListener {
            newMessageEditText?.setText("")
            composeMessageViewModel.clearDraftMessageEdit()
            composeMessageDelegate.hideSoftInputKeyboard()
        }
        composeMessageReplyGroup =
            view.findViewById(R.id.compose_message_reply_group)
        composeMessageReplySenderName =
            view.findViewById(R.id.compose_message_reply_sender_name)
        composeMessageReplyBody = view.findViewById(R.id.compose_message_reply_body)
        composeMessageReplyAttachmentCount =
            view.findViewById(R.id.compose_message_reply_attachment_count)
        val composeMessageReplyClear =
            view.findViewById<ImageView>(R.id.compose_message_reply_clear)
        composeMessageReplyClear.setOnClickListener {
            App.runThread(
                ClearDraftReplyTask(
                    discussionViewModel.discussionId
                )
            )
        }
        composeMessageReplyGroup?.setOnClickListener {
            discussionDelegate?.scrollToMessage(composeMessageReplyMessageId)
        }

        // link preview
        composeMessageLinkPreviewGroup =
            view.findViewById(R.id.message_link_preview_group)
        composeMessageLinkPreviewTitle =
            view.findViewById(R.id.message_link_preview_title)
        composeMessageLinkPreviewImage =
            view.findViewById(R.id.message_link_preview_image)
        composeMessageLinkPreviewDescription =
            view.findViewById(R.id.message_link_preview_description)
        val composeMessageLinkPreviewClear =
            view.findViewById<ImageView>(R.id.message_link_preview_clear)
        composeMessageLinkPreviewClear.setOnClickListener { linkPreviewViewModel.clearLinkPreview() }
        linkPreviewViewModel.openGraph.observe(
            viewLifecycleOwner,
            Observer { openGraph: OpenGraph? ->
                if (composeMessageLinkPreviewGroup == null) {
                    return@Observer
                }
                if (openGraph != null && !openGraph.isEmpty()) {
                    composeMessageLinkPreviewGroup?.visibility = View.VISIBLE
                    val uri = openGraph.getSafeUri()
                    if (uri != null) {
                        composeMessageLinkPreviewGroup?.setOnClickListener {
                            App.openLink(activity, uri)
                        }
                    } else {
                        composeMessageLinkPreviewGroup?.setOnClickListener(null)
                    }
                    composeMessageLinkPreviewTitle?.text = openGraph.title
                    composeMessageLinkPreviewDescription?.text = openGraph.buildDescription()
                    if (openGraph.bitmap != null) {
                        composeMessageLinkPreviewImage?.setImageBitmap(openGraph.bitmap)
                    } else {
                        composeMessageLinkPreviewImage?.setImageResource(R.drawable.mime_type_icon_link)
                    }
                } else {
                    composeMessageLinkPreviewGroup?.visibility = View.GONE
                }
            })

        // attachments recycler view
        val newMessageAttachmentRecyclerView =
            composeMessageCard.findViewById<EmptyRecyclerView>(R.id.attachments_recycler_view)

        if (audioAttachmentServiceBinding == null) {
            audioAttachmentServiceBinding =
                (activity as? DiscussionActivity)?.audioAttachmentServiceBinding
            if (audioAttachmentServiceBinding == null) {
                activity?.finish()
                return
            }
        }

        newMessageAttachmentAdapter =
            DraftAttachmentAdapter(activity, audioAttachmentServiceBinding!!)
        newMessageAttachmentAdapter?.setAttachmentLongClickListener(this)
        val attachmentListLinearLayoutManager = LinearLayoutManager(activity)
        attachmentListLinearLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
        newMessageAttachmentRecyclerView.layoutManager = attachmentListLinearLayoutManager
        newMessageAttachmentRecyclerView.adapter = newMessageAttachmentAdapter
        newMessageAttachmentRecyclerView.setHideIfEmpty(true)
        newMessageAttachmentRecyclerView.addItemDecoration(AttachmentSpaceItemDecoration(activity))
        composeMessageViewModel.getDraftMessageFyles().observe(
            viewLifecycleOwner,
            newMessageAttachmentAdapter!!
        )
        composeMessageViewModel.getDraftMessageFyles()
            .observe(viewLifecycleOwner) { fyleAndStatuses: List<FyleAndStatus?>? ->
                hasAttachments = fyleAndStatuses.isNullOrEmpty().not()
                updateComposeAreaLayout()
            }
        composeMessageViewModel.getDraftMessage()
            .observe(viewLifecycleOwner, object : Observer<Message?> {
                private var message: Message? = null
                override fun onChanged(value: Message?) {
                    if (value != null && this.message != null) {
                        if (value.id == this.message?.id) {
                            val messageEditText =
                                if (newMessageEditText?.text == null) "" else newMessageEditText?.text
                                    .toString()
                            val oldMessageText =
                                if (this.message!!.contentBody == null) "" else this.message!!.contentBody!!
                            val newMessageText =
                                if (value.contentBody == null) "" else value.contentBody!!
                            val oldMentions =
                                if (this.message!!.jsonMentions == null) "" else this.message!!.jsonMentions!!
                            val newMentions =
                                if (value.jsonMentions == null) "" else value.jsonMentions!!
                            if (messageEditText == oldMessageText && oldMessageText != newMessageText || oldMentions != newMentions) {
                                // the message text changed, but the input did not --> probably an external modification so we load the body from the new draft
                                loadDraft(value)
                            }
                            this.message = value
                            return
                        }
                    }
                    this.message = value
                    loadDraft(value)
                }

                private fun loadDraft(draftMessage: Message?) {
                    if (draftMessage?.contentBody != null) {
                        if (newMessageEditText?.text != null && draftMessage.contentBody != newMessageEditText!!.text
                                .toString()
                        ) {
                            try {
                                val spannableString = SpannableString(draftMessage.contentBody)
                                if (draftMessage.mentions != null) {
                                    for (mention in draftMessage.mentions) {
                                        if (mention.rangeEnd <= spannableString.length) {
                                            val color = getTextColor(
                                                view.context,
                                                mention.userIdentifier,
                                                AppSingleton.getContactCustomHue(mention.userIdentifier)
                                            )
                                            spannableString.setSpan(
                                                MentionUrlSpan(
                                                    mention.userIdentifier,
                                                    mention.length,
                                                    color,
                                                    null
                                                ),
                                                mention.rangeStart,
                                                mention.rangeEnd,
                                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                            )
                                        }
                                    }
                                }
                                newMessageEditText?.setText(
                                    Utils.protectMentionUrlSpansWithFEFF(
                                        spannableString
                                    )
                                )
                                newMessageEditText?.setSelection(
                                    newMessageEditText?.text?.length ?: 0
                                )
                                setShowAttachIcons(show = true, preserveOldSelection = true)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            })

        // edit message
        composeMessageViewModel.getDraftMessageEdit()
            .observe(viewLifecycleOwner) { editMessage: Message? ->
                if (editMessage != null) {
                    composeMessageEditGroup!!.visibility = View.VISIBLE
                    Utils.applyBodyWithSpans(
                        composeMessageEditBody!!,
                        editMessage.senderIdentifier,
                        editMessage,
                        null,
                        false,
                        true
                    )
                    newMessageEditText?.setHint(R.string.label_edit_your_message)
                    composeMessageEditGroup!!.setOnClickListener {
                        if (discussionDelegate != null) {
                            discussionDelegate!!.scrollToMessage(editMessage.id)
                        }
                    }
                    linkPreviewViewModel.reset()
                    if (newMessageEditText?.text != null) {
                        newMessageEditText?.setText(
                            Utils.protectMentionUrlSpansWithFEFF(
                                newMessageEditText!!.text
                            )
                        )
                        Utils.applyBodyWithSpans(
                            newMessageEditText!!,
                            editMessage.senderIdentifier,
                            editMessage,
                            null,
                            false,
                            false
                        )
                        newMessageEditText?.setSelection(newMessageEditText?.text?.length ?: 0)
                    }
                } else {
                    composeMessageEditGroup!!.visibility = View.GONE
                    composeMessageEditGroup!!.setOnClickListener(null)
                    newMessageEditText?.setHint(R.string.hint_compose_your_message)
                }
                context?.resources?.displayMetrics?.widthPixels?.let { updateIconsToShow(it) }
            }

        // reply message
        composeMessageViewModel.getDraftMessageReply()
            .observe(viewLifecycleOwner) { draftReplyMessage: Message? ->
                if (draftReplyMessage == null) {
                    composeMessageReplyGroup?.visibility = View.GONE
                    composeMessageReplyMessageId = -1
                } else {
                    composeMessageReplyGroup?.visibility = View.VISIBLE
                    composeMessageReplyMessageId = draftReplyMessage.id
                    val displayName =
                        AppSingleton.getContactCustomDisplayName(draftReplyMessage.senderIdentifier)
                    if (displayName != null) {
                        composeMessageReplySenderName?.text = displayName
                    } else {
                        composeMessageReplySenderName?.setText(R.string.text_deleted_contact)
                    }
                    val color = getTextColor(
                        requireContext(),
                        draftReplyMessage.senderIdentifier,
                        AppSingleton.getContactCustomHue(draftReplyMessage.senderIdentifier)
                    )
                    composeMessageReplySenderName?.setTextColor(color)
                    val drawable = ContextCompat.getDrawable(
                        requireContext(), R.drawable.background_reply_white
                    )
                    if (drawable is LayerDrawable) {
                        val border = drawable.findDrawableByLayerId(R.id.reply_color_border)
                        border.colorFilter =
                            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                                color,
                                SRC_IN
                            )
                        drawable.setDrawableByLayerId(R.id.reply_color_border, border)
                        composeMessageReplyGroup?.background = drawable
                    }
                    if (draftReplyMessage.totalAttachmentCount > 0) {
                        composeMessageReplyAttachmentCount?.visibility = View.VISIBLE
                        composeMessageReplyAttachmentCount?.text =
                            context?.resources?.getQuantityString(
                                R.plurals.text_reply_attachment_count,
                                draftReplyMessage.totalAttachmentCount,
                                draftReplyMessage.totalAttachmentCount
                            )
                    } else {
                        composeMessageReplyAttachmentCount?.visibility = View.GONE
                    }
                    if (draftReplyMessage.getStringContent(activity).isEmpty()) {
                        composeMessageReplyBody?.visibility = View.GONE
                    } else {
                        composeMessageReplyBody?.visibility = View.VISIBLE
                        composeMessageReplyBody?.let {
                            Utils.applyBodyWithSpans(
                                it,
                                if (discussionViewModel.discussion.value != null) discussionViewModel.discussion.value!!.bytesOwnedIdentity else null,
                                draftReplyMessage,
                                null,
                                true,
                                true
                            )
                        }
                    }
                }
            }
        composeMessageViewModel.ephemeralSettingsChanged.observe(viewLifecycleOwner) { changed: Boolean? ->
            hideOrShowEphemeralMarker(
                changed != null && changed
            )
        }
        composeMessageViewModel.getRecordingLiveData()
            .observe(viewLifecycleOwner) { recording: Boolean ->
                this.recording = recording
                if (recording) {
                    setShowAttachIcons(
                        show = false,
                        preserveOldSelection = true
                    )
                }
            }
        context?.resources?.displayMetrics?.widthPixels?.let {
            updateIconsToShow(it)
        }
    }

    fun setAudioAttachmentServiceBinding(audioAttachmentServiceBinding: AudioAttachmentServiceBinding?) {
        this.audioAttachmentServiceBinding = audioAttachmentServiceBinding
    }

    private fun isEditMode(): Boolean = composeMessageViewModel.getDraftMessageEdit().value != null

    private fun onSendAction() {
        if (isEditMode()) {
            if (composeMessageViewModel.getDraftMessageEdit().value != null) {
                editMessage(composeMessageViewModel.getDraftMessageEdit().value!!.id)
            }
        } else {
            sendMessage()
        }
    }

    private var sending = false
    private fun sendMessage() {
        if (sending) {
            return
        }
        composeMessageLinkPreviewGroup!!.visibility = View.GONE
        if (discussionViewModel.discussionId != null) {
            if (composeMessageViewModel.trimmedNewMessageText != null || composeMessageViewModel.hasAttachments() || recording) {
                if (discussionDelegate != null) {
                    discussionDelegate!!.markMessagesRead()
                }
                val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                    composeMessageViewModel.rawNewMessageText ?: "",
                    mentionViewModel.mentions
                )

                // if there is a link preview currently loading, delay message sending a bit (max 2 second)
                sending = true
                sendButton?.isEnabled = false
                linkPreviewViewModel.waitForPreview {
                    App.runThread {
                        if (recording) {
                            voiceMessageRecorder?.stopRecord(discard = false, async = false)
                        }
                        PostMessageInDiscussionTask(
                            trimAndMentions.first,
                            discussionViewModel.discussionId,
                            true,
                            linkPreviewViewModel.openGraph.value,
                            trimAndMentions.second
                        ).run()
                    }
                    sending = false
                    linkPreviewViewModel.reset()
                    newMessageEditText?.setText("")
                    sendButton?.isEnabled = true
                }
            }
        }
    }

    private fun editMessage(messageId: Long) {
        composeMessageEditGroup!!.visibility = View.GONE
        composeMessageLinkPreviewGroup!!.visibility = View.GONE
        if (composeMessageViewModel.trimmedNewMessageText != null) {
            val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                composeMessageViewModel.rawNewMessageText ?: "", mentionViewModel.mentions
            )
            App.runThread(
                UpdateMessageBodyTask(
                    messageId,
                    trimAndMentions.first,
                    trimAndMentions.second
                )
            )
        }
        newMessageEditText?.setText("")
        composeMessageViewModel.clearDraftMessageEdit()
        linkPreviewViewModel.reset()
        composeMessageDelegate.showSoftInputKeyboard()
    }

    override fun onResume() {
        super.onResume()
        if (newMessageEditText != null) {
            try {
                if (composeMessageViewModel.rawNewMessageText != null) {
                    try {
                        newMessageEditText?.setText(composeMessageViewModel.rawNewMessageText)
                        newMessageEditText?.setSelection(
                            composeMessageViewModel.rawNewMessageText?.length ?: 0
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    newMessageEditText?.setText("")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateComposeAreaLayout()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceMessageRecorder?.stopRecord(true)
    }

    private fun hideMentionPicker() {
        if (mentionCandidatesFragment.isVisible) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.hide(mentionCandidatesFragment)
            transaction.commit()
            mentionCandidatesSpacer!!.visibility = View.GONE
        }
    }

    private fun showMentionPicker() {
        if (mentionCandidatesFragment.isHidden) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.show(mentionCandidatesFragment)
            transaction.commit()
            mentionCandidatesSpacer!!.visibility = View.INVISIBLE
        }
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.attach_timer) {
            composeMessageViewModel.openEphemeralSettings =
                !composeMessageViewModel.openEphemeralSettings
            if (emojiKeyboardShowing) {
                hideEmojiKeyboard()
            }
            if (composeMessageViewModel.openEphemeralSettings) {
                ephemeralViewModel.setDiscussionId(discussionViewModel.discussionId, false)
            }
        } else if (id == R.id.compose_message_send_button) {
            onSendAction()
        } else if (id == R.id.attach_configure) {
            showIconOrderSelector()
        } else if (id == R.id.attach_file) {
            if (discussionDelegate != null) {
                discussionDelegate!!.doNotMarkAsReadOnPause()
            }
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                .setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            App.prepareForStartActivityForResult(this)
            attachFileLauncher!!.launch(intent)
        } else if (id == R.id.attach_image) {
            if (discussionDelegate != null) {
                discussionDelegate!!.doNotMarkAsReadOnPause()
            }
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                .setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            App.prepareForStartActivityForResult(this)
            attachFileLauncher!!.launch(intent)
        } else if (id == R.id.attach_camera) {
            if (hasCamera) {
                if (ContextCompat.checkSelfPermission(
                        view.context,
                        permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionForPictureLauncher!!.launch(permission.CAMERA)
                } else {
                    takePicture()
                }
            } else {
                App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT)
            }
        } else if (id == R.id.attach_video) {
            if (hasCamera) {
                if (ContextCompat.checkSelfPermission(
                        view.context,
                        permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionForVideoLauncher!!.launch(permission.CAMERA)
                } else {
                    takeVideo()
                }
            } else {
                App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT)
            }
        } else if (id == R.id.attach_emoji) {
            showEmojiKeyboard()
        } else if (id == R.id.attach_location) {
            // if currently sharing location: stop sharing location
            if (LocationSharingSubService.isDiscussionSharingLocation(discussionViewModel.discussionId)) {
                SecureAlertDialogBuilder(view.context, R.style.CustomAlertDialog)
                    .setTitle(R.string.title_stop_sharing_location)
                    .setMessage(R.string.label_stop_sharing_location)
                    .setPositiveButton(R.string.button_label_stop) { _, _ ->
                        LocationSharingSubService.stopSharingInDiscussion(
                            discussionViewModel.discussionId, false
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .create()
                    .show()
                return
            }
            when (SettingsActivity.getLocationIntegration()) {
                OSM, MAPS, CUSTOM_OSM -> {
                    val dialogFragment = SendLocationMapDialogFragment.newInstance(
                        discussionViewModel.discussionId,
                        SettingsActivity.getLocationIntegration()
                    )
                    dialogFragment.show(childFragmentManager, "send-location-fragment-osm")
                }

                BASIC -> {
                    val dialogFragment =
                        SendLocationBasicDialogFragment.newInstance(discussionViewModel.discussionId)
                    dialogFragment.show(childFragmentManager, "send-location-fragment-basic")
                }

                NONE -> {
                    LocationIntegrationSelectorDialog(
                        view.context,
                        false,
                        object : LocationIntegrationSelectorDialog.OnIntegrationSelectedListener {
                            override fun onIntegrationSelected(
                                integration: LocationIntegrationEnum,
                                customOsmServerUrl: String?
                            ) {
                                SettingsActivity.setLocationIntegration(
                                    integration.string,
                                    customOsmServerUrl
                                )
                                // re-run onClick if something was selected
                                if (integration == OSM || integration == MAPS || integration == BASIC || integration == CUSTOM_OSM) {
                                    onClick(view)
                                }
                            }
                        }).show()
                }
            }
        } else if (id == R.id.attach_stuff_plus) {
            if (isEditMode()) {
                showEmojiKeyboard()
            } else if (showAttachIcons || neverOverflow) {
                val imm =
                    view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(newMessageEditText?.windowToken, 0)
                Handler(Looper.getMainLooper()).postDelayed({ showOverflowedPopupMenu() }, 100)
            } else {
                setShowAttachIcons(show = true, preserveOldSelection = true)
            }
        }
    }

    private fun takePicture() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        composeMessageViewModel.photoOrVideoUri = null
        composeMessageViewModel.photoOrVideoFile = null
        if (context?.packageManager?.let { takePictureIntent.resolveActivity(it) } != null) {
            val context = context ?: return
            val photoDir = File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
            val photoFile = File(
                photoDir, SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
                    Date()
                ) + ".jpg"
            )
            try {
                photoDir.mkdirs()
                if (!photoFile.createNewFile()) {
                    return
                }
                composeMessageViewModel.photoOrVideoFile = photoFile
                val photoUri = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                    photoFile
                )
                composeMessageViewModel.photoOrVideoUri = photoUri
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                if (discussionDelegate != null) {
                    discussionDelegate!!.doNotMarkAsReadOnPause()
                }
                App.prepareForStartActivityForResult(this)
                takePictureLauncher!!.launch(takePictureIntent)
            } catch (e: IOException) {
                Logger.w("Error creating photo capture file $photoFile")
            }
        }
    }

    private fun takeVideo() {
        val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        composeMessageViewModel.photoOrVideoUri = null
        composeMessageViewModel.photoOrVideoFile = null
        if (context?.packageManager?.let { takeVideoIntent.resolveActivity(it) } != null) {
            val context = context ?: return
            val videoDir = File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
            val videoFile = File(
                videoDir, SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
                    Date()
                ) + ".mp4"
            )
            try {
                videoDir.mkdirs()
                if (!videoFile.createNewFile()) {
                    return
                }
                composeMessageViewModel.photoOrVideoFile = videoFile
                val photoUri = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                    videoFile
                )
                composeMessageViewModel.photoOrVideoUri = photoUri
                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                if (discussionDelegate != null) {
                    discussionDelegate!!.doNotMarkAsReadOnPause()
                }
                App.prepareForStartActivityForResult(this)
                takeVideoLauncher!!.launch(takeVideoIntent)
            } catch (e: IOException) {
                Logger.w("Error creating video capture file $videoFile")
            }
        }
    }

    private var longClickedFyleAndStatus: FyleAndStatus? = null
    override fun attachmentLongClicked(
        longClickedFyleAndStatus: FyleAndStatus,
        clickedView: View,
        visibility: Visibility,
        readOnce: Boolean,
        multipleAttachments: Boolean
    ) {
        this.longClickedFyleAndStatus = longClickedFyleAndStatus
        val popup = PopupMenu(activity, clickedView)
        popup.inflate(R.menu.popup_attachment_incomplete_or_draft)
        popup.setOnMenuItemClickListener(this)
        popup.show()
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        val itemId = menuItem.itemId
        if (itemId == R.id.popup_action_delete_attachment) {
            val builder = context?.let {
                SecureAlertDialogBuilder(it, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_attachment)
                    .setMessage(
                        getString(
                            R.string.dialog_message_delete_attachment,
                            longClickedFyleAndStatus!!.fyleMessageJoinWithStatus.fileName
                        )
                    )
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        App.runThread(
                            DeleteAttachmentTask(longClickedFyleAndStatus)
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
            }
            builder?.create()?.show()
            return true
        } else if (itemId == R.id.popup_action_open_attachment) {
            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
                    PreviewUtils.getNonNullMimeType(
                        longClickedFyleAndStatus!!.fyleMessageJoinWithStatus.mimeType,
                        longClickedFyleAndStatus!!.fyleMessageJoinWithStatus.fileName
                    )
                ) && SettingsActivity.useInternalImageViewer()
            ) {
                // we do not mark as opened here as this is done in the gallery activity
                App.openDiscussionGalleryActivity(
                    activity,
                    discussionViewModel.discussionId,
                    longClickedFyleAndStatus!!.fyleMessageJoinWithStatus.messageId,
                    longClickedFyleAndStatus!!.fyleMessageJoinWithStatus.fyleId,
                    true
                )
                if (discussionDelegate != null) {
                    discussionDelegate!!.doNotMarkAsReadOnPause()
                }
            } else {
                App.openFyleInExternalViewer(activity, longClickedFyleAndStatus) {
                    if (discussionDelegate != null) {
                        discussionDelegate!!.doNotMarkAsReadOnPause()
                    }
                    longClickedFyleAndStatus!!.fyleMessageJoinWithStatus.markAsOpened()
                }
            }
            return true
        }
        return false
    }

    // region implement ComposeMessageDelegate
    interface ComposeMessageDelegate {
        fun setDiscussionId(discussionId: Long)
        fun hideSoftInputKeyboard()
        fun showSoftInputKeyboard()
        fun stopVoiceRecorderIfRecording(): Boolean // returns true if a recording was indeed in progress
        fun addComposeMessageHeightListener(composeMessageHeightListener: ComposeMessageHeightListener)
        fun removeComposeMessageHeightListener(composeMessageHeightListener: ComposeMessageHeightListener)
        fun setAnimateLayoutChanges(animateLayoutChanges: Boolean)
        fun setEmojiKeyboardAttachDelegate(emojiKeyboardAttachDelegate: EmojiKeyboardAttachDelegate?)
    }

    val composeMessageDelegate: ComposeMessageDelegate = object : ComposeMessageDelegate {
        override fun setDiscussionId(discussionId: Long) {
            if (newMessageEditText != null) {
                newMessageEditText?.setText("")
            }
            if (newMessageAttachmentAdapter != null) {
                newMessageAttachmentAdapter!!.setDiscussionId(discussionId)
            }
        }

        override fun hideSoftInputKeyboard() {
            if (activity != null) {
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                if (imm != null && newMessageEditText != null) {
                    imm.hideSoftInputFromWindow(newMessageEditText?.windowToken, 0)
                }
                if (emojiKeyboardShowing) {
                    hideEmojiKeyboard()
                }
                setShowAttachIcons(show = true, preserveOldSelection = true)
            }
        }

        override fun showSoftInputKeyboard() {
            if (activity != null) {
                if (!emojiKeyboardShowing) {
                    (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(
                        newMessageEditText,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }
                setShowAttachIcons(show = false, preserveOldSelection = true)
            }
        }

        override fun stopVoiceRecorderIfRecording(): Boolean {
            if (voiceMessageRecorder?.isRecording() == true) {
                voiceMessageRecorder?.stopRecord(true)
                return true
            }
            return false
        }

        override fun addComposeMessageHeightListener(composeMessageHeightListener: ComposeMessageHeightListener) {
            synchronized(composeMessageHeightListeners) {
                composeMessageHeightListeners.add(
                    composeMessageHeightListener
                )
            }
        }

        override fun removeComposeMessageHeightListener(composeMessageHeightListener: ComposeMessageHeightListener) {
            synchronized(composeMessageHeightListeners) {
                composeMessageHeightListeners.remove(
                    composeMessageHeightListener
                )
            }
        }

        override fun setAnimateLayoutChanges(animateLayoutChanges: Boolean) {
            this@ComposeMessageFragment.animateLayoutChanges = animateLayoutChanges
        }

        override fun setEmojiKeyboardAttachDelegate(emojiKeyboardAttachDelegate: EmojiKeyboardAttachDelegate?) {
            this@ComposeMessageFragment.emojiKeyboardAttachDelegate = emojiKeyboardAttachDelegate
        }
    }
    private var adapterIcons: MutableList<Int>? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private fun showIconOrderSelector() {
        val inflater = LayoutInflater.from(activity)
        val iconOrderRecyclerView = RecyclerView(inflater.context)
        iconOrderRecyclerView.setPadding(0, 2 * fourDp, 0, 0)
        iconOrderRecyclerView.layoutManager = LinearLayoutManager(activity)
        val diffUtilCallback: ItemCallback<Int> = object : ItemCallback<Int>() {
            override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                return oldItem == newItem
            }
        }
        val adapter: ListAdapter<Int, IconOrderViewHolder> =
            object : ListAdapter<Int, IconOrderViewHolder>(diffUtilCallback) {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): IconOrderViewHolder {
                    return if (viewType == VIEW_TYPE_ICON) {
                        IconOrderViewHolder(
                            inflater.inflate(
                                R.layout.item_view_attach_icon_orderer,
                                parent,
                                false
                            ),
                            object : OnHandlePressedListener {
                                override fun onHandlePressed(iconOrderViewHolder: IconOrderViewHolder?) {
                                    if (itemTouchHelper != null) {
                                        itemTouchHelper!!.startDrag(iconOrderViewHolder!!)
                                    }
                                }
                            })
                    } else {
                        IconOrderViewHolder(
                            inflater.inflate(
                                R.layout.item_view_attach_icon_orderer_separator,
                                parent,
                                false
                            ), null
                        )
                    }
                }

                override fun onBindViewHolder(holder: IconOrderViewHolder, position: Int) {
                    val icon = adapterIcons!![position]
                    if (icon != -1) {
                        holder.textView.setText(getStringResourceForIcon(icon))
                        holder.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            getImageResourceForIcon(icon),
                            0,
                            0,
                            0
                        )
                    }
                }

                override fun getItemViewType(position: Int): Int {
                    return if (adapterIcons!![position] == -1) {
                        VIEW_TYPE_SEPARATOR
                    } else {
                        VIEW_TYPE_ICON
                    }
                }
            }
        iconOrderRecyclerView.adapter = adapter
        val simpleCallback: SimpleCallback =
            object : SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: ViewHolder,
                    target: ViewHolder
                ): Boolean {
                    val from = viewHolder.absoluteAdapterPosition
                    val to = target.absoluteAdapterPosition
                    val fromVal = adapterIcons!![from]
                    adapterIcons!![from] = adapterIcons!![to]
                    adapterIcons!![to] = fromVal
                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun getDragDirs(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
                    val pos = viewHolder.absoluteAdapterPosition
                    return if (adapterIcons!![pos] == -1) {
                        0
                    } else super.getDragDirs(recyclerView, viewHolder)
                }

                override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder.itemView.alpha = .5f
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.alpha = 1f
                }

                override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                    // no swipe here
                }
            }
        itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper!!.attachToRecyclerView(iconOrderRecyclerView)
        adapterIcons = SettingsActivity.getComposeMessageIconPreferredOrder()
        if (adapterIcons == null) {
            adapterIcons = DEFAULT_ICON_ORDER.toMutableList()
            adapterIcons?.add(-1)
        } else if (adapterIcons!!.size < DEFAULT_ICON_ORDER.size) {
            adapterIcons!!.add(-1)
            for (icon in DEFAULT_ICON_ORDER) {
                if (!adapterIcons!!.contains(icon)) {
                    adapterIcons!!.add(icon)
                }
            }
        } else {
            adapterIcons!!.add(-1)
        }
        adapter.submitList(adapterIcons)
        val builder = Builder(
            iconOrderRecyclerView.context, R.style.CustomAlertDialog
        )
            .setTitle(R.string.dialog_title_choose_icon_order)
            .setView(iconOrderRecyclerView)
            .setPositiveButton(R.string.button_label_save) { _: DialogInterface?, _: Int ->
                val end = adapterIcons!!.indexOf(-1)
                SettingsActivity.setComposeMessageIconPreferredOrder(
                    (if (end == -1) adapterIcons else adapterIcons!!.subList(
                        0,
                        end
                    ))!!
                )
                updateIconsToShow(previousWidth)
            }
            .setNegativeButton(R.string.button_label_cancel, null)
        builder.create().show()
    }

    private interface OnHandlePressedListener {
        fun onHandlePressed(iconOrderViewHolder: IconOrderViewHolder?)
    }

    private class IconOrderViewHolder @SuppressLint("ClickableViewAccessibility") constructor(
        itemView: View,
        onHandlePressedListener: OnHandlePressedListener?
    ) : ViewHolder(itemView) {
        val textView: TextView
        val handle: View?

        init {
            textView = itemView.findViewById(R.id.icon_text_view)
            handle = itemView.findViewById(R.id.handle)
            if (handle != null && onHandlePressedListener != null) {
                handle.setOnTouchListener(OnTouchListener { _: View?, event: MotionEvent ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onHandlePressedListener.onHandlePressed(this)
                    }
                    true
                })
            }
        }
    }

    // endregion
    // region Compose area layout
    private var iconSize = 0
    private var fourDp = 0
    private var previousWidth = 0
    private val iconsShown: MutableList<Int> = ArrayList()
    private val iconsOverflow: MutableList<Int> = DEFAULT_ICON_ORDER.toMutableList()
    private var neverOverflow = false
    private var recording = false
    private var hasAttachments = false
    private var hasText = false
    private var showAttachIcons = true
    private var showEphemeralMarker = false
    private var previousSelectionStart = -1
    private var previousSelectionEnd = -1

    // return true if an updateComposeAreaLayout was triggered
    fun setShowAttachIcons(show: Boolean, preserveOldSelection: Boolean): Boolean {
        if (show == showAttachIcons && !recording) {
            return false
        }
        showAttachIcons = show
        updateComposeAreaLayout()
        if (show) {
            previousSelectionStart = newMessageEditText?.selectionStart ?: 0
            previousSelectionEnd = newMessageEditText?.selectionEnd ?: 0
        } else if (preserveOldSelection) {
            val messageLength =
                if (newMessageEditText?.text == null) 0 else newMessageEditText?.text!!.length
            if (previousSelectionStart >= 0 && previousSelectionEnd >= 0 && previousSelectionStart <= messageLength && previousSelectionEnd <= messageLength) {
                newMessageEditText?.setSelection(previousSelectionStart, previousSelectionEnd)
            } else {
                newMessageEditText?.setSelection(messageLength)
            }
        }
        return true
    }

    private fun updateIconsToShow(widthPixels: Int) {
        previousWidth = widthPixels
        val metrics = context?.resources?.displayMetrics ?: return
        val widthDp = widthPixels.toFloat() / metrics.density
        iconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, metrics).toInt()
        fourDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, metrics).toInt()
        var icons = SettingsActivity.getComposeMessageIconPreferredOrder()
        if (icons == null) {
            icons = DEFAULT_ICON_ORDER.toList()
        }
        if (!hasCamera) {
            icons.remove(ICON_TAKE_PICTURE)
            icons.remove(ICON_TAKE_VIDEO)
        }

        // Compose area layout
        // 4 + 36 + (36 x icon_count) + 4 || 6 + 24 + 2 + [ text ] + 32 || 4 = 112 + [ text ] + (36 x icon_count)
        // min text width --> 160dp
        // text width > 400dp with all icons --> don't overflow
        iconsShown.clear()
        iconsOverflow.clear()
        if (widthDp > 512 + 36 * icons.size) {
            neverOverflow = true
            iconsShown.addAll(icons)
        } else {
            neverOverflow = false
            val iconsToShow = max(0, min((widthDp - 272).toInt() / 36, icons.size))
            iconsShown.addAll(icons.subList(0, iconsToShow))
            iconsOverflow.addAll(icons.subList(iconsToShow, icons.size))
        }
        if (icons.size < DEFAULT_ICON_ORDER.size) {
            for (icon in DEFAULT_ICON_ORDER) {
                if (!icons.contains(icon)) {
                    iconsOverflow.add(icon)
                }
            }
        }
        attachIconsGroup.removeAllViews()
        for (icon in iconsShown) {
            val imageView = ImageView(ContextThemeWrapper(activity, R.style.SubtleBlueRipple))
            imageView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            imageView.setPadding(fourDp, 0, 0, 0)
            imageView.setBackgroundResource(R.drawable.background_circular_ripple)
            imageView.setImageResource(getImageResourceForIcon(icon))
            imageView.id = getViewIdForIcon(icon)
            imageView.setOnClickListener(this)
            if (imageView.id == R.id.attach_timer) {
                imageView.doOnLayout {
                    it.post { attachTimerPosition = it.screenLocation }
                }
            }
            attachIconsGroup.addView(imageView, 0)
        }
        currentLayout = 0 // do this to force relayout
        updateComposeAreaLayout()
    }

    private var attachTimerPosition by mutableStateOf(intArrayOf(0, 0))

    private var widthAnimator: ValueAnimator? = null
    private var currentLayout = 0
    fun updateComposeAreaLayout() {
        if (!recording && !hasAttachments && !hasText && !isEditMode()) {
            sendButton!!.isGone = true
            directAttachVoiceMessageImageView!!.isVisible = true
        } else {
            if (isEditMode()) {
                sendButton?.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        requireContext().resources, R.drawable.ic_send_edit, null
                    )
                )
            } else {
                sendButton?.setImageDrawable(
                    context?.resources?.let {
                        ResourcesCompat.getDrawable(
                            it, R.drawable.ic_send, null
                        )
                    }
                )
            }
            sendButton!!.isVisible = true
            directAttachVoiceMessageImageView!!.isGone = true
            sendButton!!.isEnabled =
                (hasAttachments || composeMessageViewModel.trimmedNewMessageText != null || recording) && !identicalEditMessage()
        }
        val attachIconsGroupParams = attachIconsGroup.layoutParams as LayoutParams
        // depending on the state, pick the appropriate layout:
        // 1 - show a (+) and some icons on the side, with a compact message edit zone
        // 2 - only for "never overflow" mode in very wide screens where all icons are always displayed next to the (+)
        // 3 - show a (>) with no icons next to it to maximize EditText space
        // 4 - edit mode, like 3 but with the emoji icon instead of (>)
        if (showAttachIcons && !isEditMode()) {
            if (currentLayout != 1) {
                currentLayout = 1
                attachStuffPlus!!.setImageResource(R.drawable.ic_attach_add)
                newMessageEditText?.maxLines = 1
                newMessageEditText?.isVerticalScrollBarEnabled = false
                newMessageEditText?.movementMethod = null
                hideMentionPicker()

                widthAnimator?.cancel()
                if (animateLayoutChanges) {
                    widthAnimator = ValueAnimator.ofInt(
                        attachIconsGroupParams.width,
                        attachIconsGroup.childCount * iconSize
                    )
                    widthAnimator?.duration = 200
                    widthAnimator?.addUpdateListener { animation: ValueAnimator ->
                        attachIconsGroupParams.width = animation.animatedValue as Int
                        attachIconsGroup.layoutParams = attachIconsGroupParams
                    }
                    widthAnimator?.start()
                } else {
                    attachIconsGroupParams.width = attachIconsGroup.childCount * iconSize
                    attachIconsGroup.layoutParams = attachIconsGroupParams
                }
            }
        } else {
            if (currentLayout == 1) {
                newMessageEditText?.maxLines = 6
                newMessageEditText?.isVerticalScrollBarEnabled = true
                newMessageEditText?.movementMethod = ArrowKeyMovementMethod.getInstance()
            }
            if (neverOverflow && !isEditMode()) {
                if (currentLayout != 2) {
                    currentLayout = 2
                    attachStuffPlus!!.setImageResource(R.drawable.ic_attach_add)
                    if (widthAnimator != null) {
                        widthAnimator!!.cancel()
                    }
                    if (animateLayoutChanges) {
                        widthAnimator = ValueAnimator.ofInt(
                            attachIconsGroupParams.width,
                            attachIconsGroup.childCount * iconSize
                        )
                        widthAnimator?.duration = 200
                        widthAnimator?.addUpdateListener { animation: ValueAnimator ->
                            attachIconsGroupParams.width = animation.animatedValue as Int
                            attachIconsGroup.layoutParams = attachIconsGroupParams
                        }
                        widthAnimator?.start()
                    } else {
                        attachIconsGroupParams.width = attachIconsGroup.childCount * iconSize
                        attachIconsGroup.layoutParams = attachIconsGroupParams
                    }
                }
            } else {
                if (currentLayout != 3 && currentLayout != 4) {
                    if (widthAnimator != null) {
                        widthAnimator!!.cancel()
                    }
                    if (animateLayoutChanges) {
                        widthAnimator = ValueAnimator.ofInt(attachIconsGroupParams.width, -3)
                        widthAnimator?.duration = 200
                        widthAnimator?.addUpdateListener { animation: ValueAnimator ->
                            attachIconsGroupParams.width = animation.animatedValue as Int
                            attachIconsGroup.layoutParams = attachIconsGroupParams
                        }
                        widthAnimator?.start()
                    } else {
                        attachIconsGroupParams.width = -3
                        attachIconsGroup.layoutParams = attachIconsGroupParams
                    }
                }
                if (!isEditMode()) {
                    if (currentLayout != 3) {
                        currentLayout = 3
                        attachStuffPlus!!.setImageResource(R.drawable.ic_attach_chevron)
                    }
                } else {
                    if (currentLayout != 4) {
                        currentLayout = 4
                        attachStuffPlus!!.setImageResource(R.drawable.ic_attach_emoji)
                    }
                }
            }
        }
        hideOrShowEphemeralMarker(showEphemeralMarker)
    }

    private fun identicalEditMessage(): Boolean {
        val draftEdit = composeMessageViewModel.getDraftMessageEdit().value ?: return false
        val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
            composeMessageViewModel.rawNewMessageText ?: "", mentionViewModel.mentions
        )
        if (draftEdit.contentBody != trimAndMentions.first) {
            return false
        }
        val mentions = draftEdit.mentions
        val mentionSet = if (mentions == null) null else HashSet(mentions)
        val newMentionSet =
            if (trimAndMentions.second == null) null else HashSet(trimAndMentions.second)
        return mentionSet == newMentionSet
    }

    private fun hideOrShowEphemeralMarker(showMarker: Boolean) {
        showEphemeralMarker = showMarker
        val attachTimeView = attachIconsGroup.findViewById<ImageView>(R.id.attach_timer)
        if (attachTimeView != null) {
            if (showMarker) {
                attachTimeView.setImageResource(R.drawable.ic_attach_timer_modified)
                attachStuffPlusGoldenDot.visibility =
                    if (showAttachIcons || neverOverflow) View.GONE else View.VISIBLE
            } else {
                attachTimeView.setImageResource(R.drawable.ic_attach_timer)
                attachStuffPlusGoldenDot.visibility = View.GONE
            }
        } else {
            attachStuffPlusGoldenDot.visibility = if (showMarker) View.VISIBLE else View.GONE
        }
    }

    private fun showOverflowedPopupMenu() {
        @SuppressLint("InflateParams") val popupView = LayoutInflater.from(activity).inflate(
            R.layout.popup_discussion_attach_stuff, null
        )
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 12f
        popupWindow.setBackgroundDrawable(
            ContextCompat.getDrawable(
                popupView.context,
                R.drawable.background_popup_discussion_owned_identity
            )
        )
        val onClickListener = OnClickListener { v: View ->
            popupWindow.dismiss()
            onClick(v)
        }
        val popupAttachList = popupView.findViewById<LinearLayout>(R.id.popup_attach_list)
        popupAttachList.clipToOutline = true
        val attachConfigure = popupAttachList.findViewById<TextView>(R.id.attach_configure)
        attachConfigure.setOnClickListener(onClickListener)
        if (iconsOverflow.size == 0) {
            popupAttachList.findViewById<View>(R.id.separator).visibility = View.GONE
        } else {
            val greyColor = ContextCompat.getColor(popupView.context, R.color.grey)
            val backgroundDrawable = TypedValue()
            popupView.context.theme.resolveAttribute(
                attr.selectableItemBackground,
                backgroundDrawable,
                true
            )
            for (icon in iconsOverflow) {
                val textView: TextView =
                    AppCompatTextView(ContextThemeWrapper(activity, R.style.SubtleBlueRipple))
                textView.id = getViewIdForIcon(icon)
                textView.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    getImageResourceForIcon(
                        icon
                    ), 0, 0, 0
                )
                textView.setText(getStringResourceForIcon(icon))
                textView.maxLines = 1
                textView.ellipsize = END
                textView.setTextColor(greyColor)
                textView.gravity = Gravity.CENTER_VERTICAL
                textView.setPadding(fourDp, fourDp, 2 * fourDp, fourDp)
                textView.compoundDrawablePadding = fourDp
                textView.setBackgroundResource(backgroundDrawable.resourceId)
                textView.setOnClickListener(onClickListener)
                popupAttachList.addView(textView)
            }
        }
        val popupHeight = (iconsOverflow.size + 2) * iconSize + fourDp
        popupWindow.animationStyle = R.style.FadeInAndOutAnimation
        popupWindow.showAsDropDown(
            attachStuffPlus,
            fourDp / 2,
            -popupHeight,
            Gravity.TOP or Gravity.START
        )
    }

    private fun getImageResourceForIcon(icon: Int): Int {
        return when (icon) {
            ICON_ATTACH_FILE -> R.drawable.ic_attach_file
            ICON_ATTACH_PICTURE -> R.drawable.ic_attach_image
            ICON_EPHEMERAL_SETTINGS -> if (showEphemeralMarker) {
                R.drawable.ic_attach_timer_modified
            } else {
                R.drawable.ic_attach_timer
            }

            ICON_TAKE_PICTURE -> R.drawable.ic_attach_camera
            ICON_TAKE_VIDEO -> R.drawable.ic_attach_video
            ICON_EMOJI -> R.drawable.ic_attach_emoji
            ICON_SEND_LOCATION -> R.drawable.ic_attach_location
            else -> 0
        }
    }

    private fun getViewIdForIcon(icon: Int): Int {
        return when (icon) {
            ICON_ATTACH_FILE -> R.id.attach_file
            ICON_ATTACH_PICTURE -> R.id.attach_image
            ICON_EPHEMERAL_SETTINGS -> R.id.attach_timer
            ICON_TAKE_PICTURE -> R.id.attach_camera
            ICON_TAKE_VIDEO -> R.id.attach_video
            ICON_EMOJI -> R.id.attach_emoji
            ICON_SEND_LOCATION -> R.id.attach_location
            else -> 0
        }
    }

    private fun getStringResourceForIcon(icon: Int): Int {
        return when (icon) {
            ICON_ATTACH_FILE -> R.string.label_attach_file
            ICON_ATTACH_PICTURE -> R.string.label_attach_image
            ICON_EPHEMERAL_SETTINGS -> R.string.label_attach_timer
            ICON_TAKE_PICTURE -> R.string.label_attach_camera
            ICON_TAKE_VIDEO -> R.string.label_attach_video
            ICON_EMOJI -> R.string.label_attach_emoji
            ICON_SEND_LOCATION -> R.string.label_send_your_location
            else -> 0
        }
    }

    // endregion
    // region EmojiPickerFragment Keyboard
    private var emojiKeyboardFragment: EmojiKeyboardFragment? = null
    var emojiKeyboardShowing = false
    private var emojiKeyboardAttachDelegate: EmojiKeyboardAttachDelegate? = null

    interface EmojiKeyboardAttachDelegate {
        fun attachKeyboardFragment(fragment: Fragment)
        fun detachKeyboardFragment(fragment: Fragment)
    }

    private fun showEmojiKeyboard() {
        if (activity != null && emojiKeyboardAttachDelegate != null) {
            emojiKeyboardShowing = true
            val imm =
                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm != null && newMessageEditText != null) {
                imm.hideSoftInputFromWindow(newMessageEditText?.windowToken, 0)
            }
            setShowAttachIcons(show = false, preserveOldSelection = true)
            if (emojiKeyboardFragment == null) {
                initializeEmojiKeyboard()
            }
            newMessageEditText?.showSoftInputOnFocus = false
            val ic = newMessageEditText?.onCreateInputConnection(EditorInfo())
            emojiKeyboardFragment!!.setInputConnection(ic)
            emojiKeyboardAttachDelegate!!.attachKeyboardFragment(emojiKeyboardFragment!!)
        }
    }

    private fun hideEmojiKeyboard() {
        if (activity != null && emojiKeyboardAttachDelegate != null) {
            newMessageEditText?.showSoftInputOnFocus = true
            if (emojiKeyboardFragment != null) {
                emojiKeyboardFragment!!.setInputConnection(null)
                emojiKeyboardAttachDelegate!!.detachKeyboardFragment(emojiKeyboardFragment!!)
            }
            emojiKeyboardShowing = false
        }
    }

    private fun initializeEmojiKeyboard() {
        emojiKeyboardFragment =
            EmojiKeyboardFragment()
        emojiKeyboardFragment!!.setRestoreKeyboardListener {
            hideEmojiKeyboard()
            composeMessageDelegate.showSoftInputKeyboard()
        }
    }

    // endregion
    // region ComposeMessageHeightListener
    interface ComposeMessageHeightListener {
        fun onNewComposeMessageHeight(heightPixels: Int)
    }

    private val composeMessageHeightListeners: MutableList<ComposeMessageHeightListener> =
        ArrayList()
    private val composeMessageSizeChangeListener =
        OnLayoutChangeListener { _: View?, left: Int, top: Int, right: Int, bottom: Int, _: Int, oldTop: Int, _: Int, oldBottom: Int ->
            if (previousWidth != right - left) {
                Handler(Looper.getMainLooper()).post { updateIconsToShow(right - left) }
            }
            if (top - bottom != oldTop - oldBottom) {
                var listeners: List<ComposeMessageHeightListener>
                synchronized(composeMessageHeightListeners) {
                    listeners = ArrayList(composeMessageHeightListeners)
                }
                for (listener in listeners) {
                    Handler(Looper.getMainLooper()).post { listener.onNewComposeMessageHeight(bottom - top) }
                }
            }
        } // endregion

    companion object {
        const val ICON_EPHEMERAL_SETTINGS = 1
        const val ICON_ATTACH_FILE = 2
        const val ICON_ATTACH_PICTURE = 3
        const val ICON_TAKE_PICTURE = 4
        const val ICON_TAKE_VIDEO = 5
        const val ICON_EMOJI = 6
        const val ICON_SEND_LOCATION = 7
        val DEFAULT_ICON_ORDER = listOf(
            ICON_EMOJI,
            ICON_EPHEMERAL_SETTINGS,
            ICON_ATTACH_FILE,
            ICON_ATTACH_PICTURE,
            ICON_TAKE_PICTURE,
            ICON_TAKE_VIDEO,
            ICON_SEND_LOCATION
        )

        // endregion
        // region icon order selector
        private const val VIEW_TYPE_ICON = 1
        private const val VIEW_TYPE_SEPARATOR = 2
    }
}