package com.evalwithin.olook;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.app.Fragment;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.evalwithin.olook.Data.AreaOfInterest;
import com.evalwithin.olook.Data.DataManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapsActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback, GPSListener, OrientationListener {

    private GPSTracker gpsTracker;
    private OrientationTracker orientationTracker;
    private FilterItems filters;

    private static String TAG = MapsActivity.class.getSimpleName();

    private GoogleMap mMap;
    private Marker mLastOpenned = null;
    private Compass mCompass;

    private View mBottomSheet;
    private TextView mTitle;
    private TextView mDescription;
    private BottomSheetBehavior mBottomSheetBehavior;

    Location lastDatapoint = null;

    private Map<String, ArrayList<Marker>> markerMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout1);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);
        MapUtils.init(getResources());

        String[] filterNames = getResources().getStringArray(R.array.filter_array);

        filters = new FilterItems();
        filters.addFilters(Arrays.asList(filterNames));

        DataManager dataManager = DataManager.getInstance();
        if (dataManager.getState() == Thread.State.NEW)
            dataManager.start();

        gpsTracker = new GPSTracker(getApplicationContext());
        gpsTracker.addListener(this);

        orientationTracker = new OrientationTracker(getApplicationContext());
        orientationTracker.addListener(this);

        Menu menu = navigationView.getMenu();
        MenuItem cameraItem = menu.findItem(R.id.nav_camera);
        cameraItem.setIcon(R.drawable.ic_menu_camera);
        cameraItem.setTitle(R.string.camera);

        mCompass = (Compass) findViewById(R.id.compass);

        markerMap = new HashMap<>();

        mTitle = (TextView) findViewById(R.id.bottom_sheet_title);
        mDescription = (TextView) findViewById(R.id.bottom_sheet_description);
        mBottomSheet = findViewById( R.id.bottom_sheet );
        mBottomSheetBehavior = BottomSheetBehavior.from(mBottomSheet);

        mBottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(menu.size() == 0)
        {
            for (FilterItems.FilterItem filterItem : filters.getFilterItems()) {
                menu.add(0, filterItem.getId(), Menu.NONE, filterItem.getName()).setCheckable(true).setChecked(true);
            }
        }

        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        menuItem.setChecked(!menuItem.isChecked());

        filters.changeActive(menuItem.getItemId());

        for(FilterItems.FilterItem item : filters.getFilterItems())
        {
            String key = item.getName();
            ArrayList<Marker> curMarker = markerMap.get(key);

            if(curMarker == null)
                continue;

            for (Marker marker : curMarker)
            {
                marker.setVisible(item.isActive());
            }
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout1);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {

        mMap = googleMap;
        mMap.getUiSettings().setScrollGesturesEnabled(false);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            public boolean onMarkerClick(Marker marker) {
                if(marker.equals(MapUtils.userMarker))
                    return false;

                if (mLastOpenned != null) {
                    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    if (mLastOpenned.equals(marker)) {
                        mLastOpenned = null;
                        return true;
                    }
                }

                mLastOpenned = marker;
                mTitle.setText(marker.getTitle());
                mDescription.setText(marker.getSnippet());
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                return true;
            }
        });

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        //mMap.getUiSettings().setScrollGesturesEnabled(false);
        //mMap.getUiSettings().setCompassEnabled(false);

        Location loc = gpsTracker.getLocation();
        LatLng ll = new LatLng(loc.getLatitude(), loc.getLongitude());

        centerMap(loc, 15);

        MapUtils.setMyLocation(mMap, ll);
        //MapUtils.addInterestPoint(googleMap, new LatLng(45.410600, -71.887200), MapUtils.IconIndex.WIFI, "Nice area!");

        googleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            private float lastZoom = 1;
            private LatLng lastPos = new LatLng(0, 0);

            private float cumulativeZoom = 0;

            @Override
            public void onCameraChange(CameraPosition pos) {
                float maxLevel = 18f;
                float minLevel = 15f;

                if (pos.zoom > maxLevel) {
                    // TODO : Mettre le mode camera
                    //googleMap.animateCamera(CameraUpdateFactory.zoomTo(maxLevel), 0, null);
                    googleMap.moveCamera(CameraUpdateFactory.zoomTo(maxLevel));
                } else if (pos.zoom < minLevel) {
                    //googleMap.animateCamera(CameraUpdateFactory.zoomTo(minLevel), 0, null);
                    googleMap.moveCamera(CameraUpdateFactory.zoomTo(minLevel));
                }

                boolean needToCenter = false;

                if (cumulativeZoom < 0.2) {
                    cumulativeZoom += pos.zoom - lastZoom;
                } else {
                    needToCenter = true;
                    cumulativeZoom = 0;
                }

                if (true) {//needToCenter) {
                    centerMap(gpsTracker.getLocation());
                }

                lastZoom = pos.zoom;
            }
        });

        // rotate 90 degrees
        CameraPosition oldPos = googleMap.getCameraPosition();
        CameraPosition pos = CameraPosition.builder(oldPos).bearing(245.0F).build();
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
    }

    @Override
    public void onOrientationChanged(float azimut, float pitch, float roll) {
        mCompass.updateDirection(azimut);
    }

    @Override
    public void onAccuracyChanged(int accuracy) {

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            Intent myIntent = new Intent(this, CameraActivity.class);
            this.startActivity(myIntent);
        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout1);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onGPSLocationChanged(Location newLocation) {
        if(lastDatapoint == null || MapUtils.getDistance(lastDatapoint, newLocation) > 250) {
            clearMarkers();
            fillMarkers();
            lastDatapoint = newLocation;
        }

        centerMap(newLocation);
        LatLng ll = new LatLng(newLocation.getLatitude(), newLocation.getLongitude());
        MapUtils.setMyLocation(mMap, ll);
    }

    private void centerMap(Location location) {
        if(location == null)
            return;

        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(myLatLng));
    }

    private void centerMap(Location location, float zoom) {
        if(location == null)
            return;

        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, zoom));
    }

    private void clearMarkers(){
        Set<String> keys = markerMap.keySet();

        for (String key: keys)
            for (Marker marker : markerMap.get(key))
                marker.remove();

        markerMap.clear();
    }

    private void fillMarkers(){
        Location loc = gpsTracker.getLocation();
        double radius = 2500;
        Map<String, ArrayList<AreaOfInterest>> data = DataManager.getInstance().getAreaOfInterestValues(loc.getLongitude(), loc.getLatitude(), radius);

        Set<String> keys = data.keySet();

        for (String key: keys) {
            MapUtils.IconIndex idx = MapUtils.getIconIndex(key);
            ArrayList<Marker> markerList = new ArrayList<>();
            for (AreaOfInterest area : data.get(key)) {
                Marker marker = MapUtils.addInterestPoint(mMap, new LatLng(area.getLocY(), area.getLocX()), idx, area.getLocationName(), area.getLocationDesc());
                markerList.add(marker);
            }
            markerMap.put(key, markerList);
        }

    }
}
