package com.sammy.arcgisapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.MapView
import com.sammy.arcgisapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main)
        setUp()
    }
    private fun setUp(){
        //setup api_key
        ArcGISRuntimeEnvironment.setApiKey("AAPKcfa6e3953cf74fe0ac1e6fec3182f7bemTnSis64O4dZee6vuODz_thUx4JU1XmQik7Gxax8Jk0p7YGM3gIFDyvOxgQilGV4")
        //create a map with the basemapstyle streets
        val map = ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC)
        binding.mapView.map = map
        binding.mapView.setViewpoint(Viewpoint(34.0270, -118.8050, 72000.0))

    }
    override fun onPause() {
        binding.mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
    }

    override fun onDestroy() {
        binding.mapView.dispose()
        super.onDestroy()
    }
}