package com.mytube.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Where the library is — the screen, with its state holder attached.
 *
 * This half exists only to unwrap the ViewModel. Everything drawable lives in
 * [ServerSetupContent], which takes plain values, and the split is not
 * ceremony: a composable holding a ViewModel cannot be previewed, cannot be
 * rendered in a test without a fake state holder, and cannot be shown in a state
 * that is hard to reach through the real one. Every screen here is split the
 * same way.
 */
@Composable
fun ServerSetupScreen(
    viewModel: ServerSetupViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ServerSetupContent(
        state = state,
        onAddressChanged = viewModel::onAddressChanged,
        onCheck = viewModel::check,
        onSave = {
            viewModel.save()
            onDone()
        },
    )
}

/**
 * The first thing anybody sees on a fresh install.
 *
 * One job: get an address in, and let somebody find out whether it works before
 * they walk away believing it does.
 */
@Composable
fun ServerSetupContent(
    state: ServerSetupState,
    onAddressChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onSave: () -> Unit,
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineMedium,
                color = Tokens.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.setupSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Tokens.text2,
            )

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChanged,
                label = { Text(strings.serverAddressLabel) },
                // Not translated, and not copy: it is an example address, which
                // reads the same in both languages.
                placeholder = { Text("10.0.0.5:8180") },
                singleLine = true,
                // A URI keyboard: dots and slashes to hand, and no
                // auto-capitalisation to fight.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state.check) {
                    // Every state says something. A blank line where a verdict
                    // belongs reads as the button not having worked.
                    CheckState.Untested -> strings.checkUntested
                    CheckState.Checking -> strings.checkChecking
                    CheckState.Reachable -> strings.checkReachable
                    CheckState.Failed -> strings.checkFailed
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.check == CheckState.Failed) Tokens.brand else Tokens.text2,
            )

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onCheck,
                enabled = state.canSubmit && state.check != CheckState.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.check == CheckState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(strings.check)
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                // Saving without a successful check is allowed on purpose: the
                // Mac may simply be asleep while somebody sets their phone up,
                // and refusing to remember a correct address would be the app
                // being clever at their expense.
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.save)
            }
        }
    }
}

// The states worth looking at, rather than one preview of the happy path.
//
// A screen is only as good as its worst state, and the failure message here is
// the longest string in the app — which is exactly the one that wraps badly on a
// narrow phone if nobody ever renders it.

@Preview
@Composable
private fun ServerSetupEmptyPreview() = MytubeTheme {
    ServerSetupContent(ServerSetupState(), {}, {}, {})
}

@Preview
@Composable
private fun ServerSetupCheckingPreview() = MytubeTheme {
    ServerSetupContent(
        ServerSetupState(address = "10.0.0.5:8180", check = CheckState.Checking),
        {}, {}, {},
    )
}

@Preview
@Composable
private fun ServerSetupFailedPreview() = MytubeTheme {
    ServerSetupContent(
        ServerSetupState(address = "10.0.0.9:8180", check = CheckState.Failed),
        {}, {}, {},
    )
}

/**
 * The same screen in Vietnamese.
 *
 * Kept because the two languages are not the same length: the failure line is
 * meaningfully longer in one of them, and a layout that only ever gets looked at
 * in English is a layout that breaks for half the household.
 */
@Preview
@Composable
private fun ServerSetupVietnamesePreview() = MytubeTheme {
    androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        ServerSetupContent(
            ServerSetupState(address = "10.0.0.9:8180", check = CheckState.Failed),
            {}, {}, {},
        )
    }
}
