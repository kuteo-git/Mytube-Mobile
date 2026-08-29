package com.mytube.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mytube.app.ui.App

class MainActivity : ComponentActivity() {

    /**
     * Asks once, on launch, and ignores the answer.
     *
     * Not a dialog with an explanation first, deliberately: the permission is
     * for the media notification, and the honest moment to explain it is when
     * somebody notices they cannot pause from the lock screen — not before they
     * have played anything. Refusing costs the notification and nothing else;
     * playback is unaffected, which is why this must not block the app.
     */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }


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
        askForNotifications()
        val container = MytubeApp.container(applicationContext)
        setContent { App(container) }
    }
}
