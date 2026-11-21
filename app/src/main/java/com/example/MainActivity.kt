package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.ui.screens.PatchForgeApp
import com.example.ui.theme.PatchForgeTheme
import com.example.ui.viewmodel.PatchForgeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase ops.
        configureAppCheck(this, intent)

        // STEP 2: Dark forged-steel edge-to-edge system bar styling.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // STEP 3: Mount Compose UI.
        setContent {
            PatchForgeTheme {
                val viewModel: PatchForgeViewModel = viewModel(
                    factory = PatchForgeViewModel.Factory(applicationContext)
                )
                PatchForgeApp(viewModel = viewModel)
            }
        }
    }
}
