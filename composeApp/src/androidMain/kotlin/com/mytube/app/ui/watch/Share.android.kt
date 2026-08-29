package com.mytube.app.ui.watch

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShare(): (String) -> Unit {
    val context = LocalContext.current
    return { link ->
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        // A chooser rather than the last app used. Android will otherwise pick a
        // default silently, and a share button that always opens the same app is
        // one people stop trusting.
        context.startActivity(Intent.createChooser(send, null))
    }
}
