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

package io.olvid.messenger.fragments.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.discussion.message.reactions.ReactionViewModel
import io.olvid.messenger.discussion.message.reactions.emoji.SearchableEmojiPicker
import io.olvid.messenger.discussion.message.reactions.emoji.rememberEmojiSearchState
import io.olvid.messenger.settings.SettingsActivity.Companion.preventScreenCapture

class DefaultReactionPickerDialogFragment : DialogFragment() {
    private val reactionViewModel: ReactionViewModel by viewModels()

    var currentReaction: String? = null
    var appDefaultReaction: String? = null // when non-null, offer a "use app default" option
    var onEmojiSelected: ((String) -> Unit)? = null
    var onAppDefaultSelected: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // the callbacks do not survive fragment recreation: close instead of reopening a dead dialog
            dismissAllowingStateLoss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            if (preventScreenCapture()) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val emojiSearchState = rememberEmojiSearchState()
                val recentReactions by reactionViewModel.recentReactions.observeAsState(emptyList())
                BaseDialogContent(
                    modifier = Modifier.padding(16.dp),
                    title = stringResource(R.string.pref_default_reaction_title),
                    content = {
                        SearchableEmojiPicker(
                            emojiSearchState = emojiSearchState,
                            searchBarModifier = Modifier
                                .padding(vertical = 8.dp)
                                .height(40.dp),
                            currentReaction = currentReaction,
                            recentEmojis = recentReactions,
                            onReact = { emoji ->
                                onEmojiSelected?.invoke(emoji)
                                dismiss()
                            },
                            onToggleFavorite = { reactionViewModel.toggleFavorite(it) }
                        )
                    },
                    actions = {
                        appDefaultReaction?.let { appDefault ->
                            OlvidTextButton(
                                text = stringResource(
                                    R.string.pref_text_app_default_string,
                                    appDefault
                                ),
                                onClick = {
                                    onAppDefaultSelected?.invoke()
                                    dismiss()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OlvidTextButton(
                            text = stringResource(R.string.button_label_cancel),
                            onClick = { dismiss() }
                        )
                    }
                )
            }
        }
    }
}
