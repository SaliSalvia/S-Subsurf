package com.salisalvia.subsurface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salisalvia.subsurface.feature.map.MapScreen
import com.salisalvia.subsurface.feature.map.MapViewModel
import com.salisalvia.subsurface.ui.theme.SubsurfaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubsurfaceTheme {
                val mapViewModel: MapViewModel = viewModel()
                val validationState = mapViewModel.validationState.collectAsStateWithLifecycle()
                MapScreen(
                    validationState = validationState.value,
                    onValidateRegion = mapViewModel::validateRegion,
                    onDismissValidation = mapViewModel::dismissValidation,
                )
            }
        }
    }
}
