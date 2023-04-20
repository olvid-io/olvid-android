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

package io.olvid.messenger.discussion.location;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.settings.SettingsActivity;

public class FullscreenMapDialogFragment extends AbstractLocationDialogFragment {

    private final Message message;
    private final long discussionId;
    private final SettingsActivity.LocationIntegrationEnum integration;

    private FragmentActivity activity;

    private View rootView;

    MapViewAbstractFragment mapView;

    private FloatingActionButton centerOnMarkersFab;
    private FloatingActionButton openInThirdPartyAppFab;
    private FloatingActionButton backFab;

    private LiveData<List<Message>> sharingLocationMessageLiveData;
    private final List<Long> currentlyShownMessagesIdList = new ArrayList<>(); // contains message id of all messages with a symbol currently shown on map
    // need to center on marker on first call of sharingLocationMessageLiveData observer
    private boolean centerOnMarkersOnNextLocationMessagesUpdate;

    // show a sharing location map for a discussion or message
    public FullscreenMapDialogFragment(Message message, long discussionId, SettingsActivity.LocationIntegrationEnum integration) {
        this.message = message;
        this.discussionId = discussionId;
        this.integration = integration;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get current activity
        this.activity = requireActivity();

        // make fragment transparent
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme_NoActionBar_Transparent);
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setWindowAnimations(R.style.FadeInAnimation);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_fullscreen_map, container, false);

        mapView = MapFragmentProvider.getMapFragmentForProvider(integration);
        if (mapView == null) {
            return null;
        }

        getChildFragmentManager().beginTransaction().replace(R.id.fullscreen_map_map_view_container, mapView).commit();

        mapView.setOnMapReadyCallback(this::onMapReadyCallback);

        centerOnMarkersFab = rootView.findViewById(R.id.fullscreen_map_center_on_markers_fab);
        openInThirdPartyAppFab = rootView.findViewById(R.id.fullscreen_map_open_in_third_party_app_fab);
        backFab = rootView.findViewById(R.id.fullscreen_map_back_fab);

        // setup fabs
        centerOnMarkersFab.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_location_center_on_markers));
        centerOnMarkersFab.setOnClickListener(this::handleCenterOnMarkersFabClick);
        centerOnMarkersFab.setVisibility(View.VISIBLE);

        openInThirdPartyAppFab.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_open_location_in_third_party_app_48));
        openInThirdPartyAppFab.setOnClickListener(this::handleOpenInThirdPartyAppFabClick);
        openInThirdPartyAppFab.setVisibility(View.VISIBLE);

        backFab.setOnClickListener(this::handleBackFabClick);

        return rootView;
    }

    @SuppressLint("MissingPermission")
    public void onMapReadyCallback() {
        // if user already gave location access, and location is enabled show it's location on map
        mapView.setEnableCurrentLocation(isLocationPermissionGranted(this.activity) && isLocationEnabled());

        // if showing a location or a finished sharing: zoom on location, center camera and add a pointer on it
        if (message != null && message.locationType != Message.LOCATION_TYPE_SHARE) {
            Message.JsonLocation location = message.getJsonLocation();
            if (message.locationType == Message.LOCATION_TYPE_SEND) {
                mapView.addMarker(message.id, getPinMarkerIcon(), new LatLngWrapper(location), location.getPrecision());
            } else if (message.locationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
                mapView.addMarker(message.id, getInitialViewMarkerIcon(message.senderIdentifier), new LatLngWrapper(location), location.getPrecision());
            }
            mapView.centerOnMarkers(false, false);
        } else {
            // if showing sharing locations: retrieve every sharing location messages, add marker for all of them, center on it
            // observe sharing messages to update map when messages are updated
            centerOnMarkersOnNextLocationMessagesUpdate = true;
            sharingLocationMessageLiveData = AppDatabase.getInstance().messageDao().getCurrentlySharingLocationMessagesInDiscussionLiveData(discussionId);
            sharingLocationMessageLiveData.observe(this, this::sharingLocationMessagesObserver);
        }
    }

    private void sharingLocationMessagesObserver(List<Message> messages) {
        if (messages == null) {
            return;
        }

        Set<Long> noMoreSharingMessagesId = new HashSet<>(currentlyShownMessagesIdList);

        openInThirdPartyAppFab.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_location_person_pin));

        for (Message message : messages) {
            Message.JsonLocation location = message.getJsonLocation();
            // update symbol if needed
            if (currentlyShownMessagesIdList.contains(message.id)) {
                mapView.updateMarker(message.id, new LatLngWrapper(location), location.getPrecision());
            } else {
                // add symbol for this message
                mapView.addMarker(message.id, getInitialViewMarkerIcon(message.senderIdentifier), new LatLngWrapper(location), location.getPrecision());
                currentlyShownMessagesIdList.add(message.id);
            }
            noMoreSharingMessagesId.remove(message.id);
        }

        // remove symbols for no more sharing messages
        for (Long messageId : noMoreSharingMessagesId) {
            mapView.removeMarker(messageId);
        }

        // on first call center on markers
        if (centerOnMarkersOnNextLocationMessagesUpdate) {
            mapView.centerOnMarkers(false, false);
            centerOnMarkersOnNextLocationMessagesUpdate = false;
        }
    }

    // FAB HANDLERS
    private void handleBackFabClick(View view) {
        this.dismiss();
    }

    private void handleCenterOnMarkersFabClick(View view) {
        if (mapView != null) {
            mapView.centerOnMarkers(true, true);
        }
    }

    private void handleOpenInThirdPartyAppFabClick(View view) {
        // if live sharing, open bottom sheet, otherwise open third party app on fab click
        if (sharingLocationMessageLiveData != null && sharingLocationMessageLiveData.getValue() != null) {
            FullscreenMapBottomSheetDialog.newInstance(discussionId, this).show(activity.getSupportFragmentManager(), "fullscreen-map-bottom-sheet");
        } else if (message != null) {
            Message.JsonLocation jsonLocation = message.getJsonLocation();
            App.openLocationInMapApplication(activity, jsonLocation.getTruncatedLatitudeString(), jsonLocation.getTruncatedLongitudeString(), message.contentBody, null);
        }
    }

    @Override
    protected void checkPermissionsAndUpdateDialog() {
        // in FullscreenMapDialogFragment we should never be requesting location permission --> do nothing
    }

    private Bitmap getPinMarkerIcon() {
        Drawable locationMarkerDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_location_red, null);
        if (locationMarkerDrawable != null) {
            Canvas canvas = new Canvas();
            // we adjust the height of the bitmap so that the tip of the pin is right in the center
            Bitmap bitmap = Bitmap.createBitmap(locationMarkerDrawable.getIntrinsicWidth(), 11*locationMarkerDrawable.getIntrinsicHeight()/6, Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);
            locationMarkerDrawable.setBounds(0, 0, locationMarkerDrawable.getIntrinsicWidth(), locationMarkerDrawable.getIntrinsicHeight());
            locationMarkerDrawable.draw(canvas);
            return bitmap;
        }
        return null;
    }

    private Bitmap getInitialViewMarkerIcon(byte[] bytesIdentity) {
        InitialView initialView = new InitialView(this.activity);
        initialView.setFromCache(bytesIdentity);

        // prepare bitmap for marker icon
        int initialViewSize = (int) (64 * getResources().getDisplayMetrics().density);
        int shadowHeight = initialViewSize / 3;
        // we adjust the height of the bitmap so that the dot is right in the center
        Bitmap markerIcon = Bitmap.createBitmap(initialViewSize, 2*initialViewSize + shadowHeight, Bitmap.Config.ARGB_8888);
        Canvas markerIconCanvas = new Canvas(markerIcon);

        // load shadow as a bitmap
        Bitmap shadowBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.location_pin_shadow);

        // resize shadow
        Matrix resizeShadowMatrix = new Matrix();
        resizeShadowMatrix.postScale(
                ((float) initialViewSize) / shadowBitmap.getWidth(),
                ((float)(shadowHeight)) / shadowBitmap.getHeight()
        );
        Bitmap resizedBitmap = Bitmap.createBitmap(shadowBitmap, 0, 0, shadowBitmap.getWidth(), shadowBitmap.getHeight(), resizeShadowMatrix, false);
        shadowBitmap.recycle();
        shadowBitmap = resizedBitmap;

        // make shadow transparent and write to bitmap
        Paint alphaWhitePaint = new Paint();
        alphaWhitePaint.setAlpha(128);
        markerIconCanvas.drawBitmap(
                shadowBitmap,
                0,
                initialViewSize,
                alphaWhitePaint
        );

        // add black circle in the middle of shadow
        Paint blackPaint = new Paint();
        blackPaint.setColor(Color.BLACK);
        markerIconCanvas.drawCircle(
                (float) initialViewSize /2,
                initialViewSize + (float)shadowHeight/2,
                2*getResources().getDisplayMetrics().density,
                blackPaint
        );

        // prepare and draw initial view
        initialView.setSize(initialViewSize, initialViewSize);
        initialView.drawOnCanvas(markerIconCanvas);

        return markerIcon;
    }

    @Override
    public void onRequestCanceled() {}
}
