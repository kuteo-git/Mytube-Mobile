package com.mytube.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
        // These names describe the *background*, not the ink: `dark` means the
        // strip behind the clock, battery and signal is dark, so the system
        // draws them in white. That is what is wanted here — the bar is
        // transparent and every screen behind it is `Tokens.bg` (#0F0F0F), on
        // which the dark glyphs `light` produces are invisible. Measured: that
        // is exactly what shipped for one build.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // **Below Android 11 the keyboard's inset is never dispatched.**
        //
        // `WindowInsets.ime` arrived in API 30. Under it the platform reports
        // nothing, and this app's manifest says `adjustNothing` — which is
        // correct from 30 up, where `imePadding()` does the moving. Together
        // the two mean that on Android 10 *nothing at all* answers the
        // keyboard: measured on an API 29 emulator, every element of the
        // server-address form sat at the same y with the keyboard up as with
        // it down, and the Save button was underneath it. Reported as the
        // search row and this form both being covered, and only there.
        //
        // So on those versions the window is asked to resize instead, which is
        // the mechanism that predates the inset. `imePadding()` then adds zero,
        // because the inset it reads is still empty — the two cannot
        // double-count, which is the fault the search row's own note records
        // from the other direction.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        askForNotifications()
        val container = MytubeApp.container(applicationContext)
        setContent { App(container) }
    }
}
