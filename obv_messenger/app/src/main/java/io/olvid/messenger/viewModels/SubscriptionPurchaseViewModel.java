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

package io.olvid.messenger.viewModels;

import androidx.lifecycle.ViewModel;

import com.android.billingclient.api.SkuDetails;

import java.util.Arrays;
import java.util.List;

public class SubscriptionPurchaseViewModel extends ViewModel {
    public byte[] bytesOwnedIdentity = null;
    public boolean showSubscriptionPlans = false;

    public Boolean freeTrialResults = null;
    public boolean freeTrialFailed = false;
    public List<SkuDetails> subscriptionDetails = null;
    public boolean subscriptionFailed = false;

    public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
        if (!Arrays.equals(bytesOwnedIdentity, this.bytesOwnedIdentity)) {
            this.bytesOwnedIdentity = bytesOwnedIdentity;

            // identity changed --> reset all properties
            this.showSubscriptionPlans = false;
            this.freeTrialResults = null;
            this.freeTrialFailed = false;
            this.subscriptionDetails = null;
            this.subscriptionFailed = false;
        }
    }
}
