package com.salisalvia.subsurface

import android.app.Application
import org.maplibre.android.MapLibre

class SubsurfaceExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
