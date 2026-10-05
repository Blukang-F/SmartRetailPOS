package com.industri.smartpos

import android.app.Application
import timber.log.Timber

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Hanya mengaktifkan Timber di mode DEBUG
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
