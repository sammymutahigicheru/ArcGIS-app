package com.sammy.arcgisapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.text.style.TtsSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
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
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.mapping.view.LocationDisplay.AutoPanModeChangedEvent
import com.esri.arcgisruntime.navigation.DestinationStatus
import com.esri.arcgisruntime.navigation.RouteTracker
import com.esri.arcgisruntime.navigation.RouteTracker.NewVoiceGuidanceEvent
import com.esri.arcgisruntime.navigation.TrackingStatus
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
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
import timber.log.Timber
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationDisplay: LocationDisplay
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private lateinit var currentLocation: Location

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //    setSupportActionBar(binding.navigationToolbar)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        latitude = intent?.getDoubleExtra("lat", 0.0)!!
        longitude = intent?.getDoubleExtra("lon", 0.0)!!
        Timber.e("Latitude: $latitude")
        Timber.e("Latitude: $longitude")

        if (isLocationPermissionEnabled) {
            getCurrentLocation()
        }
    }


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

    private fun setupMap() {
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.MAP_KEY)
        binding.mapView.map = ArcGISMap(BasemapStyle.ARCGIS_NAVIGATION)

        binding.mapView.setViewpoint(
            Viewpoint(
                currentLocation.latitude,
                currentLocation.longitude,
                50000.0
            )
        )

        locationDisplay = binding.mapView.locationDisplay
        locationDisplay.addDataSourceStatusChangedListener { event ->
            if (!event.isStarted && event.error != null) {
                requestLocationPermission()
            }

        }

        binding.mapView.map.addLoadStatusChangedListener {
            if (it.newLoadStatus == LoadStatus.LOADED) {
                Timber.e("map is loaded")
                locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.RECENTER
                locationDisplay.startAsync()
            }
        }

        binding.mapView.graphicsOverlays.add(GraphicsOverlay())
        generateRoute()

    }


    private fun generateRoute() {

        val routeTask = RouteTask(this, getString(R.string.routing_service_url))

        val routeStops: List<Stop> = listOf(
            Stop(
                Point(
                    currentLocation.longitude,
                    currentLocation.latitude,
                    SpatialReferences.getWgs84()
                )
            ),

            //Meridian court
//            -1.31754, 36.84411

            Stop(Point(longitude, latitude, SpatialReferences.getWgs84())),
        )


        val routeParametersFuture = routeTask.createDefaultParametersAsync()
        routeParametersFuture.addDoneListener {
            try {
                val routeParameters = routeParametersFuture.get()
                routeParameters.apply {
                    setStops(routeStops)
                    isReturnDirections = true
                    directionsLanguage = "en"
                }


                val routeResultFuture = routeTask.solveRouteAsync(routeParameters)
                routeResultFuture.addDoneListener {
                    try {
                        // get the route geometry from the route result
                        val routeResult = routeResultFuture.get()
                        val route = routeResult.routes[0]
                        val routeGeometry = route.routeGeometry
                        // create a graphic for the route geometry
                        Timber.e("route Geometry ${routeGeometry.parts[0]}")

                        locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION


                        val routeGraphic = Graphic(
                            route.routeGeometry,
                            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 10F)
                        )
                        binding.mapView.graphicsOverlays[0].graphics.add(routeGraphic)


                    } catch (e: Exception) {
                        e.printStackTrace()
                        when (e) {
                            is InterruptedException, is ExecutionException -> {
                                val error = "Error creating the route result: " + e.message
                                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                            }
                            else -> throw e
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is InterruptedException, is ExecutionException -> {
                        val error = "Error getting the default route parameters: " + e.message
                        Toast.makeText(this, error, Toast.LENGTH_LONG)
                            .show()
                        Timber.e(error)
                    }
                    else -> throw e
                }
            }
        }

    }


    private fun requestLocationPermission() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        Dexter.withContext(this)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let { result ->
                        if (result.areAllPermissionsGranted()) {
                            Timber.d("permissions allowed")
                        } else {
                            Toast.makeText(
                                this@MainActivity,
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
                    Toast.makeText(
                        this@MainActivity,
                        "enable location",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }).check()

    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {

        if (isLocationPermissionEnabled) {
            Timber.e("getting current location")
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


    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val lastLocation = locationResult.lastLocation
            currentLocation = lastLocation
            setupMap()
            fusedLocationProviderClient.removeLocationUpdates(this)
            Timber.e("onLocationResult: location $lastLocation")
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