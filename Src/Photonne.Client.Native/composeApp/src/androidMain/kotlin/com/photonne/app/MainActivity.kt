package com.photonne.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.photonne.app.ui.platform.OrientationController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status + navigation bars so they pick up the app's
        // background color instead of staying opaque white. Material3 Scaffold
        // / TopAppBar / NavigationBar apply the system-bar insets automatically.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Let commonMain flip orientation (the video viewer relaxes the
        // manifest's portrait lock). Cleared in onDestroy to avoid leaking
        // this Activity past recreation.
        OrientationController.attach(this)
        setContent { App() }
    }

    override fun onDestroy() {
        OrientationController.detach(this)
        super.onDestroy()
    }
}
