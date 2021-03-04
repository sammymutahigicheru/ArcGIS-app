package com.sammy.arcgisapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.location.RouteTrackerLocationDataSource
import com.esri.arcgisruntime.location.SimulatedLocationDataSource
import com.esri.arcgisruntime.location.SimulationParameters
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.LocationDisplay.AutoPanModeChangedEvent
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.navigation.DestinationStatus
import com.esri.arcgisruntime.navigation.RouteTracker
import com.esri.arcgisruntime.navigation.RouteTracker.NewVoiceGuidanceEvent
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask
import com.esri.arcgisruntime.tasks.networkanalysis.Stop
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.sammy.arcgisapp.databinding.ActivityMainBinding
import java.util.*
import java.util.concurrent.ExecutionException


class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationDisplay: LocationDisplay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        setupMap()
    }

    private fun setupMap() {
        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.MAP_KEY)
        binding.mapView.map = ArcGISMap(BasemapStyle.ARCGIS_STREETS)
        locationDisplay = binding.mapView.locationDisplay

        locationDisplay.addDataSourceStatusChangedListener { event ->
            if (!event.isStarted && event.error != null) {
                requestLocationPermission()
            }

        }

        binding.mapView.map.addLoadStatusChangedListener {
            if (it.newLoadStatus == LoadStatus.LOADED) {
                locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
                locationDisplay.startAsync()
              //  activateMyLocation()

            }

        }
    }
//    private fun activateMyLocation() {
//        binding.imageButton.apply {
//            visibility = View.VISIBLE
//            setOnClickListener {
//                locationDisplay.apply {
//                    autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
//                    startAsync()
//                }
//            }
//        }
//    }

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.dispose()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {

        if (isLocationPermissionEnabled) {
            val locationRequest = LocationRequest().apply {
                interval = 1000L
                fastestInterval = 500L
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            requestLocationPermission()
        }


    }

    private fun requestLocationPermission() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        Dexter.withContext(applicationContext)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let { result ->
                        if (result.areAllPermissionsGranted()) {
                        } else {
                            Toast.makeText(
                                applicationContext,
                                "Permissions denied",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    Toast.makeText(this@MainActivity, "enable location", Toast.LENGTH_SHORT).show()
                }

            }).check()

    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val lastLocation = locationResult.lastLocation
            fusedLocationProviderClient.removeLocationUpdates(this)
        //    Timber.e("onLocationResult: location $lastLocation")
            Toast.makeText(this@MainActivity, "$lastLocation", Toast.LENGTH_LONG).show()
        }
    }

    private val isLocationPermissionEnabled: Boolean
        get() {
            return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

}