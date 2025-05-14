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
package io.olvid.messenger.customClasses

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Paint.Align.CENTER
import android.graphics.Paint.Style.FILL
import android.graphics.PorterDuff.Mode.CLEAR
import android.graphics.PorterDuff.Mode.SRC_IN
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.exifinterface.media.ExifInterface
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R.color
import io.olvid.messenger.R.dimen
import io.olvid.messenger.R.drawable
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion
import java.io.IOException
import kotlin.math.min

class InitialView : View {
    private var bytes: ByteArray? = null
    private var initial: String? = null
    var photoUrl // absolute path
            : String? = null
        private set
    private var keycloakCertified = false
    private var locked = false
    private var inactive = false
    private var notOneToOne = false
    private var recentlyOnline = true
    private var contactTrustLevel: Int? = null
    private var backgroundPaint: Paint? = null
    private var insidePaint: Paint? = null
    private var overlayBitmap: Bitmap? = null
    private var width = 0
    private var height = 0
    private var size = 0
    private var top = 0f
    private var left = 0f
    private var bitmap: Bitmap? = null
    private var insideX = 0f
    private var insideY = 0f

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        if (isInEditMode) {
            setInitial(byteArrayOf(0, 1, 35), "A")
            setKeycloakCertified(true)
        }
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    fun setContact(contact: Contact) {
        var changed = false
        if (!contact.bytesContactIdentity.contentEquals(bytes)) {
            bytes = contact.bytesContactIdentity
            changed = true
        }
        val contactInitial = StringUtils.getInitial(contact.getCustomDisplayName())
        if (initial != contactInitial) {
            initial = contactInitial
            changed = true
        }
        val contactPhotoUrl = App.absolutePathFromRelative(contact.getCustomPhotoUrl())
        if (photoUrl != contactPhotoUrl) {
            photoUrl = contactPhotoUrl
            changed = true
        }
        if (keycloakCertified != contact.keycloakManaged) {
            keycloakCertified = contact.keycloakManaged
            changed = true
        }
        if (inactive == contact.active) { // We are indeed checking that the value changed ;)
            inactive = !contact.active
            changed = true
        }
        if (locked) {
            locked = false
            changed = true
        }
        if (notOneToOne == contact.oneToOne) { // We are indeed checking that the value changed ;)
            notOneToOne = !contact.oneToOne
            changed = true
        }
        if (recentlyOnline != contact.recentlyOnline) {
            recentlyOnline = contact.recentlyOnline
            changed = true
        }
        if (contactTrustLevel == null || contactTrustLevel != contact.trustLevel) {
            contactTrustLevel = contact.trustLevel
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setOwnedIdentity(ownedIdentity: OwnedIdentity) {
        var changed = false
        if (!ownedIdentity.bytesOwnedIdentity.contentEquals(bytes)) {
            bytes = ownedIdentity.bytesOwnedIdentity
            changed = true
        }
        val contactInitial = StringUtils.getInitial(ownedIdentity.getCustomDisplayName())
        if (initial != contactInitial) {
            initial = contactInitial
            changed = true
        }
        val contactPhotoUrl = App.absolutePathFromRelative(ownedIdentity.photoUrl)
        if (photoUrl != contactPhotoUrl) {
            photoUrl = contactPhotoUrl
            changed = true
        }
        if (keycloakCertified != ownedIdentity.keycloakManaged) {
            keycloakCertified = ownedIdentity.keycloakManaged
            changed = true
        }
        if (inactive == ownedIdentity.active) { // We are indeed checking that the value changed ;)
            inactive = !ownedIdentity.active
            changed = true
        }
        if (locked) {
            locked = false
            changed = true
        }
        if (notOneToOne) {
            notOneToOne = false
            changed = true
        }
        if (!recentlyOnline) {
            recentlyOnline = true
            changed = true
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setGroup(group: Group) {
        var changed = false
        if (!group.bytesGroupOwnerAndUid.contentEquals(bytes)) {
            bytes = group.bytesGroupOwnerAndUid
            changed = true
        }
        if (initial != null) {
            initial = null
            changed = true
        }
        val groupPhotoUrl = App.absolutePathFromRelative(group.getCustomPhotoUrl())
        if (photoUrl != groupPhotoUrl) {
            photoUrl = groupPhotoUrl
            changed = true
        }
        if (keycloakCertified) {
            keycloakCertified = false
            changed = true
        }
        if (inactive) {
            inactive = false
            changed = true
        }
        if (locked) {
            locked = false
            changed = true
        }
        if (notOneToOne) {
            notOneToOne = false
            changed = true
        }
        if (!recentlyOnline) {
            recentlyOnline = true
            changed = true
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setGroup2(group: Group2) {
        var changed = false
        if (!group.bytesGroupIdentifier.contentEquals(bytes)) {
            bytes = group.bytesGroupIdentifier
            changed = true
        }
        if (initial != null) {
            initial = null
            changed = true
        }
        val groupPhotoUrl = App.absolutePathFromRelative(group.getCustomPhotoUrl())
        if (photoUrl != groupPhotoUrl) {
            photoUrl = groupPhotoUrl
            changed = true
        }
        if (keycloakCertified != group.keycloakManaged) {
            keycloakCertified = group.keycloakManaged
            changed = true
        }
        if (inactive) {
            inactive = false
            changed = true
        }
        if (locked) {
            locked = false
            changed = true
        }
        if (notOneToOne) {
            notOneToOne = false
            changed = true
        }
        if (!recentlyOnline) {
            recentlyOnline = true
            changed = true
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setDiscussion(discussion: Discussion) {
        var changed = false
        when (discussion.status) {
            Discussion.STATUS_LOCKED -> {
                if (!locked) {
                    locked = true
                    changed = true
                }
                if (bytes == null || bytes!!.isNotEmpty()) {
                    bytes = ByteArray(0)
                    changed = true
                }
                if (initial == null || initial!!.isNotEmpty()) {
                    initial = ""
                    changed = true
                }
                if (!recentlyOnline) {
                    recentlyOnline = true
                    changed = true
                }
            }
            else -> {
                if (locked) {
                    locked = false
                    changed = true
                }
                when (discussion.discussionType) {
                    Discussion.TYPE_CONTACT -> {
                        val discussionInitial = StringUtils.getInitial(discussion.title)
                        if (initial != discussionInitial) {
                            initial = discussionInitial
                            changed = true
                        }
                        val contactRecentlyOnline = AppSingleton.getContactCacheInfo(discussion.bytesDiscussionIdentifier)?.recentlyOnline ?: true
                        if (recentlyOnline != contactRecentlyOnline) {
                            recentlyOnline = contactRecentlyOnline
                            changed = true
                        }
                    }
                    Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 -> {
                        if (initial != null) {
                            initial = null
                            changed = true
                        }
                        if (!recentlyOnline) {
                            recentlyOnline = true
                            changed = true
                        }
                    }
                    else -> {
                        Logger.e("Unknown discussion type")
                        return
                    }
                }
                if (!bytes.contentEquals(discussion.bytesDiscussionIdentifier)) {
                    bytes = discussion.bytesDiscussionIdentifier
                    changed = true
                }
            }
        }
        val discussionPhotoUrl = App.absolutePathFromRelative(discussion.photoUrl)
        if (photoUrl != discussionPhotoUrl) {
            photoUrl = discussionPhotoUrl
            changed = true
        }
        if (keycloakCertified != discussion.keycloakManaged) {
            keycloakCertified = discussion.keycloakManaged
            changed = true
        }
        if (inactive == discussion.active) { // We are indeed checking that the value changed ;)
            inactive = !discussion.active
            changed = true
        }
        if (contactTrustLevel != discussion.trustLevel) {
            contactTrustLevel = discussion.trustLevel
            changed = true
        }
        if (notOneToOne) {
            notOneToOne = false
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setDiscussion(discussion: SearchableDiscussion) {
        var changed = false
        if (!bytes.contentEquals(discussion.byteIdentifier)) {
            bytes = discussion.byteIdentifier
            changed = true
        }
        if (discussion.isGroupDiscussion) {
            if (initial != null) {
                initial = null
                changed = true
            }
            if (locked) {
                locked = false
                changed = true
            }
        } else if (discussion.byteIdentifier.isNotEmpty()) {
            val discussionInitial = StringUtils.getInitial(discussion.title)
            if (initial != discussionInitial) {
                initial = discussionInitial
                changed = true
            }
            if (locked) {
                locked = false
                changed = true
            }
        } else {
            if (initial == null || initial!!.isNotEmpty()) {
                initial = ""
                changed = true
            }
            if (!locked) {
                locked = true
                changed = true
            }
        }
        val discussionPhotoUrl = App.absolutePathFromRelative(discussion.photoUrl)
        if (photoUrl != discussionPhotoUrl) {
            photoUrl = discussionPhotoUrl
            changed = true
        }
        if (keycloakCertified != discussion.keycloakManaged) {
            keycloakCertified = discussion.keycloakManaged
            changed = true
        }
        if (inactive == discussion.active) { // We are indeed checking that the value changed ;)
            inactive = !discussion.active
            changed = true
        }
        if (notOneToOne) {
            notOneToOne = false
            changed = true
        }
        if (!recentlyOnline) {
            recentlyOnline = true
            changed = true
        }
        if (contactTrustLevel != null) {
            // we could properly set this for ontToOne discussions, but this is not worth the added work!
            contactTrustLevel = null
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setFromCache(bytesIdentifier: ByteArray) {
        var changed = false
        val displayName: String? =
            if (bytesIdentifier.contentEquals(AppSingleton.getBytesCurrentIdentity())) {
                val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value
                if (ownedIdentity != null) {
                    ownedIdentity.getCustomDisplayName()
                } else {
                    AppSingleton.getContactCustomDisplayName(bytesIdentifier)
                }
            } else {
                AppSingleton.getContactCustomDisplayName(bytesIdentifier)
            }
        val contactInitial: String
        val bytes: ByteArray
        if (displayName == null) {
            contactInitial = "?"
            bytes = ByteArray(0)
        } else {
            contactInitial = StringUtils.getInitial(displayName)
            bytes = bytesIdentifier
        }
        if (!this.bytes.contentEquals(bytes)) {
            this.bytes = bytes
            changed = true
        }
        if (initial != contactInitial) {
            initial = contactInitial
            changed = true
        }
        val contactPhotoUrl =
            App.absolutePathFromRelative(AppSingleton.getContactPhotoUrl(bytesIdentifier))
        if (photoUrl != contactPhotoUrl) {
            photoUrl = contactPhotoUrl
            changed = true
        }
        val contactCacheInfo =
            AppSingleton.getContactCacheInfo(bytesIdentifier) ?: ContactCacheInfo(
                keycloakManaged = false,
                active = true,
                oneToOne = true,
                recentlyOnline = true,
                trustLevel = 0
            )
        if (keycloakCertified != contactCacheInfo.keycloakManaged) {
            keycloakCertified = contactCacheInfo.keycloakManaged
            changed = true
        }
        if (inactive == contactCacheInfo.active) { // We are indeed checking that the value changed ;)
            inactive = !contactCacheInfo.active
            changed = true
        }
        if (locked) {
            locked = false
            changed = true
        }
        if (notOneToOne == contactCacheInfo.oneToOne) { // We are indeed checking that the value changed ;)
            notOneToOne = !contactCacheInfo.oneToOne
            changed = true
        }
        if (recentlyOnline != contactCacheInfo.recentlyOnline) {
            recentlyOnline = contactCacheInfo.recentlyOnline
            changed = true
        }
        if (contactTrustLevel != contactCacheInfo.trustLevel) {
            contactTrustLevel = contactCacheInfo.trustLevel
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setUnknown() {
        var changed = false
        if (bytes == null || bytes!!.isNotEmpty()) {
            bytes = ByteArray(0)
            changed = true
        }
        if (initial != "?") {
            initial = "?"
            changed = true
        }
        if (photoUrl != null) {
            photoUrl = null
            changed = true
        }
        if (keycloakCertified) {
            keycloakCertified = false
            changed = true
        }
        if (inactive) {
            inactive = false
            changed = true
        }
        if (locked) {
            locked = false
            changed = true
        }
        if (notOneToOne) {
            notOneToOne = false
            changed = true
        }
        if (!recentlyOnline) {
            recentlyOnline = true
            changed = true
        }
        if (contactTrustLevel != null) {
            contactTrustLevel = null
            changed = true
        }
        if (changed) {
            bitmap = null
            init()
        }
    }

    fun setGroup(groupId: ByteArray?) {
        bitmap = null
        photoUrl = null
        bytes = groupId
        initial = null
        init()
    }

    fun setInitial(identityBytes: ByteArray?, initial: String?) {
        bitmap = null
        photoUrl = null
        bytes = identityBytes
        this.initial = initial
        init()
    }

    fun setPhotoUrl(bytes: ByteArray?, relativePathPhotoUrl: String?) {
        bitmap = null
        photoUrl = App.absolutePathFromRelative(relativePathPhotoUrl)
        this.bytes = bytes
        init()
    }

    fun setAbsolutePhotoUrl(bytes: ByteArray?, absolutePhotoUrl: String?) {
        bitmap = null
        photoUrl = absolutePhotoUrl
        this.bytes = bytes
        initial = null
        init()
    }

    fun reset() {
        bytes = null
        bitmap = null
        keycloakCertified = false
        locked = false
        inactive = false
        notOneToOne = false
        recentlyOnline = true
        contactTrustLevel = null
    }

    fun setKeycloakCertified(keycloakCertified: Boolean) {
        if (this.keycloakCertified != keycloakCertified) {
            bitmap = null
            this.keycloakCertified = keycloakCertified
            init()
        }
    }

    fun setLocked(locked: Boolean) {
        if (this.locked != locked) {
            this.locked = locked
            init()
        }
    }

    fun setInactive(inactive: Boolean) {
        if (this.inactive != inactive) {
            this.inactive = inactive
            init()
        }
    }

//    fun setNotOneToOne() {
//        if (notOneToOne) {
//            notOneToOne = false
//            init()
//        }
//    }
//
//    fun setRecentlyOnline(recentlyOnline: Boolean) {
//        if (this.recentlyOnline != recentlyOnline) {
//            this.recentlyOnline = recentlyOnline
//            init()
//        }
//    }
//
//    fun setNullTrustLevel() {
//        if (contactTrustLevel != null) {
//            contactTrustLevel = null
//            init()
//        }
//    }

    private fun init() {
        if (bytes == null || size == 0) {
            return
        }
        invalidate()
        top = (height - size) * .5f
        left = (width - size) * .5f
        if (photoUrl != null) {
            var options = Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(photoUrl, options)
            val photoSize = min(options.outWidth, options.outHeight)
            if (photoSize != -1) {
                val subSampling = photoSize / size
                options = Options()
                options.inSampleSize = subSampling
                var squareBitmap = BitmapFactory.decodeFile(photoUrl, options)
                if (squareBitmap != null) {
                    try {
                        val exifInterface = ExifInterface(
                            photoUrl!!
                        )
                        val orientation = exifInterface.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        squareBitmap = PreviewUtils.rotateBitmap(squareBitmap, orientation)
                    } catch (e: IOException) {
                        // exif error, do nothing
                    }
                    bitmap = Bitmap.createBitmap(size, size, ARGB_8888)
                    bitmap?.let {
                        val canvas = Canvas(it)
                        val roundedDrawable =
                            RoundedBitmapDrawableFactory.create(resources, squareBitmap)
                        roundedDrawable.cornerRadius = size / 2f
                        roundedDrawable.setBounds(0, 0, size, size)
                        if (locked || inactive) {
                            val colorMatrix = ColorMatrix()
                            colorMatrix.setSaturation(.5f)
                            roundedDrawable.colorFilter = ColorMatrixColorFilter(colorMatrix)
                        }
                        roundedDrawable.draw(canvas)
                        if (contactTrustLevel != null && contactTrustLevel != -1 && SettingsActivity.showTrustLevels()) {
                            val dotSize = (.3f * size).toInt()
                            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                            clearPaint.color = Color.TRANSPARENT
                            clearPaint.xfermode = PorterDuffXfermode(CLEAR)
                            canvas.drawOval(
                                RectF(
                                    (size - dotSize).toFloat(),
                                    (size - dotSize).toFloat(),
                                    size.toFloat(),
                                    size.toFloat()
                                ), clearPaint
                            )
                            val dotPaint = getTrustPaint(context, contactTrustLevel!!)
                            canvas.drawOval(
                                RectF(
                                    size - .875f * dotSize,
                                    size - .875f * dotSize,
                                    size - .125f * dotSize,
                                    size - .125f * dotSize
                                ), dotPaint
                            )
                            if (notOneToOne) {
                                canvas.drawOval(
                                    RectF(
                                        size - .65f * dotSize,
                                        size - .65f * dotSize,
                                        size - .35f * dotSize,
                                        size - .35f * dotSize
                                    ), clearPaint
                                )
                            }
                        }
                        if (locked) {
                            val lockSize = (.3f * size).toInt()
                            val keycloakBitmap = Bitmap.createBitmap(lockSize, lockSize, ARGB_8888)
                            val keycloakCanvas = Canvas(keycloakBitmap)
                            val lockDrawable =
                                ResourcesCompat.getDrawable(resources, drawable.ic_lock_circled, null)
                            if (lockDrawable != null) {
                                lockDrawable.setBounds(0, 0, lockSize, lockSize)
                                lockDrawable.draw(keycloakCanvas)
                            }
                            canvas.drawBitmap(keycloakBitmap, (size - lockSize).toFloat(), 0f, null)
                        } else if (inactive) {
                            val blockedSize = (.8f * size).toInt()
                            val blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, ARGB_8888)
                            val blockedCanvas = Canvas(blockedBitmap)
                            val blockedDrawable =
                                ResourcesCompat.getDrawable(resources, drawable.ic_block_outlined, null)
                            if (blockedDrawable != null) {
                                blockedDrawable.setBounds(0, 0, blockedSize, blockedSize)
                                blockedDrawable.draw(blockedCanvas)
                            }
                            canvas.drawBitmap(
                                blockedBitmap,
                                (size - blockedSize) / 2f,
                                (size - blockedSize) / 2f,
                                null
                            )
                        } else if (keycloakCertified) {
                            val keycloakSize = (.3f * size).toInt()
                            val keycloakBitmap =
                                Bitmap.createBitmap(keycloakSize, keycloakSize, ARGB_8888)
                            val keycloakCanvas = Canvas(keycloakBitmap)
                            val keycloakDrawable = ResourcesCompat.getDrawable(
                                resources,
                                drawable.ic_keycloak_certified,
                                null
                            )
                            if (keycloakDrawable != null) {
                                keycloakDrawable.setBounds(0, 0, keycloakSize, keycloakSize)
                                keycloakDrawable.draw(keycloakCanvas)
                            }
                            canvas.drawBitmap(keycloakBitmap, (size - keycloakSize).toFloat(), 0f, null)
                        } else if (!recentlyOnline) {
                            val asleepSize = (.3f * size).toInt()
                            val asleepBitmap =
                                Bitmap.createBitmap(asleepSize, asleepSize, ARGB_8888)
                            val asleepCanvas = Canvas(asleepBitmap)
                            val asleepDrawable = ResourcesCompat.getDrawable(
                                resources,
                                drawable.ic_snooze,
                                null
                            )
                            if (asleepDrawable != null) {
                                asleepDrawable.setBounds(0, 0, asleepSize, asleepSize)
                                asleepDrawable.draw(asleepCanvas)
                            }
                            canvas.drawBitmap(asleepBitmap, (size - asleepSize).toFloat(), 0f, null)
                        }
                    }
                    return
                }
            }
        }
        val lightColor = getLightColor(context, bytes!!)
        val darkColor = getDarkColor(context, bytes!!)
        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundPaint!!.style = FILL
        backgroundPaint!!.color = lightColor
        insidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = FILL
            color = darkColor
            textSize = size * .6f
            textAlign = CENTER
            typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
        }

        if (locked) {
            val bitmapSize = (size * .45f).toInt()
            overlayBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, ARGB_8888)
            overlayBitmap?.let {
                val bitmapCanvas = Canvas(it)
                val overlayDrawable = ResourcesCompat.getDrawable(resources, drawable.ic_lock, null)
                if (overlayDrawable != null) {
                    overlayDrawable.colorFilter = PorterDuffColorFilter(darkColor, SRC_IN)
                    overlayDrawable.setBounds(0, 0, bitmapSize, bitmapSize)
                    overlayDrawable.draw(bitmapCanvas)
                }
            }
            insideX = (size - bitmapSize) * .5f
            insideY = (size - bitmapSize) * .5f
        } else {
            if (initial == null) {
                val bitmapSize = (size * .75f).toInt()
                overlayBitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, ARGB_8888)
                overlayBitmap?.let {
                    val bitmapCanvas = Canvas(it)
                    val groupDrawable = ResourcesCompat.getDrawable(resources, drawable.ic_group, null)
                    if (groupDrawable != null) {
                        groupDrawable.colorFilter = PorterDuffColorFilter(darkColor, SRC_IN)
                        groupDrawable.setBounds(0, 0, bitmapSize, bitmapSize)
                        groupDrawable.draw(bitmapCanvas)
                    }
                }
                insideX = (size - bitmapSize) * .5f
                insideY = (size - bitmapSize) * .5f
            } else {
                overlayBitmap = null
                insideX = size * .5f
                insideY = size * .5f - (insidePaint!!.descent() + insidePaint!!.ascent()) / 2f
            }
        }
    }


    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        setSize(w, h)
    }

    fun setSize(w: Int, h: Int) {
        bitmap = null
        width = w
        height = h
        size = min(width, height)
        init()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOnCanvas(canvas)
    }

    fun drawOnCanvas(canvas: Canvas) {
        try {
            if (bitmap == null) {
                if (backgroundPaint == null) {
                    return
                }
                val localBitmap = Bitmap.createBitmap(size, size, ARGB_8888)
                val bitmapCanvas = Canvas(localBitmap)
                bitmapCanvas.drawOval(
                    RectF(0f, 0f, size.toFloat(), size.toFloat()),
                    backgroundPaint!!
                )
                if (contactTrustLevel != null && contactTrustLevel != -1 && SettingsActivity.showTrustLevels()) {
                    val dotSize = (.3f * size).toInt()
                    val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    clearPaint.color = Color.TRANSPARENT
                    clearPaint.xfermode = PorterDuffXfermode(CLEAR)
                    bitmapCanvas.drawOval(
                        RectF(
                            (size - dotSize).toFloat(),
                            (size - dotSize).toFloat(),
                            size.toFloat(),
                            size.toFloat()
                        ), clearPaint
                    )
                    val dotPaint = getTrustPaint(context, contactTrustLevel!!)
                    bitmapCanvas.drawOval(
                        RectF(
                            size - .875f * dotSize,
                            size - .875f * dotSize,
                            size - .125f * dotSize,
                            size - .125f * dotSize
                        ), dotPaint
                    )
                    if (notOneToOne) {
                        bitmapCanvas.drawOval(
                            RectF(
                                size - .65f * dotSize,
                                size - .65f * dotSize,
                                size - .35f * dotSize,
                                size - .35f * dotSize
                            ), clearPaint
                        )
                    }
                }
                if (overlayBitmap != null) {
                    bitmapCanvas.drawBitmap(overlayBitmap!!, insideX, insideY, insidePaint)
                } else if (initial != null) {
                    bitmapCanvas.drawText(initial!!, insideX, insideY, insidePaint!!)
                }
                if (inactive) {
                    val blockedSize = (.8f * size).toInt()
                    val blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, ARGB_8888)
                    val blockedCanvas = Canvas(blockedBitmap)
                    val blockedDrawable =
                        ResourcesCompat.getDrawable(resources, drawable.ic_block_outlined, null)
                    if (blockedDrawable != null) {
                        blockedDrawable.setBounds(0, 0, blockedSize, blockedSize)
                        blockedDrawable.draw(blockedCanvas)
                    }
                    bitmapCanvas.drawBitmap(
                        blockedBitmap,
                        (size - blockedSize) / 2f,
                        (size - blockedSize) / 2f,
                        null
                    )
                } else if (keycloakCertified) {
                    val keycloakSize = (.3f * size).toInt()
                    val keycloakBitmap = Bitmap.createBitmap(keycloakSize, keycloakSize, ARGB_8888)
                    val keycloakCanvas = Canvas(keycloakBitmap)
                    val keycloakDrawable =
                        ResourcesCompat.getDrawable(resources, drawable.ic_keycloak_certified, null)
                    if (keycloakDrawable != null) {
                        keycloakDrawable.setBounds(0, 0, keycloakSize, keycloakSize)
                        keycloakDrawable.draw(keycloakCanvas)
                    }
                    bitmapCanvas.drawBitmap(
                        keycloakBitmap,
                        (size - keycloakSize).toFloat(),
                        0f,
                        null
                    )
                } else if (!recentlyOnline) {
                    val asleepSize = (.3f * size).toInt()
                    val asleepBitmap = Bitmap.createBitmap(asleepSize, asleepSize, ARGB_8888)
                    val asleepCanvas = Canvas(asleepBitmap)
                    val asleepDrawable = ResourcesCompat.getDrawable(resources, drawable.ic_snooze, null)
                    if (asleepDrawable != null) {
                        asleepDrawable.setBounds(0, 0, asleepSize, asleepSize)
                        asleepDrawable.draw(asleepCanvas)
                    }
                    bitmapCanvas.drawBitmap(asleepBitmap, (size - asleepSize).toFloat(), 0f, null)
                }
                bitmap = localBitmap
            }
            canvas.drawBitmap(bitmap!!, left, top, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // do nothing --> no rotation
    val adaptiveBitmap: Bitmap
        get() {
            val size = context.resources.getDimensionPixelSize(dimen.shortcut_icon_size)
            val innerSize = context.resources.getDimensionPixelSize(dimen.inner_shortcut_icon_size)
            val padding = (size - innerSize) / 2
            setSize(innerSize, innerSize)
            val bitmap = Bitmap.createBitmap(size, size, ARGB_8888)
            val canvas = Canvas(bitmap)
            if (backgroundPaint != null) {
                canvas.drawPaint(backgroundPaint!!)
            } else {
                val blackBackgroundPaint = Paint()
                blackBackgroundPaint.color = Color.BLACK
                canvas.drawPaint(blackBackgroundPaint)
            }
            if (photoUrl != null) {
                var options = Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(photoUrl, options)
                val bitmapSize = min(options.outWidth, options.outHeight)
                if (bitmapSize != -1) {
                    val subSampling = bitmapSize / innerSize
                    options = Options()
                    options.inSampleSize = subSampling
                    var squareBitmap = BitmapFactory.decodeFile(photoUrl, options)
                    if (squareBitmap != null) {
                        try {
                            val exifInterface = ExifInterface(
                                photoUrl!!
                            )
                            val orientation = exifInterface.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            squareBitmap = PreviewUtils.rotateBitmap(squareBitmap, orientation)
                        } catch (e: IOException) {
                            // do nothing --> no rotation
                        }
                        if (locked || inactive) {
                            val colorMatrix = ColorMatrix()
                            colorMatrix.setSaturation(.5f)
                            val matrixPaint = Paint()
                            matrixPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                            canvas.drawBitmap(
                                squareBitmap!!,
                                null,
                                Rect(padding, padding, innerSize + padding, innerSize + padding),
                                matrixPaint
                            )
                        } else {
                            canvas.drawBitmap(
                                squareBitmap!!,
                                null,
                                Rect(padding, padding, innerSize + padding, innerSize + padding),
                                null
                            )
                        }
                    }
                }
            } else {
                if (overlayBitmap != null) {
                    canvas.drawBitmap(
                        overlayBitmap!!,
                        padding + insideX,
                        padding + insideY,
                        insidePaint
                    )
                } else {
                    canvas.drawText(initial!!, padding + insideX, padding + insideY, insidePaint!!)
                }
                if (inactive) {
                    val blockedSize = (.8f * this.size).toInt()
                    val blockedBitmap = Bitmap.createBitmap(blockedSize, blockedSize, ARGB_8888)
                    val blockedCanvas = Canvas(blockedBitmap)
                    val blockedDrawable =
                        ResourcesCompat.getDrawable(resources, drawable.ic_block_outlined, null)
                    if (blockedDrawable != null) {
                        blockedDrawable.setBounds(0, 0, blockedSize, blockedSize)
                        blockedDrawable.draw(blockedCanvas)
                    }
                    canvas.drawBitmap(
                        blockedBitmap,
                        (this.size - blockedSize) / 2f,
                        (this.size - blockedSize) / 2f,
                        null
                    )
                }
            }
            return bitmap
        }

    companion object {
        @JvmStatic
        fun getTextColor(context: Context, bytes: ByteArray, hue: Int?): Int {
            val computedHue = hue ?: hueFromBytes(bytes)
            return if (bytes.isEmpty()) {
                ColorUtils.HSLToColor(floatArrayOf(computedHue.toFloat(), 0f, 0.4f))
            } else {
                if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ColorUtils.HSLToColor(
                        floatArrayOf(
                            computedHue.toFloat(),
                            0.5f,
                            0.4f
                        )
                    )
                } else {
                    ColorUtils.HSLToColor(
                        floatArrayOf(
                            computedHue.toFloat(),
                            0.7f,
                            0.4f
                        )
                    )
                }
            }
        }

        @JvmStatic
        fun getLightColor(context: Context, bytes: ByteArray): Int {
            val hue = hueFromBytes(bytes)
            return if (bytes.isEmpty()) {
                if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0f, 0.5f))
                } else {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0f, 0.9f))
                }
            } else {
                if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0.6f, 0.7f))
                } else {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0.8f, 0.9f))
                }
            }
        }

        @JvmStatic
        fun getDarkColor(context: Context, bytes: ByteArray): Int {
            val hue = hueFromBytes(bytes)
            return if (bytes.isEmpty()) {
                if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0f, 0.4f))
                } else {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0f, 0.7f))
                }
            } else {
                if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0.5f, 0.5f))
                } else {
                    ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 0.7f, 0.7f))
                }
            }
        }

        private fun getTrustPaint(context: Context, contactTrustLevel: Int): Paint {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            when (contactTrustLevel) {
                4 -> paint.color = ContextCompat.getColor(context, color.green)
                3 -> paint.color = ContextCompat.getColor(context, color.olvid_gradient_light)
                2, 1 -> paint.color = ContextCompat.getColor(context, color.orange)
                else -> paint.color = ContextCompat.getColor(context, color.mediumGrey)
            }
            return paint
        }

        fun hueFromBytes(bytes: ByteArray?): Int {
            if (bytes == null) {
                return 0
            }
            var result = 1
            for (element in bytes) {
                result = 31 * result + element
            }
            return (result and 0xff) * 360 / 256
        }
    }
}