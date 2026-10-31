package eu.colega.tacho.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; Hilt generates the dependency graph. */
@HiltAndroidApp
class TachoApplication : Application()
