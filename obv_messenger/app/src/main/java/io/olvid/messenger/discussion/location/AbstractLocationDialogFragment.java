/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.discussion.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;

public abstract class AbstractLocationDialogFragment extends DialogFragment {
    private ActivityResultLauncher<String[]> grantLocationPermissionActivityResultLauncher;
    private ActivityResultLauncher<String> grantBackgroundLocationPermissionActivityResultLauncher;
    private ActivityResultLauncher<Intent> enableLocationActivityResultLauncher;

    protected abstract void checkPermissionsAndUpdateDialog();

    // what to do if location or permissions requests are canceled
    // (map integration do not dismiss, but basic fragment do)
    public abstract void onRequestCanceled();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // intent to open application settings if user denied access to a permission

        // Location fragments shall wait for location permission granting
        // here we directly ask for os to show permission granting window. If user decline or OS ignores request (user already declined)
        // show a pop up to open application settings
        grantLocationPermissionActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean fineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
            Boolean coarseLocationGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
            if ((fineLocationGranted == null || !fineLocationGranted)
                    && (coarseLocationGranted == null || !coarseLocationGranted)) {
                Intent openApplicationSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", context.getPackageName(), null);
                openApplicationSettingsIntent.setData(uri);

                new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_grant_location_access)
                        .setMessage(R.string.dialog_message_grant_location_access)
                        .setPositiveButton(R.string.button_label_open_settings, (DialogInterface dialogInterface, int i) -> {
                            if (context instanceof AppCompatActivity) {
                                App.prepareForStartActivityForResult((AppCompatActivity) context);
                            }
                            context.startActivity(openApplicationSettingsIntent);
                        })
                        .setNegativeButton(R.string.button_label_cancel, (DialogInterface dialogInterface, int i) -> this.onRequestCanceled())
                        .setOnCancelListener(dialogInterface -> this.onRequestCanceled())
                        .create()
                        .show();
                return;
            }
            checkPermissionsAndUpdateDialog();
        });

        grantBackgroundLocationPermissionActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (result == null || !result) {
                    App.toast(R.string.toast_message_background_location_denied, Toast.LENGTH_SHORT);
                }
            }
            checkPermissionsAndUpdateDialog();
        });

        ActivityResultContracts.StartActivityForResult locationActivationStartActivityForResult = new ActivityResultContracts.StartActivityForResult();
        enableLocationActivityResultLauncher = registerForActivityResult(locationActivationStartActivityForResult, result -> checkPermissionsAndUpdateDialog());
    }


    // LOCATION PERMISSION GRANTING ZONE
    public static boolean isLocationPermissionGranted(FragmentActivity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermission() {
        grantLocationPermissionActivityResultLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
        });
    }

    public static boolean isBackgroundLocationPermissionGranted(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void requestBackgroundLocationPermission(FragmentActivity activity) {
        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_background_location_access)
                .setNegativeButton(R.string.button_label_cancel, null);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                builder.setMessage(R.string.dialog_message_background_location_access_api_q)
                        .setPositiveButton(R.string.button_label_grant_access, (DialogInterface dialogInterface, int i) -> grantBackgroundLocationPermissionActivityResultLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION));
            } else {
                Intent openApplicationSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                openApplicationSettingsIntent.setData(uri);

                builder.setMessage(getString(R.string.dialog_message_background_location_access, getString(R.string.text_allow_all_the_time)));
                builder.setPositiveButton(R.string.button_label_open_settings, (DialogInterface dialogInterface, int i) -> {
                    App.prepareForStartActivityForResult(activity);
                    activity.startActivity(openApplicationSettingsIntent);
                });
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setMessage(getString(R.string.dialog_message_background_location_access, activity.getPackageManager().getBackgroundPermissionOptionLabel()));
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                builder.setPositiveButton(R.string.button_label_open_settings, (DialogInterface dialogInterface, int i) -> {
                    App.prepareForStartActivityForResult(activity);
                    grantBackgroundLocationPermissionActivityResultLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                });
            } else {
                Intent openApplicationSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                openApplicationSettingsIntent.setData(uri);

                builder.setPositiveButton(R.string.button_label_open_settings, (DialogInterface dialogInterface, int i) -> {
                    App.prepareForStartActivityForResult(activity);
                    activity.startActivity(openApplicationSettingsIntent);
                });
            }
        }
        builder.create().show();
    }



    // LOCATION ACTIVATION ZONE
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) App.getContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            return LocationManagerCompat.isLocationEnabled(locationManager);
        } else {
            return false;
        }
    }

    public void requestLocationActivation(FragmentActivity activity) {
        Intent openLocationSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        openLocationSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_enable_location)
                .setMessage(R.string.label_enable_location)
                .setPositiveButton(R.string.button_label_open_settings, (DialogInterface dialogInterface, int i) -> {
                    dialogInterface.dismiss();
                    App.prepareForStartActivityForResult(activity);
                    enableLocationActivityResultLauncher.launch(openLocationSettingsIntent);
                })
                .setNegativeButton(R.string.button_label_cancel, (DialogInterface dialogInterface, int i) -> this.onRequestCanceled())
                .setOnCancelListener((dialogInterface -> this.onRequestCanceled()))
                .create().show();
    }
}
