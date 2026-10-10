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

import androidx.compose.runtime.Immutable
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.ContactCacheInfo
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.GroupAvatarCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Group
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.avatar.AvatarSource.Companion.fromCache
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion

/**
 * Empty marker for anything the InitialView composable can render.
 * Adding an implementor requires a branch in [toAvatarSource]; unmapped implementors
 * throw in debug builds and render the "?" avatar in release builds.
 */
interface AvatarTarget

/**
 * Resolved, self-contained render data for the InitialView composable.
 * Sealed so the renderer handles every case exhaustively; implements [AvatarTarget]
 * so already-derived sources flow through the same single composable parameter.
 */
@Immutable
sealed interface AvatarSource : AvatarTarget {

    data class Single(
        val bytes: ByteArray,            // drives the avatar hue; empty => grey/unknown
        val initial: String? = null,     // null => group icon fallback; "" + locked => lock glyph
        val photoUrl: String? = null,    // absolute path (already resolved via App.absolutePathFromRelative)
        val keycloakCertified: Boolean = false,
        val locked: Boolean = false,
        val inactive: Boolean = false,
        val notOneToOne: Boolean = false,
        val recentlyOnline: Boolean = true,
        val trustLevel: Int? = null,
    ) : AvatarSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Single) return false
            return bytes.contentEquals(other.bytes)
                    && initial == other.initial
                    && photoUrl == other.photoUrl
                    && keycloakCertified == other.keycloakCertified
                    && locked == other.locked
                    && inactive == other.inactive
                    && notOneToOne == other.notOneToOne
                    && recentlyOnline == other.recentlyOnline
                    && trustLevel == other.trustLevel
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (initial?.hashCode() ?: 0)
            result = 31 * result + (photoUrl?.hashCode() ?: 0)
            result = 31 * result + keycloakCertified.hashCode()
            result = 31 * result + locked.hashCode()
            result = 31 * result + inactive.hashCode()
            result = 31 * result + notOneToOne.hashCode()
            result = 31 * result + recentlyOnline.hashCode()
            result = 31 * result + (trustLevel ?: 0)
            return result
        }
    }

    data class GroupCluster(
        val bytes: ByteArray,                 // group identifier (drives badge/fallback hue)
        val members: List<MemberAvatar>,      // ranked, at most 3, yourself included when there is room
        val totalCount: Int,                  // full member count, drives the +N badge
        val keycloakCertified: Boolean = false,
        val inactive: Boolean = false,
    ) : AvatarSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GroupCluster) return false
            return bytes.contentEquals(other.bytes)
                    && members == other.members
                    && totalCount == other.totalCount
                    && keycloakCertified == other.keycloakCertified
                    && inactive == other.inactive
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + members.hashCode()
            result = 31 * result + totalCount
            result = 31 * result + keycloakCertified.hashCode()
            result = 31 * result + inactive.hashCode()
            return result
        }
    }

    /**
     * A bytes identity that becomes a source linked to ContactCacheSingleton: it is
     * resolved at render time (inside the composable, keyed on the cache version) so
     * cache updates recompose. Ports the legacy setFromCache(). Always built through
     * [fromCache].
     */
    data class Cached internal constructor(val bytesIdentifier: ByteArray) : AvatarSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Cached) return false
            return bytesIdentifier.contentEquals(other.bytesIdentifier)
        }

        override fun hashCode(): Int = bytesIdentifier.contentHashCode()
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun unknown(keycloakCertified: Boolean = false) =
            Single(bytes = ByteArray(0), initial = "?", keycloakCertified = keycloakCertified)

        @JvmStatic
        @JvmOverloads
        fun initial(bytes: ByteArray?, initial: String?, keycloakCertified: Boolean = false) =
            Single(
                bytes = bytes ?: ByteArray(0),
                initial = initial,
                keycloakCertified = keycloakCertified
            )

        /** A bare group-icon avatar (no initial) tinted from [bytes]; ports legacy setGroup(ByteArray). */
        @JvmStatic
        fun group(bytes: ByteArray?) =
            Single(bytes = bytes ?: ByteArray(0), initial = null)

        @JvmStatic
        @JvmOverloads
        fun photo(
            bytes: ByteArray?,
            relativePhotoUrl: String?,
            keycloakCertified: Boolean = false
        ) =
            Single(
                bytes = bytes ?: ByteArray(0),
                photoUrl = App.absolutePathFromRelative(relativePhotoUrl),
                keycloakCertified = keycloakCertified,
            )

        @JvmStatic
        fun absolutePhoto(bytes: ByteArray?, absolutePhotoUrl: String?) =
            Single(bytes = bytes ?: ByteArray(0), photoUrl = absolutePhotoUrl)

        /**
         * Explicit single avatar (an absolute photo, an initial, or a locked glyph). Ports the
         * legacy setLocked()+setAbsolutePhotoUrl()/setInitial() combos used by the edit dialog.
         */
        @JvmStatic
        fun of(bytes: ByteArray?, initial: String?, absolutePhotoUrl: String?, locked: Boolean): AvatarSource =
            Single(
                bytes = bytes ?: ByteArray(0),
                initial = initial,
                photoUrl = absolutePhotoUrl,
                locked = locked,
            )

        @JvmStatic
        fun fromCache(bytesIdentifier: ByteArray): AvatarSource = Cached(bytesIdentifier)

        /**
         * This method is used during a group creation.
         * - we do not have a groupIdentifier yet, so gray fallback
         * - sort contacts with photos first for the cluster
         */
        fun of(contacts: List<Contact>): AvatarSource =
            GroupCluster(
                bytes = ByteArray(0),
                members = contacts.sortedBy { contact -> contact.getCustomPhotoUrl() == null }.map { contact ->
                    MemberAvatar(
                        bytes = contact.bytesContactIdentity,
                        initial = StringUtils.getInitial(contact.getCustomDisplayName()),
                        photoUrl = App.absolutePathFromRelative(contact.getCustomPhotoUrl()),
                    )
                }.take(3).withOwnedIdentityIfRoom(),
                totalCount = contacts.size + 1,
                keycloakCertified = false,
                inactive = false,
            )
    }
}

data class MemberAvatar(
    val bytes: ByteArray,
    val initial: String?,
    val photoUrl: String?,   // absolute path
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemberAvatar) return false
        return bytes.contentEquals(other.bytes)
                && initial == other.initial
                && photoUrl == other.photoUrl
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (initial?.hashCode() ?: 0)
        result = 31 * result + (photoUrl?.hashCode() ?: 0)
        return result
    }
}

/**
 * Central dispatch: the only place mapping renderable types to their AvatarSource.
 * Every AvatarTarget implementor needs a branch here.
 */
internal fun AvatarTarget.toAvatarSource(): AvatarSource = when (this) {
    is AvatarSource -> this
    is Contact -> toAvatarSource()
    is OwnedIdentity -> toAvatarSource()
    is Group -> toAvatarSource()
    is Group2 -> toAvatarSource()
    is Discussion -> toAvatarSource()
    is SearchableDiscussion -> toAvatarSource()
    else -> {
        if (BuildConfig.DEBUG) {
            error("Unmapped AvatarTarget: ${this::class}")
        }
        Logger.e("Unmapped AvatarTarget: ${this::class}")
        AvatarSource.unknown()
    }
}

private fun Contact.toAvatarSource() = AvatarSource.Single(
    bytes = bytesContactIdentity,
    initial = StringUtils.getInitial(getCustomDisplayName()),
    photoUrl = App.absolutePathFromRelative(getCustomPhotoUrl()),
    keycloakCertified = keycloakManaged,
    inactive = !active,
    notOneToOne = !oneToOne,
    recentlyOnline = recentlyOnline,
    trustLevel = trustLevel,
)

private fun OwnedIdentity.toAvatarSource() = AvatarSource.Single(
    bytes = bytesOwnedIdentity,
    initial = StringUtils.getInitial(getCustomDisplayName()),
    photoUrl = App.absolutePathFromRelative(photoUrl),
    keycloakCertified = keycloakManaged,
    inactive = !active,
)

private fun Group.toAvatarSource() = AvatarSource.Single(
    bytes = bytesGroupOwnerAndUid,
    initial = null,
    photoUrl = App.absolutePathFromRelative(getCustomPhotoUrl()),
)

private fun Group2.toAvatarSource(): AvatarSource {
    val photoUrl = App.absolutePathFromRelative(getCustomPhotoUrl())
    return if (photoUrl != null) {
        AvatarSource.Single(
            bytes = bytesGroupIdentifier,
            initial = null,
            photoUrl = photoUrl,
            keycloakCertified = keycloakManaged,
        )
    } else {
        groupCluster(
            bytesGroupIdentifier = bytesGroupIdentifier,
            keycloakCertified = keycloakManaged,
        )
    }
}

private fun Discussion.toAvatarSource(): AvatarSource {
    if (status == Discussion.STATUS_LOCKED) {
        return AvatarSource.Single(
            bytes = ByteArray(0),
            initial = "",
            photoUrl = App.absolutePathFromRelative(photoUrl),
            keycloakCertified = keycloakManaged,
            locked = true,
            inactive = !active,
            trustLevel = trustLevel,
        )
    }
    return when (discussionType) {
        Discussion.TYPE_CONTACT -> AvatarSource.Single(
            bytes = bytesDiscussionIdentifier,
            initial = StringUtils.getInitial(title),
            photoUrl = App.absolutePathFromRelative(photoUrl),
            keycloakCertified = keycloakManaged,
            inactive = !active,
            recentlyOnline = ContactCacheSingleton.getContactCacheInfo(bytesDiscussionIdentifier)?.recentlyOnline != false,
            trustLevel = trustLevel,
        )

        Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 -> {
            val photoUrl = App.absolutePathFromRelative(photoUrl)
            if (photoUrl == null && discussionType == Discussion.TYPE_GROUP_V2) {
                groupCluster(
                    bytesGroupIdentifier = bytesDiscussionIdentifier,
                    keycloakCertified = keycloakManaged,
                    inactive = !active,
                )
            } else {
                AvatarSource.Single(
                    bytes = bytesDiscussionIdentifier,
                    initial = null,
                    photoUrl = photoUrl,
                    keycloakCertified = keycloakManaged,
                    inactive = !active,
                )
            }
        }

        else -> {
            Logger.e("Unknown discussion type")
            AvatarSource.unknown()
        }
    }
}

private fun SearchableDiscussion.toAvatarSource(): AvatarSource {
    val photoUrl = App.absolutePathFromRelative(photoUrl)
    return when {
        isGroupDiscussion -> if (photoUrl == null) {
            groupCluster(
                bytesGroupIdentifier = byteIdentifier,
                keycloakCertified = keycloakManaged,
                inactive = !active,
            )
        } else {
            AvatarSource.Single(
                bytes = byteIdentifier,
                initial = null,
                photoUrl = photoUrl,
                keycloakCertified = keycloakManaged,
                inactive = !active,
            )
        }

        byteIdentifier.isNotEmpty() -> AvatarSource.Single(
            bytes = byteIdentifier,
            initial = StringUtils.getInitial(title),
            photoUrl = photoUrl,
            keycloakCertified = keycloakManaged,
            inactive = !active,
        )

        else -> AvatarSource.Single(
            bytes = byteIdentifier,
            initial = "",
            photoUrl = photoUrl,
            keycloakCertified = keycloakManaged,
            locked = true,
            inactive = !active,
        )
    }
}

private fun groupCluster(
    bytesGroupIdentifier: ByteArray,
    keycloakCertified: Boolean = false,
    inactive: Boolean = false,
): AvatarSource.GroupCluster {
    val entry = GroupAvatarCacheSingleton.getGroupAvatarInfo(bytesGroupIdentifier)
    return AvatarSource.GroupCluster(
        bytes = bytesGroupIdentifier,
        members = entry?.orderedMemberBytes.orEmpty().map { memberBytes ->
            MemberAvatar(
                bytes = memberBytes,
                initial = ContactCacheSingleton.getContactCustomDisplayName(memberBytes)
                    ?.let { StringUtils.getInitial(it) },
                photoUrl = App.absolutePathFromRelative(
                    ContactCacheSingleton.getContactPhotoUrl(memberBytes)
                ),
            )
        }.withOwnedIdentityIfRoom(),
        totalCount = entry?.totalCount ?: 0,
        keycloakCertified = keycloakCertified,
        inactive = inactive,
    )
}

/**
 * A group with a single other member would otherwise render exactly like a 1-to-1 discussion,
 * so when the cluster has a free slot it is filled with your own avatar. You take the back-left
 * slot (index 1 in both the 2- and 3-member layouts) so the other members stay highlighted.
 * An empty group keeps the group glyph, and 3+ members keep the ranked members + "+N" badge.
 */
private fun List<MemberAvatar>.withOwnedIdentityIfRoom(): List<MemberAvatar> {
    if (size !in 1..2) {
        return this
    }
    val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value ?: return this
    return toMutableList().apply {
        add(
            1,
            MemberAvatar(
                bytes = ownedIdentity.bytesOwnedIdentity,
                initial = StringUtils.getInitial(ownedIdentity.getCustomDisplayName()),
                photoUrl = App.absolutePathFromRelative(ownedIdentity.photoUrl),
            )
        )
    }
}

/** resolve a contact/owned identity from the caches. */
internal fun AvatarSource.Cached.resolve(): AvatarSource.Single = runCatching {
    val displayName: String? =
        if (bytesIdentifier.contentEquals(AppSingleton.getBytesCurrentIdentity())) {
            AppSingleton.getCurrentIdentityLiveData().value?.getCustomDisplayName()
                ?: ContactCacheSingleton.getContactCustomDisplayName(bytesIdentifier)
        } else {
            ContactCacheSingleton.getContactCustomDisplayName(bytesIdentifier)
        }
    val contactCacheInfo = ContactCacheSingleton.getContactCacheInfo(bytesIdentifier)
        ?: ContactCacheInfo(
            keycloakManaged = false,
            active = true,
            oneToOne = true,
            recentlyOnline = true,
            trustLevel = 0
        )
    AvatarSource.Single(
        bytes = if (displayName == null) ByteArray(0) else bytesIdentifier,
        initial = if (displayName == null) "?" else StringUtils.getInitial(displayName),
        photoUrl = App.absolutePathFromRelative(
            ContactCacheSingleton.getContactPhotoUrl(bytesIdentifier)
        ),
        keycloakCertified = contactCacheInfo.keycloakManaged,
        inactive = !contactCacheInfo.active,
        notOneToOne = !contactCacheInfo.oneToOne,
        recentlyOnline = contactCacheInfo.recentlyOnline,
        trustLevel = contactCacheInfo.trustLevel,
    )
}.getOrDefault(AvatarSource.unknown())
