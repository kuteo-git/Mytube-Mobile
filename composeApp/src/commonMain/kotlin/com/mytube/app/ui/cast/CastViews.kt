package com.mytube.app.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastRefusal
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.home.stroke
import com.mytube.app.ui.home.filled
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.GLASS_MARGIN
import com.mytube.app.ui.shell.GLASS_SHAPE
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.GlassSheet
import com.mytube.app.ui.shell.GlassSlider
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.shell.pressableGlassControl
import com.mytube.app.ui.shell.pressableGlassSurface
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.CloseIcon
import com.mytube.app.ui.watch.NextIcon
import com.mytube.app.ui.watch.PauseIcon
import com.mytube.app.ui.watch.PlayIcon
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

internal val CastIcon: ImageVector by lazy {
    stroke("Cast") {
        moveTo(3f, 8f); lineTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(14f, 19f)
        moveTo(3f, 11.5f); quadTo(10.5f, 11.5f, 10.5f, 19f)
        moveTo(3f, 15f); quadTo(7f, 15f, 7f, 19f)
        moveTo(3f, 19f); lineTo(3.01f, 19f)
    }
}

/** [CastIcon] lit: something is playing on a speaker. Filled against outlined, the charter's change of kind. */
internal val CastActiveIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CastActive", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.White), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 8f); lineTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(14f, 19f)
            moveTo(3f, 11.5f); quadTo(10.5f, 11.5f, 10.5f, 19f)
            moveTo(3f, 15f); quadTo(7f, 15f, 7f, 19f)
            moveTo(3f, 19f); lineTo(3.01f, 19f)
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 8f); lineTo(18f, 8f); lineTo(18f, 16f); lineTo(13.4f, 16f)
            quadTo(12.6f, 10.2f, 6f, 9.6f); close()
        }
    }.build()
}

private val SpeakerIcon: ImageVector by lazy {
    stroke("Speaker") {
        moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 21f); lineTo(7f, 21f); close()
        moveTo(12f, 6.5f); lineTo(12.01f, 6.5f)
        moveTo(15f, 14f); quadTo(15f, 17f, 12f, 17f); quadTo(9f, 17f, 9f, 14f); quadTo(9f, 11f, 12f, 11f); quadTo(15f, 11f, 15f, 14f)
    }
}

private val StopIcon: ImageVector by lazy {
    filled("Stop") { moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close() }
}

private val UpIcon: ImageVector by lazy { stroke("Up") { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) } }

private val DownIcon: ImageVector by lazy { stroke("Down") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }

/**
 * A queue item's artwork, as something loadable. The cast service writes a
 * relative thumbnail as `/media/…` already, which [imageModel] would prefix a
 * second time.
 */
internal fun castImage(mediaBaseUrl: String, url: String): String = when {
    url.isEmpty() -> ""
    url.startsWith("/") -> mediaBaseUrl.trimEnd('/') + url
    else -> imageModel(mediaBaseUrl, url)
}

/** What to say for a notice, or empty for nothing. Outside composition, so it takes [Strings]. */
internal fun castNoticeText(notice: CastNotice, strings: Strings): String = when (notice) {
    CastNotice.None -> ""
    CastNotice.QueueReloaded -> strings.castQueueReloaded
    is CastNotice.Refused -> when (notice.why) {
        CastRefusal.Live -> strings.castLive
        CastRefusal.Offline -> strings.castOffline
        else -> strings.castFailed
    }
    is CastNotice.Ended -> when (notice.end) {
        CastEnd.TakenOver -> strings.castTakenOver
        CastEnd.StoppedOnDevice -> strings.castStoppedOnDevice
        CastEnd.DeviceOffline -> strings.castDeviceOffline
        CastEnd.Failed -> strings.castQueueFailed
        CastEnd.Replaced -> strings.castReplaced
        else -> ""
    }
}

internal fun castStatus(session: CastSession, strings: Strings): String = when (session.state) {
    CastState.Loading -> strings.castLoadingOn(session.device.name)
    CastState.Paused -> strings.castPausedOn(session.device.name)
    else -> strings.castPlayingOn(session.device.name)
}

/** The speakers to choose from. Drawn from App.kt, outside every recording. */
@Composable
fun BoxScope.CastPickerSheet(
    devices: List<CastDevice>,
    loading: Boolean,
    visible: Boolean,
    backdrop: LayerBackdrop?,
    onPick: (CastDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    GlassSheet(visible = visible, backdrop = backdrop, scrim = Color.Transparent, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, bottom = Space.lg)) {
            Text(
                strings.castPickerTitle, color = Tokens.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Space.md),
            )
            when {
                devices.isEmpty() && loading -> Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Tokens.text2, strokeWidth = 2.dp)
                }
                devices.isEmpty() -> Text(strings.castPickerEmpty, color = Tokens.text2, fontSize = 14.sp)
                else -> devices.forEach { device ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(GlassRadius.control)
                            .pressable { onPick(device) }
                            .padding(horizontal = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (device.kind == CastDeviceKind.R1) SpeakerIcon else CastIcon,
                            contentDescription = null, tint = Tokens.text, modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, color = Tokens.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (device.kind == CastDeviceKind.R1) strings.castKindR1 else strings.castKindGoogle,
                                color = Tokens.text2, fontSize = 12.sp, lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A slider that sends when it settles: a seek per step of a drag would reload
 * the song on the R1 once per step. The shown value follows the finger at once.
 */
@Composable
private fun SettlingSlider(value: Int, max: Int, onSettled: (Int) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf<Int?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    LaunchedEffect(generation) {
        val target = held ?: return@LaunchedEffect
        delay(SETTLE_MILLIS)
        onSettled(target)
        held = null
    }
    GlassSlider(
        value = (held ?: value).toFloat(),
        onValueChange = {
            held = it.toInt()
            generation++
        },
        valueRange = 0f..max.coerceAtLeast(1).toFloat(),
        modifier = modifier,
    )
}

private const val SETTLE_MILLIS = 400L

/** The remote: what the speaker is playing and everything that can be done to it. */
@Composable
fun BoxScope.CastRemoteSheet(
    session: CastSession,
    visible: Boolean,
    backdrop: LayerBackdrop?,
    mediaBaseUrl: String,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
    onVolume: (Int) -> Unit,
    onHandoff: () -> Unit,
    onJump: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    val item = session.current
    GlassSheet(
        visible = visible, backdrop = backdrop, scrim = Color.Transparent, onDismiss = onDismiss,
        maxHeightFraction = REMOTE_FRACTION,
    ) {
        Column(
            Modifier
                // No scroll of its own: [GlassSheet] scrolls what it holds, and a
                // second one inside it is measured with an infinite height —
                // which crashed the iPhone the moment the remote opened.
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.lg, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    castStatus(session, strings), color = Tokens.text2, fontSize = 14.sp, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                RoundControl(CloseIcon, strings.castClose, onDismiss)
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Tokens.surface),
            ) {
                val cover = castImage(mediaBaseUrl, item?.thumbnailUrl.orEmpty())
                if (cover.isNotEmpty()) {
                    AsyncImage(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column {
                Text(
                    item?.title.orEmpty(), color = Tokens.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(item?.channel.orEmpty(), color = Tokens.text2, fontSize = 13.sp, maxLines = 1)
            }
            Column {
                SettlingSlider(session.positionSeconds, session.durationSeconds, onSeek)
                Row {
                    Text(formatDuration(session.positionSeconds), color = Tokens.text2, fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(session.durationSeconds), color = Tokens.text2, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterHorizontally),
            ) {
                if (session.state == CastState.Paused) {
                    RoundControl(PlayIcon, strings.castPlay, onPlayPause)
                } else {
                    RoundControl(PauseIcon, strings.castPause, onPlayPause, enabled = session.state != CastState.Loading)
                }
                RoundControl(NextIcon, strings.castNext, onNext, enabled = session.hasNext)
                RoundControl(StopIcon, strings.castStop, onStop)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.castVolume, color = Tokens.text2, fontSize = 14.sp, lineHeight = 18.sp)
                Spacer(Modifier.width(Space.md))
                SettlingSlider(session.volume, 100, onVolume, Modifier.weight(1f))
            }
            GlassButton(strings.castHandoff, onHandoff, modifier = Modifier.fillMaxWidth())
            Text(strings.castQueue, color = Tokens.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            session.queue.forEachIndexed { index, queued ->
                QueueRow(
                    item = queued, index = index, current = index == session.currentIndex,
                    last = index == session.queue.lastIndex, single = session.queue.size == 1,
                    mediaBaseUrl = mediaBaseUrl, onJump = onJump, onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown, onRemove = onRemove,
                )
            }
        }
    }
}

private const val REMOTE_FRACTION = 0.92f

@Composable
private fun QueueRow(
    item: CastItem,
    index: Int,
    current: Boolean,
    last: Boolean,
    single: Boolean,
    mediaBaseUrl: String,
    onJump: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .pressable(enabled = !current) { onJump(index) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(80.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Tokens.surface)) {
                val art = castImage(mediaBaseUrl, item.thumbnailUrl)
                if (art.isNotEmpty()) {
                    AsyncImage(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title, color = Tokens.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    if (current) strings.castNowPlaying else item.channel,
                    color = Tokens.text2, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
                )
            }
        }
        SmallControl(UpIcon, strings.castMoveUp(item.title), { onMoveUp(index) }, enabled = index > 0)
        SmallControl(DownIcon, strings.castMoveDown(item.title), { onMoveDown(index) }, enabled = !last)
        SmallControl(CloseIcon, strings.castRemove(item.title), { onRemove(index) }, enabled = !single)
    }
}

@Composable
private fun RoundControl(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Icon(
        icon, contentDescription = label, tint = if (enabled) Tokens.text else Tokens.text2,
        modifier = Modifier
            .size(Size.iconButton + 8.dp)
            .clip(CircleShape)
            .pressableGlassControl(CircleShape, enabled = enabled, onClick = onClick)
            .padding(Space.md),
    )
}

@Composable
private fun SmallControl(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    Icon(
        icon, contentDescription = label, tint = if (enabled) Tokens.text2 else Tokens.text2.copy(alpha = 0.3f),
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
    )
}

/**
 * "Something is playing on a speaker", for whoever is in the house: a session
 * started on another phone is just as much this one's to pause. The miniplayer's
 * capsule, so the two read as one family when both are up.
 */
@Composable
fun CastBar(session: CastSession, mediaBaseUrl: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val item = session.current
    Row(
        modifier
            .padding(horizontal = GLASS_MARGIN)
            .fillMaxWidth()
            .height(Size.topBar)
            .pressableGlassSurface(GLASS_SHAPE, onClick = onOpen)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CastIcon, contentDescription = strings.castOpenRemote, tint = Tokens.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        val art = castImage(mediaBaseUrl, item?.thumbnailUrl.orEmpty())
        if (art.isNotEmpty()) {
            AsyncImage(
                art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(56.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(item?.title.orEmpty(), color = Tokens.text, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(castStatus(session, strings), color = Tokens.text2, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        }
    }
}

// --- previews ----------------------------------------------------------------

private val previewKitchen = CastDevice("k", "Kitchen speaker", CastDeviceKind.Google)
private val previewR1 = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1)

private fun previewSession(state: CastState = CastState.Playing) = CastSession(
    id = "s1", device = previewR1, state = state, end = CastEnd.Running,
    queue = listOf(
        CastItem("a", "Motley Crew", "Post Malone", "", 185),
        CastItem("b", "Ta Nghe Gió Về", "Kênh", "", 229),
        CastItem("c", "Cay", "Khắc Hưng", "", 255),
    ),
    currentIndex = 0, positionSeconds = 90, durationSeconds = 185, volume = 40, version = 2,
)

@Composable
private fun PreviewFrame(vietnamese: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    MytubeTheme {
        CompositionLocalProvider(LocalStrings provides if (vietnamese) VietnameseStrings else LocalStrings.current) {
            Box(Modifier.fillMaxSize().background(Tokens.bg), content = content)
        }
    }
}

@Preview
@Composable
private fun CastPickerPreview() = PreviewFrame {
    CastPickerSheet(listOf(previewR1, previewKitchen), loading = false, visible = true, backdrop = null, onPick = {}, onDismiss = {})
}

@Preview
@Composable
private fun CastPickerLoadingPreview() = PreviewFrame {
    CastPickerSheet(emptyList(), loading = true, visible = true, backdrop = null, onPick = {}, onDismiss = {})
}

@Preview
@Composable
private fun CastPickerEmptyPreview() = PreviewFrame {
    CastPickerSheet(emptyList(), loading = false, visible = true, backdrop = null, onPick = {}, onDismiss = {})
}

@Preview
@Composable
private fun CastRemotePreview() = PreviewFrame { RemotePreviewContent(previewSession()) }

@Preview
@Composable
private fun CastRemoteLoadingPreview() = PreviewFrame { RemotePreviewContent(previewSession(CastState.Loading)) }

@Preview
@Composable
private fun CastRemoteVietnamesePreview() = PreviewFrame(vietnamese = true) {
    RemotePreviewContent(previewSession(CastState.Paused))
}

@Preview
@Composable
private fun CastBarPreview() = PreviewFrame(vietnamese = true) {
    CastBar(previewSession(), mediaBaseUrl = "", onOpen = {}, modifier = Modifier.align(Alignment.BottomCenter))
}

@Composable
private fun BoxScope.RemotePreviewContent(session: CastSession) {
    CastRemoteSheet(
        session = session, visible = true, backdrop = null, mediaBaseUrl = "", onDismiss = {}, onPlayPause = {},
        onNext = {}, onStop = {}, onSeek = {}, onVolume = {}, onHandoff = {}, onJump = {}, onMoveUp = {},
        onMoveDown = {}, onRemove = {},
    )
}
