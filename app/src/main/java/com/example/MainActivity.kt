package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory FirebaseApp and App Check initialization before any UI or Firebase operations
        ensureFirebaseInitialized(this)
        configureAppCheck(this, intent)

        // STEP 2: Edge-to-edge system insets
        enableEdgeToEdge()

        // STEP 3: Render Compose UI
        setContent {
            MyApplicationTheme {
                VideoEditorScreen()
            }
        }
    }
}
