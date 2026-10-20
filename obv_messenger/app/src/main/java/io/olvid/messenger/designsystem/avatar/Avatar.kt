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

package io.olvid.messenger.designsystem.avatar

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.GroupAvatarCacheSingleton

/**
 * Pure-Compose avatar. [target] is any [AvatarTarget] (Contact, Group2, Discussion, …,
 * or an [AvatarSource] factory for primitive cases); it is resolved to an [AvatarSource]
 * keyed on the contact/group cache versions so cache updates recompose.
 * A null [target] renders the "?" placeholder.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Avatar(
    target: AvatarTarget?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    border: BorderStroke? = null,
    showBadges: Boolean = true,
    showMemberCount: Boolean = true,
    unreadMessages: Boolean = false,
    admin: Boolean = false,
    muted: Boolean = false,
    locked: Boolean = false,
    onCall: Boolean = false,
    editable: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val contactCacheVersion by ContactCacheSingleton.cacheVersion.observeAsState()
    val groupCacheVersion by GroupAvatarCacheSingleton.cacheVersion.observeAsState()
    // group clusters may include your own avatar, so re-resolve when your profile changes
    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()
    val source = remember(target, contactCacheVersion, groupCacheVersion, ownedIdentity) {
        when (val resolved = (target ?: AvatarSource.unknown()).toAvatarSource()) {
            is AvatarSource.Cached -> resolved.resolve()
            else -> resolved
        }
    }
    val alpha: Float by animateFloatAsState(
        targetValue = if (selected) 0f else 1f,
        animationSpec = tween(), label = "alpha"
    )
    BoxWithConstraints(
        modifier = modifier
    ) {
        val coreModifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .align(Alignment.BottomStart)
        when (source) {
            is AvatarSource.Single -> SingleAvatar(
                source = source,
                showBadges = showBadges,
                shape = shape,
                border = border,
                modifier = coreModifier,
            )

            is AvatarSource.GroupCluster -> GroupClusterAvatar(
                source = source,
                showBadges = showBadges,
                showMemberCount = showMemberCount,
                shape = shape,
                border = border,
                modifier = coreModifier,
            )

            is AvatarSource.Cached -> {} // resolved above, cannot happen
        }
        OverlayBadges(
            selected = selected,
            unreadMessages = unreadMessages,
            admin = admin,
            muted = muted,
            locked = locked,
            onCall = onCall,
            editable = editable,
        )
        if (onClick != null || onLongClick != null) {
            // separate clipped layer: the ripple follows the avatar shape and the outer box
            // stays unclipped so overlay badges can keep drawing outside the bounds
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .combinedClickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
            )
        }
    }
}

@Composable
private fun SingleAvatar(
    modifier: Modifier = Modifier,
    source: AvatarSource.Single,
    showBadges: Boolean,
    shape: Shape = CircleShape,
    border: BorderStroke? = null,
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val avatarSize = min(maxWidth, maxHeight)
        // the actual pixels are produced by the shared DrawScope.drawAvatar (AvatarDrawing.kt), the
        // exact same routine the off-screen notification/widget renderer runs — so they can't diverge
        val photo = rememberAvatarPhoto(source.photoUrl, avatarSize)
        Canvas(
            modifier = Modifier
                .size(avatarSize)
                .align(Alignment.Center)
                // offscreen layer so the trust dot's BlendMode.Clear punches a clean ring
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .then(border?.let { Modifier.border(it, shape) } ?: Modifier),
        ) {
            drawAvatar(source, photo, context, textMeasurer, showBadges)
        }
    }
}

/**
 * Loads [photoUrl] through Coil, decoded for [avatarSize], as an [ImageBitmap] [drawAvatar] can draw.
 * Seeded synchronously from Coil's memory cache so a cached photo is on the very first frame.
 */
@Composable
private fun rememberAvatarPhoto(photoUrl: String?, avatarSize: Dp): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) {
        if (avatarSize.isSpecified && avatarSize != Dp.Infinity) avatarSize.roundToPx() else 0
    }
    val cacheKey = photoUrl?.let { MemoryCache.Key("avatar:$it:$sizePx") }
    // keyed remember: a recycled row must never keep the previous row's photo while the new one loads
    var bitmap by remember(cacheKey) {
        mutableStateOf(cacheKey?.let { App.imageLoader.memoryCache?.get(it)?.bitmap?.asImageBitmap() })
    }
    LaunchedEffect(cacheKey) {
        if (bitmap != null || photoUrl == null || cacheKey == null) return@LaunchedEffect
        bitmap = runCatching {
            val request = ImageRequest.Builder(context)
                .data(photoUrl)
                .apply { if (sizePx > 0) size(sizePx) }
                .precision(Precision.INEXACT)
                .memoryCacheKey(cacheKey)
                .allowHardware(false) // software bitmap: drawable on any (incl. software) canvas
                .build()
            (App.imageLoader.execute(request) as? SuccessResult)
                ?.let { (it.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap() }
        }.getOrNull()
    }
    return bitmap
}

// fractions of the avatar square
private const val CLUSTER_MEMBER_SIZE = .58f
private const val CLUSTER_BADGE_SIZE = .32f
private val CLUSTER_BADGE_MIN_SIZE = 20.dp
// below this, the min-size badge hides too much of the cluster
private val CLUSTER_BADGE_MIN_AVATAR_SIZE = 40.dp

// transparent gap punched between overlapping cluster members (replaced by the border when set)
private val CLUSTER_MEMBER_GAP = 1.5.dp

// member positions
// "+N" badge sits bottom-left: left member is the bottom layer, bottom-right is on top
private const val CLUSTER_2_MEMBER_SIZE = .62f
private const val CLUSTER_LEFT_MEMBER_Y = .14f

// the top-right and bottom-right members sit flush in the far corners, so their offset on
// each axis is exactly what the member size leaves over (1 - size)
private const val CLUSTER_MEMBER_OFFSET = 1f - CLUSTER_MEMBER_SIZE
private const val CLUSTER_BADGE_X = .18f
private const val CLUSTER_BADGE_Y = .59f

// [AvatarTextStyle] is shared with the headless renderer; see AvatarDrawing.kt.
// Use with this modifier so a text wider than its circle (large font scale, "+10"…) is
// measured unconstrained and stays centered instead of clipping right.
private val avatarTextModifier = Modifier.wrapContentSize(unbounded = true)

@Composable
private fun GroupClusterAvatar(
    modifier: Modifier = Modifier,
    source: AvatarSource.GroupCluster,
    showBadges: Boolean,
    showMemberCount: Boolean,
    shape: Shape = CircleShape,
    border: BorderStroke? = null,
) {
    // when switching profile, the source.members may be reset while the cluster is still visible
    // this may trigger a "flash of empty group". We capture the currentIdentity and only update the
    // members to display if the currentIdentity has not changed
    var members by remember { mutableStateOf(source.members) }
    var bytesCurrentIdentity  : ByteArray? by remember { mutableStateOf(null) }

    if (bytesCurrentIdentity == null) {
        bytesCurrentIdentity = AppSingleton.getBytesCurrentIdentity()
    }
    if (bytesCurrentIdentity.contentEquals(AppSingleton.getBytesCurrentIdentity())) {
        members = source.members
    }


    if (members.isEmpty()) {
        // no members to show: fall back to the classic group-icon avatar
        SingleAvatar(
            source = AvatarSource.Single(
                bytes = source.bytes,
                initial = null,
                keycloakCertified = source.keycloakCertified,
                inactive = source.inactive,
            ),
            showBadges = showBadges,
            shape = shape,
            border = border,
            modifier = modifier,
        )
        return
    }
    BoxWithConstraints(modifier = modifier) {
        val avatarSize = min(maxWidth, maxHeight)
        // default separation between overlapping members is a transparent gap punched through
        // the layers below, so the cluster matches the design on any background in both light
        // and dark mode; passing a border replaces the gap with a colored ring
        val gapPx = with(LocalDensity.current) { (border?.width ?: CLUSTER_MEMBER_GAP).toPx() }
        val memberSeparation = Modifier
            .drawBehind {
                drawCircle(
                    color = Color.Transparent,
                    radius = size.minDimension / 2f + gapPx,
                    blendMode = BlendMode.Clear,
                )
            }
            .then(border?.let { Modifier.border(it, CircleShape) } ?: Modifier)
        val memberSize = avatarSize * CLUSTER_MEMBER_SIZE
        Box(
            modifier = Modifier
                .size(avatarSize)
                .align(Alignment.Center)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            when (members.size) {
                1 -> MemberAvatar(
                    member = members[0],
                    modifier = Modifier.fillMaxSize(),
                )

                2 -> {
                    MemberAvatar(
                        member = members[1],
                        modifier = Modifier
                            .size(avatarSize * CLUSTER_2_MEMBER_SIZE)
                            .align(Alignment.BottomStart),
                    )
                    MemberAvatar(
                        member = members[0],
                        modifier = Modifier
                            .size(avatarSize * CLUSTER_2_MEMBER_SIZE)
                            .align(Alignment.TopEnd)
                            .then(memberSeparation),
                    )
                }

                else -> {
                    // left (bottom layer), top-right, then bottom-right on top
                    MemberAvatar(
                        member = members[1],
                        modifier = Modifier
                            .size(memberSize)
                            .offset(x = 0.dp, y = avatarSize * CLUSTER_LEFT_MEMBER_Y),
                    )
                    MemberAvatar(
                        member = members[0],
                        modifier = Modifier
                            .size(memberSize)
                            .offset(x = avatarSize * CLUSTER_MEMBER_OFFSET, y = 0.dp)
                            .then(memberSeparation),
                    )
                    MemberAvatar(
                        member = members[2],
                        modifier = Modifier
                            .size(memberSize)
                            .offset(
                                x = avatarSize * CLUSTER_MEMBER_OFFSET,
                                y = avatarSize * CLUSTER_MEMBER_OFFSET
                            )
                            .then(memberSeparation),
                    )
                }
            }
            // totalCount includes yourself, so only show the badge when some OTHER members are
            // actually hidden — never a lone "+1" that would just be you
            val overflow = source.totalCount - members.size
            if (showMemberCount && avatarSize >= CLUSTER_BADGE_MIN_AVATAR_SIZE && source.totalCount - 1 > members.size) {
                // enforce a minimum badge size and keep it inside the avatar bounds
                val badgeSize = maxOf(avatarSize * CLUSTER_BADGE_SIZE, CLUSTER_BADGE_MIN_SIZE)
                val badgeText = "+$overflow"
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .offset(
                            x = minOf(avatarSize * CLUSTER_BADGE_X, avatarSize - badgeSize),
                            y = minOf(avatarSize * CLUSTER_BADGE_Y, avatarSize - badgeSize),
                        )
                        .then(memberSeparation)
                        .background(colorResource(id = R.color.avatarClusterBadge), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        modifier = avatarTextModifier,
                        text = badgeText,
                        color = colorResource(id = R.color.avatarClusterBadgeText),
                        fontSize = with(LocalDensity.current) {
                            (badgeSize * if (badgeText.length <= 3) .45f else .36f).toSp()
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = AvatarTextStyle,
                    )
                }
            }
            if (showBadges) {
                if (source.inactive) {
                    Image(
                        modifier = Modifier
                            .size(avatarSize * .8f)
                            .align(Alignment.Center),
                        painter = painterResource(id = R.drawable.ic_block_outlined),
                        contentDescription = null,
                    )
                } else if (source.keycloakCertified) {
                    Image(
                        modifier = Modifier
                            .size(avatarSize * .3f)
                            .align(Alignment.TopEnd),
                        painter = painterResource(id = R.drawable.ic_keycloak_certified),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

/** One member of a group cluster: photo if available, else pattern or colored circle + initial. */
@Composable
private fun MemberAvatar(member: MemberAvatar, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val memberSize = min(maxWidth, maxHeight)
        val photo = rememberAvatarPhoto(member.photoUrl, memberSize)
        if (photo != null) {
            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                bitmap = photo,
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else if (usePatternAvatars && member.bytes.isNotEmpty()) {
            val darkTheme = isDarkTheme
            val pattern = remember(member, darkTheme) { avatarPattern(member.bytes, darkTheme) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind { drawAvatarPattern(pattern) },
                contentAlignment = Alignment.Center,
            ) {
                if (member.initial == null) {
                    Image(
                        modifier = Modifier.size(memberSize * .75f),
                        painter = painterResource(id = R.drawable.ic_group),
                        colorFilter = ColorFilter.tint(pattern.glyphColor),
                        contentDescription = null,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(avatarLightColor(member.bytes), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (member.initial == null) {
                    Image(
                        modifier = Modifier.size(memberSize * .75f),
                        painter = painterResource(id = R.drawable.ic_group),
                        colorFilter = ColorFilter.tint(avatarDarkColor(member.bytes)),
                        contentDescription = null,
                    )
                } else {
                    Text(
                        modifier = avatarTextModifier,
                        text = member.initial,
                        color = avatarDarkColor(member.bytes),
                        fontSize = with(LocalDensity.current) { (memberSize * .6f).toSp() },
                        style = AvatarTextStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.OverlayBadges(
    selected: Boolean,
    unreadMessages: Boolean,
    admin: Boolean,
    muted: Boolean,
    locked: Boolean,
    onCall: Boolean,
    editable: Boolean,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = selected,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Image(
            modifier = Modifier
                .background(Color.Gray, CircleShape)
                .padding(8.dp)
                .fillMaxSize(),
            painter = painterResource(id = R.drawable.ic_check),
            contentDescription = "selected"
        )
    }
    if (unreadMessages && locked.not()) {
        Image(
            modifier = Modifier
                .size(maxWidth.times(0.4f))
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
            painter = painterResource(id = R.drawable.ic_dot_white_bordered),
            contentDescription = stringResource(
                id = R.string.content_description_message_status
            )
        )
    } else if (admin) {
        Image(
            modifier = Modifier
                .size(maxWidth.times(0.4f))
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
            painter = painterResource(id = R.drawable.ic_crown_white_circle),
            contentDescription = stringResource(
                id = R.string.content_description_message_status
            )
        )
    }
    if (muted) {
        Image(
            modifier = Modifier
                .size(maxWidth.times(0.4f))
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp),
            painter = painterResource(id = R.drawable.ic_notification_muted_circle),
            contentDescription = stringResource(
                id = R.string.content_description_message_status
            )
        )
    }
    if (onCall) {
        AndroidView(
            modifier = Modifier
                .size(maxWidth.times(0.4f))
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .background(color = colorResource(R.color.dialogBackground), shape = CircleShape)
                .padding(2.dp),
            factory = { context ->
                ImageView(context).apply {
                    setColorFilter(R.color.olvid_gradient_dark)
                    setImageResource(R.drawable.ic_phone_animated)
                    (drawable as AnimationDrawable).start()
                }
            })
    }
    if (editable) {
        Image(
            modifier = Modifier
                .size(maxWidth.times(0.4f))
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp),
            painter = painterResource(id = R.drawable.ic_camera_bordered),
            contentDescription = null
        )
    }
}

@Preview
@Composable
private fun AvatarPreview() {
    Column(verticalArrangement = Arrangement.SpaceEvenly) {
        // initial + unread / muted / onCall overlays
        Avatar(
            modifier = Modifier
                .padding(16.dp)
                .requiredSize(56.dp),
            target = AvatarSource.initial(byteArrayOf(0, 1, 35), "A"),
            unreadMessages = true,
            muted = true,
            onCall = true,
        )
        // locked discussion
        Avatar(
            modifier = Modifier
                .padding(16.dp)
                .requiredSize(56.dp),
            target = AvatarSource.Single(bytes = ByteArray(0), initial = "", locked = true),
        )
        // keycloak + trust level
        Avatar(
            modifier = Modifier
                .padding(16.dp)
                .requiredSize(56.dp),
            target = AvatarSource.Single(
                bytes = byteArrayOf(0, 1, 35),
                initial = "K",
                keycloakCertified = true,
                trustLevel = 4,
                notOneToOne = true,
            ),
        )
        // group icon fallback
        Avatar(
            modifier = Modifier
                .padding(16.dp)
                .requiredSize(56.dp),
            target = AvatarSource.group(byteArrayOf(0, 1, 35)),
        )
        // group cluster 3 + N
        Avatar(
            modifier = Modifier
                .padding(16.dp)
                .requiredSize(56.dp),
            target = AvatarSource.GroupCluster(
                bytes = byteArrayOf(0, 1, 35),
                members = listOf(
                    MemberAvatar(byteArrayOf(1), "A", null),
                    MemberAvatar(byteArrayOf(2), "B", null),
                    MemberAvatar(byteArrayOf(3), "C", null),
                ),
                totalCount = 7,
            ),
            admin = true,
        )
    }
}

// One row per identity color family (top → bottom: Terracotta … Rose), 8 seeded geometries each.
// Draws through avatarPattern/drawAvatarPattern directly to sidestep the usePatternAvatars
// preference, which is not readable in previews.
@PreviewLightDark
@Composable
private fun AvatarPatternPreview() {
    val darkTheme = isDarkTheme
    val familySeeds = remember {
        val buckets = List(12) { mutableListOf<ByteArray>() }
        var candidate = 0
        while (buckets.any { it.size < 8 }) {
            val bytes = byteArrayOf((candidate shr 8).toByte(), candidate.toByte())
            buckets[hueFromBytes(bytes) * 12 / 360].let { if (it.size < 8) it.add(bytes) }
            candidate++
        }
        buckets
    }
    Column(
        modifier = Modifier
            .background(if (darkTheme) Color.Black else Color.White)
            .padding(8.dp)
    ) {
        familySeeds.forEach { seeds ->
            Row {
                seeds.forEach { bytes ->
                    val pattern = avatarPattern(bytes, darkTheme)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(40.dp)
                            .drawBehind { drawAvatarPattern(pattern) }
                    )
                }
            }
        }
    }
}
