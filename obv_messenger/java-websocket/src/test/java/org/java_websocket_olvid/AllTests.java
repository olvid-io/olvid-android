/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package org.java_websocket_olvid;

import org.java_websocket_olvid.client.AllClientTests;
import org.java_websocket_olvid.drafts.AllDraftTests;
import org.java_websocket_olvid.exceptions.AllExceptionsTests;
import org.java_websocket_olvid.framing.AllFramingTests;
import org.java_websocket_olvid.issues.AllIssueTests;
import org.java_websocket_olvid.misc.AllMiscTests;
import org.java_websocket_olvid.protocols.AllProtocolTests;
import org.java_websocket_olvid.util.Base64Test;
import org.java_websocket_olvid.util.ByteBufferUtilsTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;


@RunWith(Suite.class)
@Suite.SuiteClasses({
    ByteBufferUtilsTest.class,
    Base64Test.class,
    AllClientTests.class,
    AllDraftTests.class,
    AllIssueTests.class,
    AllExceptionsTests.class,
    AllMiscTests.class,
    AllProtocolTests.class,
    AllFramingTests.class
})
/**
 * Start all tests
 */
public class AllTests {

}
