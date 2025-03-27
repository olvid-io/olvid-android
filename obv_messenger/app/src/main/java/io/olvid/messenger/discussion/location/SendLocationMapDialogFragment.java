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

package io.olvid.messenger.discussion.location;

import static io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService.PASSIVE_PROVIDER_UPDATE_INTERVAL_MILLIS;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.Executor;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.HandlerExecutor;
import io.olvid.messenger.databases.tasks.PostOsmLocationMessageInDiscussionTask;
import io.olvid.messenger.settings.SettingsActivity;

public class SendLocationMapDialogFragment extends AbstractLocationDialogFragment {
    private static final String DEFAULT_PELIAS_SERVER = "https://pelias.olvid.io";
    public static final String DISCUSSION_ID_KEY = "discussion_id";
    public static final String INTEGRATION_KEY = "integration";
    private long discussionId;
    private SettingsActivity.LocationIntegrationEnum integration;
    private FragmentActivity activity;
    private LocationManager locationManager = null;
    private final LocationListenerCompat locationListenerCompat = this::onLocationUpdate;

    private MapViewAbstractFragment mapView;
    private TextView addressTextView;
    private TextView fetchingAddressTextView;
    private boolean showAddress = false;
    private boolean showFetchingAddress = false;

    private ImageView mapCenterPointerImageView;
    private ImageView mapCenterPointerShadowImageView;

    private LinearLayout loadingSpinnerLayout;

    // live data used by address requests tasks to update front
    private final MutableLiveData<String> currentAddressLiveData = new MutableLiveData<>(null);
    // handler and task for current addresses requests
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable addressRequestTask;

    // internally used by functions managing center marker animations
    private boolean centerMarkerIsUp = false;
    // used to avoid strange animation when map start centered on current location
    private boolean skipNextMarkerAnimation = false;

    private String peliasServer = DEFAULT_PELIAS_SERVER;

    public static SendLocationMapDialogFragment newInstance(long discussionId, SettingsActivity.LocationIntegrationEnum integration) {
        SendLocationMapDialogFragment fragment = new SendLocationMapDialogFragment();
        Bundle args = new Bundle();
        args.putLong(DISCUSSION_ID_KEY, discussionId);
        args.putInt(INTEGRATION_KEY, integration.ordinal());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setWindowAnimations(R.style.FadeInAndOutAnimation);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get current activity
        this.activity = requireActivity();
        this.locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        Bundle arguments = getArguments();
        if (arguments != null) {
            discussionId = arguments.getLong(DISCUSSION_ID_KEY);
            integration = SettingsActivity.LocationIntegrationEnum.values()[arguments.getInt(INTEGRATION_KEY)];
        } else {
            dismiss();
            return;
        }

        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme_NoActionBar_Transparent);

        String customPeliasServer = SettingsActivity.getLocationCustomAddressServer();
        if (customPeliasServer != null) {
            this.peliasServer = customPeliasServer;
        } else {
            String peliasServer = AppSingleton.getEngine().getAddressServerUrl(AppSingleton.getBytesCurrentIdentity());
            if (peliasServer != null) {
                this.peliasServer = peliasServer;
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_send_location_map, container, false);

        mapView = MapFragmentProvider.getMapFragmentForProvider(integration);
        if (mapView == null) {
            return null;
        }

        this.getChildFragmentManager().beginTransaction().replace(R.id.send_location_map_map_view_container, mapView).commit();

        mapCenterPointerImageView = rootView.findViewById(R.id.send_location_map_center_pointer);
        mapCenterPointerShadowImageView = rootView.findViewById(R.id.send_location_map_center_pointer_shadow);

        setCenterPointerVisibility(false);
        mapView.setOnMapReadyCallback(() -> {
            // when map is ready: show center pointer, and hide spinner

            // register to GPS updates
            registerToLocationUpdate();

            // if location is possible use current location as center and manually request address
            if (!isLocationPermissionGranted(activity)) {
                requestLocationPermission();
            } else {
                skipNextMarkerAnimation = true;
                mapView.centerOnCurrentLocation(false);
            }
            loadingSpinnerLayout.setVisibility(View.GONE);
            setCenterPointerVisibility(true);
        });

        addressTextView = rootView.findViewById(R.id.send_location_maps_choose_address_text_view);
        fetchingAddressTextView = rootView.findViewById(R.id.send_location_maps_fetching_address_text_view);

        loadingSpinnerLayout = rootView.findViewById(R.id.send_location_map_loading_spinner_layout);

        FloatingActionButton currentLocationButtonFab = rootView.findViewById(R.id.send_location_maps_current_location_fab);
        FloatingActionButton backButtonFab = rootView.findViewById(R.id.send_location_maps_back_fab);
        ImageView layersButton = rootView.findViewById(R.id.send_location_layers_button);
        mapView.setLayersButtonVisibilitySetter((Boolean visible) -> layersButton.setVisibility((visible != null && visible) ? View.VISIBLE : View.GONE));
        layersButton.setOnClickListener(mapView::onLayersButtonClicked);

        // setup button
        rootView.findViewById(R.id.button_send_location).setOnClickListener(this::handleSendLocationButtonClick);
        rootView.findViewById(R.id.button_live_share_location).setOnClickListener(this::handleShareLocationButtonClick);
        currentLocationButtonFab.setOnClickListener(this::handleCenterOnCurrentLocationFabClick);
        mapView.currentlyCenteredOnGpsPosition.observe(this, tracking -> {
            if (tracking != null && tracking) {
                currentLocationButtonFab.setImageResource(R.drawable.ic_location_current_location_enabled);
            } else {
                currentLocationButtonFab.setImageResource(R.drawable.ic_location_current_location_disabled);
            }
        });
        backButtonFab.setOnClickListener(this::handleBackFabClick);

        // every time camera center is modified (camera stopped moving) update address field
        // if set to null this means camera is moving (cancel pending address requests)
        mapView.getCurrentCameraCenterLiveData().observe(this, latLngWrapper -> {
            // handle marker center animations
            if (latLngWrapper == null) {
                moveCenterMarkerUp();
            } else {
                moveCenterMarkerDown();
            }

            // remove pending task if there is one in every cases
            if (this.addressRequestTask != null) {
                handler.removeCallbacks(addressRequestTask);
                addressRequestTask = null;
            }
            // launch a new delayed request if there are coordinates
            if (SettingsActivity.getLocationDisableAddressLookup()) {
                currentAddressLiveData.postValue(" ");
            } else if (latLngWrapper != null) {
                if (mapView.getCameraZoom() < RequestAndUpdateAddressFieldTask.MIN_ZOOM_FOR_REQUESTS) {
                    currentAddressLiveData.postValue(" ");
                } else {
                    currentAddressLiveData.postValue(null);
                    this.addressRequestTask = new RequestAndUpdateAddressFieldTask(activity, peliasServer, latLngWrapper, (RequestAndUpdateAddressFieldTask requestTask, @Nullable String address) -> {
                        if (addressRequestTask == requestTask) {
                            if (address == null) {
                                currentAddressLiveData.postValue("");
                            } else {
                                currentAddressLiveData.postValue(address);
                            }
                        }
                    });
                    handler.postDelayed(() -> {
                        if (this.addressRequestTask != null) {
                            App.runThread(this.addressRequestTask);
                        }
                    }, 100);
                }
            } else {
                if (mapView.getCameraZoom() >= RequestAndUpdateAddressFieldTask.MIN_ZOOM_FOR_REQUESTS) {
                    currentAddressLiveData.postValue(null);
                }
            }
        });

        int thirtyTwoDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());

        // update current address field when requests update live data
        currentAddressLiveData.observe(this, (String address) -> {
            if (address == null) {
                fetchingAddressTextView.setText(R.string.label_fetching_address);
                fade(addressTextView, thirtyTwoDp, false, false);
                fade(fetchingAddressTextView, thirtyTwoDp, false, true);
            } else if (address.isEmpty()) {
                fetchingAddressTextView.setText(R.string.label_no_address_found);
                fade(addressTextView, thirtyTwoDp, false, false);
                fade(fetchingAddressTextView, thirtyTwoDp, false, true);
            } else if (" ".equals(address)) {
                fetchingAddressTextView.setText(R.string.label_zoom_in_for_address);
                fade(addressTextView, thirtyTwoDp, false, false);
                fade(fetchingAddressTextView, thirtyTwoDp, false, true);
            } else if (" ".equals(address)) {
                fetchingAddressTextView.setText(R.string.label_address_lookup_disabled);
                fade(addressTextView, thirtyTwoDp, false, false);
                fade(fetchingAddressTextView, thirtyTwoDp, false, true);
            } else {
                addressTextView.setText(address);
                fade(addressTextView, thirtyTwoDp, true, true);
                fade(fetchingAddressTextView, thirtyTwoDp, true, false);
            }
        });
        addressTextView.setVisibility(View.VISIBLE);
        fetchingAddressTextView.setVisibility(View.VISIBLE);

        return rootView;
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onPause() {
        super.onPause();
        if (locationManager != null) {
            if (isLocationPermissionGranted(activity)) {
                LocationManagerCompat.removeUpdates(locationManager, locationListenerCompat);
                LocationManagerCompat.removeUpdates(locationManager, fakeLocationListenerForGps);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        registerToLocationUpdate();
    }

    private final LocationListenerCompat fakeLocationListenerForGps = (Location location) -> {};

    @SuppressLint("MissingPermission")
    private void registerToLocationUpdate() {
        if (locationManager != null
                && isLocationPermissionGranted(activity)
                && isLocationEnabled()) {
            // setup location updates
            LocationRequestCompat locationRequest = new LocationRequestCompat.Builder(1000)
                    .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                    .build();

            // get executor
            Executor executor;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                executor = App.getContext().getMainExecutor();
            } else {
                executor = new HandlerExecutor(Looper.getMainLooper());
            }


            List<String> providers = locationManager.getProviders(true);
            if (providers.contains(LocationManager.PASSIVE_PROVIDER)) { // this should always be the case (if the documentation is correct)
                LocationManagerCompat.requestLocationUpdates(locationManager, LocationManager.PASSIVE_PROVIDER, new LocationRequestCompat.Builder(PASSIVE_PROVIDER_UPDATE_INTERVAL_MILLIS).build(), executor, locationListenerCompat);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && providers.contains(LocationManager.FUSED_PROVIDER)) {
                LocationManagerCompat.requestLocationUpdates(locationManager, LocationManager.FUSED_PROVIDER, locationRequest, executor, locationListenerCompat);
                if (providers.contains(LocationManager.GPS_PROVIDER)) {
                    // also enable gps callback (on Android 12 emulator, fused does not always trigger updates on its own)
                    LocationManagerCompat.requestLocationUpdates(locationManager, LocationManager.GPS_PROVIDER, locationRequest, executor, fakeLocationListenerForGps);
                }
            } else if (providers.contains(LocationManager.GPS_PROVIDER) || providers.contains(LocationManager.NETWORK_PROVIDER)) {
                if (providers.contains(LocationManager.GPS_PROVIDER)) {
                    LocationManagerCompat.requestLocationUpdates(locationManager, LocationManager.GPS_PROVIDER, locationRequest, executor, locationListenerCompat);
                }
                if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                    LocationManagerCompat.requestLocationUpdates(locationManager, LocationManager.NETWORK_PROVIDER, locationRequest, executor, locationListenerCompat);
                }
            }
        }
    }

    private void onLocationUpdate(Location location) {
        if (mapView != null) {
            mapView.onLocationUpdate(location);
        }
    }

    private void fade(View view, int translationPx, boolean directionUp, boolean showView) {
        boolean currentShowView;
        if (view.getId() == R.id.send_location_maps_choose_address_text_view) {
            currentShowView = showAddress;
            showAddress = showView;
        } else {
            currentShowView = showFetchingAddress;
            showFetchingAddress = showView;
        }
        if (currentShowView != showView) {
            view.clearAnimation();
            AnimationSet set = new AnimationSet(true);

            Animation translate = new TranslateAnimation(0, 0,
                    showView ? (directionUp ? translationPx : -translationPx) : 0,
                    showView ? 0 : (directionUp ? -translationPx : translationPx));
            translate.setDuration(250);
            translate.setFillAfter(true);

            Animation fade = new AlphaAnimation(
                    showView ? 0f : 1f,
                    showView ? 1f : 0f);
            fade.setDuration(250);
            fade.setFillAfter(true);

            set.addAnimation(translate);
            set.addAnimation(fade);
            set.setFillAfter(true);
            view.startAnimation(set);
        }
    }

    private void handleBackFabClick(View v) {
        this.dismiss();
    }

    private void handleCenterOnCurrentLocationFabClick(View v) {
        requestLocationPermission(); // will check permissions and center on current location
    }

    @SuppressLint("MissingPermission")
    public void handleSendLocationButtonClick(View v) {
        if (mapView.getCurrentCameraCenterLiveData().getValue() == null) {
            Logger.e("SendLocationMapDialogFragment: mapView returned a null currentLocation");
            return;
        }
        Location location = mapView.getCurrentCameraCenterLiveData().getValue().toLocation();
        if (mapView.currentlyCenteredOnGpsPosition.getValue() != null && mapView.currentlyCenteredOnGpsPosition.getValue()) {
            location.setAccuracy(mapView.getLatestLocationAccuracy());
            location.setAltitude(mapView.getLatestLocationAltitude());
        }

        // show spinner and hide center pointer
        loadingSpinnerLayout.setVisibility(View.VISIBLE);
        setCenterPointerVisibility(false);

        // disable current location
        mapView.setEnableCurrentLocation(false);

        // hide center marker
        setCenterPointerVisibility(false);

        // launch snapshot
        mapView.launchMapSnapshot((bitmap -> {
            // manually add marker to bitmap
            Canvas markCanvas = new Canvas(bitmap);
            DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            int markerSize = (int) (displayMetrics.density * 48);
            Bitmap markerBitmap = Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888);
            Drawable markerDrawable = ResourcesCompat.getDrawable(activity.getResources(), R.drawable.ic_location_red, null);
            if (markerDrawable != null) {
                markerDrawable.setBounds(
                        bitmap.getWidth() / 2 - markerSize / 2,
                        (bitmap.getHeight() / 2 - markerSize) + (int)displayMetrics.density * 6, // add 6 dp padding to align
                        bitmap.getWidth() / 2 + markerSize / 2,
                        (bitmap.getHeight() / 2) + (int)displayMetrics.density * 6);  // add 6 dp padding to align
                markerDrawable.draw(markCanvas);
            }
            markCanvas.drawBitmap(markerBitmap, bitmap.getWidth(), bitmap.getHeight(), null);
            markCanvas.drawBitmap(markerBitmap, 0, 0, null);

            // send message
            App.runThread(new PostOsmLocationMessageInDiscussionTask(location, discussionId, bitmap, currentAddressLiveData.getValue()));
            this.dismiss();
        }));
    }

    public void handleShareLocationButtonClick(View v) {
        SendLocationBasicDialogFragment fragment = SendLocationBasicDialogFragment.newInstance(discussionId, true);
        fragment.show(getChildFragmentManager(), "send-location-fragment-share-dialog");
    }

    // called if necessary to request permission / activation
    // will be called until location permission and activation are OK (or user canceled)
    // then we can centerOnCurrentLocation
    @SuppressLint("MissingPermission")
    @Override
    protected void checkPermissionsAndUpdateDialog() {
        // check location services are accessible
        if (locationManager == null) {
            App.toast(R.string.toast_message_location_services_unavailable, Toast.LENGTH_SHORT);
            dismiss();
            return;
        }

        // check permission and location is enabled
        if (!isLocationPermissionGranted(activity)) {
            requestLocationPermission();
            return ;
        } else if (!isLocationEnabled()) {
            requestLocationActivation(activity);
            return ;
        }

        // register to GPS updates
        registerToLocationUpdate();

        // if we can reach here this means user allowed and enabled location (so force centering)
        if (mapView != null) {
            mapView.centerOnCurrentLocation(true);
        }
    }

    private void setCenterPointerVisibility(boolean visible) {
        this.mapCenterPointerImageView.setVisibility(visible ? View.VISIBLE : View.GONE);
        this.mapCenterPointerShadowImageView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRequestCanceled() {}

    // marker animations zone
    private void moveCenterMarkerUp() {
        if (skipNextMarkerAnimation) {
            return;
        }
        if (!centerMarkerIsUp) {
            activity.runOnUiThread(() -> {
                Animation getUp = new TranslateAnimation(0, 0, 0, -64);
                getUp.setDuration(200);
                getUp.setRepeatMode(1);
                getUp.setFillAfter(true);
                mapCenterPointerImageView.startAnimation(getUp);
                centerMarkerIsUp = true;
            });
        }
        else {
            Logger.e("SendLocationMapLibre: trying to get marker up but it is already up");
        }
    }

    private void moveCenterMarkerDown() {
        if (skipNextMarkerAnimation) {
            skipNextMarkerAnimation = false;
            return;
        }
        if (centerMarkerIsUp) {
            // do not animate if hidden
            AnimationSet set = new AnimationSet(false);
            Animation getDown = new TranslateAnimation(0, 0, -64, 6);
            getDown.setDuration(150);
            getDown.setRepeatMode(1);
            Animation getUpToCenter = new TranslateAnimation(0, 0, 6, -6);
            getUpToCenter.setDuration(100);
            getUpToCenter.setStartOffset(150);
            getUpToCenter.setRepeatMode(1);

            set.addAnimation(getDown);
            set.addAnimation(getUpToCenter);
            set.setFillAfter(true);

            mapCenterPointerImageView.startAnimation(set);
            centerMarkerIsUp = false;
        }
        else {
            Logger.e("SendLocationMapDialogFragment: trying to get marker down but it is already down");
        }
    }
    // end marker animations zone
}
