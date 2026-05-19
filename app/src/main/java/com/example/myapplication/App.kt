package com.example.myapplication

import android.app.Application
import com.example.myapplication.BuildConfig
import com.example.myapplication.di.appModule
import com.google.android.libraries.places.api.Places
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPS_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }
}
