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
package io.olvid.messenger.discussion

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.TextUtils.TruncateAt.END
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Max
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.CopySelectedMessageTask
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.PropagateBookmarkedMessageChangeTask
import io.olvid.messenger.databases.tasks.ShareSelectedMessageTask
import io.olvid.messenger.databases.tasks.UpdateReactionsTask
import io.olvid.messenger.discussion.DiscussionActivity.DiscussionDelegate
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.BOOKMARK
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.COPY
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.DELETE
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.DETAILS
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.EDIT
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.FORWARD
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.REPLY
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.SELECT
import io.olvid.messenger.discussion.MessageLongPressPopUp.PopupActionType.SHARE
import io.olvid.messenger.discussion.compose.emoji.EmojiClickListener
import io.olvid.messenger.discussion.compose.emoji.EmojiPickerViewFactory
import io.olvid.messenger.settings.SettingsActivity
import kotlin.math.max
import kotlin.math.min

class MessageLongPressPopUp(
    private val activity: FragmentActivity,
    private val discussionDelegate: DiscussionDelegate,
    private val parentView: View,
    private val clickX: Int,
    private val clickY: Int,
    messageViewBottomPx: Int,
    private val messageId: Long
) {
    private val messageViewBottomPx = max(0.0, messageViewBottomPx.toDouble())
        .toInt()
    private val vibrator: Vibrator? =
        activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val metrics: DisplayMetrics = activity.resources.displayMetrics
    private var previousReaction: String? = null

    private var message: Message? = null
    private var discussion: Discussion? = null

    private var wrappedContext: Context? = null
    private var popupWindow: PopupWindow? = null
    private var reactionsPopUpLinearLayout: LinearLayout? = null
    private var reactionConstraintLayout: ConstraintLayout? = null
    private var reactionFlow: Flow? = null
    private var viewSizePx = 0
    private var fontSizeDp = 0
    private var plusButton: ImageView? = null
    private var plusOpen = false
    private var additionalBottomPadding = 0

    private var separatorTextView: TextView? = null
    private var emojiPickerView: View? = null
    private var emojiPickerRows = 4

    private val maxVisibleActions = 4

    init {
        App.runThread {
            this.message = AppDatabase.getInstance().messageDao()[messageId]
            if (message == null) {
                return@runThread
            }
            this.discussion =
                AppDatabase.getInstance().discussionDao().getById(message!!.discussionId)
            if (discussion == null) {
                return@runThread
            }

            val reaction = AppDatabase.getInstance().reactionDao().getMyReactionForMessage(
                messageId
            )
            this.previousReaction = reaction?.emoji
            activity.runOnUiThread { this.buildPopupWindow() }
        }
    }

    enum class PopupActionType(@StringRes val stringRes: Int) {
        REPLY(R.string.label_swipe_reply),
        SHARE(R.string.menu_action_share),
        FORWARD(R.string.menu_action_forward),
        COPY(R.string.label_swipe_copy),
        SELECT(R.string.label_swipe_select),
        DETAILS(R.string.label_swipe_details),
        EDIT(R.string.label_swipe_edit),
        BOOKMARK(R.string.menu_action_bookmark),
        DELETE(R.string.menu_action_delete)
    }

    @Composable
    fun PopupActionType.getImage(
        message: Message? = null,
        tint: Color? = null
    ): @Composable () -> Unit {
        return {
            when (this) {
                REPLY ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_reply),
                        contentDescription = ""
                    )

                SHARE ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_share),
                        contentDescription = ""
                    )

                FORWARD ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_forward),
                        contentDescription = ""
                    )

                COPY ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_copy),
                        contentDescription = ""
                    )

                SELECT ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_select),
                        contentDescription = ""
                    )

                DETAILS ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_info),
                        contentDescription = ""
                    )

                EDIT ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = R.drawable.ic_swipe_edit),
                        contentDescription = stringResource(id = R.string.label_swipe_edit)
                    )

                BOOKMARK ->
                    Image(
                        modifier = Modifier
                            .size(24.dp),
                        painter = painterResource(id = if (message?.bookmarked == true) R.drawable.ic_star_off else R.drawable.ic_star),
                        colorFilter = ColorFilter.tint(colorResource(id = R.color.almostBlack)),
                        contentDescription = ""
                    )

                DELETE -> Image(
                    modifier = Modifier
                        .size(24.dp),
                    colorFilter = tint?.let { ColorFilter.tint(it) },
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = ""
                )
            }
        }
    }

    @Composable
    fun RowScope.PopupActionButton(
        text: String? = null,
        color: Color = colorResource(id = R.color.almostBlack),
        backgroundColor: Color = colorResource(id = R.color.lightGrey),
        action: () -> Unit = {},
        image: @Composable () -> Unit
    ) {
        Column(
            modifier = Modifier
                .height(48.dp)
                .then(
                    if (text == null)
                        Modifier.aspectRatio(1f)
                    else
                        Modifier.weight(1f)
                )
                .background(
                    backgroundColor,
                    RoundedCornerShape(6.dp)
                )
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = rememberRipple(color = colorResource(id = R.color.whiteOverlay)),
                    onClick = action
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            image()
            text?.let {
                Text(
                    text = text,
                    fontSize = 10.sp,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    private fun onAction(actionType: PopupActionType) {
        when (actionType) {
            REPLY -> {
                discussionDelegate.replyToMessage(
                    message!!.discussionId,
                    messageId
                )
                popupWindow?.dismiss()
            }

            SHARE -> {
                App.runThread(
                    ShareSelectedMessageTask(
                        activity,
                        messageId
                    )
                )
                popupWindow?.dismiss()
            }

            FORWARD -> {
                discussionDelegate.initiateMessageForward(
                    messageId
                ) { popupWindow?.dismiss() }
            }

            COPY -> {
                if (message!!.hasAttachments() && message!!.contentBody.isNullOrEmpty()
                        .not()
                ) {
                    copyMenuExpanded = true
                } else if (message!!.hasAttachments()) {
                    App.runThread(
                        CopySelectedMessageTask(
                            activity,
                            messageId,
                            true
                        )
                    )
                    popupWindow?.dismiss()
                } else {
                    App.runThread(
                        CopySelectedMessageTask(
                            activity,
                            messageId,
                            false
                        )
                    )
                    popupWindow?.dismiss()
                }
            }

            SELECT -> {
                discussionDelegate.selectMessage(
                    messageId,
                    message?.isForwardable == true,
                    if (message?.isBookmarkableAndDetailable == true)
                        message?.bookmarked == true
                    else
                        null
                )
                popupWindow?.dismiss()
            }

            DETAILS -> {
                discussionDelegate.doNotMarkAsReadOnPause()
                App.openMessageDetails(
                    activity,
                    messageId,
                    message?.hasAttachments() == true,
                    message?.isInbound == true,
                    message?.status == Message.STATUS_SENT_FROM_ANOTHER_DEVICE
                )
                popupWindow?.dismiss()
            }

            EDIT -> {
                discussionDelegate.editMessage(message)
                popupWindow!!.dismiss()
            }

            BOOKMARK -> {
                App.runThread {
                    message?.let { message ->
                        discussion?.let { discussion ->
                            AppDatabase.getInstance().messageDao()
                                .updateBookmarked(
                                    messageId,
                                    !message.bookmarked
                                )
                            PropagateBookmarkedMessageChangeTask(
                                discussion.bytesOwnedIdentity,
                                message,
                                !message.bookmarked
                            ).run()
                        }
                    }
                }
                popupWindow?.dismiss()
            }

            DELETE -> {
                App.runThread {
                    val offerToRemoteDeleteEverywhere: Boolean
                    val remoteDeletingMakesSense: Boolean = listOf(Message.TYPE_INBOUND_MESSAGE, Message.TYPE_OUTBOUND_MESSAGE, Message.TYPE_INBOUND_EPHEMERAL_MESSAGE).contains(message?.messageType) && (message?.wipeStatus == Message.WIPE_STATUS_NONE)
                    if (remoteDeletingMakesSense) {
                        if (discussion?.discussionType == Discussion.TYPE_GROUP_V2) {
                            val group2 = AppDatabase.getInstance()
                                .group2Dao()[discussion!!.bytesOwnedIdentity, discussion!!.bytesDiscussionIdentifier]
                            if (group2 != null) {
                                offerToRemoteDeleteEverywhere = ((group2.ownPermissionEditOrRemoteDeleteOwnMessages && (message?.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                                        || (group2.ownPermissionRemoteDeleteAnything && ((message?.messageType == Message.TYPE_INBOUND_MESSAGE) || (message?.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE))))
                                        && AppDatabase.getInstance().group2MemberDao().groupHasMembers(discussion!!.bytesOwnedIdentity, discussion!!.bytesDiscussionIdentifier)
                            } else {
                                offerToRemoteDeleteEverywhere = false
                            }
                        } else if (discussion?.discussionType == Discussion.TYPE_GROUP) {
                            offerToRemoteDeleteEverywhere = (discussion!!.isNormal && (message?.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                                    && AppDatabase.getInstance().contactGroupJoinDao().groupHasMembers(discussion!!.bytesOwnedIdentity, discussion!!.bytesDiscussionIdentifier)
                        } else {
                            offerToRemoteDeleteEverywhere = (discussion!!.isNormal && (message?.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                        }
                    } else {
                        offerToRemoteDeleteEverywhere = false;
                    }

                    val builder = SecureDeleteEverywhereDialogBuilder(
                        activity,
                        SecureDeleteEverywhereDialogBuilder.Type.MESSAGE,
                        1,
                        offerToRemoteDeleteEverywhere,
                        remoteDeletingMakesSense
                    )
                        .setDeleteCallback { deletionChoice: SecureDeleteEverywhereDialogBuilder.DeletionChoice ->
                            App.runThread(DeleteMessagesTask(listOf(messageId), deletionChoice))
                        }
                    Handler(Looper.getMainLooper()).post { builder.create().show() }
                }
                popupWindow?.dismiss()
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun buildPopupWindow() {
        vibrator?.vibrate(20)

        wrappedContext = ContextThemeWrapper(activity, R.style.SubtleBlueRipple)

        val popUpView = LayoutInflater.from(activity)
            .inflate(R.layout.view_unified_reaction_and_swipe, null) as ConstraintLayout
        popUpView.setOnClickListener { popupWindow!!.dismiss() }

        val owner = activity.window.decorView.findViewTreeLifecycleOwner()
        val saveOwner = activity.window.decorView.findViewTreeSavedStateRegistryOwner()

        val actions = listOf<PopupActionType>().toMutableList().apply {
            if (discussion?.isNormal == true
                && message?.messageType == Message.TYPE_OUTBOUND_MESSAGE
                && message?.wipeStatus != Message.WIPE_STATUS_WIPED
                && message?.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED
                && message?.isLocationMessage == false
            ) {
                add(EDIT)
            }
            if (discussion?.isNormal == true
                && message?.messageType == Message.TYPE_INBOUND_MESSAGE
                && message?.wipeStatus != Message.WIPE_STATUS_WIPED
                && message?.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED
            ) {
                add(REPLY)
            }
            if (message?.isForwardable == true) {
                add(SHARE)
                add(FORWARD)
                add(COPY)
            }
            add(SELECT)
            if (message?.isBookmarkableAndDetailable == true) {
                add(DETAILS)
                if (message?.bookmarked == true) {
                    add(0, BOOKMARK) // un-bookmark as the first action for bookmarked messages
                } else {
                    add(BOOKMARK)
                }
            }
            // for outbound messages, put REPLY at the end
            if (discussion?.isNormal == true
                && message?.messageType == Message.TYPE_OUTBOUND_MESSAGE
                && message?.wipeStatus != Message.WIPE_STATUS_WIPED
                && message?.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED
            ) {
                add(REPLY)
            }
            add(DELETE)
        }


        val popupActionMenu = ComposeView(activity).apply {
            id = R.id.popup_action_menu
            consumeWindowInsets = false
            setContent {
                AppCompatTheme {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            modifier = Modifier
                                .shadow(elevation = 10.dp)
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(colorResource(id = R.color.almostWhite))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions.subList(0, min(actions.size, maxVisibleActions))
                                .forEach { popupActionType ->

                                    when (popupActionType) {
                                        REPLY, SHARE, FORWARD, SELECT, DETAILS, EDIT ->
                                            PopupActionButton(
                                                text = stringResource(id = popupActionType.stringRes),
                                                action = { onAction(popupActionType) },
                                                image = popupActionType.getImage(message)
                                            )

                                        COPY ->
                                            PopupActionButton(text = stringResource(id = popupActionType.stringRes),
                                                action = { onAction(popupActionType) }) {
                                                popupActionType.getImage(message = message).invoke()
                                                CopyPopupMenu(expanded = copyMenuExpanded,
                                                    dismiss = {
                                                        copyMenuExpanded = false
                                                        popupWindow?.dismiss()
                                                    })
                                            }

                                        BOOKMARK ->
                                            PopupActionButton(
                                                text = if (message?.bookmarked == true) stringResource(
                                                    id = R.string.menu_action_unbookmark
                                                )
                                                else stringResource(id = R.string.menu_action_bookmark),
                                                action = { onAction(popupActionType) },
                                                image = popupActionType.getImage(message)
                                            )

                                        DELETE ->
                                            PopupActionButton(
                                                text = stringResource(id = popupActionType.stringRes),
                                                color = Color.White,
                                                backgroundColor = colorResource(id = R.color.red),
                                                action = { onAction(popupActionType) },
                                                image = popupActionType.getImage(message)
                                            )
                                    }

                                }
                            if (actions.size > maxVisibleActions) {
                                var overflowMenuExpanded by remember {
                                    mutableStateOf(false)
                                }
                                PopupActionButton(action = {
                                    overflowMenuExpanded = true
                                }) {
                                    if (overflowMenuExpanded) {
                                        Popup(alignment = Alignment.BottomEnd,
                                            onDismissRequest = { overflowMenuExpanded = false }
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp)
                                                    .widthIn(max = 300.dp),
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.width(Max)) {
                                                    actions.subList(maxVisibleActions, actions.size)
                                                        .forEach { popupActionType ->
                                                            DropdownMenuItem(
                                                                leadingIcon = popupActionType.getImage(
                                                                    message = message,
                                                                    tint = colorResource(
                                                                        id = R.color.red
                                                                    ).takeIf { popupActionType == DELETE }),
                                                                text = {
                                                                    Text(
                                                                        text = if (popupActionType == BOOKMARK) {
                                                                            if (message?.bookmarked == true) stringResource(
                                                                                id = R.string.menu_action_unbookmark
                                                                            )
                                                                            else stringResource(id = R.string.menu_action_bookmark)
                                                                        } else {
                                                                            stringResource(id = popupActionType.stringRes)
                                                                        },
                                                                        color = if (popupActionType == DELETE) colorResource(
                                                                            id = R.color.red
                                                                        ) else colorResource(
                                                                            id = R.color.almostBlack
                                                                        )
                                                                    )
                                                                },
                                                                onClick = {
                                                                    overflowMenuExpanded = false
                                                                    onAction(popupActionType)
                                                                })
                                                        }
                                                }
                                            }
                                        }
                                    }
                                    Image(
                                        modifier = Modifier
                                            .size(24.dp),
                                        imageVector = Icons.Rounded.MoreVert,
                                        colorFilter = ColorFilter.tint(colorResource(id = R.color.almostBlack)),
                                        contentDescription = "more"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        popUpView.apply {
            id = android.R.id.content
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(saveOwner)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(popupActionMenu)
        }

        popupWindow = PopupWindow(popUpView).apply {
            isFocusable = true
            elevation = 12f
            animationStyle = R.style.FadeInAndOutAnimation
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(ColorDrawable())
            setOnDismissListener { discussionDelegate.setAdditionalBottomPadding(0) }
        }

        reactionsPopUpLinearLayout = popUpView.findViewById(R.id.reactions_popup_linear_view)
        reactionsPopUpLinearLayout?.isClickable = true

        // determine the max panel width: screen width with 16dp margin start and end
        val maxPanelWidthDp = ((parentView.width / metrics.density) - 32).toInt()

        // compute the font size so that 7 reactions (6 and the +) fit
        val viewSizeDp = min(56.0, (maxPanelWidthDp / 7).toDouble()).toInt()
        viewSizePx = (viewSizeDp * metrics.density).toInt()
        fontSizeDp = (viewSizeDp * 5) / 8 // font size if 5/8 of view size

        reactionConstraintLayout = popUpView.findViewById(R.id.reactions_constraint_layout)
        reactionFlow = popUpView.findViewById(R.id.reactions_flow)
        plusButton = popUpView.findViewById(R.id.plus_button)

        if ((message!!.messageType != Message.TYPE_OUTBOUND_MESSAGE && message!!.messageType != Message.TYPE_INBOUND_MESSAGE)
            || (message!!.wipeStatus == Message.WIPE_STATUS_WIPED
                    ) || (message!!.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
                    ) || !discussion!!.isNormalOrReadOnly
        ) {
            // no reactions in this case
            reactionsPopUpLinearLayout?.visibility = View.GONE
        } else {
            // reactions are possible
            val layoutTransition = LayoutTransition()
            layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)
            layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
            layoutTransition.disableTransitionType(LayoutTransition.APPEARING)
            layoutTransition.disableTransitionType(LayoutTransition.DISAPPEARING)
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            reactionConstraintLayout?.setLayoutTransition(layoutTransition)

            val plusLayout = LayoutParams(viewSizePx, viewSizePx)
            plusLayout.bottomToBottom = LayoutParams.PARENT_ID
            plusLayout.endToEnd = LayoutParams.PARENT_ID
            plusButton?.setLayoutParams(plusLayout)

            plusButton?.setPadding(viewSizePx / 8, viewSizePx / 8, viewSizePx / 8, viewSizePx / 8)
            plusButton?.setOnClickListener {
                plusOpen = !plusOpen
                fillReactions()
            }
        }

        val buttonsHeightPx = (48 * metrics.density).toInt()
        if (messageViewBottomPx < buttonsHeightPx) {
            additionalBottomPadding = buttonsHeightPx - messageViewBottomPx
            discussionDelegate.setAdditionalBottomPadding(additionalBottomPadding)
        } else {
            additionalBottomPadding = 0
        }

        // only fill reactions at the end because we need the additionalBottomPadding
        fillReactions()

        popupWindow!!.width = parentView.width
        popupWindow!!.height = parentView.height
        if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP_MR1) {
            val pos = IntArray(2)
            parentView.getLocationOnScreen(pos)
            popupWindow!!.showAtLocation(parentView, Gravity.NO_GRAVITY, 0, pos[1])
        } else {
            popupWindow!!.showAtLocation(parentView, Gravity.NO_GRAVITY, 0, 0)
        }
    }

    private var copyMenuExpanded by mutableStateOf(false)

    @Composable
    private fun CopyPopupMenu(expanded: Boolean = false, dismiss: () -> Unit) {
        if (expanded) {
            Popup(
                alignment = Alignment.BottomEnd,
                onDismissRequest = dismiss
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .widthIn(max = 300.dp),
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.width(Max)) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.menu_action_copy_message_text)) },
                            onClick = {
                                App.runThread(CopySelectedMessageTask(activity, messageId, false))
                                dismiss()
                            })
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.menu_action_copy_text_and_attachments)) },
                            onClick = {
                                App.runThread(CopySelectedMessageTask(activity, messageId, true))
                                dismiss()
                            })
                    }
                }
            }
        }
    }

    private fun fillReactions() {
        val reactions = SettingsActivity.getPreferredReactions()
        if (previousReaction != null && !reactions.contains(previousReaction)) {
            reactions.add(previousReaction!!)
        }

        val sixteenDpInPx = (16 * metrics.density).toInt()

        val panelWidthPx: Int
        val constraintLayoutHeightPx: Int
        if (plusOpen) {
            panelWidthPx = parentView.width - 2 * sixteenDpInPx
            val reactionsPerRow = panelWidthPx / viewSizePx
            constraintLayoutHeightPx = (1 + reactions.size / reactionsPerRow) * viewSizePx
        } else {
            panelWidthPx = (min(7.0, (reactions.size + 1).toDouble()) * viewSizePx).toInt()
            constraintLayoutHeightPx = (1 + reactions.size / 7) * viewSizePx
        }

        val layoutParams = reactionConstraintLayout!!.layoutParams
        layoutParams.width = panelWidthPx
        layoutParams.height = constraintLayoutHeightPx

        val previousIds = reactionFlow!!.referencedIds
        for (viewId in previousIds) {
            val view = reactionConstraintLayout!!.findViewById<View>(viewId)
            if (view != null) {
                reactionFlow!!.removeView(view)
                reactionConstraintLayout!!.removeView(view)
            }
        }

        for (reaction in reactions) {
            val textView: TextView = AppCompatTextView(wrappedContext!!)
            textView.id = View.generateViewId()
            textView.setTextColor(-0x1000000)
            textView.gravity = Gravity.CENTER
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSizeDp.toFloat())
            textView.text = reaction
            textView.layoutParams = ViewGroup.LayoutParams(viewSizePx, viewSizePx)

            if (previousReaction == reaction) {
                textView.setBackgroundResource(R.drawable.background_reactions_panel_previous_reaction)
                textView.setOnClickListener {
                    react(null)
                    popupWindow!!.dismiss()
                }
                textView.setOnLongClickListener {
                    if (!plusOpen) {
                        react(null)
                        popupWindow!!.dismiss()
                    }
                    true
                }
            } else {
                textView.setBackgroundResource(R.drawable.background_circular_ripple)
                textView.setOnClickListener {
                    react(reaction)
                    popupWindow!!.dismiss()
                }
                textView.setOnLongClickListener {
                    if (plusOpen) {
                        togglePreferred(reaction)
                    } else {
                        react(reaction)
                        popupWindow!!.dismiss()
                    }
                    true
                }
            }

            reactionConstraintLayout!!.addView(textView)
            reactionFlow!!.addView(textView)
        }

        reactionConstraintLayout!!.requestLayout()

        val panelHeightPx: Int
        if (plusOpen) {
            plusButton!!.setImageResource(R.drawable.ic_minus_reaction)
            if (separatorTextView == null) {
                separatorTextView = TextView(wrappedContext)
                separatorTextView!!.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (24 * metrics.density).toInt(),
                    0f
                )
                separatorTextView!!.setBackgroundColor(
                    ContextCompat.getColor(
                        activity,
                        R.color.lighterGrey
                    )
                )
                separatorTextView!!.setTextColor(ContextCompat.getColor(activity, R.color.greyTint))
                separatorTextView!!.isAllCaps = true
                separatorTextView!!.text =
                    activity.getString(R.string.label_long_press_for_favorite)
                separatorTextView!!.maxLines = 1
                separatorTextView!!.ellipsize = END
                separatorTextView!!.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
                separatorTextView!!.gravity = Gravity.CENTER_VERTICAL
                separatorTextView!!.setPadding(
                    (8 * metrics.density).toInt(),
                    0,
                    (8 * metrics.density).toInt(),
                    0
                )
            }
            if (separatorTextView!!.parent == null) {
                reactionsPopUpLinearLayout!!.addView(separatorTextView, 1)
            }


            if (emojiPickerView == null) {
                val emojiClickListener: EmojiClickListener = object : EmojiClickListener {
                    override fun onClick(emoji: String) {
                        react(emoji)
                        popupWindow!!.dismiss()
                    }

                    override fun onHighlightedClick(emojiView: View, emoji: String) {
                        react(null)
                        popupWindow!!.dismiss()
                    }

                    override fun onLongClick(emoji: String) {
                        togglePreferred(emoji)
                    }
                }

                emojiPickerRows = max(
                    1.0,
                    min(
                        4.0,
                        ((parentView.height - (constraintLayoutHeightPx + (24 * metrics.density).toInt() + metrics.density.toInt() + (28 * metrics.density).toInt())) / (40 * metrics.density).toInt()).toDouble()
                    )
                )
                    .toInt()

                emojiPickerView = EmojiPickerViewFactory.createEmojiPickerView(
                    activity,
                    parentView,
                    emojiClickListener,
                    null,
                    emojiPickerRows,
                    true,
                    previousReaction
                )
                emojiPickerView?.setLayoutParams(
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0f
                    )
                )
            }
            if (emojiPickerView!!.parent == null) {
                reactionsPopUpLinearLayout!!.addView(emojiPickerView, 2)
            }
            panelHeightPx =
                constraintLayoutHeightPx + (24 * metrics.density).toInt() + emojiPickerRows * ((40 * metrics.density).toInt()) + metrics.density.toInt() + (28 * metrics.density).toInt()
        } else {
            plusButton!!.setImageResource(R.drawable.ic_plus_reaction)
            if (separatorTextView != null && separatorTextView!!.parent != null) {
                reactionsPopUpLinearLayout!!.removeView(separatorTextView)
            }
            if (emojiPickerView != null && emojiPickerView!!.parent != null) {
                reactionsPopUpLinearLayout!!.removeView(emojiPickerView)
            }
            panelHeightPx = constraintLayoutHeightPx
        }

        var fingerOffset =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 8f, metrics).toInt()
        fingerOffset += additionalBottomPadding
        val x = max(
            sixteenDpInPx.toDouble(),
            min(
                (clickX - panelWidthPx / 2).toDouble(),
                (parentView.width - panelWidthPx - sixteenDpInPx).toDouble()
            )
        )
            .toInt()

        // adjust fingerOffset so the popup remains on screen
        if (clickY - constraintLayoutHeightPx - fingerOffset < 0) {
            fingerOffset = clickY - constraintLayoutHeightPx
        } else if (clickY - constraintLayoutHeightPx - fingerOffset + panelHeightPx > parentView.height) {
            fingerOffset = clickY - constraintLayoutHeightPx + panelHeightPx - parentView.height
        }


        val reactionsPopUpLayoutParams = reactionsPopUpLinearLayout!!.layoutParams as LayoutParams
        reactionsPopUpLayoutParams.topMargin = clickY - constraintLayoutHeightPx - fingerOffset
        reactionsPopUpLayoutParams.leftMargin = x
        reactionsPopUpLayoutParams.height = panelHeightPx
        reactionsPopUpLayoutParams.width = panelWidthPx

        reactionsPopUpLinearLayout!!.layoutParams = reactionsPopUpLayoutParams
    }

    private fun togglePreferred(emoji: String) {
        val preferredReactions = SettingsActivity.getPreferredReactions()
        if (preferredReactions.contains(emoji)) {
            preferredReactions.remove(emoji)
        } else {
            preferredReactions.add(emoji)
        }
        SettingsActivity.setPreferredReactions(preferredReactions)
        fillReactions()
    }

    private fun react(emoji: String?) {
        vibrator?.vibrate(20)
        App.runThread(UpdateReactionsTask(messageId, emoji, null, System.currentTimeMillis(), true))
    }
}
