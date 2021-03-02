package com.sammy.arcgisapp

import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
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
import java.util.*
import java.util.concurrent.ExecutionException


class MainActivity : AppCompatActivity() {
    private var mTextToSpeech: TextToSpeech? = null
    private var mIsTextToSpeechInitialized = false
    private lateinit var mSimulatedLocationDataSource: SimulatedLocationDataSource
    private lateinit var mMapView: MapView
    private lateinit var mRouteTracker: RouteTracker
    private lateinit var mRouteAheadGraphic: Graphic
    private lateinit var mRouteTraveledGraphic: Graphic
    private lateinit var mRecenterButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.MAP_KEY)

        // get a reference to the map view
        mMapView = findViewById(R.id.mapView)
        // create a map and set it to the map view
        val map = ArcGISMap(BasemapStyle.ARCGIS_STREETS)
        mMapView.setMap(map)

        // create a graphics overlay to hold our route graphics
        val graphicsOverlay = GraphicsOverlay()
        mMapView.getGraphicsOverlays().add(graphicsOverlay)

        // initialize text-to-speech to replay navigation voice guidance
        mTextToSpeech = TextToSpeech(this) { status: Int ->
            if (status != TextToSpeech.ERROR) {
                mTextToSpeech!!.language = Resources.getSystem().configuration.locale
                mIsTextToSpeechInitialized = true
            }
        }

        // clear any graphics from the current graphics overlay
        mMapView.getGraphicsOverlays()[0].graphics.clear()

        // generate a route with directions and stops for navigation
        val routeTask = RouteTask(this, getString(R.string.routing_service_url))
        val routeParametersFuture = routeTask.createDefaultParametersAsync()
        routeParametersFuture.addDoneListener {
            try {
                // define the route parameters
                val routeParameters = routeParametersFuture.get()
                routeParameters.setStops(stops)
                routeParameters.isReturnDirections = true
                routeParameters.isReturnStops = true
                routeParameters.isReturnRoutes = true
                val routeResultFuture = routeTask.solveRouteAsync(routeParameters)
                routeParametersFuture.addDoneListener {
                    try {
                        // get the route geometry from the route result
                        val routeResult = routeResultFuture.get()
                        val routeGeometry = routeResult.routes[0].routeGeometry
                        // create a graphic for the route geometry
                        val routeGraphic = Graphic(routeGeometry,
                                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5f))
                        // add it to the graphics overlay
                        mMapView.getGraphicsOverlays()[0].graphics.add(routeGraphic)
                        // set the map view view point to show the whole route
                        mMapView.setViewpointAsync(Viewpoint(routeGeometry.extent))

                        // create a button to start navigation with the given route
                        val navigateRouteButton = findViewById<Button>(R.id.navigateRouteButton)
                        navigateRouteButton.setOnClickListener { v: View? -> startNavigation(routeTask, routeParameters, routeResult) }

                        // start navigating
                        startNavigation(routeTask, routeParameters, routeResult)
                    } catch (e: ExecutionException) {
                        val error = "Error creating default route parameters: " + e.message
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        Log.e(TAG, error)
                    } catch (e: InterruptedException) {
                        val error = "Error creating default route parameters: " + e.message
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        Log.e(TAG, error)
                    }
                }
            } catch (e: InterruptedException) {
                val error = "Error getting the route result " + e.message
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                Log.e(TAG, error)
            } catch (e: ExecutionException) {
                val error = "Error getting the route result " + e.message
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                Log.e(TAG, error)
            }
        }

        // wire up recenter button
        mRecenterButton = findViewById(R.id.recenterButton)
        mRecenterButton.setEnabled(false)
        mRecenterButton.setOnClickListener(View.OnClickListener { v: View? ->
            mMapView.getLocationDisplay().autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
            mRecenterButton.setEnabled(false)
        })
    }

    private fun startNavigation(routeTask: RouteTask, routeParameters: RouteParameters, routeResult: RouteResult) {

        // clear any graphics from the current graphics overlay
        mMapView!!.graphicsOverlays[0].graphics.clear()

        // get the route's geometry from the route result
        val routeGeometry = routeResult.routes[0].routeGeometry
        // create a graphic (with a dashed line symbol) to represent the route
        mRouteAheadGraphic = Graphic(routeGeometry,
                SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.MAGENTA, 5f))
        mMapView!!.graphicsOverlays[0].graphics.add(mRouteAheadGraphic)
        // create a graphic (solid) to represent the route that's been traveled (initially empty)
        mRouteTraveledGraphic = Graphic(routeGeometry,
                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5f))
        mMapView!!.graphicsOverlays[0].graphics.add(mRouteTraveledGraphic)

        // get the map view's location display
        val locationDisplay = mMapView!!.locationDisplay
        // set up a simulated location data source which simulates movement along the route
        mSimulatedLocationDataSource = SimulatedLocationDataSource()
        val simulationParameters = SimulationParameters(Calendar.getInstance(), 35.0, 5.0, 5.0)
        mSimulatedLocationDataSource!!.setLocations(routeGeometry, simulationParameters)

        // set up a RouteTracker for navigation along the calculated route
        mRouteTracker = RouteTracker(applicationContext, routeResult, 0, true)
        mRouteTracker!!.enableReroutingAsync(routeTask, routeParameters,
                RouteTracker.ReroutingStrategy.TO_NEXT_WAYPOINT, true)

        // create a route tracker location data source to snap the location display to the route
        val routeTrackerLocationDataSource = RouteTrackerLocationDataSource(mRouteTracker, mSimulatedLocationDataSource)
        // set the route tracker location data source as the location data source for this app
        locationDisplay.locationDataSource = routeTrackerLocationDataSource
        locationDisplay.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
        // if the user navigates the map view away from the location display, activate the recenter button
        locationDisplay.addAutoPanModeChangedListener { autoPanModeChangedEvent: AutoPanModeChangedEvent? -> mRecenterButton!!.isEnabled = true }

        // get a reference to navigation text views
        val distanceRemainingTextView = findViewById<TextView>(R.id.distanceRemainingTextView)
        val timeRemainingTextView = findViewById<TextView>(R.id.timeRemainingTextView)
        val nextDirectionTextView = findViewById<TextView>(R.id.nextDirectionTextView)

        // listen for changes in location
        locationDisplay.addLocationChangedListener { locationChangedEvent: LocationDisplay.LocationChangedEvent? ->
            // listen for new voice guidance events
            mRouteTracker!!.addNewVoiceGuidanceListener { newVoiceGuidanceEvent: NewVoiceGuidanceEvent ->
                // use Android's text to speech to speak the voice guidance
                speakVoiceGuidance(newVoiceGuidanceEvent.voiceGuidance.text)
                nextDirectionTextView.text = getString(R.string.next_direction, newVoiceGuidanceEvent.voiceGuidance.text)
            }

            // get the route's tracking status
            val trackingStatus = mRouteTracker!!.trackingStatus
            // set geometries for the route ahead and the remaining route
            mRouteAheadGraphic!!.geometry = trackingStatus.routeProgress.remainingGeometry
            mRouteTraveledGraphic!!.geometry = trackingStatus.routeProgress.traversedGeometry

            // get remaining distance information
            val remainingDistance = trackingStatus.destinationProgress.remainingDistance
            // covert remaining minutes to hours:minutes:seconds
            val remainingTimeString = DateUtils
                    .formatElapsedTime((trackingStatus.destinationProgress.remainingTime * 60).toLong())

            // update text views
            distanceRemainingTextView.text = getString(R.string.distance_remaining, remainingDistance.displayText,
                    remainingDistance.displayTextUnits.pluralDisplayName)
            timeRemainingTextView.text = getString(R.string.time_remaining, remainingTimeString)

            // if a destination has been reached
            if (trackingStatus.destinationStatus == DestinationStatus.REACHED) {
                // if there are more destinations to visit. Greater than 1 because the start point is considered a "stop"
                if (mRouteTracker!!.trackingStatus.remainingDestinationCount > 1) {
                    // switch to the next destination
                    mRouteTracker!!.switchToNextDestinationAsync()
                    Toast.makeText(this, "Navigating to the second stop, the Fleet Science Center.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Arrived at the final destination.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // start the LocationDisplay, which starts the RouteTrackerLocationDataSource and SimulatedLocationDataSource
        locationDisplay.startAsync()
        Toast.makeText(this, "Navigating to the first stop, the USS San Diego Memorial.", Toast.LENGTH_LONG).show()
    }

    /**
     * Uses Android's text to speak to say the latest voice guidance from the RouteTracker out loud.
     */
    private fun speakVoiceGuidance(voiceGuidanceText: String) {
        if (mIsTextToSpeechInitialized && !mTextToSpeech!!.isSpeaking) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mTextToSpeech!!.speak(voiceGuidanceText, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                mTextToSpeech!!.speak(voiceGuidanceText, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }

    override fun onPause() {
        mMapView!!.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mMapView!!.resume()
    }

    override fun onDestroy() {
        mMapView!!.dispose()
        super.onDestroy()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName// San Diego Convention Center
        // USS San Diego Memorial
        // RH Fleet Aerospace Museum
        /**
         * Creates a list of stops along a route.
         */
        private val stops: List<Stop>
            private get() {
                val stops: MutableList<Stop> = ArrayList(3)
                // San Diego Convention Center
                val conventionCenter = Stop(Point(-117.160386, 32.706608, SpatialReferences.getWgs84()))
                stops.add(conventionCenter)
                // USS San Diego Memorial
                val memorial = Stop(Point(-117.173034, 32.712327, SpatialReferences.getWgs84()))
                stops.add(memorial)
                // RH Fleet Aerospace Museum
                val aerospaceMuseum = Stop(Point(-117.147230, 32.730467, SpatialReferences.getWgs84()))
                stops.add(aerospaceMuseum)
                return stops
            }
    }
}