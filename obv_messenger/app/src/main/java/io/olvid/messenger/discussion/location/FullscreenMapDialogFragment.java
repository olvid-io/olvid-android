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
import android.widget.ImageView;

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
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonLocation;
import io.olvid.messenger.settings.SettingsActivity;

public class FullscreenMapDialogFragment extends AbstractLocationDialogFragment {
    public static final String INTEGRATION_KEY = "integration";
    public static final String TYPE_KEY = "type";
    public static final String MESSAGE_ID_KEY = "message_id";
    public static final String MESSAGE_SENDER_IDENTIFIER_KEY = "message_sender_identifier";
    public static final String MESSAGE_LOCATION_TYPE_KEY = "message_location_type";
    public static final String MESSAGE_JSON_LOCATION_KEY = "message_json_location";
    public static final String MESSAGE_CONTENT_BODY_KEY = "message_content_body";
    public static final String DISCUSSION_ID_KEY = "discussion_id";
    public static final String OWNED_IDENTITY_KEY = "owned_identity";

    public static final int TYPE_MESSAGE = 1;
    public static final int TYPE_DISCUSSION = 2;
    public static final int TYPE_OWNED_IDENTITY = 3;

    private SettingsActivity.LocationIntegrationEnum integration;
    private int type;
    private Long messageId;
    private byte[] messageSenderIdentifier;
    private int messageLocationType;
    private JsonLocation messageJsonLocation;
    private String messageContentBody;
    private Long discussionId;
    private byte[] bytesOwnedIdentity;

    private FragmentActivity activity;

    MapViewAbstractFragment mapView;

    private FloatingActionButton openInThirdPartyAppFab;

    private LiveData<List<Message>> sharingLocationMessageLiveData;
    private final List<Long> currentlyShownMessagesIdList = new ArrayList<>(); // contains message id of all messages with a symbol currently shown on map
    // need to center on marker on first call of sharingLocationMessageLiveData observer
    private boolean centerOnMarkersOnNextLocationMessagesUpdate;


    // discussionId may only be null if ownedIdentity is not null
    public static FullscreenMapDialogFragment newInstance(@Nullable Message message, @Nullable Long discussionId, @Nullable byte[] ownedIdentity, SettingsActivity.LocationIntegrationEnum integration) {
        FullscreenMapDialogFragment fragment = new FullscreenMapDialogFragment();
        Bundle args = new Bundle();
        if (ownedIdentity != null) {
            args.putInt(TYPE_KEY, TYPE_OWNED_IDENTITY);
            args.putByteArray(OWNED_IDENTITY_KEY, ownedIdentity);
        } else if (message != null && discussionId != null) {
            args.putInt(TYPE_KEY, TYPE_MESSAGE);
            args.putLong(MESSAGE_ID_KEY, message.id);
            args.putByteArray(MESSAGE_SENDER_IDENTIFIER_KEY, message.senderIdentifier);
            args.putInt(MESSAGE_LOCATION_TYPE_KEY, message.locationType);
            args.putString(MESSAGE_JSON_LOCATION_KEY, message.jsonLocation);
            args.putString(MESSAGE_CONTENT_BODY_KEY, message.contentBody);
            args.putLong(DISCUSSION_ID_KEY, discussionId);
        } else if (discussionId != null) {
            args.putInt(TYPE_KEY, TYPE_DISCUSSION);
            args.putLong(DISCUSSION_ID_KEY, discussionId);
        } else {
            return null;
        }
        args.putInt(INTEGRATION_KEY, integration.ordinal());
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get current activity
        this.activity = requireActivity();

        // make fragment transparent
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme_NoActionBar_Transparent);

        Bundle arguments = getArguments();
        if (arguments != null) {
            integration = SettingsActivity.LocationIntegrationEnum.values()[arguments.getInt(INTEGRATION_KEY)];
            type = arguments.getInt(TYPE_KEY);
            switch (type) {
                case TYPE_MESSAGE: {
                    messageId = arguments.getLong(MESSAGE_ID_KEY);
                    messageSenderIdentifier = arguments.getByteArray(MESSAGE_SENDER_IDENTIFIER_KEY);
                    messageLocationType = arguments.getInt(MESSAGE_LOCATION_TYPE_KEY);
                    String serializedJsonLocation = arguments.getString(MESSAGE_JSON_LOCATION_KEY);
                    try {
                        messageJsonLocation = AppSingleton.getJsonObjectMapper().readValue(serializedJsonLocation, JsonLocation.class);
                    } catch (Exception ignored) { }
                    messageContentBody = arguments.getString(MESSAGE_CONTENT_BODY_KEY);
                    discussionId = arguments.getLong(DISCUSSION_ID_KEY);
                    break;
                }
                case TYPE_DISCUSSION: {
                    discussionId = arguments.getLong(DISCUSSION_ID_KEY);
                    break;
                }
                case TYPE_OWNED_IDENTITY: {
                    bytesOwnedIdentity = arguments.getByteArray(OWNED_IDENTITY_KEY);
                    break;
                }
                default: {
                    dismiss();
                }
            }
        } else {
            dismiss();
        }
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
        View rootView = inflater.inflate(R.layout.fragment_fullscreen_map, container, false);

        mapView = MapFragmentProvider.getMapFragmentForProvider(integration);
        if (mapView == null) {
            return null;
        }

        getChildFragmentManager().beginTransaction().replace(R.id.fullscreen_map_map_view_container, mapView).commit();

        mapView.setOnMapReadyCallback(this::onMapReadyCallback);
        mapView.setRedrawMarkersCallback(this::redrawMarkersCallback);

        ImageView layersButton = rootView.findViewById(R.id.fullscreen_map_layers_button);
        mapView.setLayersButtonVisibilitySetter((Boolean visible) -> layersButton.setVisibility((visible != null && visible) ? View.VISIBLE : View.GONE));
        layersButton.setOnClickListener(mapView::onLayersButtonClicked);

        // setup fabs
        FloatingActionButton centerOnMarkersFab = rootView.findViewById(R.id.fullscreen_map_center_on_markers_fab);
        centerOnMarkersFab.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_location_center_on_markers));
        centerOnMarkersFab.setOnClickListener(this::handleCenterOnMarkersFabClick);
        centerOnMarkersFab.setVisibility(View.VISIBLE);

        openInThirdPartyAppFab = rootView.findViewById(R.id.fullscreen_map_open_in_third_party_app_fab);
        openInThirdPartyAppFab.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_open_location_in_third_party_app_48));
        openInThirdPartyAppFab.setOnClickListener(this::handleOpenInThirdPartyAppFabClick);
        openInThirdPartyAppFab.setVisibility(View.VISIBLE);

        FloatingActionButton backFab = rootView.findViewById(R.id.fullscreen_map_back_fab);
        backFab.setOnClickListener(this::handleBackFabClick);

        return rootView;
    }

    @SuppressLint("MissingPermission")
    public void onMapReadyCallback() {
        // if user already gave location access, and location is enabled show it's location on map
        mapView.setEnableCurrentLocation(isLocationPermissionGranted(this.activity) && isLocationEnabled());

        // if showing a location or a finished sharing: zoom on location, center camera and add a pointer on it
        if (type == TYPE_MESSAGE && messageLocationType != Message.LOCATION_TYPE_SHARE) {
            if (messageJsonLocation != null) {
                if (messageLocationType == Message.LOCATION_TYPE_SEND) {
                    mapView.addMarker(messageId, getPinMarkerIcon(), new LatLngWrapper(messageJsonLocation), messageJsonLocation.getPrecision());
                } else if (messageLocationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
                    mapView.addMarker(messageId, getInitialViewMarkerIcon(messageSenderIdentifier), new LatLngWrapper(messageJsonLocation), messageJsonLocation.getPrecision());
                }
            }
            mapView.centerOnMarkers(false, false);
        } else {
            // if showing sharing locations: retrieve every sharing location messages, add marker for all of them, center on it
            // observe sharing messages to update map when messages are updated
            centerOnMarkersOnNextLocationMessagesUpdate = true;
            if (type == TYPE_OWNED_IDENTITY) {
                sharingLocationMessageLiveData = AppDatabase.getInstance().messageDao().getCurrentlySharingLocationMessagesForOwnedIdentityLiveData(bytesOwnedIdentity);
            } else {
                sharingLocationMessageLiveData = AppDatabase.getInstance().messageDao().getCurrentlySharingLocationMessagesInDiscussionLiveData(discussionId);
            }
            sharingLocationMessageLiveData.observe(this, this::sharingLocationMessagesObserver);
        }
    }

    public void redrawMarkersCallback() {
        if (type == TYPE_MESSAGE && messageLocationType != Message.LOCATION_TYPE_SHARE) {
            if (messageJsonLocation != null) {
                if (messageLocationType == Message.LOCATION_TYPE_SEND) {
                    mapView.addMarker(messageId, getPinMarkerIcon(), new LatLngWrapper(messageJsonLocation), messageJsonLocation.getPrecision());
                } else if (messageLocationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
                    mapView.addMarker(messageId, getInitialViewMarkerIcon(messageSenderIdentifier), new LatLngWrapper(messageJsonLocation), messageJsonLocation.getPrecision());
                }
            }
        } else {
            sharingLocationMessageLiveData.removeObservers(this);
            currentlyShownMessagesIdList.clear();
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
            JsonLocation location = message.getJsonLocation();
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
            FullscreenMapBottomSheetDialog bottomFragment;
            if (type == TYPE_OWNED_IDENTITY) {
                bottomFragment = FullscreenMapBottomSheetDialog.newInstance(null, bytesOwnedIdentity, this);
            } else {
                bottomFragment = FullscreenMapBottomSheetDialog.newInstance(discussionId, null, this);
            }
            if (bottomFragment != null) {
                bottomFragment.show(activity.getSupportFragmentManager(), "fullscreen-map-bottom-sheet");
            }
        } else if (messageId != null) {
            App.openLocationInMapApplication(activity, messageJsonLocation.getTruncatedLatitudeString(), messageJsonLocation.getTruncatedLongitudeString(), messageContentBody, null);
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
