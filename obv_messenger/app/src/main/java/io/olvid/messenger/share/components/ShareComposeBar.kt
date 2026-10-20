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

package io.olvid.messenger.share.components

import android.annotation.SuppressLint
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
import android.widget.PopupMenu
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DiscussionInputEditText
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
import io.olvid.messenger.designsystem.components.dashedBorder
import io.olvid.messenger.settings.SettingsActivity

/**
 * Compose bar for the share extension.
 */
@Composable
fun ShareComposeBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    hasEphemeralSettings: Boolean,
    onPickEphemeralSettings: () -> Unit,
    onSend: () -> Unit,
    onBlockedSend: () -> Unit,
) {
    // The listeners below are attached once in factory but must see the latest lambdas/flags on
    // every keystroke, so capture them through rememberUpdatedState rather than the initial values.
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentCanSend by rememberUpdatedState(canSend)
    val currentOnBlockedSend by rememberUpdatedState(onBlockedSend)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(colorResource(R.color.lighterGrey), RoundedCornerShape(20.dp))
                .heightIn(min = 40.dp)
                .then(
                    if (hasEphemeralSettings) {
                        Modifier.dashedBorder(
                            brush = SolidColor(colorResource(R.color.darkGrey)),
                            shape = RoundedCornerShape(20.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AndroidView(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).weight(1f, true),
                factory = { ctx ->
                    @SuppressLint("InflateParams")
                    val editText = LayoutInflater.from(ctx)
                        .inflate(R.layout.view_discussion_input_edit_text, null)
                        .findViewById<DiscussionInputEditText>(R.id.discussion_input_edit_text)
                    editText.apply {
                        maxLines = 6
                        setSelectAllOnFocus(false)
                        isFocusable = true
                        textSize = 16f
                        setBackgroundColor(0)
                        setTextColor(ContextCompat.getColor(ctx, R.color.almostBlack))
                        setHintTextColor(ContextCompat.getColor(ctx, R.color.greyTint))
                        hint = ctx.getString(R.string.hint_share_message)
                        ellipsize = TextUtils.TruncateAt.END
                        imeOptions = imeOptions or EditorInfo.IME_ACTION_SEND
                        inputType = inputType or
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
                        if (SettingsActivity.useKeyboardIncognitoMode()) {
                            imeOptions =
                                imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }

                        // Seed the initial draft (e.g. EXTRA_TEXT) exactly once and style it.
                        if (text.isNotEmpty()) {
                            setText(text)
                            setSelection(length())
                            editableText?.formatMarkdown(
                                ContextCompat.getColor(ctx, R.color.olvid_gradient_contrasted)
                            )
                        }

                        addTextChangedListener(object : TextChangeListener() {
                            override fun afterTextChanged(editable: Editable) {
                                currentOnTextChange(editable.toString())
                                if (editable.isNotBlank()) {
                                    editable.formatMarkdown(
                                        ContextCompat.getColor(
                                            ctx, R.color.olvid_gradient_contrasted
                                        )
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
                        })

                        setOnEditorActionListener(
                            TextView.OnEditorActionListener { _, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_SEND) {
                                    if (currentCanSend) currentOnSend() else currentOnBlockedSend()
                                    return@OnEditorActionListener true
                                }
                                false
                            }
                        )
                        if (SettingsActivity.sendWithHardwareEnter) {
                            setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
                                if (keyCode == KeyEvent.KEYCODE_ENTER
                                    && event.action == KeyEvent.ACTION_DOWN
                                    && !event.isShiftPressed
                                ) {
                                    if (currentCanSend) currentOnSend() else currentOnBlockedSend()
                                    return@OnKeyListener true
                                }
                                false
                            })
                        }

                        // Selection action-mode "Format" entry → Markdown formatting popup, reusing
                        // the same insertMarkdown(...) helpers as the in-app composer.
                        customSelectionActionModeCallback = object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                menu.add(Menu.FIRST, 1111, 1, R.string.label_selection_formatting)
                                return true
                            }

                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true

                            override fun onActionItemClicked(
                                mode: ActionMode,
                                item: MenuItem
                            ): Boolean {
                                if (item.itemId != 1111) return false
                                val popupMenu = PopupMenu(ctx, this@apply)
                                popupMenu.inflate(R.menu.action_menu_text_selection)
                                popupMenu.setOnMenuItemClickListener { menuItem ->
                                    when (menuItem.itemId) {
                                        R.id.action_text_selection_bold -> insertMarkdown(MarkdownBold())
                                        R.id.action_text_selection_italic -> insertMarkdown(MarkdownItalic())
                                        R.id.action_text_selection_strikethrough -> insertMarkdown(MarkdownStrikeThrough())
                                        R.id.action_text_selection_heading -> return@setOnMenuItemClickListener false
                                        R.id.action_text_selection_heading_1 -> insertMarkdown(MarkdownHeading(1))
                                        R.id.action_text_selection_heading_2 -> insertMarkdown(MarkdownHeading(2))
                                        R.id.action_text_selection_heading_3 -> insertMarkdown(MarkdownHeading(3))
                                        R.id.action_text_selection_heading_4 -> insertMarkdown(MarkdownHeading(4))
                                        R.id.action_text_selection_heading_5 -> insertMarkdown(MarkdownHeading(5))
                                        R.id.action_text_selection_list -> return@setOnMenuItemClickListener false
                                        R.id.action_text_selection_list_bullet -> insertMarkdown(MarkdownListItem())
                                        R.id.action_text_selection_list_ordered -> insertMarkdown(MarkdownOrderedListItem())
                                        R.id.action_text_selection_quote -> insertMarkdown(MarkdownQuote())
                                        R.id.action_text_selection_code -> insertMarkdown(MarkdownCode())
                                        else -> insertMarkdown(null)
                                    }
                                    mode.finish()
                                    true
                                }
                                popupMenu.show()
                                return true
                            }

                            override fun onDestroyActionMode(mode: ActionMode) {}
                        }
                    }
                },
            )

            IconButton(
                modifier = Modifier.size( 32.dp, 40.dp).requiredSize(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorResource(R.color.darkGrey)
                ),
                onClick = onPickEphemeralSettings
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_ephemeral),
                    contentDescription = null
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) colorResource(R.color.olvid_gradient_light)
                    else colorResource(R.color.lightGrey)
                )
                .clickable(onClick = if (canSend) onSend else onBlockedSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier
                    .padding(top = 2.dp, end = 4.dp)
                    .size(22.dp),
                painter = painterResource(R.drawable.ic_send_up),
                tint = colorResource(R.color.alwaysWhite),
                contentDescription = stringResource(R.string.content_description_send_message)
            )
        }
    }
}
