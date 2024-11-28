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
package io.olvid.messenger.discussion.message

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView.State
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.EmptyRecyclerView
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.MessageMetadata
import io.olvid.messenger.databases.entity.MessageRecipientInfo
import io.olvid.messenger.discussion.message.MessageDetailsActivity.RecipientInfosAdapter.ViewHolder
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel
import io.olvid.messenger.viewModels.MessageDetailsViewModel
import java.io.IOException
import java.util.Arrays
import kotlin.math.roundToInt

class MessageDetailsActivity : LockableActivity() {
    private val messageDetailsViewModel: MessageDetailsViewModel by viewModels()
    val audioAttachmentServiceBinding by lazy {
        runCatching { AudioAttachmentServiceBinding(this) }.onFailure { finish() }
            .getOrNull()
    }
    private var recipientInfosRecyclerView: RecyclerView? = null
    var recipientInfosAdapter: RecipientInfosAdapter? = null
    var recipientInfoHeaderAndSeparatorDecoration: RecipientInfoHeaderAndSeparatorDecoration? = null
    private var metadataRecyclerView: EmptyRecyclerView? = null
    private var messageMetadataAdapter: MessageMetadataAdapter? = null
    private var messageDetailsActivityRoot: View? = null
    var hasAttachments: Boolean = false
    private var isInbound: Boolean = false
    private var sentFromOtherDevice: Boolean = false

    // message views
    private var messageScrollView: View? = null
    private var discussionBackground: ImageView? = null
    private val messageView by lazy { findViewById<ComposeView>(R.id.compose_message_view) }

    private var messageIsUndelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_message_details)

        messageDetailsActivityRoot = findViewById(R.id.message_details_root)

        messageDetailsActivityRoot?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                view.updatePadding(top = insets.top, bottom = insets.bottom)
                windowInsets
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.elevation = 0f

        messageView.setContent {
            AppCompatTheme {
                val message by messageDetailsViewModel.message.observeAsState()
                message?.let {
                    val messageExpiration by AppDatabase.getInstance()
                        .messageExpirationDao().getLive(it.id)
                        .observeAsState()
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)) {
                        DateHeader(
                            date = StringUtils.getDayOfDateString(LocalContext.current, it.timestamp).toString()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Message(
                            message = it,
                            scrollToMessage = {},
                            replyAction = { },
                            menuAction = { },
                            editedSeen = { },
                            showSender = false,
                            lastFromSender = false,
                            scale = 1f,
                            audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                            messageExpiration = messageExpiration,
                            openOnClick = false,
                        )
                    }

                }
            }
        }

        messageScrollView = findViewById(R.id.message_scrollview)
        discussionBackground = findViewById(R.id.discussion_background)

        val metrics = resources.displayMetrics
        messageScrollView?.let { scrollView ->
            (scrollView.layoutParams as? ConstraintLayout.LayoutParams)?.let {
                it.matchConstraintMaxHeight = (300 * metrics.density).coerceAtMost(metrics.heightPixels * .4f).roundToInt()
            }
        }

        // metadata
        metadataRecyclerView = findViewById(R.id.message_metadata_recycler_view)
        if (isInbound || sentFromOtherDevice) {
            metadataRecyclerView!!.setEmptyView(findViewById(R.id.empty_metadata_textview))
        }
        messageMetadataAdapter = MessageMetadataAdapter(this)

        metadataRecyclerView!!.setAdapter(messageMetadataAdapter)
        metadataRecyclerView!!.setLayoutManager(LinearLayoutManager(this))
        metadataRecyclerView!!.addItemDecoration(MetadataHeaderAndSeparatorDecoration())

        messageDetailsViewModel.messageMetadata.observe(this, messageMetadataAdapter!!)

        // recipients
        if (!isInbound) {
            val recipientsStatusTextView = findViewById<TextView>(R.id.recipient_status_text_view)
            val otherDeviceExplanationTextView =
                findViewById<TextView>(R.id.sent_from_other_device_text_view)
            recipientInfosRecyclerView = findViewById(R.id.recipient_infos_recycler_view)
            if (sentFromOtherDevice) {
                recipientsStatusTextView.visibility = View.GONE
                recipientInfosRecyclerView!!.setVisibility(View.GONE)
                otherDeviceExplanationTextView.visibility = View.VISIBLE
            } else {
                recipientsStatusTextView.visibility = View.VISIBLE
                recipientInfosRecyclerView!!.setVisibility(View.VISIBLE)
                otherDeviceExplanationTextView.visibility = View.GONE

                recipientInfosAdapter = RecipientInfosAdapter(
                    this
                )
                recipientInfoHeaderAndSeparatorDecoration =
                    RecipientInfoHeaderAndSeparatorDecoration()

                recipientInfosRecyclerView!!.setAdapter(recipientInfosAdapter)
                recipientInfosRecyclerView!!.setLayoutManager(LinearLayoutManager(this))
                recipientInfosRecyclerView!!.addItemDecoration(
                    recipientInfoHeaderAndSeparatorDecoration!!
                )
                messageDetailsViewModel.messageRecipientInfos.observe(
                    this,
                    recipientInfosAdapter!!
                )
            }

            // outbound status
            val statusIndicator = findViewById<View>(R.id.message_details_status_indicator)
            statusIndicator?.setOnClickListener { view: View -> this.statusClicked(view) }
        }


        messageDetailsViewModel.discussionCustomization.observe(this) { discussionCustomization: DiscussionCustomization? ->
            if (discussionCustomization != null) {
                if (discussionCustomization.backgroundImageUrl != null) {
                    App.runThread {
                        val backgroundImageAbsolutePath =
                            App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl)
                        var bitmap = BitmapFactory.decodeFile(backgroundImageAbsolutePath)
                        if (bitmap.byteCount > SelectDetailsPhotoViewModel.MAX_BITMAP_SIZE) {
                            return@runThread
                        }
                        try {
                            val exifInterface = ExifInterface(backgroundImageAbsolutePath)
                            val orientation = exifInterface.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation)
                        } catch (e: IOException) {
                            Logger.d("Error creating ExifInterface for file $backgroundImageAbsolutePath")
                        }
                        val finalBitmap = bitmap
                        Handler(Looper.getMainLooper()).post {
                            discussionBackground!!.setImageBitmap(
                                finalBitmap
                            )
                        }
                    }
                    discussionBackground!!.setBackgroundColor(0x00ffffff)
                } else {
                    discussionBackground!!.setImageDrawable(null)
                    val colorJson = discussionCustomization.colorJson
                    if (colorJson != null) {
                        val color = colorJson.color + ((colorJson.alpha * 255).toInt() shl 24)
                        discussionBackground!!.setBackgroundColor(color)
                    } else {
                        discussionBackground!!.setBackgroundColor(
                            ContextCompat.getColor(
                                this@MessageDetailsActivity,
                                R.color.almostWhite
                            )
                        )
                    }
                }
            } else {
                discussionBackground!!.setBackgroundColor(
                    ContextCompat.getColor(
                        this@MessageDetailsActivity,
                        R.color.almostWhite
                    )
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra(MESSAGE_ID_INTENT_EXTRA)) {
            val messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1)
            messageDetailsViewModel.setMessageId(messageId)
        }
        if (intent.hasExtra(INBOUND_INTENT_EXTRA)) {
            isInbound = intent.getBooleanExtra(INBOUND_INTENT_EXTRA, isInbound)
        }
        if (intent.hasExtra(MESSAGE_ID_INTENT_EXTRA)) {
            sentFromOtherDevice = intent.getBooleanExtra(SENT_FROM_OTHER_DEVICE_INTENT_EXTRA, sentFromOtherDevice)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioAttachmentServiceBinding!!.release()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun statusClicked(view: View) {
        val dialogView =
            layoutInflater.inflate(R.layout.dialog_view_message_status_explanation, null)
        val alertDialog = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        dialogView.findViewById<View>(R.id.ok_button).setOnClickListener { v: View? ->
            alertDialog.dismiss()
        }
        val window = alertDialog.window
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        alertDialog.show()
    }

    inner class RecipientInfosAdapter(context: Context?) : Adapter<ViewHolder>(),
        Observer<List<MessageRecipientInfo>?> {
        var messageRecipientInfos: List<MessageRecipientInfo>? = null
        private val inflater: LayoutInflater = LayoutInflater.from(context)
        val counts: IntArray = IntArray(6)

        init {
            setHasStableIds(true)
        }

        override fun onChanged(messageRecipientInfos: List<MessageRecipientInfo>?) {
            this.messageRecipientInfos = messageRecipientInfos
            recipientInfoHeaderAndSeparatorDecoration!!.clearCache()
            recomputeCounts()
            notifyDataSetChanged()
        }

        private fun recomputeCounts() {
            Arrays.fill(counts, 0)
            if (messageRecipientInfos != null) {
                for (mri in messageRecipientInfos!!) {
                    counts[mri.status()]++
                }
                // 5 is for undelivered messages and corresponds to not sent yet or processing mri
                counts[5] = counts[0] + counts[1]
            }
        }

        override fun getItemId(position: Int): Long {
            if (messageRecipientInfos != null) {
                return messageRecipientInfos!![position].bytesContactIdentity.contentHashCode()
                    .toLong()
            }
            return -1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = inflater.inflate(R.layout.item_view_message_recipient_info, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (messageRecipientInfos == null) {
                return
            }
            val messageRecipientInfo = messageRecipientInfos!![position]
            holder.recipientNameTextView.text =
                AppSingleton.getContactCustomDisplayName(messageRecipientInfo.bytesContactIdentity)
            if (messageRecipientInfo.timestampRead != null) {
                holder.recipientInfoTimestampTextView.text =
                    StringUtils.getPreciseAbsoluteDateString(
                        this@MessageDetailsActivity,
                        messageRecipientInfo.timestampRead!!
                    )
            } else if (messageRecipientInfo.timestampDelivered != null) {
                holder.recipientInfoTimestampTextView.text =
                    StringUtils.getPreciseAbsoluteDateString(
                        this@MessageDetailsActivity,
                        messageRecipientInfo.timestampDelivered!!
                    )
            } else if (messageRecipientInfo.timestampSent != null && messageRecipientInfo.timestampSent != 0L) {
                holder.recipientInfoTimestampTextView.text =
                    StringUtils.getPreciseAbsoluteDateString(
                        this@MessageDetailsActivity,
                        messageRecipientInfo.timestampSent!!
                    )
            } else {
                holder.recipientInfoTimestampTextView.setText(R.string.text_null_timestamp)
            }
        }

        override fun getItemCount(): Int {
            if (messageRecipientInfos != null) {
                return messageRecipientInfos!!.size
            }
            return 0
        }

        inner class ViewHolder(rootView: View) : RecyclerView.ViewHolder(rootView),
            OnClickListener {
            val recipientNameTextView: TextView =
                rootView.findViewById(R.id.recipient_name_text_view)
            val recipientInfoTimestampTextView: TextView =
                rootView.findViewById(R.id.recipient_info_timestamp_text_view)

            init {
                rootView.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                val position = layoutPosition
                if (messageRecipientInfos != null) {
                    val format =
                        "yyyy-MM-dd" + this@MessageDetailsActivity.getString(R.string.text_date_time_separator) + "HH:mm:ss"

                    val messageRecipientInfo = messageRecipientInfos!![position]
                    val nullString = getString(R.string.text_null_timestamp)
                    val sentTime =
                        if (messageRecipientInfo.timestampSent == null || messageRecipientInfo.timestampSent == 0L) nullString else (DateFormat.format(
                            format,
                            messageRecipientInfo.timestampSent!!
                        ) as String)
                    val deliveredTime =
                        if (messageRecipientInfo.timestampDelivered == null || messageRecipientInfo.timestampDelivered == 0L) nullString else (DateFormat.format(
                            format,
                            messageRecipientInfo.timestampDelivered!!
                        ) as String)
                    val readTime =
                        if (messageRecipientInfo.timestampRead == null || messageRecipientInfo.timestampRead == 0L) nullString else (DateFormat.format(
                            format,
                            messageRecipientInfo.timestampRead!!
                        ) as String)

                    val builder = SecureAlertDialogBuilder(
                        this@MessageDetailsActivity,
                        R.style.CustomAlertDialog
                    )
                        .setTitle(recipientNameTextView.text)
                        .setMessage(
                            getString(
                                R.string.dialog_message_recipient_details,
                                sentTime,
                                deliveredTime,
                                readTime
                            )
                        )
                        .setPositiveButton(R.string.button_label_ok, null)
                    builder.create().show()
                }
            }
        }
    }


    inner class RecipientInfoHeaderAndSeparatorDecoration internal constructor() :
        ItemDecoration() {
        private val headerHeight: Int
        private val separatorHeight: Int
        private val itemRect: Rect
        private val bitmapCache: Array<Bitmap?>

        init {
            val metrics = resources.displayMetrics
            headerHeight =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, metrics).toInt()
            separatorHeight =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, metrics).toInt()
            itemRect = Rect()
            bitmapCache = arrayOfNulls(6)
        }

        @SuppressLint("SetTextI18n")
        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: State) {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) {
                    continue
                }
                if (position == 0 || (recipientInfosAdapter!!.messageRecipientInfos!![position].status() != recipientInfosAdapter!!.messageRecipientInfos!![position - 1].status())) {
                    var status = recipientInfosAdapter!!.messageRecipientInfos!![position].status()
                    if (status < MessageRecipientInfo.RECIPIENT_STATUS_SENT && this@MessageDetailsActivity.messageIsUndelivered) {
                        status = 5
                    }
                    // check the cache, and compute it if needed
                    if (bitmapCache[status] == null) {
                        val headerView = LayoutInflater.from(parent.context)
                            .inflate(R.layout.view_message_details_header, parent, false)
                        val textView = headerView.findViewById<TextView>(R.id.header_status_text)
                        val imageView = headerView.findViewById<ImageView>(R.id.header_status_image)
                        val countView = headerView.findViewById<TextView>(R.id.header_count_text)
                        if (recipientInfosAdapter!!.messageRecipientInfos!!.size > 2) {
                            if (status == MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED_AND_READ) {
                                countView.text = getString(
                                    R.string.at_least_xxx,
                                    recipientInfosAdapter!!.counts[status]
                                )
                            } else {
                                countView.text = recipientInfosAdapter!!.counts[status]
                                    .toString() + "/" + recipientInfosAdapter!!.messageRecipientInfos!!.size
                            }
                        } else {
                            countView.visibility = View.GONE
                        }

                        when (status) {
                            MessageRecipientInfo.RECIPIENT_STATUS_NOT_SENT_YET -> {
                                textView.setText(R.string.text_not_sent_yet)
                                imageView.setImageResource(R.drawable.ic_message_status_not_sent_yet)
                                countView.text = recipientInfosAdapter!!.counts[status]
                                    .toString() + "/" + recipientInfosAdapter!!.messageRecipientInfos!!.size
                            }

                            MessageRecipientInfo.RECIPIENT_STATUS_PROCESSING -> {
                                textView.setText(R.string.text_processing)
                                imageView.setImageResource(R.drawable.ic_message_status_processing)
                                countView.text = recipientInfosAdapter!!.counts[status]
                                    .toString() + "/" + recipientInfosAdapter!!.messageRecipientInfos!!.size
                            }

                            MessageRecipientInfo.RECIPIENT_STATUS_SENT -> {
                                textView.setText(R.string.text_sent)
                                imageView.setImageResource(R.drawable.ic_message_status_sent)
                                countView.text =
                                    (recipientInfosAdapter!!.counts[MessageRecipientInfo.RECIPIENT_STATUS_SENT] + recipientInfosAdapter!!.counts[MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED] + recipientInfosAdapter!!.counts[MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED_AND_READ]).toString() + "/" + recipientInfosAdapter!!.messageRecipientInfos!!.size
                            }

                            MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED -> {
                                textView.setText(R.string.text_delivered)
                                imageView.setImageResource(R.drawable.ic_message_status_delivered_one)
                                countView.text =
                                    (recipientInfosAdapter!!.counts[MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED] + recipientInfosAdapter!!.counts[MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED_AND_READ]).toString() + "/" + recipientInfosAdapter!!.messageRecipientInfos!!.size
                            }

                            MessageRecipientInfo.RECIPIENT_STATUS_DELIVERED_AND_READ -> {
                                textView.setText(R.string.text_read)
                                imageView.setImageResource(R.drawable.ic_message_status_delivered_and_read_one)
                                countView.text = getString(
                                    R.string.at_least_xxx,
                                    recipientInfosAdapter!!.counts[status]
                                )
                            }

                            5 -> {
                                textView.setText(R.string.text_undelivered)
                                imageView.setImageResource(R.drawable.ic_message_status_undelivered)
                                countView.text = recipientInfosAdapter!!.counts[status]
                                    .toString() + "/" + recipientInfosAdapter!!.messageRecipientInfos!!.size
                            }
                        }
                        headerView.measure(
                            MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(headerHeight, MeasureSpec.EXACTLY)
                        )
                        headerView.layout(0, 0, headerView.measuredWidth, headerHeight)
                        val headerBitmap =
                            Bitmap.createBitmap(headerView.measuredWidth, headerHeight, ARGB_8888)
                        val bitmapCanvas = Canvas(headerBitmap)
                        headerView.draw(bitmapCanvas)
                        bitmapCache[status] = headerBitmap
                    }
                    canvas.save()
                    parent.getDecoratedBoundsWithMargins(child, itemRect)
                    itemRect.top = (itemRect.top + child.translationY).toInt()
                    itemRect.bottom = (itemRect.bottom + child.translationY).toInt()
                    canvas.drawBitmap(
                        bitmapCache[status]!!,
                        itemRect.left.toFloat(),
                        itemRect.top.toFloat(),
                        null
                    )
                    canvas.restore()
                }
            }
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            super.getItemOffsets(outRect, view, parent, state)
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) {
                return
            }
            if (position == 0 || (recipientInfosAdapter!!.messageRecipientInfos!![position].status() != recipientInfosAdapter!!.messageRecipientInfos!![position - 1].status())) {
                outRect.top += headerHeight
            } else {
                outRect.top += separatorHeight
            }
        }

        fun clearCache() {
            Arrays.fill(bitmapCache, null)
        }
    }

    private inner class MessageMetadataAdapter(context: Context?) :
        Adapter<MessageMetadataAdapter.ViewHolder>(), Observer<List<MessageMetadata>> {
        private var messageMetadatas: List<MessageMetadata>? = null
        private val inflater: LayoutInflater = LayoutInflater.from(context)
        private var hasUploadedMetadata = false
        private var sentTimestamp: Long? = null
        private var inbound = false

        init {
            setHasStableIds(true)
        }

        fun setSentTimestamp(sentTimestamp: Long?, inbound: Boolean) {
            this.sentTimestamp = sentTimestamp
            this.inbound = inbound
            notifyDataSetChanged()
        }

        override fun onChanged(messageMetadatas: List<MessageMetadata>) {
            // check if a messageMetadata is of kind KIND_UPLOADED
            hasUploadedMetadata = false
            for (messageMetadata in messageMetadatas) {
                if (messageMetadata.kind == MessageMetadata.KIND_UPLOADED) {
                    hasUploadedMetadata = true
                    break
                }
            }
            this.messageMetadatas = messageMetadatas
            notifyDataSetChanged()
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = inflater.inflate(R.layout.item_view_message_metadata, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (messageMetadatas != null) {
                val metadata: MessageMetadata
                if (sentTimestamp == null || (hasUploadedMetadata && inbound)) {
                    metadata = messageMetadatas!![position]
                } else {
                    if (position == 0) {
                        holder.metadataTimestampDateTextView.text =
                            StringUtils.getPreciseAbsoluteDateString(
                                this@MessageDetailsActivity,
                                sentTimestamp!!
                            )
                        if (inbound) {
                            holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_uploaded)
                        } else {
                            holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_sent)
                        }
                        return
                    } else {
                        metadata = messageMetadatas!![position - 1]
                    }
                }

                holder.metadataTimestampDateTextView.text =
                    StringUtils.getPreciseAbsoluteDateString(
                        this@MessageDetailsActivity,
                        metadata.timestamp
                    )
                when (metadata.kind) {
                    MessageMetadata.KIND_UPLOADED -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_uploaded)
                    }

                    MessageMetadata.KIND_DELIVERED -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_delivered)
                    }

                    MessageMetadata.KIND_READ -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_read)
                    }

                    MessageMetadata.KIND_WIPED -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_wiped)
                    }

                    MessageMetadata.KIND_EDITED -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_edited)
                    }

                    MessageMetadata.KIND_LOCATION_SHARING_LATEST_UPDATE -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_location_sharing_latest_update)
                    }

                    MessageMetadata.KIND_LOCATION_SHARING_END -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_location_sharing_end)
                    }

                    MessageMetadata.KIND_REMOTE_DELETED -> {
                        val bytesCurrentIdentity = AppSingleton.getBytesCurrentIdentity()
                        if (bytesCurrentIdentity.contentEquals(metadata.bytesRemoteIdentity)) {
                            holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_remote_deleted_by_you)
                        } else {
                            val contactName =
                                AppSingleton.getContactCustomDisplayName(metadata.bytesRemoteIdentity)
                            if (contactName == null) {
                                holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_remote_deleted)
                            } else {
                                holder.metadataDescriptionTextView.text = getString(
                                    R.string.label_metadata_kind_remote_deleted_by,
                                    contactName
                                )
                            }
                        }
                    }

                    MessageMetadata.KIND_UNDELIVERED -> {
                        holder.metadataDescriptionTextView.setText(R.string.label_metadata_kind_undelivered)
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            val count = if ((messageMetadatas == null)) 0 else messageMetadatas!!.size
            return if (sentTimestamp == null || (hasUploadedMetadata && inbound)) {
                count
            } else {
                count + 1
            }
        }

        internal inner class ViewHolder(rootView: View) : RecyclerView.ViewHolder(rootView) {
            val metadataDescriptionTextView: TextView =
                rootView.findViewById(R.id.metadata_description_text_view)
            val metadataTimestampDateTextView: TextView =
                rootView.findViewById(R.id.metadata_timestamp_date_text_view)
        }
    }

    inner class MetadataHeaderAndSeparatorDecoration internal constructor() : ItemDecoration() {
        private val headerHeight: Int
        private val separatorHeight: Int
        private val itemRect: Rect
        private var bitmapCache: Bitmap?

        init {
            val metrics = resources.displayMetrics
            headerHeight =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, metrics).toInt()
            separatorHeight =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, metrics).toInt()
            itemRect = Rect()
            bitmapCache = null
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: State) {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) {
                    continue
                }
                if (position == 0) {
                    if (bitmapCache == null) {
                        val headerView = LayoutInflater.from(parent.context)
                            .inflate(R.layout.view_message_details_header, parent, false)
                        val textView = headerView.findViewById<TextView>(R.id.header_status_text)
                        val imageView = headerView.findViewById<ImageView>(R.id.header_status_image)
                        headerView.findViewById<View>(R.id.header_count_text).visibility =
                            View.GONE

                        textView.setText(R.string.text_message_timeline)
                        imageView.setImageResource(R.drawable.ic_timer)

                        headerView.measure(
                            MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(headerHeight, MeasureSpec.EXACTLY)
                        )
                        headerView.layout(0, 0, headerView.measuredWidth, headerHeight)
                        val headerBitmap =
                            Bitmap.createBitmap(headerView.measuredWidth, headerHeight, ARGB_8888)
                        val bitmapCanvas = Canvas(headerBitmap)
                        headerView.draw(bitmapCanvas)
                        bitmapCache = headerBitmap
                    }
                    canvas.save()
                    parent.getDecoratedBoundsWithMargins(child, itemRect)
                    itemRect.top = (itemRect.top + child.translationY).toInt()
                    itemRect.bottom = (itemRect.bottom + child.translationY).toInt()
                    canvas.drawBitmap(
                        bitmapCache!!,
                        itemRect.left.toFloat(),
                        itemRect.top.toFloat(),
                        null
                    )
                    canvas.restore()
                }
            }
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            super.getItemOffsets(outRect, view, parent, state)
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) {
                return
            }
            if (position == 0) {
                outRect.top += headerHeight
            } else {
                outRect.top += separatorHeight
            }
        }
    }

    companion object {
        const val MESSAGE_ID_INTENT_EXTRA: String = "message_id"
        const val INBOUND_INTENT_EXTRA: String = "inbound"
        const val SENT_FROM_OTHER_DEVICE_INTENT_EXTRA: String = "sent_from_other_device"
    }
}
