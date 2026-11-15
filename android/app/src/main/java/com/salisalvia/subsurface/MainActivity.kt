package com.salisalvia.subsurface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salisalvia.subsurface.feature.map.MapScreen
import com.salisalvia.subsurface.feature.map.MapViewModel
import com.salisalvia.subsurface.ui.theme.SubsurfaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubsurfaceTheme {
                val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.Factory)
                MapScreen(viewModel = mapViewModel)
            }
        }
    }
}
