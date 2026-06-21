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

package io.olvid.messenger

import io.olvid.messenger.customClasses.BytesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UnreadCountsSingletonTest {

    private val ownerA = BytesKey(byteArrayOf(0x01))
    private val ownerB = BytesKey(byteArrayOf(0x02))

    @Test
    fun `isMuted - pref off is never muted`() {
        assertFalse(isMuted(prefMuteNotifications = false, prefMuteNotificationsTimestamp = null, now = 100L))
        assertFalse(isMuted(prefMuteNotifications = false, prefMuteNotificationsTimestamp = 1000L, now = 100L))
    }

    @Test
    fun `isMuted - pref on with null timestamp is muted forever`() {
        assertTrue(isMuted(prefMuteNotifications = true, prefMuteNotificationsTimestamp = null, now = 100L))
        assertTrue(isMuted(prefMuteNotifications = true, prefMuteNotificationsTimestamp = null, now = Long.MAX_VALUE))
    }

    @Test
    fun `isMuted - pref on with future timestamp is muted`() {
        assertTrue(isMuted(prefMuteNotifications = true, prefMuteNotificationsTimestamp = 1000L, now = 100L))
    }

    @Test
    fun `isMuted - pref on with past or equal timestamp is no longer muted`() {
        assertFalse(isMuted(prefMuteNotifications = true, prefMuteNotificationsTimestamp = 100L, now = 100L))
        assertFalse(isMuted(prefMuteNotifications = true, prefMuteNotificationsTimestamp = 50L, now = 100L))
    }

    @Test
    fun `computeOwnedIdentityCounts - empty inputs produce empty map`() {
        val result = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = emptyMap(),
            mentionMessageCountsByDiscussion = emptyMap(),
            discussionMetadata = emptyMap(),
            unreadDiscussionsByOwnedIdentity = emptyMap(),
            invitationsByOwnedIdentity = emptyMap(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeOwnedIdentityCounts - unread messages attributed to owner via metadata`() {
        val result = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = mapOf(1L to 3, 2L to 5),
            mentionMessageCountsByDiscussion = emptyMap(),
            discussionMetadata = mapOf(
                1L to DiscussionOwnerMetadata(ownerA, archived = false),
                2L to DiscussionOwnerMetadata(ownerA, archived = false),
            ),
            unreadDiscussionsByOwnedIdentity = emptyMap(),
            invitationsByOwnedIdentity = emptyMap(),
        )
        assertEquals(1, result.size)
        val counts = result[ownerA]!!
        assertEquals(8, counts.unreadMessageCountNonArchived)
        assertEquals(0, counts.unreadDiscussionCount)
        assertEquals(0, counts.invitationCount)
    }

    @Test
    fun `computeOwnedIdentityCounts - archived discussions excluded from non-archived count`() {
        val result = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = mapOf(1L to 3, 2L to 5),
            mentionMessageCountsByDiscussion = emptyMap(),
            discussionMetadata = mapOf(
                1L to DiscussionOwnerMetadata(ownerA, archived = false),
                2L to DiscussionOwnerMetadata(ownerA, archived = true),
            ),
            unreadDiscussionsByOwnedIdentity = emptyMap(),
            invitationsByOwnedIdentity = emptyMap(),
        )
        val counts = result[ownerA]!!
        assertEquals(3, counts.unreadMessageCountNonArchived)
    }

    @Test
    fun `computeOwnedIdentityCounts - discussions without metadata are ignored`() {
        val result = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = mapOf(1L to 3, 99L to 100),
            mentionMessageCountsByDiscussion = emptyMap(),
            discussionMetadata = mapOf(1L to DiscussionOwnerMetadata(ownerA, archived = false)),
            unreadDiscussionsByOwnedIdentity = emptyMap(),
            invitationsByOwnedIdentity = emptyMap(),
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `computeOwnedIdentityCounts - counts split across multiple owners`() {
        val result = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = mapOf(1L to 2, 2L to 4),
            mentionMessageCountsByDiscussion = emptyMap(),
            discussionMetadata = mapOf(
                1L to DiscussionOwnerMetadata(ownerA, archived = false),
                2L to DiscussionOwnerMetadata(ownerB, archived = false),
            ),
            unreadDiscussionsByOwnedIdentity = mapOf(ownerB to setOf(10L, 11L)),
            invitationsByOwnedIdentity = mapOf(ownerA to setOf(UUID.randomUUID())),
        )
        assertEquals(2, result.size)
        assertEquals(1, result[ownerA]!!.invitationCount)
        assertEquals(0, result[ownerA]!!.unreadDiscussionCount)
        assertEquals(2, result[ownerB]!!.unreadDiscussionCount)
        assertEquals(0, result[ownerB]!!.invitationCount)
    }

    @Test
    fun `computeOwnedIdentityCounts - owner only present via invitations still gets an entry`() {
        val result = computeOwnedIdentityCounts(
            unreadMessageCountsByDiscussion = emptyMap(),
            mentionMessageCountsByDiscussion = emptyMap(),
            discussionMetadata = emptyMap(),
            unreadDiscussionsByOwnedIdentity = emptyMap(),
            invitationsByOwnedIdentity = mapOf(ownerA to setOf(UUID.randomUUID(), UUID.randomUUID())),
        )
        assertEquals(1, result.size)
        assertEquals(2, result[ownerA]!!.invitationCount)
    }

    @Test
    fun `hasNotificationDot - reflects non-archived messages, unread discussions, or invitations`() {
        assertFalse(OwnedIdentityUnreadCounts().hasNotificationDot())
        assertTrue(
            OwnedIdentityUnreadCounts(unreadMessageCountNonArchived = 1).hasNotificationDot()
        )
        assertTrue(
            OwnedIdentityUnreadCounts(unreadDiscussionCount = 1).hasNotificationDot()
        )
        assertTrue(
            OwnedIdentityUnreadCounts(invitationCount = 1).hasNotificationDot()
        )
    }
}
