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

package io.olvid.messenger.databases.entity

import io.olvid.engine.engine.types.ObvDialog.Category
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationTest {

    @Test
    fun `categoryRequiresAction - SAS exchange requires action, SAS confirmed only waits for the contact`() {
        assertTrue(Invitation.categoryRequiresAction(Category.SAS_EXCHANGE_DIALOG_CATEGORY))
        assertFalse(Invitation.categoryRequiresAction(Category.SAS_CONFIRMED_DIALOG_CATEGORY))
    }

    @Test
    fun `categoryRequiresAction - sent or accepted invitations do not require action`() {
        listOf(
            Category.INVITE_SENT_DIALOG_CATEGORY,
            Category.INVITE_ACCEPTED_DIALOG_CATEGORY,
            Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY,
            Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY,
            Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY,
        ).forEach { assertFalse(Invitation.categoryRequiresAction(it)) }
    }
}
