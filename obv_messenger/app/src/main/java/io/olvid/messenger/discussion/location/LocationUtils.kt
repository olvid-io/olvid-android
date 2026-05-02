package io.olvid.messenger.discussion.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.olvid.messenger.App

object LocationUtils {
    @JvmStatic
    fun isLocationPermissionGranted(context: Context): Boolean = (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)


    @JvmStatic
    fun isBackgroundLocationPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    @JvmStatic
    fun isLocationEnabled(): Boolean = (App.getContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.run { LocationManagerCompat.isLocationEnabled(this) } == true
}
