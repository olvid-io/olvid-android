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

package io.olvid.messenger.customClasses;


import junit.framework.TestCase;

public class StringUtilsTest extends TestCase {
    public void testRemoveCompany() {
        assertEquals("Cindy Joy", StringUtils.removeCompanyFromDisplayName("Cindy Joy (CFO @ ACME)"));
        assertEquals("Cindy Joy", StringUtils.removeCompanyFromDisplayName("Cindy Joy"));
        assertEquals("Cindy Joy", StringUtils.removeCompanyFromDisplayName("Cindy Joy (ACME)"));
        assertEquals("Cindy Joy (", StringUtils.removeCompanyFromDisplayName("Cindy Joy ("));
        assertEquals("Cindy Joy", StringUtils.removeCompanyFromDisplayName("Cindy Joy (Jr.) (Head of HR)"));
        assertEquals("Cindy Joy", StringUtils.removeCompanyFromDisplayName("Cindy Joy (Head of HR @ Holo (c))"));
    }
}
