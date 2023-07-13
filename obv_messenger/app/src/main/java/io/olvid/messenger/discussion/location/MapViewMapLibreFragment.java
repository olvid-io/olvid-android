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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.location.LocationManagerCompat;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.maps.SupportMapFragment;
import com.mapbox.mapboxsdk.plugins.annotation.Symbol;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.HandlerExecutor;
import io.olvid.messenger.settings.SettingsActivity;

public class MapViewMapLibreFragment extends MapViewAbstractFragment implements OnMapReadyCallback {

    private static final String DEFAULT_OSM_SERVER_URL = "https://map.olvid.io";
    private static final String OSM_STYLE_NAME = "planet";
    private static final double DEFAULT_ZOOM = 15;
    private static final int TRANSITION_DURATION_MS = 500;

    @Nullable private Runnable onMapReadyCallback = null;
    private FragmentActivity activity;

    private SupportMapFragment mapFragment;
    @Nullable private MapboxMap mapboxMap;

    // symbols and markers
    private @Nullable SymbolManager symbolManager;
    private final HashMap<Long, Symbol> symbolsByIdHashMap = new HashMap<>();

    // current camera center live data (set to null if camera is moving)
    private final MutableLiveData<LatLngWrapper> currentCameraCenterLiveData = new MutableLiveData<>();

    // determine if we need to use fallback style or not (when using localized styles)
    private boolean triedStyleFallbackUrl = false;

    // store previously centered marker to unset Zindex
    private Symbol currentlyCenteredSymbol = null;
    private String osmServerUrl = DEFAULT_OSM_SERVER_URL;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get current activity
        this.activity = requireActivity();

        // init MapView
        Mapbox.getInstance(this.activity);

        // prepare map fragment
        MapboxMapOptions options = MapboxMapOptions.createFromAttributes(this.activity);
        mapFragment = SupportMapFragment.newInstance(options);
        mapFragment.getMapAsync(this);

        String osmServer = AppSingleton.getEngine().getOsmServerUrl(AppSingleton.getBytesCurrentIdentity());
        if (osmServer != null) {
            this.osmServerUrl = osmServer;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map_view_map_libre, container, false);

        this.getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.map_view_container, mapFragment)
                .commit();

        return rootView;
    }


    private String getStyleUrl() {
        String language = SettingsActivity.getLocationOpenStreetMapLanguage();
        if (language.equals("fr") || language.equals("en")) {
            return String.format("%s/styles/%s-%s/style.json", osmServerUrl, OSM_STYLE_NAME, language);
        }
        triedStyleFallbackUrl = true;
        return String.format("%s/styles/%s/style.json", osmServerUrl, OSM_STYLE_NAME);
    }
    private String getFallbackStyleUrl() {
        triedStyleFallbackUrl = true;
        return String.format("%s/styles/%s/style.json", osmServerUrl, OSM_STYLE_NAME);
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;

        mapboxMap.setStyle(new Style.Builder().fromUri(getStyleUrl()), this::onStyleLoaded);

        if (mapFragment.getView() != null && mapFragment.getView() instanceof MapView) {
            // if style loading fail
            ((MapView) mapFragment.getView()).addOnDidFailLoadingMapListener((errorMessage) -> {
                Logger.d("Language style not found, trying fallback style");
                if (!triedStyleFallbackUrl) {
                    mapboxMap.setStyle(new Style.Builder().fromUri(getFallbackStyleUrl()), this::onStyleLoaded);
                }
            });
        }
    }

    public void onStyleLoaded(Style style) {
        if (style == null || mapboxMap == null) {
            Logger.i("MapLibre.onStyleLoaded: map not initialized or style is null");
            return;
        }

        // setup ui
        mapboxMap.getUiSettings().setCompassEnabled(true);
        int twelveDp = (int) (12 * activity.getResources().getDisplayMetrics().density);
        mapboxMap.getUiSettings().setCompassMargins(0, twelveDp * 2, twelveDp, 0);
        mapboxMap.getUiSettings().setCompassGravity(Gravity.TOP | Gravity.END);

        mapboxMap.getUiSettings().setLogoEnabled(false);

        // show and change attributions
        mapboxMap.getUiSettings().setAttributionEnabled(true);
        mapboxMap.getUiSettings().setAttributionGravity(Gravity.BOTTOM | Gravity.START);
        mapboxMap.getUiSettings().setAttributionMargins(twelveDp, twelveDp, twelveDp, twelveDp);
        mapboxMap.getUiSettings().setAttributionDialogManager(new MapLibreCustomAttributionDialogManager(this.activity, mapboxMap));

        // create symbol manager and set options
        if (mapFragment.getView() != null) {
            symbolManager = new SymbolManager((MapView) mapFragment.getView(), mapboxMap, style);
            symbolManager.setIconAllowOverlap(true);
            symbolManager.setIconIgnorePlacement(false);

            symbolManager.addClickListener((symbol) -> {
                // center on all markers if clicking on same marker when we have multiple markers (to continue following marker if there is only one)
                if (currentlyCenteredSymbol == symbol && symbolsByIdHashMap.size() > 1) {
                    centerOnMarkers(true, true);
                } else {
                    centerOnSymbol(symbol, true);
                }
                return true;
            });
        }
        else {
            Logger.w("Symbol manager cannot be created !");
        }

        // setup listeners for map gestures
        mapboxMap.addOnCameraMoveStartedListener((reason) -> {
            currentCameraCenterLiveData.postValue(null);

            if (reason == MapboxMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                setCurrentlyCenteredSymbol(null);
            }
        });
        mapboxMap.addOnCameraIdleListener(() -> currentCameraCenterLiveData.postValue(new LatLngWrapper(mapboxMap.getCameraPosition().target)));
        mapboxMap.addOnCameraMoveCancelListener(() -> currentCameraCenterLiveData.postValue(new LatLngWrapper(mapboxMap.getCameraPosition().target)));

        // call parent callback if set
        if (onMapReadyCallback != null) {
            onMapReadyCallback.run();
        }
    }

    // Enable location tracking if possible, it check:
    // mapbox is ready (and style too)
    // location permission and enabled (show pop up if not)
    // location component activated and enabled (activate and enable if not)
    // enable tracking
    @SuppressLint("MissingPermission")
    @Override
    public boolean setEnableCurrentLocation(boolean enabled) {
        // check map and style was initialized
        if (mapboxMap == null || mapboxMap.getStyle() == null) {
            Logger.i("MapLibre.centerOnCurrentLocation: map not initialized");
            return false;
        }

        // check permission and location is enabled (do not ask, father is supposed to do)
        if (!AbstractLocationDialogFragment.isLocationPermissionGranted(activity)) {
            return false;
        }
        if (!AbstractLocationDialogFragment.isLocationEnabled()) {
            return false;
        }

        // activate location component if needed
        if (!mapboxMap.getLocationComponent().isLocationComponentActivated()) {
            if (mapboxMap.getStyle() == null) {
                Logger.i("MapLibre.centerOnCurrentLocation: subscribeToLocationUpdates: mapboxMap.getStyle is null");
                return false;
            }
            mapboxMap.getLocationComponent().activateLocationComponent(LocationComponentActivationOptions.builder(activity, mapboxMap.getStyle()).build());

            // on enable add listener on location icon to recenter on it
            mapboxMap.getLocationComponent().addOnLocationClickListener(() -> centerOnCurrentLocation(true));
        }
        // enable location component if needed
        if (mapboxMap.getLocationComponent().isLocationComponentEnabled() != enabled) {
            try {
                mapboxMap.getLocationComponent().setLocationComponentEnabled(enabled);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void centerOnCurrentLocation(boolean animate) {
        if (!setEnableCurrentLocation(true) || mapboxMap == null) {
            return;
        }

        // if lastKnownLocation is accessible use it to center
        Location lastKnownLocation = mapboxMap.getLocationComponent().getLastKnownLocation();
        if (mapboxMap.getLocationComponent().isLocationComponentEnabled() &&  lastKnownLocation != null) {
            centerOnLocation(lastKnownLocation, animate);
            mapboxMap.getLocationComponent().forceLocationUpdate(lastKnownLocation);
        } else {
            // else try to get best provider last location, and else request current location (can be quite long)
            LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
            String provider = locationManager.getBestProvider(new Criteria(), true);
            if (provider != null) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location!= null) {
                    centerOnLocation(location, animate);
                    mapboxMap.getLocationComponent().forceLocationUpdate(mapboxMap.getLocationComponent().getLastKnownLocation());
                    return;
                }
            }

            Executor executor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? App.getContext().getMainExecutor() : new HandlerExecutor(Looper.getMainLooper());
            if (provider != null) {
                LocationManagerCompat.getCurrentLocation(locationManager, provider, null, executor, location -> {
                    if (location != null) {
                        centerOnLocation(location, animate);
                        mapboxMap.getLocationComponent().forceLocationUpdate(location);
                    }
                });
            }
        }
    }

    private void centerOnLocation(@NonNull Location location, boolean animate) {
        if (mapboxMap != null) {
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    Double.max(DEFAULT_ZOOM, mapboxMap.getCameraPosition().zoom));
            if (animate) {
                mapboxMap.animateCamera(cameraUpdate, 750);
            }
            else {
                mapboxMap.moveCamera(cameraUpdate);
            }
        }
    }

    @Override
    public @NonNull MutableLiveData<LatLngWrapper> getCurrentCameraCenterLiveData() {
        return currentCameraCenterLiveData;
    }

    @Override
    public double getCameraZoom() {
        if (mapboxMap == null) {
            return 0;
        }
        return mapboxMap.getCameraPosition().zoom;
    }

    @Override
    public void launchMapSnapshot(@NonNull Consumer<Bitmap> onSnapshotReadyCallback) {
        if (mapboxMap == null) {
            Logger.i("MapLibre.launchMapSnapshot: map not ready when taking snapshot");
            onSnapshotReadyCallback.accept(null);
            return;
        }
        // hide attributions icon7
        mapboxMap.getUiSettings().setAttributionEnabled(false);
        mapboxMap.getUiSettings().setCompassEnabled(false);

        // manually add attributions to bitmap before passing to parent
        mapboxMap.snapshot((bitmap) -> {
            String attributionString = "©OpenStreetMap ©OpenMapTiles ©Olvid";
            float padding = 4 * activity.getResources().getDisplayMetrics().density;
            float textSize = 12 * activity.getResources().getDisplayMetrics().density;

            // prepare bitmap and canvas
            Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            Canvas canvas = new Canvas(result);
            canvas.drawBitmap(bitmap, 0, 0, null);

            // prepare text
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(textSize);
            textPaint.setAntiAlias(true);
            textPaint.setAlpha(255);

            // determine text dimensions to draw background
            Rect textBounds = new Rect();
            textPaint.getTextBounds(attributionString, 0, attributionString.length(), textBounds);

            // rectangle paints
            Paint rectanglePaint = new Paint();
            rectanglePaint.setStyle(Paint.Style.FILL);
            rectanglePaint.setColor(Color.WHITE);
            rectanglePaint.setAntiAlias(true);

            // draw a rounded rectangle as a text background
            RectF rectF = new RectF(
                    0, // left
                    (int) (bitmap.getHeight() - textBounds.height() - padding), // top
                    (int) (textBounds.width() + 2*padding),// right
                    bitmap.getHeight() // bottom
            );
            // make corner squares for left and bottom right corners
            Rect leftCorners = new Rect(
                    0, // left
                    (int) (bitmap.getHeight() - textBounds.height() - padding), // top
                    (int) (textBounds.width() + 2*padding) / 2,// right
                    bitmap.getHeight() // bottom
            );
            Rect bottomRightCorner = new Rect(
                    (int) (textBounds.width() + 2*padding) / 2, // left
                    (int) (bitmap.getHeight() - textBounds.height() - padding) + textBounds.height() / 2, // top
                    (int) (textBounds.width() + 2*padding),// right
                    bitmap.getHeight() // bottom
            );

            // draw background rectangles and then text
            canvas.drawRect(leftCorners, rectanglePaint);
            canvas.drawRect(bottomRightCorner, rectanglePaint);
            canvas.drawRoundRect(rectF, 10, 10, rectanglePaint);
            canvas.drawText(attributionString, padding, bitmap.getHeight() - padding, textPaint);

            // pass result bitmap to parent
            onSnapshotReadyCallback.accept(result);
        });
    }
    @Override
    public void setGestureEnabled(boolean enabled) {
        if (mapboxMap == null || mapboxMap.getStyle() == null) {
            Logger.i("MapLibreMapView: setGestureEnabled: mapboxMap is not ready to use");
            return;
        }
        mapboxMap.getUiSettings().setAllGesturesEnabled(enabled);
    }

    @Override
    public void setOnMapClickListener(Runnable clickListener) {
        if (mapboxMap == null || mapboxMap.getStyle() == null) {
            Logger.i("MapLibreMapView: setOnMapClickListener: mapboxMap is not ready to use");
            return;
        }
        mapboxMap.addOnMapClickListener((latLng) -> {
            clickListener.run();
            return false;
        });
    }

    @Override
    public void setOnMapLongClickListener(Runnable clickListener) {
        if (mapboxMap == null || mapboxMap.getStyle() == null) {
            Logger.i("MapLibreMapView: setOnMapLongClickListener: mapboxMap is not ready to use");
            return;
        }
        mapboxMap.addOnMapLongClickListener((latLng) -> {
            clickListener.run();
            return false;
        });
    }

    @Override
    public void setOnMapReadyCallback(@Nullable Runnable onMapReadyCallback) {
        this.onMapReadyCallback = onMapReadyCallback;
    }

    @Override
    public void addMarker(long id, Bitmap icon, @NonNull LatLngWrapper latLngWrapper, @Nullable Float precision) {
        if (mapboxMap == null || mapboxMap.getStyle() == null || symbolManager == null) {
            Logger.i("MapLibreMapView: addMarker: mapboxMap is not ready to use");
            return;
        }

        if (symbolsByIdHashMap.containsKey(id)) {
            Logger.d("MapLibreMapView: addMarker: adding a symbol for an existing id !");
            removeMarker(id);
        }

        mapboxMap.getStyle().addImage("marker-icon-" + id, icon);

        Symbol symbol = symbolManager.create(new SymbolOptions()
                .withIconImage("marker-icon-" + id)
                .withLatLng(latLngWrapper.toMapLibre())
                .withSymbolSortKey(0F));
        symbolsByIdHashMap.put(id, symbol);
    }

    @Override
    public void updateMarker(long id, @NonNull LatLngWrapper latLngWrapper, @Nullable Float precision) {
        if (symbolManager == null) {
            Logger.i("MapViewMapLibreFragment: called updateInitialViewMarker before map was initialized");
            return;
        }

        Symbol symbol = symbolsByIdHashMap.get(id);
        if (symbol != null) {
            symbol.setLatLng(latLngWrapper.toMapLibre());
            symbolManager.update(symbol);
            if (symbol.equals(currentlyCenteredSymbol)) {
                centerOnMarker(id, true);
            }
        }
    }

    @Override
    public void removeMarker(long id) {
        if (symbolManager == null) {
            Logger.i("MapViewMapLibreFragment: called removeMarker before map was initialized");
            return;
        }

        Symbol symbol = symbolsByIdHashMap.get(id);
        if (symbol != null) {
            // remove and delete symbol
            symbolManager.delete(symbol);
            symbolsByIdHashMap.remove(id);
            // clean created images
            if (mapboxMap != null && mapboxMap.getStyle() != null) {
                mapboxMap.getStyle().removeImage("marker-icon" + id);
            }
        }
    }

    @Override
    public void centerOnMarkers(boolean animate, boolean includeMyLocation) {
        if (mapboxMap == null) {
            return;
        }

        List<LatLngWrapper> markersPositions = new ArrayList<>();
        for (Symbol symbol : symbolsByIdHashMap.values()) {
            markersPositions.add(new LatLngWrapper(symbol.getLatLng()));
        }
        // if current location is enabled add it in bounds
        if (includeMyLocation && mapboxMap.getLocationComponent().isLocationComponentActivated()
                && mapboxMap.getLocationComponent().isLocationComponentEnabled()
                && mapboxMap.getLocationComponent().getLastKnownLocation() != null) {
            markersPositions.add(new LatLngWrapper(mapboxMap.getLocationComponent().getLastKnownLocation()));
        }

        MapboxMap.CancelableCallback cancelableCallback = new MapboxMap.CancelableCallback() {
            @Override
            public void onCancel() { mapboxMap.getUiSettings().setAllGesturesEnabled(true); }
            @Override
            public void onFinish() { mapboxMap.getUiSettings().setAllGesturesEnabled(true); }
        };
        Pair<LatLngWrapper, LatLngWrapper> bounds = computeBounds(markersPositions);
        if (bounds == null) {
            // if no symbols: center on 0 0 0
            mapboxMap.getUiSettings().setAllGesturesEnabled(false);
            mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.world(), 0), TRANSITION_DURATION_MS, cancelableCallback);
        } else if (bounds.second == null) {
            // else center on single symbol
            double zoom = Math.max(DEFAULT_ZOOM, mapboxMap.getCameraPosition().zoom);
            if (animate) {
                mapboxMap.getUiSettings().setAllGesturesEnabled(false);
                mapboxMap.easeCamera(CameraUpdateFactory.newLatLngZoom(bounds.first.toMapLibre(), zoom), TRANSITION_DURATION_MS, cancelableCallback);
            } else {
                mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.first.toMapLibre(), zoom));
            }
        } else {
            // arbitrary computing InitialView markers heights, and using it to compute padding :S
            int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
            if (animate) {
                mapboxMap.getUiSettings().setAllGesturesEnabled(false);
                mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bounds.second.getLatitude(), bounds.second.getLongitude(), bounds.first.getLatitude(), bounds.first.getLongitude()), padding), TRANSITION_DURATION_MS, cancelableCallback);
            }
            else {
                mapboxMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bounds.second.getLatitude(), bounds.second.getLongitude(), bounds.first.getLatitude(), bounds.first.getLongitude()), padding));
            }
        }

        setCurrentlyCenteredSymbol(null);
    }

    @Override
    public void centerOnMarker(long id, boolean animate) {
        centerOnSymbol(symbolsByIdHashMap.get(id), animate);
    }

    private void centerOnSymbol(Symbol symbol, boolean animate) {
        if (symbolManager == null || mapboxMap == null) {
            Logger.i("MapViewMapLibreFragment: called centerOnSymbol before map was initialized");
            return;
        }

        if (symbol != null) {
            setCurrentlyCenteredSymbol(symbol);

            // move camera to marker
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(symbol.getLatLng(), Math.max(DEFAULT_ZOOM, mapboxMap.getCameraPosition().zoom));
            if (animate) {
                mapboxMap.easeCamera(cameraUpdate, TRANSITION_DURATION_MS);
            }
            else {
                mapboxMap.moveCamera(cameraUpdate);
            }
        }
    }

    private void setCurrentlyCenteredSymbol(@Nullable Symbol symbol) {
        if (symbolManager == null || mapboxMap == null) {
            Logger.i("MapViewMapLibreFragment: called setCurrentlyCenteredSymbol before map was initialized");
            return;
        }

        // unset previously centered marker
        if (currentlyCenteredSymbol != null) {
            currentlyCenteredSymbol.setSymbolSortKey(0F);
            symbolManager.update(currentlyCenteredSymbol);
        }

        // set new marker as centered
        if (symbol != null) {
            symbol.setSymbolSortKey(10F);
            symbolManager.update(symbol);
        }

        currentlyCenteredSymbol = symbol;
    }
}
