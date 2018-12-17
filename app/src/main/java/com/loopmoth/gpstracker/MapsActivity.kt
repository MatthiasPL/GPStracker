package com.loopmoth.gpstracker

import android.Manifest
import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import android.location.LocationManager
import android.content.pm.PackageManager
import android.hardware.*
import android.location.Location
import android.location.LocationListener
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.*
import java.util.*

class MapsActivity : AppCompatActivity(), SensorEventListener, OnMapReadyCallback {

    private val PERMISSIONS_REQUEST = 100
    private var mMap: GoogleMap? = null
    private var locationManager : LocationManager? = null
    private var mapFragment: SupportMapFragment? = null
    private val mRotationMatrix = FloatArray(16)
    private var field: GeomagneticField? = null
    private lateinit var mSensorManager: SensorManager
    private var mRotation: Sensor? =null
    private var user: LatLng?=null
    private var locationSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            finish()
        }

        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (permission == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        }
        else{
            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            ActivityCompat.requestPermissions(this,
                permissions,
                PERMISSIONS_REQUEST)
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment!!.getMapAsync(this)
    }

    override fun onResume() {
        super.onResume()
        mRotation?.also { rotation ->
            mSensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST && grantResults.size == 1
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            getLocation()

            mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
            mapFragment!!.getMapAsync(this)
        } else {
            Toast.makeText(this, "Please enable location services to allow GPS tracking", Toast.LENGTH_SHORT).show();
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap!!.setMinZoomPreference(17f)
        mMap!!.setMaxZoomPreference(17f)
        mMap!!.getUiSettings().setScrollGesturesEnabled(false)

        getLocation()

        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        if(hourOfDay>17 || hourOfDay < 7){
            mMap!!.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_night))
        }
    }

    fun getLocation(){
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        try {
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
        } catch(ex: SecurityException) {
            Log.d("Warning", "Security Exception, no location available");
        }

    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(
                mRotationMatrix, event.values
            )
            if(field!=null && mapFragment!=null && user!=null && locationSet){
                val orientation = FloatArray(3)
                SensorManager.getOrientation(mRotationMatrix, orientation)
                val bearing = Math.toDegrees(orientation[0].toDouble()) + field!!.declination
                val cameraPosition = CameraPosition.Builder().target(user!!).bearing(bearing.toFloat()).build()
                mMap!!.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                //Toast.makeText(this@MapsActivity, "działa", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            field = GeomagneticField(
                location.getLatitude().toFloat(),
                location.getLongitude().toFloat(),
                location.getAltitude().toFloat(),
                System.currentTimeMillis()
            )

            user = LatLng(location.latitude, location.longitude)
            if(mapFragment!=null){
                //Toast.makeText(this@MapsActivity, location.longitude.toString() + " " + location.latitude.toString(), Toast.LENGTH_SHORT).show()
                mMap!!.clear()
                mMap!!.addMarker(MarkerOptions().position(user!!).title("User").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))

                val cameraPosition = CameraPosition.Builder()
                    .target(user)
                    .build()
                mMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                locationSet=true
            }
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}
