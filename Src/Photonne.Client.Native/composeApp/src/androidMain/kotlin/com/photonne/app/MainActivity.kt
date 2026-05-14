package com.photonne.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status + navigation bars so they pick up the app's
        // background color instead of staying opaque white. Material3 Scaffold
        // / TopAppBar / NavigationBar apply the system-bar insets automatically.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
