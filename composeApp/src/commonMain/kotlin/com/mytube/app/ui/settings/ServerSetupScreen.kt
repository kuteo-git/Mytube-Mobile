package com.mytube.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassTextField
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
    /**
     * Null on a fresh install, and a way out everywhere else.
     *
     * This screen is two things: the first thing anybody sees, where there is
     * nowhere to go back *to*, and a row in Settings, where leaving without
     * changing the address has to be possible. A back arrow drawn in the first
     * case would be a button that does nothing.
     */
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ServerSetupContent(
        state = state,
        onBack = onBack,
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
    onBack: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Same reason as the feed: draw edge to edge, but keep the
                // content out from under the system bars.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = strings.appName,
                color = Tokens.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.setupSubtitle,
                color = Tokens.text2,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(24.dp))
            Text(strings.serverAddressLabel, color = Tokens.text2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            GlassTextField(
                value = state.address,
                onValueChange = onAddressChanged,
                // Not translated, and not copy: it is an example address, which
                // reads the same in both languages.
                placeholder = "10.0.0.5:8180",
                // A URI keyboard: dots and slashes to hand, and no
                // auto-capitalisation to fight.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
                fontSize = 13.sp,
                color = if (state.check == CheckState.Failed) Tokens.brand else Tokens.text2,
            )

            Spacer(Modifier.height(24.dp))
            GlassButton(
                label = strings.check,
                onClick = onCheck,
                enabled = state.canSubmit && state.check != CheckState.Checking,
                loading = state.check == CheckState.Checking,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.sm))
            GlassButton(
                label = strings.save,
                onClick = onSave,
                // Saving without a successful check is allowed on purpose: the
                // Mac may simply be asleep while somebody sets their phone up,
                // and refusing to remember a correct address would be the app
                // being clever at their expense.
                enabled = state.canSubmit,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Drawn over the form, the same as the saved shelf's and the channel
        // page's. This screen has no tab bar, Android's system back leaves the
        // app and iOS has no system back at all — so the way out has to be on
        // the screen, and only where there is one.
        if (onBack != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues()
                            .calculateTopPadding(),
                    )
                    .padding(start = Space.xs)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SetupBackIcon,
                    contentDescription = strings.back,
                    tint = Tokens.text,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        }
    }
}

/** The same chevron every screen opened from another one carries. */
private val SetupBackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SetupBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(15.5f, 4f)
            lineTo(7.5f, 12f)
            lineTo(15.5f, 20f)
            lineTo(17f, 18.5f)
            lineTo(10.5f, 12f)
            lineTo(17f, 5.5f)
            close()
        }
    }.build()
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
