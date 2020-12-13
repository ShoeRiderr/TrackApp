package com.example.truckapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Display {@link SymbolLayer} icons on the map.
 */
public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, PermissionsListener {

    TextView traveledDistanceText;
    Button geoLocationButton;

    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private MapView mapView;

    private LocationEngine locationEngine;
    private long DEFAULT_INTERVAL_IN_MILLISECONDS = 100L;
    private long DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS;
    private Double latitudeStart;
    private Double longitudeStart;

    private Chronometer chronometer;
    private boolean running;
    private long pauseOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);

        geoLocationButton = findViewById(R.id.geoLocationButton);
        geoLocationButton.setTag(1);
        traveledDistanceText = findViewById(R.id.traveledDistanceText);

        chronometer = findViewById(R.id.chronometer);
        chronometer.setFormat("Time: %s");
        chronometer.setBase(SystemClock.elapsedRealtime());

        chronometer.setOnChronometerTickListener(chronometer -> {

        });

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 100);
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        geoLocationButton.setOnClickListener(v -> {
            final int status = (Integer) v.getTag();
            if (status ==1) {
                startChronometer(v);
                mapView.getMapAsync(mapboxMap -> {
                    MainActivity.this.mapboxMap = mapboxMap;

                    if (latitudeStart == null) {
                        latitudeStart = mapboxMap.getLocationComponent().getLastKnownLocation().getLatitude();
                    }
                    if (longitudeStart == null) {
                        longitudeStart = mapboxMap.getLocationComponent().getLastKnownLocation().getLongitude();
                    }
                    mapboxMap.setStyle(Style.LIGHT,
                            style -> enableLocationComponent(style, true));

                });
                geoLocationButton.setText("STOP");
                v.setTag(0);
            } else {
                pauseChronometer(v);
                mapView.getMapAsync(mapboxMap -> {
                    MainActivity.this.mapboxMap = mapboxMap;

                    if (latitudeStart == null) {
                        latitudeStart = mapboxMap.getLocationComponent().getLastKnownLocation().getLatitude();
                    }
                    if (longitudeStart == null) {
                        longitudeStart = mapboxMap.getLocationComponent().getLastKnownLocation().getLongitude();
                    }

                    mapboxMap.setStyle(Style.LIGHT,
                            style -> {});
                    mapboxMap.setStyle(Style.LIGHT,
                            style -> enableLocationComponent(style, false));
                });

                geoLocationButton.setText("START");
                v.setTag(1);
            }

        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.timer_reset:
                resetChronometer(findViewById(R.id.chronometer));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    //Timer
    public void startChronometer(View v) {
        if (!running) {
            chronometer.setBase(SystemClock.elapsedRealtime() - pauseOffset);
            chronometer.start();
            running = true;
        }
    }
    public void pauseChronometer(View v) {
        if (running) {
            chronometer.stop();
            pauseOffset = SystemClock.elapsedRealtime() - chronometer.getBase();
            running = false;
        }
    }

    public void resetChronometer(View v) {
        chronometer.setBase(SystemClock.elapsedRealtime());
        pauseOffset = 0;
    }

    //Map
    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        MainActivity.this.mapboxMap = mapboxMap;

        mapboxMap.setStyle(Style.LIGHT,
                style -> enableLocationComponent(style, false));
    }

    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle, boolean initLocationEngine) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Get an instance of the component
            LocationComponent locationComponent = mapboxMap.getLocationComponent();

            // Set the LocationComponent activation options
            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, loadedMapStyle)
                            .useDefaultLocationEngine(false)
                            .build();

            // Activate with options
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());

            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);

            if (initLocationEngine == true) {
                initLocationEngine();
            }

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressLint("MissingPermission")
    private void initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this);

        LocationEngineRequest request = new LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build();
        MainActivityLocationCallback callback = new MainActivityLocationCallback(this, latitudeStart, longitudeStart, traveledDistanceText);
        locationEngine.requestLocationUpdates(request, callback, getMainLooper());
        locationEngine.getLastLocation(callback);
    }

    //Map callback
    private static class MainActivityLocationCallback
            implements LocationEngineCallback<LocationEngineResult> {

        private final WeakReference<MainActivity> activityWeakReference;
        private Double latitudeStart;
        private Double longitudeStart;
        private TextView traveledDistanceText;
        private Double oldDistance;

        MainActivityLocationCallback(MainActivity activity,
                                     Double latitudeStart,
                                     Double longitudeStart,
                                     TextView traveledDistanceText) {
            this.activityWeakReference = new WeakReference<>(activity);
            this.latitudeStart = latitudeStart;
            this.longitudeStart = longitudeStart;
            this.traveledDistanceText = traveledDistanceText;
        }

        /**
         * The LocationEngineCallback interface's method which fires when the device's location has changed.
         *
         * @param result the LocationEngineResult object which has the last known location within it.
         */
        @Override
        public void onSuccess(LocationEngineResult result) {
            MainActivity activity = activityWeakReference.get();

            if (activity != null) {
                Location location = result.getLastLocation();

                if (location == null) {
                    return;
                }

                if (latitudeStart != null && longitudeStart != null) {
                    MapboxDirections.builder()
                            .origin(Point.fromLngLat(longitudeStart, latitudeStart))
                            .destination(Point.fromLngLat(result.getLastLocation().getLongitude(), result.getLastLocation().getLatitude()))
                            .overview(DirectionsCriteria.OVERVIEW_FULL)
                            .profile(DirectionsCriteria.PROFILE_DRIVING)
                            .accessToken(activity.getString(R.string.mapbox_access_token))
                            .build()
                            .enqueueCall(new Callback<DirectionsResponse>() {
                        @Override
                        public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {

                            if (response.body() != null) {
                                Double distance = response.body().routes().get(0).distance();
                                if (oldDistance != null && oldDistance > distance) {
                                    oldDistance = oldDistance + distance;
                                } else {
                                    oldDistance = distance;
                                }
                                traveledDistanceText.setText(String.format("Distance: %.2f km", oldDistance / 1000.0));
                            } else if (response.body().routes().size() < 1) {
                                return;
                            }
                        }

                        @Override
                        public void onFailure(Call<DirectionsResponse> call, Throwable t) {

                        }
                    });
                }

                // Pass the new location to the Maps SDK's LocationComponent
                if (activity.mapboxMap != null && result.getLastLocation() != null) {
                    activity.mapboxMap.getLocationComponent().forceLocationUpdate(result.getLastLocation());
                }
            }
        }

        /**
         * The LocationEngineCallback interface's method which fires when the device's location can not be captured
         *
         * @param exception the exception message
         */
        @Override
        public void onFailure(@NonNull Exception exception) {
            Log.d("LocationChangeActivity", exception.getLocalizedMessage());
            MainActivity activity = activityWeakReference.get();
            if (activity != null) {
                Toast.makeText(activity, exception.getLocalizedMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "permission", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style, false);
                }
            });
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    @SuppressWarnings( {"MissingPermission"})
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}