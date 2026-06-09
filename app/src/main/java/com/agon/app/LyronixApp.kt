package com.agon.app

import android.app.Application
import com.google.firebase.FirebaseApp
import timber.log.Timber

class LyronixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Init Timber logging
        Timber.plant(Timber.DebugTree())

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
