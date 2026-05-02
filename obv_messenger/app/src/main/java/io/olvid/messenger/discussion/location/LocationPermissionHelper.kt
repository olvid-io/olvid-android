package io.olvid.messenger.discussion.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder

class LocationPermissionHelper(
    activityResultCaller: ActivityResultCaller,
    private val context: Context,
    private val onPermissionsUpdated: () -> Unit = {}
) {

    private val grantLocationPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activityResultCaller.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineLocationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (!fineLocationGranted && !coarseLocationGranted) {
                showPermissionDeniedDialog()
            } else {
                onPermissionsUpdated()
            }
        }

    private val grantBackgroundLocationPermissionLauncher: ActivityResultLauncher<String> =
        activityResultCaller.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isGranted) {
                 App.toast(R.string.toast_message_background_location_denied, android.widget.Toast.LENGTH_SHORT)
            }
            onPermissionsUpdated()
        }

    private val enableLocationLauncher: ActivityResultLauncher<Intent> =
        activityResultCaller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onPermissionsUpdated()
        }

    fun checkPermissionsAndEnableLocation(
        requestIfNotGranted: Boolean = true,
        requestIfNotEnabled: Boolean = true
    ): Boolean {
        if (!LocationUtils.isLocationPermissionGranted(context)) {
            if (requestIfNotGranted) {
                requestLocationPermission()
            }
            return false
        }

        if (!LocationUtils.isLocationEnabled()) {
            if (requestIfNotEnabled) {
                requestLocationActivation()
            }
            return false
        }
        return true
    }

    fun requestLocationPermission() {
        grantLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun requestBackgroundLocationPermission() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                 .setTitle(R.string.dialog_title_background_location_access)
                 .setNegativeButton(R.string.button_label_cancel, null)

             if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                 // Android 10 (Q) specific logic
                  if ((context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == true) {
                     builder.setMessage(R.string.dialog_message_background_location_access_api_q)
                         .setPositiveButton(R.string.button_label_grant_access) { _, _ ->
                             grantBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                         }
                 } else {
                     builder.setMessage(context.getString(R.string.dialog_message_background_location_access, context.getString(R.string.text_allow_all_the_time)))
                         .setPositiveButton(R.string.button_label_open_settings) { _, _ ->
                             openApplicationSettings()
                         }
                 }
                 builder.create().show()

             } else {
                 // Android 11+ (R) specific logic
                 val packageManager = context.packageManager
                 val backgroundPermissionOptionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     packageManager.backgroundPermissionOptionLabel
                 } else {
                     context.getString(R.string.text_allow_all_the_time)
                 }
                 
                 builder.setMessage(context.getString(R.string.dialog_message_background_location_access, backgroundPermissionOptionLabel))
                 
                if ((context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == true) {
                     builder.setPositiveButton(R.string.button_label_open_settings) { _, _ ->
                         if (context is FragmentActivity) {
                             App.prepareForStartActivityForResult(context)
                         }
                         grantBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                     }
                 } else {
                      builder.setPositiveButton(R.string.button_label_open_settings) { _, _ ->
                         openApplicationSettings()
                     }
                 }
                 builder.create().show()
             }
         } else {
             // below Q, background location is granted with foreground location
             onPermissionsUpdated()
         }
    }

    fun requestLocationActivation() {
        val openLocationSettingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_enable_location)
            .setMessage(R.string.label_enable_location)
            .setPositiveButton(R.string.button_label_open_settings) { dialog, _ ->
                dialog.dismiss()
                if (context is FragmentActivity) {
                     App.prepareForStartActivityForResult(context)
                }
                enableLocationLauncher.launch(openLocationSettingsIntent)
            }
            .setNegativeButton(R.string.button_label_cancel, null)
            .create()
            .show()
    }
    
    private fun showPermissionDeniedDialog() {
        SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_grant_location_access)
            .setMessage(R.string.dialog_message_grant_location_access)
            .setPositiveButton(R.string.button_label_open_settings) { _, _ ->
                openApplicationSettings()
            }
            .setNegativeButton(R.string.button_label_cancel, null)
            .create()
            .show()
    }

    private fun openApplicationSettings() {
        val openApplicationSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        if (context is FragmentActivity) {
             App.prepareForStartActivityForResult(context)
        }
        context.startActivity(openApplicationSettingsIntent)
    }
}
