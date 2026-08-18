package com.smartaodi.dshandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartaodi.dshandroid.ui.DshAndroidScreen
import com.smartaodi.dshandroid.ui.theme.DshAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DshAndroidTheme {
                val harnessViewModel: HarnessViewModel = viewModel()
                DshAndroidScreen(harnessViewModel)
            }
        }
    }
}
