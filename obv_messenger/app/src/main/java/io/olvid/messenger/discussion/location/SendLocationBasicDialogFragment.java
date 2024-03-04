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

package io.olvid.messenger.discussion.location;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.HandlerExecutor;
import io.olvid.messenger.customClasses.LocationShareQuality;
import io.olvid.messenger.databases.entity.jsons.JsonLocation;
import io.olvid.messenger.databases.tasks.PostLocationMessageInDiscussionTask;
import io.olvid.messenger.settings.SettingsActivity;

public class SendLocationBasicDialogFragment extends AbstractLocationDialogFragment implements View.OnClickListener {

    public static final String DISCUSSION_ID_KEY = "discussion_id";
    public static final String CONTINUOUS_SHARING_KEY = "continuous_sharing";
    private static final double GREEN_PRECISION_LIMIT = 20.0;
    private static final double ORANGE_PRECISION_LIMIT = 50.0;

    // used to manually request location permission and activation on first launch
    private boolean firstCallToOnStart = true;
    // map integrations use this fragment for sharing location, behaviour is a little bit different
    private long discussionId;
    private boolean continuousLocationSharing;

    private LocationManager locationManager;
    private FragmentActivity activity;


    private Location currentLocation = null;
    private final LocationListenerCompat locationListenerCompat = this::onLocationUpdate;

    private View rootView;

    private TextView locationTextView;
    private TextView precisionTextView;
    private TextView altitudeTextView;
    private ImageView locationImageView;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch shareLocationSwitch;
    private TextView shareLocationDurationTextView;
    private Long shareLocationCurrentDuration;
    private LocationShareQuality shareLocationCurrentQuality;

    private ConstraintLayout shareLocationIntervalLayout;
    private TextView shareLocationIntervalTextView;

    private Button validateButton;

    public static SendLocationBasicDialogFragment newInstance(long discussionId) {
        return newInstance(discussionId, false);
    }

    public static SendLocationBasicDialogFragment newInstance(long discussionId, boolean continuousLocationSharing) {
        SendLocationBasicDialogFragment fragment = new SendLocationBasicDialogFragment();
        Bundle args = new Bundle();
        args.putLong(DISCUSSION_ID_KEY, discussionId);
        args.putBoolean(CONTINUOUS_SHARING_KEY, continuousLocationSharing);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get activity
        this.activity = requireActivity();

        Bundle arguments = getArguments();
        if (arguments != null) {
            this.discussionId = arguments.getLong(DISCUSSION_ID_KEY);
            this.continuousLocationSharing = arguments.getBoolean(CONTINUOUS_SHARING_KEY);
        } else {
            dismiss();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // inflate view
        rootView = inflater.inflate(R.layout.fragment_send_location_basic, container, false);

        // find all view elements
        locationTextView = rootView.findViewById(R.id.send_location_basic_position_coordinates_text_view);
        precisionTextView = rootView.findViewById(R.id.send_location_basic_position_precision_text_view);
        altitudeTextView = rootView.findViewById(R.id.send_location_basic_position_altitude_text_view);
        locationImageView = rootView.findViewById(R.id.send_location_basic_position_image_view);

        shareLocationSwitch = rootView.findViewById(R.id.send_location_basic_share_switch);
        shareLocationDurationTextView = rootView.findViewById(R.id.send_location_basic_share_duration_text_view);

        shareLocationIntervalLayout = rootView.findViewById(R.id.send_location_basic_share_interval_layout);
        shareLocationIntervalTextView = rootView.findViewById(R.id.send_location_basic_share_interval_text_view);

        validateButton = rootView.findViewById(R.id.send_location_basic_button_validate);
        validateButton.setOnClickListener(this);
        Button cancelButton = rootView.findViewById(R.id.send_location_basic_button_cancel);
        cancelButton.setOnClickListener(this);

        // share duration default value
        shareLocationCurrentDuration = SettingsActivity.getLocationDefaultSharingDurationValue();
        shareLocationDurationTextView.setText(SettingsActivity.getLocationDefaultSharingDurationLongString(activity));

        // share duration dropdown menu setup
        shareLocationDurationTextView.setOnClickListener((view) -> {
            ShareLocationPopupMenu shareLocationPopupMenu = ShareLocationPopupMenu.getDurationPopUpMenu(this.activity, view);
            shareLocationPopupMenu.setOnMenuItemClickListener(item -> {
                // set text
                shareLocationDurationTextView.setText(shareLocationPopupMenu.getItemLongString(item));
                // keep duration in memory
                shareLocationCurrentDuration = shareLocationPopupMenu.getItemDuration(item);
                // enable sharing if it was not
                if (!shareLocationSwitch.isChecked()) {
                    shareLocationSwitch.setChecked(true);
                }
                return true;
            });
            shareLocationPopupMenu.show();
        });

        // share quality default value
        shareLocationCurrentQuality = SettingsActivity.getLocationDefaultShareQuality();
        shareLocationIntervalTextView.setText(shareLocationCurrentQuality.getFullString(activity));

        // share duration dropdown menu setup
        shareLocationIntervalTextView.setOnClickListener((view) -> {
            ShareLocationPopupMenu shareLocationPopupMenu = ShareLocationPopupMenu.getQualityPopUpMenu(this.activity, view);
            shareLocationPopupMenu.setOnMenuItemClickListener(item -> {
                // set text
                shareLocationIntervalTextView.setText(shareLocationPopupMenu.getItemLongString(item));
                // keep quality in memory
                shareLocationCurrentQuality = shareLocationPopupMenu.getItemQuality(item);
                // enable sharing if it was not
                if (!shareLocationSwitch.isChecked()) {
                    shareLocationSwitch.setChecked(true);
                }
                return true;
            });
            shareLocationPopupMenu.show();
        });

        // setup sharing switch
        shareLocationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                validateButton.setText(R.string.button_label_start_sharing);
                shareLocationIntervalLayout.setVisibility(View.VISIBLE);

                if (!isBackgroundLocationPermissionGranted(activity)) {
                    requestBackgroundLocationPermission(activity);
                }
            } else {
                validateButton.setText(R.string.button_label_send);
                shareLocationIntervalLayout.setVisibility(View.GONE);
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        // set dialog dimensions and make it transparent
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        }

        if (firstCallToOnStart) {
            checkPermissionsAndUpdateDialog();
            firstCallToOnStart = false;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onResume() {
        super.onResume();

        if (locationManager != null
                && isLocationPermissionGranted(activity)
                && isLocationEnabled()) {
            // setup location updates
            LocationRequestCompat locationRequest = new LocationRequestCompat.Builder(0)
                    .setMinUpdateIntervalMillis(0)
                    .setMinUpdateDistanceMeters(0)
                    .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                    .build();

            // get executor
            Executor executor;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                executor = App.getContext().getMainExecutor();
            } else {
                executor = new HandlerExecutor(Looper.getMainLooper());
            }

            // subscribe to all providers locations updates (to have results as fast as possible)
            for (String provider : locationManager.getProviders(true)) {
                LocationManagerCompat.requestLocationUpdates(locationManager, provider, locationRequest, executor, locationListenerCompat);
                LocationManagerCompat.getCurrentLocation(locationManager, provider, null, executor, this::onLocationUpdate);
            }

            // when in share location only we try to use last know locations to be able to start location sharing even if gps is not available
            // not in normal mode cause we do not want to be able to send a location that is not your current location !
            if (continuousLocationSharing) {
                for (String p : locationManager.getProviders(true)) {
                    onLocationUpdate(locationManager.getLastKnownLocation(p));
                }
            }
        }
    }

    @Override
    protected void checkPermissionsAndUpdateDialog() {
        // check location services are accessible
        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            App.toast(R.string.toast_message_location_services_unavailable, Toast.LENGTH_SHORT);
            dismiss();
            return;
        }

        // ask permission grant if necessary and verify it was granted
        if (!isLocationPermissionGranted(this.activity)) {
            rootView.setVisibility(View.GONE);
            requestLocationPermission();
            return;
        } else if (!isLocationEnabled()) {
            // check location is enabled
            rootView.setVisibility(View.GONE);
            requestLocationActivation(this.activity);
            return;
        }

        rootView.setVisibility(View.VISIBLE);

        // if share location only: force sharing mode and hide switch
        if (continuousLocationSharing) {
            shareLocationSwitch.setEnabled(false);
            shareLocationSwitch.setChecked(true);
            shareLocationSwitch.setVisibility(View.GONE);
        }

        if (currentLocation == null) {
            // first mark waiting for location message and start spinner rotation
            locationTextView.setText(R.string.label_waiting_for_location);
            RotateAnimation spinnerRotateAnimation = new RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            spinnerRotateAnimation.setInterpolator(new LinearInterpolator());
            spinnerRotateAnimation.setRepeatCount(Animation.INFINITE);
            spinnerRotateAnimation.setDuration(2000);
            locationImageView.startAnimation(spinnerRotateAnimation);
        }
    }

    private void onLocationUpdate(Location location) {
        if (location == null || !isAdded()) {
            return;
        }

        // first location found: replace spinner by icon
        if (currentLocation == null) {
            locationImageView.clearAnimation();
            Drawable drawable = AppCompatResources.getDrawable(App.getContext(), R.drawable.ic_map_and_pin_animated);
            locationImageView.setImageDrawable(drawable);
            if (drawable instanceof AnimationDrawable) {
                ((AnimationDrawable) drawable).start();
            }
            validateButton.setEnabled(true);
        }

        // update location
        currentLocation = location;

        // truncate float to show
        JsonLocation jsonLocation = JsonLocation.sendLocationMessage(currentLocation);

        locationTextView.setText(getString(R.string.label_location_message_content_position, jsonLocation.getTruncatedLatitudeString(), jsonLocation.getTruncatedLongitudeString()));
        altitudeTextView.setText(getString(R.string.label_location_message_content_altitude, jsonLocation.getTruncatedAltitudeString(activity)));
        precisionTextView.setText(getString(R.string.label_location_message_content_precision, jsonLocation.getTruncatedPrecisionString(activity)));

        if (currentLocation.getAccuracy() < GREEN_PRECISION_LIMIT) {
            precisionTextView.setTextColor(this.activity.getResources().getColor(R.color.green));
        } else if (currentLocation.getAccuracy() < ORANGE_PRECISION_LIMIT) {
            precisionTextView.setTextColor(this.activity.getResources().getColor(R.color.orange));
        } else {
            precisionTextView.setTextColor(this.activity.getResources().getColor(R.color.red));
        }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onPause() {
        super.onPause();
        if (locationManager != null) {
            if (isLocationPermissionGranted(activity)) {
                LocationManagerCompat.removeUpdates(locationManager, locationListenerCompat);
            }
        }
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.send_location_basic_button_cancel) {
            dismiss();
        } else if (id == R.id.send_location_basic_button_validate) {
            if (currentLocation != null) {
                // if share check box is checked: start sharing position
                if (shareLocationSwitch.isChecked()) {
                    startSharingLocation();
                }
                // else post location position
                else {
                    App.runThread(PostLocationMessageInDiscussionTask.postSendLocationMessageInDiscussionTask(currentLocation, discussionId, true));
                }
            }
            dismiss();
            // if shareLocationOnly dismiss parent (map fragment) fragment when start sharing
            if (continuousLocationSharing && getParentFragment() != null && getParentFragment() instanceof DialogFragment) {
                ((DialogFragment) getParentFragment()).dismiss();
            }
        }
    }

    private void startSharingLocation() {
        Long shareExpirationInMs;
        if (shareLocationCurrentDuration == null || shareLocationCurrentDuration < 0) {
            shareExpirationInMs = null;
        } else {
            shareExpirationInMs = System.currentTimeMillis() + shareLocationCurrentDuration;
        }

        // post first location message (will start location sharing service)
        App.runThread(PostLocationMessageInDiscussionTask.startLocationSharingInDiscussionTask(currentLocation, discussionId, true, shareExpirationInMs, shareLocationCurrentQuality));
    }

    // dismiss if request for permission or location activation was canceled
    @Override
    public void onRequestCanceled() {
        this.dismiss();
    }
}
