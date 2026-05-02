/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.view.ActionMode
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.customClasses.MAX_BITMAP_SIZE
import io.olvid.messenger.customClasses.MarkdownBold
import io.olvid.messenger.customClasses.MarkdownCode
import io.olvid.messenger.customClasses.MarkdownHeading
import io.olvid.messenger.customClasses.MarkdownItalic
import io.olvid.messenger.customClasses.MarkdownListItem
import io.olvid.messenger.customClasses.MarkdownOrderedListItem
import io.olvid.messenger.customClasses.MarkdownQuote
import io.olvid.messenger.customClasses.MarkdownStrikeThrough
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.customClasses.insertMarkdown
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask
import io.olvid.messenger.databases.tasks.SaveDraftTask
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.Utils
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.settings.SettingsActivity


@Composable
fun DiscussionInputEditText(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    composeMessageViewModel: ComposeMessageViewModel,
    mentionViewModel: MentionViewModel,
    ephemeralMessage: Boolean,
    linkPreviewViewModel: LinkPreviewViewModel,
    discussionViewModel: DiscussionViewModel,
    onSendMessage: () -> Unit,
    onViewCreated: (DiscussionInputEditText) -> Unit
) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            @SuppressLint("InflateParams")
            val layout = LayoutInflater.from(ctx).inflate(R.layout.view_discussion_input_edit_text, null)
            layout.findViewById<DiscussionInputEditText>(R.id.discussion_input_edit_text).apply {
                maxLines = 6
                setSelectAllOnFocus(false)
                isFocusable = true
                imeOptions = imeOptions or EditorInfo.IME_ACTION_SEND
                inputType =
                    inputType or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
                ellipsize = TextUtils.TruncateAt.END
                setBackgroundColor(0)

                if (SettingsActivity.useKeyboardIncognitoMode()) {
                    imeOptions =
                        imeOptions or
                                EditorInfoCompat
                                    .IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                addTextChangedListener(
                    object : TextChangeListener() {
                        override fun afterTextChanged(
                            editable: Editable
                        ) {
                            composeMessageViewModel
                                .setNewMessageText(editable)
                            val hasText = editable.isNotBlank()
                            composeMessageViewModel.hasText = hasText
                            composeMessageViewModel.editIsSendable = hasText && composeMessageViewModel.getMessageBeingEdited().value?.contentBody?.trim() != editable.toString().trim()
                            mentionViewModel.updateMentions(
                                editable,
                                selectionEnd
                            )
                            if (hasText) {
                                editable.formatMarkdown(
                                    ContextCompat.getColor(
                                        context,
                                        R.color
                                            .olvid_gradient_contrasted
                                    )
                                )
                            }
                            if (SettingsActivity.isLinkPreviewOutbound &&
                                composeMessageViewModel.getMessageBeingEdited().value == null
                            ) {
                                linkPreviewViewModel
                                    .findLinkPreview(
                                        editable.toString(),
                                        MAX_BITMAP_SIZE,
                                        MAX_BITMAP_SIZE
                                    )
                            }

                            // We add this to force instant scroll when typing text
                            post {
                                val selectionStart = selectionStart
                                val layout = this@apply.layout
                                if (layout != null && selectionStart != -1) {
                                    val line = layout.getLineForOffset(selectionStart)
                                    val lineBottom = layout.getLineBottom(line)

                                    val visibleHeight = height - paddingTop - paddingBottom
                                    val scrollY = scrollY

                                    // If the cursor is below the visible area, scroll down immediately
                                    if (lineBottom > scrollY + visibleHeight) {
                                        scrollTo(0, lineBottom - visibleHeight)
                                    }
                                }
                            }
                        }
                    }
                )
                setOnSelectionChangeListener { editable: Editable?,
                                               start: Int,
                                               end: Int ->
                    if (start == end) {
                        mentionViewModel.updateMentions(editable, start)
                    }
                }
                setImeContentCommittedHandler { contentUri: Uri?,
                                                fileName: String?,
                                                mimeType: String?,
                                                callMeWhenDone: Runnable? ->
                    if (composeMessageViewModel.trimmedNewMessageText != null) {
                        val trimAndMentions =
                            Utils.removeProtectionFEFFsAndTrim(
                                composeMessageViewModel.rawNewMessageText,
                                mentionViewModel.mentions
                            )
                        SaveDraftTask(
                            discussionViewModel.discussionId!!,
                            trimAndMentions.first,
                            composeMessageViewModel.getDraftMessage().value,
                            trimAndMentions.second,
                            true
                        ).run()
                    }
                    contentUri?.let {
                        AddFyleToDraftFromUriTask(
                            contentUri,
                            fileName,
                            mimeType,
                            discussionViewModel.discussionId!!
                        ).run()
                    }
                    callMeWhenDone?.run()
                }
                setOnEditorActionListener(
                    TextView.OnEditorActionListener { v: TextView,
                                                      actionId: Int,
                                                      _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            onSendMessage()
                            imm?.hideSoftInputFromWindow(
                                v.windowToken,
                                0
                            )
                            return@OnEditorActionListener true
                        }
                        false
                    }
                )
                if (SettingsActivity.sendWithHardwareEnter) {
                    setOnKeyListener(
                        View.OnKeyListener { _: View?,
                                             keyCode: Int,
                                             event: KeyEvent ->
                            if (keyCode == KeyEvent.KEYCODE_ENTER &&
                                event.action ==
                                KeyEvent.ACTION_DOWN &&
                                !event.isShiftPressed
                            ) {
                                onSendMessage()
                                return@OnKeyListener true
                            }
                            false
                        }
                    )
                }
                customSelectionActionModeCallback =
                    object : ActionMode.Callback {
                        override fun onCreateActionMode(
                            mode: ActionMode,
                            menu: Menu
                        ): Boolean {
                            menu.add(
                                Menu.FIRST,
                                1111,
                                1,
                                R.string.label_selection_formatting
                            )
                            return true
                        }

                        override fun onPrepareActionMode(
                            mode: ActionMode,
                            menu: Menu
                        ): Boolean {
                            // Possible improvement: don't show markdown
                            // menu if not relevant:
                            // - selection crosses already present
                            // inline markdown
                            // - selection is inside a code block or
                            // inline code
                            return true
                        }

                        override fun onActionItemClicked(
                            mode: ActionMode,
                            item: MenuItem
                        ): Boolean {
                            if (item.itemId == 1111) {
                                val popupMenu =
                                    PopupMenu(context, this@apply)
                                popupMenu.inflate(
                                    R.menu.action_menu_text_selection
                                )
                                popupMenu.setOnMenuItemClickListener { menuItem: MenuItem ->
                                    when (menuItem.itemId) {
                                        R.id.action_text_selection_bold -> {
                                            insertMarkdown(
                                                MarkdownBold()
                                            )
                                        }

                                        R.id.action_text_selection_italic -> {
                                            insertMarkdown(
                                                MarkdownItalic()
                                            )
                                        }

                                        R.id.action_text_selection_strikethrough -> {
                                            insertMarkdown(
                                                MarkdownStrikeThrough()
                                            )
                                        }

                                        R.id.action_text_selection_heading -> {
                                            return@setOnMenuItemClickListener false
                                        }

                                        R.id.action_text_selection_heading_1 -> {
                                            insertMarkdown(
                                                MarkdownHeading(1)
                                            )
                                        }

                                        R.id.action_text_selection_heading_2 -> {
                                            insertMarkdown(
                                                MarkdownHeading(2)
                                            )
                                        }

                                        R.id.action_text_selection_heading_3 -> {
                                            insertMarkdown(
                                                MarkdownHeading(3)
                                            )
                                        }

                                        R.id.action_text_selection_heading_4 -> {
                                            insertMarkdown(
                                                MarkdownHeading(4)
                                            )
                                        }

                                        R.id.action_text_selection_heading_5 -> {
                                            insertMarkdown(
                                                MarkdownHeading(5)
                                            )
                                        }

                                        R.id.action_text_selection_list -> {
                                            return@setOnMenuItemClickListener false
                                        }

                                        R.id.action_text_selection_list_bullet -> {
                                            insertMarkdown(
                                                MarkdownListItem()
                                            )
                                        }

                                        R.id.action_text_selection_list_ordered -> {
                                            insertMarkdown(
                                                MarkdownOrderedListItem()
                                            )
                                        }

                                        R.id.action_text_selection_quote -> {
                                            insertMarkdown(
                                                MarkdownQuote()
                                            )
                                        }

                                        R.id.action_text_selection_code -> {
                                            insertMarkdown(
                                                MarkdownCode()
                                            )
                                        }

                                        else -> {
                                            insertMarkdown(null)
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

                        override fun onDestroyActionMode(
                            mode: ActionMode
                        ) {
                        }
                    }
                onViewCreated(this)
            }
        },
        update = { view ->
            view.textSize = 18f * fontScale
            view.hint = view.context.getString(if (ephemeralMessage) R.string.hint_compose_your_message_ephemeral else R.string.hint_compose_your_message)
        }
    )
}
