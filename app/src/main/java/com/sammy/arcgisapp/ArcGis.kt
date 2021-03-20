package com.sammy.arcgisapp

import android.app.Application
import timber.log.Timber

class ArcGis:Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}