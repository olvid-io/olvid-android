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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;

import org.maplibre.geojson.MultiPolygon;
import org.maplibre.geojson.Point;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdate;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.maps.SupportMapFragment;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyValue;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.JsonOsmStyle;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LocationShareQuality;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;

public class MapViewMapLibreFragment extends MapViewAbstractFragment implements OnMapReadyCallback {

    private static final String FALLBACK_OSM_STYLE_URL = "https://map.olvid.io/styles/osm.json";
    public static final String OSM_STYLE_LANGUAGE_PLACEHOLDER = "[LANG]";
    private static final double DEFAULT_ZOOM = 17;
    private static final int TRANSITION_DURATION_MS = 500;

    @Nullable private Runnable onMapReadyCallback = null;
    @Nullable private Runnable redrawMarkersCallback = null;
    @Nullable private Consumer<Boolean> layersButtonVisibilitySetter = null;
    private FragmentActivity activity;

    private SupportMapFragment mapFragment;
    @Nullable private MapLibreMap mapLibreMap;

    // symbols and markers
    private @Nullable SymbolManager symbolManager;
    private final HashMap<Long, Symbol> symbolsByIdHashMap = new HashMap<>();
    private final HashMap<Long, List<Point>> precisionCirclesHashMap = new HashMap<>();

    // current camera center live data (set to null if camera is moving)
    private final MutableLiveData<LatLngWrapper> currentCameraCenterLiveData = new MutableLiveData<>();

    // determine if we need to use fallback style or not (when using localized styles)
    private boolean triedStyleFallbackUrl = false;
    private boolean firstStyleLoaded = false;

    // store previously centered marker to unset Zindex
    private Symbol currentlyCenteredSymbol = null;

    @NonNull
    private Map<String, JsonOsmStyle> osmServerStyles = Collections.emptyMap();
    private String currentStyleId = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get current activity
        this.activity = requireActivity();

        loadStyleUrls();

        // init MapView
        MapLibre.getInstance(this.activity);

        // prepare map fragment
        MapLibreMapOptions options = MapLibreMapOptions.createFromAttributes(this.activity);
        mapFragment = SupportMapFragment.newInstance(options);
        mapFragment.getMapAsync(this);
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


    private void loadStyleUrls() {
        if (SettingsActivity.getLocationIntegration() == SettingsActivity.LocationIntegrationEnum.OSM) {
            List<JsonOsmStyle> osmStyles = AppSingleton.getEngine().getOsmStyles(AppSingleton.getBytesCurrentIdentity());
            if (osmStyles == null || osmStyles.isEmpty()) {
                loadFallbackStyleUrl();
            } else {
                currentStyleId = SettingsActivity.getLocationLastOsmStyleId();
                osmServerStyles = new LinkedHashMap<>();
                for (JsonOsmStyle osmStyle : osmStyles) {
                    if (osmStyle.id != null && osmStyle.url != null && osmStyle.name != null) {
                        osmServerStyles.put(osmStyle.id, osmStyle);
                    }
                }
            }
        } else if (SettingsActivity.getLocationIntegration() == SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM) {
            currentStyleId = "custom";
            osmServerStyles = Collections.singletonMap("custom", new JsonOsmStyle("custom", SettingsActivity.getLocationCustomOsmServerUrl()));
        } else {
            loadFallbackStyleUrl();
        }
    }

    private void loadFallbackStyleUrl() {
        triedStyleFallbackUrl = true;
        currentStyleId = "fallback";
        osmServerStyles = Collections.singletonMap("fallback", new JsonOsmStyle("fallback", FALLBACK_OSM_STYLE_URL));
    }

    @NonNull
    private String getStyleUrl() {
        if (currentStyleId != null) {
            JsonOsmStyle osmStyle = osmServerStyles.get(currentStyleId);
            if (osmStyle != null) {
                return replaceLanguageInStyleUrl(osmStyle);
            }
        }
        for (Map.Entry<String, JsonOsmStyle> osmStyleEntry : osmServerStyles.entrySet()) {
            currentStyleId = osmStyleEntry.getKey();
            return replaceLanguageInStyleUrl(osmStyleEntry.getValue());
        }
        return "";
    }

    @NonNull
    private String replaceLanguageInStyleUrl(@NonNull JsonOsmStyle osmStyle) {
        String language = activity.getString(R.string.language_short_string);
        if (osmStyle.name.containsKey(language)) {
            return osmStyle.url.replace(OSM_STYLE_LANGUAGE_PLACEHOLDER, "_" + language);
        }
        return osmStyle.url.replace(OSM_STYLE_LANGUAGE_PLACEHOLDER, "_en");
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        this.mapLibreMap = mapLibreMap;

        View mapView = mapFragment.getView();
        if (mapView instanceof MapView) {
            // if style loading fail
            ((MapView) mapView).addOnDidFailLoadingMapListener((errorMessage) -> {
                Logger.w("OSM style not found, trying fallback style");
                if (!triedStyleFallbackUrl) {
                    triedStyleFallbackUrl = true;
                    mapLibreMap.setStyle(new Style.Builder().fromUri(FALLBACK_OSM_STYLE_URL), this::onStyleLoaded);
                }
            });
        }

        // set style, with a callback for the loading of the first style
        mapLibreMap.setStyle(new Style.Builder().fromUri(getStyleUrl()), this::onStyleLoaded);
    }

    public void onStyleLoaded(Style style) {
        if (style == null || mapLibreMap == null) {
            Logger.i("MapLibre.onStyleLoaded: map not initialized or style is null");
            return;
        }

        // reset this to allow reloading it when the user selects another not found style
        triedStyleFallbackUrl = false;

        // setup ui
        mapLibreMap.getUiSettings().setCompassEnabled(true);
        Drawable compass = ContextCompat.getDrawable(activity, R.drawable.map_compass);
        if (compass != null) {
            mapLibreMap.getUiSettings().setCompassImage(compass);
        }
        int sixteenDp = (int) (16 * activity.getResources().getDisplayMetrics().density);
        mapLibreMap.getUiSettings().setCompassMargins(0, (osmServerStyles.size() > 1) ? sixteenDp * 4 : sixteenDp, sixteenDp, 0);
        mapLibreMap.getUiSettings().setCompassGravity(Gravity.TOP | Gravity.END);
        mapLibreMap.getUiSettings().setLogoEnabled(false);

        if (layersButtonVisibilitySetter != null) {
            layersButtonVisibilitySetter.accept(osmServerStyles.size() > 1);
        }

        // show and change attributions
        mapLibreMap.getUiSettings().setAttributionEnabled(true);
        mapLibreMap.getUiSettings().setAttributionGravity(Gravity.BOTTOM | Gravity.START);
        mapLibreMap.getUiSettings().setAttributionMargins(sixteenDp, sixteenDp, sixteenDp, sixteenDp);
        mapLibreMap.getUiSettings().setAttributionDialogManager(new MapLibreCustomAttributionDialogManager(this.activity, mapLibreMap));


        // create symbol manager and set options
        if (mapFragment.getView() != null) {
            symbolManager = new SymbolManager((MapView) mapFragment.getView(), mapLibreMap, style);
            symbolManager.setIconAllowOverlap(true);
            symbolManager.setIconIgnorePlacement(true);

            symbolManager.addClickListener((symbol) -> {
                // center on all markers if clicking on same marker when we have multiple markers (to continue following marker if there is only one)
                if (currentlyCenteredSymbol == symbol && symbolsByIdHashMap.size() > 1) {
                    centerOnMarkers(true, true);
                } else {
                    centerOnSymbol(symbol, true);
                }
                return true;
            });
        } else {
            Logger.w("Symbol manager cannot be created !");
        }

        // in case a circle was already added, recompute the precision circles layer
        recomputePrecisionCirclesLayer();


        // call parent callback if set
        if (onMapReadyCallback != null) {
            onMapReadyCallback.run();
            onMapReadyCallback = null;
        } else if (redrawMarkersCallback != null) {
            redrawMarkersCallback.run();
        }

        if (!firstStyleLoaded) {
            firstStyleLoaded = true;

            // setup listeners for map gestures
            mapLibreMap.addOnCameraMoveStartedListener((reason) -> {
                currentCameraCenterLiveData.postValue(null);

                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    currentlyCenteredOnGpsPosition.postValue(false);
                    setCurrentlyCenteredSymbol(null);
                }
            });
            //noinspection DataFlowIssue
            mapLibreMap.addOnCameraIdleListener(() -> currentCameraCenterLiveData.postValue(new LatLngWrapper(mapLibreMap.getCameraPosition().target)));
            //noinspection DataFlowIssue
            mapLibreMap.addOnCameraMoveCancelListener(() -> currentCameraCenterLiveData.postValue(new LatLngWrapper(mapLibreMap.getCameraPosition().target)));
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
        if (mapLibreMap == null || mapLibreMap.getStyle() == null) {
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
        if (!mapLibreMap.getLocationComponent().isLocationComponentActivated()) {
            if (mapLibreMap.getStyle() == null) {
                Logger.i("MapLibre.centerOnCurrentLocation: subscribeToLocationUpdates: MapLibreMap.getStyle is null");
                return false;
            }
            mapLibreMap.getLocationComponent().activateLocationComponent(LocationComponentActivationOptions.builder(activity, mapLibreMap.getStyle()).build());

            // on enable add listener on location icon to recenter on it
            mapLibreMap.getLocationComponent().addOnLocationClickListener(() -> centerOnCurrentLocation(true));
        }
        // enable location component if needed
        if (mapLibreMap.getLocationComponent().isLocationComponentEnabled() != enabled) {
            try {
                mapLibreMap.getLocationComponent().setLocationComponentEnabled(enabled);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void centerOnCurrentLocation(boolean animate) {
        if (!setEnableCurrentLocation(true) || mapLibreMap == null) {
            return;
        }

        currentlyCenteredOnGpsPosition.postValue(true);
        setCurrentlyCenteredSymbol(null);

        // if lastKnownLocation is accessible use it to center
        Location lastKnownLocation = mapLibreMap.getLocationComponent().getLastKnownLocation();
        if (mapLibreMap.getLocationComponent().isLocationComponentEnabled() && lastKnownLocation != null) {
            lastLocation = lastKnownLocation;
            lastLocationUpdate = System.currentTimeMillis();
            centerOnLocation(lastKnownLocation, animate);
            mapLibreMap.getLocationComponent().forceLocationUpdate(lastKnownLocation);
        }
    }

    private void centerOnLocation(@NonNull Location location, boolean animate) {
        if (mapLibreMap != null) {
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    Double.max(DEFAULT_ZOOM, mapLibreMap.getCameraPosition().zoom));
            if (animate) {
                mapLibreMap.animateCamera(cameraUpdate, TRANSITION_DURATION_MS);
            } else {
                mapLibreMap.moveCamera(cameraUpdate);
            }
        }
    }

    @Override
    public @NonNull MutableLiveData<LatLngWrapper> getCurrentCameraCenterLiveData() {
        return currentCameraCenterLiveData;
    }

    @Override
    public double getCameraZoom() {
        if (mapLibreMap == null) {
            return 0;
        }
        return mapLibreMap.getCameraPosition().zoom;
    }

    @Override
    public void launchMapSnapshot(@NonNull Consumer<Bitmap> onSnapshotReadyCallback) {
        if (mapLibreMap == null) {
            Logger.i("MapLibre.launchMapSnapshot: map not ready when taking snapshot");
            onSnapshotReadyCallback.accept(null);
            return;
        }
        // hide attributions icon7
        mapLibreMap.getUiSettings().setAttributionEnabled(false);
        mapLibreMap.getUiSettings().setCompassEnabled(false);

        // manually add attributions to bitmap before passing to parent
        mapLibreMap.snapshot((bitmap) -> {
            String attributionString = "©OpenStreetMap ©OpenMapTiles ©Olvid";
            float padding = 4 * activity.getResources().getDisplayMetrics().density;
            float textSize = 12 * activity.getResources().getDisplayMetrics().density;

            if (bitmap.getConfig() == null) {
                return;
            }

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
    void setLayersButtonVisibilitySetter(Consumer<Boolean> layersButtonVisibilitySetter) {
        this.layersButtonVisibilitySetter = layersButtonVisibilitySetter;
        if (currentStyleId != null) {
            layersButtonVisibilitySetter.accept(osmServerStyles.size() > 1);
        }
    }

    @Override
    void onLayersButtonClicked(View view) {
        if (osmServerStyles.size() <= 1) {
            return;
        }
        ArrayList<String> menuStyleIds = new ArrayList<>(osmServerStyles.size());
        PopupMenu popup = new PopupMenu(activity, view, Gravity.TOP | Gravity.END);
        Menu menu = popup.getMenu();
        int index = 0;
        String lang = getString(R.string.language_short_string);
        for (JsonOsmStyle osmStyle : osmServerStyles.values()) {
            menuStyleIds.add(osmStyle.id);
            CharSequence text;
            if (osmStyle.name.containsKey(lang)) {
                text = osmStyle.name.get(lang);
            } else {
                text = osmStyle.name.get("en");
            }
            MenuItem menuItem = menu.add(0, index, index, text);
            if (Objects.equals(currentStyleId, osmStyle.id)) {
                menuItem.setChecked(true);
            }
            index++;
        }
        menu.setGroupCheckable(0, true, true);
        popup.setOnMenuItemClickListener(item -> {
            if (!Objects.equals(currentStyleId, menuStyleIds.get(item.getItemId()))) {
                currentStyleId = menuStyleIds.get(item.getItemId());
                SettingsActivity.setLocationLastOsmStyleId(currentStyleId);
                if (mapLibreMap != null) {
                    removeAllMarkers();
                    mapLibreMap.setStyle(new Style.Builder().fromUri(getStyleUrl()), this::onStyleLoaded);
                }
            }
            return true;
        });
        popup.show();
    }

    //    @Override
//    public void setGestureEnabled(boolean enabled) {
//        if (mapboxMap == null || MapLibreMap.getStyle() == null) {
//            Logger.i("MapLibreMapView: setGestureEnabled: mapboxMap is not ready to use");
//            return;
//        }
//        MapLibreMap.getUiSettings().setAllGesturesEnabled(enabled);
//    }
//
//    @Override
//    public void setOnMapClickListener(Runnable clickListener) {
//        if (mapboxMap == null || MapLibreMap.getStyle() == null) {
//            Logger.i("MapLibreMapView: setOnMapClickListener: mapboxMap is not ready to use");
//            return;
//        }
//        MapLibreMap.addOnMapClickListener((latLng) -> {
//            clickListener.run();
//            return false;
//        });
//    }
//
//    @Override
//    public void setOnMapLongClickListener(Runnable clickListener) {
//        if (mapboxMap == null || MapLibreMap.getStyle() == null) {
//            Logger.i("MapLibreMapView: setOnMapLongClickListener: mapboxMap is not ready to use");
//            return;
//        }
//        MapLibreMap.addOnMapLongClickListener((latLng) -> {
//            clickListener.run();
//            return false;
//        });
//    }

    @Override
    public void setOnMapReadyCallback(@Nullable Runnable onMapReadyCallback) {
        this.onMapReadyCallback = onMapReadyCallback;
    }

    @Override
    void setRedrawMarkersCallback(@Nullable Runnable callback) {
        this.redrawMarkersCallback = callback;
    }

    @Override
    public void addMarker(long id, Bitmap icon, @NonNull LatLngWrapper latLngWrapper, @Nullable Float precision) {
        if (mapLibreMap == null || mapLibreMap.getStyle() == null || symbolManager == null) {
            Logger.i("MapLibreMapView: addMarker: maplibreMap is not ready to use");
            return;
        }

        if (symbolsByIdHashMap.containsKey(id)) {
            Logger.d("MapLibreMapView: addMarker: adding a symbol for an existing id !");
            removeMarker(id);
        }

        mapLibreMap.getStyle().addImage("marker-icon-" + id, icon);

        Symbol symbol = symbolManager.create(new SymbolOptions()
                .withIconImage("marker-icon-" + id)
                .withLatLng(latLngWrapper.toMapLibre())
                .withSymbolSortKey(0F));
        symbolsByIdHashMap.put(id, symbol);

        if (precision != null) {
            precisionCirclesHashMap.put(id, computePrecisionCirclePolyline(latLngWrapper.toMapLibre(), precision));
            recomputePrecisionCirclesLayer();
        } else if (precisionCirclesHashMap.remove(id) != null) {
            recomputePrecisionCirclesLayer();
        }
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

        if (precision != null) {
            precisionCirclesHashMap.put(id, computePrecisionCirclePolyline(latLngWrapper.toMapLibre(), precision));
            recomputePrecisionCirclesLayer();
        } else if (precisionCirclesHashMap.remove(id) != null) {
            recomputePrecisionCirclesLayer();
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
            if (mapLibreMap != null && mapLibreMap.getStyle() != null) {
                mapLibreMap.getStyle().removeImage("marker-icon" + id);
            }
        }
        if (precisionCirclesHashMap.remove(id) != null) {
            recomputePrecisionCirclesLayer();
        }
    }

    private void removeAllMarkers() {
        if (symbolManager == null) {
            Logger.i("MapViewMapLibreFragment: called removeAllMarkers before map was initialized");
            return;
        }

        for (Map.Entry<Long, Symbol> entry : symbolsByIdHashMap.entrySet()) {
            symbolManager.delete(entry.getValue());
            if (mapLibreMap != null && mapLibreMap.getStyle() != null) {
                mapLibreMap.getStyle().removeImage("marker-icon" + entry.getKey());
            }
        }
        symbolsByIdHashMap.clear();
        precisionCirclesHashMap.clear();
        recomputePrecisionCirclesLayer();
    }

    @Override
    public void centerOnMarkers(boolean animate, boolean includeMyLocation) {
        if (mapLibreMap == null) {
            return;
        }

        List<LatLngWrapper> markersPositions = new ArrayList<>();
        for (Symbol symbol : symbolsByIdHashMap.values()) {
            markersPositions.add(new LatLngWrapper(symbol.getLatLng()));
        }
        // if current location is enabled add it in bounds
        if (includeMyLocation && mapLibreMap.getLocationComponent().isLocationComponentActivated()
                && mapLibreMap.getLocationComponent().isLocationComponentEnabled()
                && mapLibreMap.getLocationComponent().getLastKnownLocation() != null) {
            markersPositions.add(new LatLngWrapper(mapLibreMap.getLocationComponent().getLastKnownLocation()));
        }

        MapLibreMap.CancelableCallback cancelableCallback = new MapLibreMap.CancelableCallback() {
            @Override
            public void onCancel() { mapLibreMap.getUiSettings().setAllGesturesEnabled(true); }
            @Override
            public void onFinish() { mapLibreMap.getUiSettings().setAllGesturesEnabled(true); }
        };
        Pair<LatLngWrapper, LatLngWrapper> bounds = computeBounds(markersPositions);
        if (bounds == null) {
            // if no symbols: center on 0 0 0
            mapLibreMap.getUiSettings().setAllGesturesEnabled(false);
            mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.world(), 0), TRANSITION_DURATION_MS, cancelableCallback);
        } else if (bounds.second == null) {
            // else center on single symbol
            double zoom = markersPositions.size() == 1 ? Math.max(DEFAULT_ZOOM, mapLibreMap.getCameraPosition().zoom) : DEFAULT_ZOOM;
            if (animate) {
                mapLibreMap.getUiSettings().setAllGesturesEnabled(false);
                mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngZoom(bounds.first.toMapLibre(), zoom), TRANSITION_DURATION_MS, cancelableCallback);
            } else {
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.first.toMapLibre(), zoom));
            }
        } else {
            int padding = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) * 2 / 7;
            if (animate) {
                mapLibreMap.getUiSettings().setAllGesturesEnabled(false);
                mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bounds.second.getLatitude(), bounds.second.getLongitude(), bounds.first.getLatitude(), bounds.first.getLongitude()), padding), TRANSITION_DURATION_MS, cancelableCallback);
            } else {
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bounds.second.getLatitude(), bounds.second.getLongitude(), bounds.first.getLatitude(), bounds.first.getLongitude()), padding));
            }
        }

        currentlyCenteredOnGpsPosition.postValue(false);
        setCurrentlyCenteredSymbol(null);
    }

    @Override
    public void centerOnMarker(long id, boolean animate) {
        centerOnSymbol(symbolsByIdHashMap.get(id), animate);
    }


    private Location lastLocation = null;
    private long lastLocationUpdate = 0;

    @Override
    void onLocationUpdate(Location location) {
        if (currentlyCenteredOnGpsPosition.getValue() != null
                && currentlyCenteredOnGpsPosition.getValue()
                && UnifiedForegroundService.LocationSharingSubService.filterLocationUpdate(lastLocation, lastLocationUpdate, location, LocationShareQuality.QUALITY_BALANCED, false)) {
            lastLocation = location;
            lastLocationUpdate = System.currentTimeMillis();
            centerOnLocation(location, true);
        }
    }

    @Override
    Double getLatestLocationAltitude() {
        return lastLocation == null ? null : lastLocation.getAltitude();
    }

    @Override
    Float getLatestLocationAccuracy() {
        return lastLocation == null ? null : lastLocation.getAccuracy();
    }

    private void centerOnSymbol(Symbol symbol, boolean animate) {
        if (symbolManager == null || mapLibreMap == null) {
            Logger.i("MapViewMapLibreFragment: called centerOnSymbol before map was initialized");
            return;
        }

        if (symbol != null) {
            currentlyCenteredOnGpsPosition.postValue(false);
            setCurrentlyCenteredSymbol(symbol);

            // move camera to marker
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(symbol.getLatLng(), Math.max(DEFAULT_ZOOM, mapLibreMap.getCameraPosition().zoom));
            if (animate) {
                mapLibreMap.easeCamera(cameraUpdate, TRANSITION_DURATION_MS);
            }
            else {
                mapLibreMap.moveCamera(cameraUpdate);
            }
        }
    }

    private void setCurrentlyCenteredSymbol(@Nullable Symbol symbol) {
        if (symbolManager == null || mapLibreMap == null) {
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


    private final static int CIRCLE_POLYLINE_STEPS = 90;
    @NonNull
    private List<Point> computePrecisionCirclePolyline(@NonNull LatLng center, float radiusMeters) {
        // convert radius meters to radians
        double radiusRadians = (double) radiusMeters / 6373000d;
        // convert center coordinates to radians too
        double centerLat = (center.getLatitude() % 360) * Math.PI / 180;
        double centerLong = (center.getLongitude() % 360) * Math.PI / 180;

        List<Point> points = new ArrayList<>();
        for (int i = 0; i < CIRCLE_POLYLINE_STEPS; i++) {
            double angle = i * 2 * Math.PI / CIRCLE_POLYLINE_STEPS;
            double pointLat =  Math.asin(Math.sin(centerLat) * Math.cos(radiusRadians) + Math.cos(centerLat) * Math.sin(radiusRadians) * Math.cos(angle));
            double pointLong =  centerLong + Math.atan2(
                    Math.sin(angle) * Math.sin(radiusRadians) * Math.cos(centerLat),
                    Math.cos(radiusRadians) - Math.sin(centerLat) * Math.sin(pointLat)
            );
            points.add(Point.fromLngLat(
                    pointLong * 180 / Math.PI,
                    pointLat * 180 / Math.PI
            ));
        }

        // re-add the first point to close the curve
        points.add(points.get(0));
        return points;
    }


    private void recomputePrecisionCirclesLayer() {
        if (mapLibreMap != null && symbolManager != null) {
            Style style = mapLibreMap.getStyle();
            if (style != null) {
                // remove any outdated source/layer
                Layer circlesLayer = style.getLayer("circles-layer");
                if (circlesLayer != null) {
                    style.removeLayer(circlesLayer);
                }
                Layer circleOutlinesLayer = style.getLayer("circle-outlines-layer");
                if (circleOutlinesLayer != null) {
                    style.removeLayer(circleOutlinesLayer);
                }
                Source circlesSource = style.getSource("precision-circles");
                if (circlesSource != null) {
                    style.removeSource(circlesSource);
                }

                List<List<List<Point>>> circles = new ArrayList<>();
                for (List<Point> circle : precisionCirclesHashMap.values()) {
                    circles.add(Collections.singletonList(circle));
                }
                circlesSource = new GeoJsonSource("precision-circles", MultiPolygon.fromLngLats(circles));
                circlesLayer = new FillLayer("circles-layer", "precision-circles").withProperties(
                        new PropertyValue<String>("fill-color", "#0099ff"),
                        new PropertyValue<Float>("fill-opacity", 0.094f)
                );
                circleOutlinesLayer = new LineLayer("circle-outlines-layer", "precision-circles").withProperties(
                        new PropertyValue<String>("line-color", "#0099ff"),
                        new PropertyValue<Float>("line-opacity", 0.266f)
                );
                style.addSource(circlesSource);
                style.addLayerBelow(circlesLayer, symbolManager.getLayerId());
                style.addLayerAbove(circleOutlinesLayer, "circles-layer");
            }
        }
    }
}
