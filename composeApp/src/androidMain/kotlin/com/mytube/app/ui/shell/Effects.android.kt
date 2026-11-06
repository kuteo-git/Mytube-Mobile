package com.mytube.app.ui.shell

import android.os.Build

actual val effectsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
