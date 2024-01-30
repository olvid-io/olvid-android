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

package io.olvid.messenger.google_services;

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.DriveScopes;

import org.checkerframework.checker.nullness.qual.NonNull;

import io.olvid.messenger.R;

public class GoogleServicesUtils {
    public static boolean googleServicesAvailable(Context context) {
        return ConnectionResult.SUCCESS == GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    }

    public static String getSignInEmail(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            return null;
        }
        return account.getEmail();
    }

    public static void requestGoogleSignIn(Fragment fragment, int requestCode) {
        GoogleSignIn.getClient(fragment.requireActivity(), GoogleSignInOptions.DEFAULT_SIGN_IN)
                .signOut()
                .addOnCompleteListener((Task<Void> task) -> {
                    if (fragment.getActivity() != null) {
                        try {
                            GoogleSignIn.requestPermissions(
                                    fragment,
                                    requestCode,
                                    null,
                                    new Scope(DriveScopes.DRIVE_APPDATA),
                                    new Scope(Scopes.EMAIL));
                        } catch (Exception ignored) {}
                    }
                });
    }

    public static void requestGoogleSignOut(FragmentActivity activity) {
        // sign out any google drive
        GoogleSignIn.getClient(activity, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
    }

    public static void openOssMenuActivity(@NonNull FragmentActivity activity) {
        OssLicensesMenuActivity.setActivityTitle(activity.getString(R.string.activity_title_open_source_licenses));
        activity.startActivity(new Intent(activity, OssLicensesMenuActivity.class));
    }
}
