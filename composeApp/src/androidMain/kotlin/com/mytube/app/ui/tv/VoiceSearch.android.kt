package com.mytube.app.ui.tv

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberVoiceSearch(languageCode: String, onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val deliver = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (heard.isNotBlank()) deliver.value(heard)
    }
    val intent = remember(languageCode) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
    }
    // Asked of the package manager rather than assumed: a television without
    // Google's services has no recogniser, and then there is no microphone.
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    if (!available) return null
    return { launcher.launch(intent) }
}
