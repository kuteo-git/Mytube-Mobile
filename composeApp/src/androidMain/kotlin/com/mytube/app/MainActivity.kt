package com.mytube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mytube.app.ui.App

class MainActivity : ComponentActivity() {

    /**
     * The container is built once for the process, not once per Activity.
     *
     * An Activity is recreated on every rotation, and rebuilding the HTTP client
     * with it would drop the connection pool each time — a new TCP handshake to
     * the same server for turning the phone sideways.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = MytubeApp.container(applicationContext)
        setContent { App(container) }
    }
}
