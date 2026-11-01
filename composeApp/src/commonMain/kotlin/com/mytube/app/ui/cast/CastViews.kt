package com.mytube.app.ui.cast

import androidx.compose.foundation.background
import com.mytube.app.ui.shell.rememberSelectionTick
import com.mytube.app.ui.shell.rememberLandingKnock
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import com.mytube.app.ui.watch.ControlButton
import com.mytube.app.ui.watch.DiscButton
import com.mytube.app.ui.watch.PreviousIcon
import com.mytube.app.ui.watch.ChevronIcon
import com.mytube.app.ui.shell.GlassItem
import com.mytube.app.ui.shell.GlassPaneShape
import com.mytube.app.ui.shell.GlassPane
import com.mytube.app.ui.watch.MiniBar
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
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
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.GlassSheet
import com.mytube.app.ui.shell.GlassSlider
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.shell.pressableGlassControl
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

/** Three bars: the handle a row is dragged by. */
private val HandleIcon: ImageVector by lazy {
    stroke("Handle") {
        moveTo(5f, 8f); lineTo(19f, 8f)
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(5f, 16f); lineTo(19f, 16f)
    }
}


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

/**
 * Where the picture is on the watch screen while a speaker plays the song: its
 * artwork. The watch screen *is* the remote — the description, the comments and
 * the queue are below, as they are for a video playing here.
 *
 * Apart from [CastStage], because the two do different things when the screen
 * is dragged down: the artwork shrinks into the miniplayer's window, as the
 * video does, and the controls fade, as the player's do.
 */
@Composable
fun CastArtwork(session: CastSession, mediaBaseUrl: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        val cover = castImage(mediaBaseUrl, session.current?.thumbnailUrl.orEmpty())
        if (cover.isNotEmpty()) {
            AsyncImage(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // The controls are white over a picture of any brightness.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = STAGE_SCRIM)))
    }
}

/**
 * What the player draws over a video, for a speaker: the same glass panes and
 * buttons as [com.mytube.app.ui.watch.PlayerControls] — platform glass on iOS
 * 26, painted elsewhere — so the speaker's screen is the watch screen's.
 */
@Composable
fun CastStage(
    session: CastSession,
    onBack: () -> Unit,
    onCast: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val playing = session.state != CastState.Paused
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassPane("cast-back", GlassPaneShape.Capsule) {
                GlassItem(id = "cast-back", symbol = "chevron.down", onPress = onBack) {
                    ControlButton(icon = ChevronIcon, label = strings.back, width = 48.dp, onClick = onBack)
                }
            }
            Text(
                castStatus(session, strings), color = Color.White, fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Space.md),
            )
            GlassPane("cast-cast", GlassPaneShape.Capsule) {
                GlassItem(id = "cast-cast", symbol = "hifispeaker.fill", onPress = onCast) {
                    ControlButton(icon = CastActiveIcon, label = strings.castButton, width = 48.dp, onClick = onCast)
                }
            }
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiscButton(
                id = "cast-previous", symbol = "backward.end.fill", icon = PreviousIcon,
                label = strings.playPrevious, enabled = session.currentIndex > 0, onClick = onPrevious,
            )
            DiscButton(
                id = "cast-play", symbol = if (playing) "pause.fill" else "play.fill",
                icon = if (playing) PauseIcon else PlayIcon,
                label = if (playing) strings.castPause else strings.castPlay,
                enabled = session.state != CastState.Loading, size = 64.dp, onClick = onPlayPause,
            )
            DiscButton(
                id = "cast-next", symbol = "forward.end.fill", icon = NextIcon,
                label = strings.castNext, enabled = session.hasNext, onClick = onNext,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.md)) {
            SettlingSlider(session.positionSeconds, session.durationSeconds, onSeek)
            Row(Modifier.padding(bottom = Space.sm)) {
                Text(formatDuration(session.positionSeconds), color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(session.durationSeconds), color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

private const val STAGE_SCRIM = 0.45f

/**
 * The speaker's volume, in the alert the lit cast button opens — beside the
 * other things done to the speaker as a whole. The phone's own buttons move it
 * too (see [FollowVolume]).
 */
@Composable
fun CastVolume(session: CastSession, onVolume: (Int) -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.castVolume, color = Tokens.text2, fontSize = 14.sp, lineHeight = 18.sp)
        Spacer(Modifier.width(Space.md))
        SettlingSlider(session.volume, 100, onVolume, Modifier.weight(1f))
    }
}

/** The speaker's queue, where up next is for a video playing here. */
fun LazyListScope.castQueueItems(
    session: CastSession,
    mediaBaseUrl: String,
    drag: QueueDrag,
    /** The list this is in, scrolled while a row is held near its edge. */
    list: LazyListState,
    onJump: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    item(key = "cast-queue") {
        Text(
            LocalStrings.current.castQueue, color = Tokens.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = Space.sm),
        )
    }
    val shown = drag.order ?: session.queue
    val keys = queueKeys(shown)
    val playing = queueKeys(session.queue).getOrNull(session.currentIndex)
    items(shown.size, key = { keys[it] }) { i ->
        val key = keys[i]
        val held = drag.holding && drag.heldIndex == i
        val index by rememberUpdatedState(i)
        val queue by rememberUpdatedState(session.queue)
        // Lifted and set down are a knock each; every new place on the way is a
        // tick, as a picker's notches are.
        val knock = rememberLandingKnock()
        val tick = rememberSelectionTick()
        val density = LocalDensity.current
        if (held) {
            LaunchedEffect(Unit) {
                val edge = with(density) { QUEUE_SCROLL_EDGE.toPx() }
                val most = with(density) { QUEUE_SCROLL_STEP.toPx() }
                while (true) {
                    withFrameNanos {}
                    val info = list.layoutInfo
                    val row = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
                    val top = row.offset + drag.offsetPx
                    val step = edgeScrollStep(
                        top, top + row.size,
                        viewportStart = 0f,
                        viewportEnd = (info.viewportEndOffset - info.afterContentPadding).toFloat(),
                        edgePx = edge, maxStepPx = most,
                    )
                    // The list moves under a finger that has not: the row
                    // keeps up by the distance actually scrolled.
                    if (step != 0f && drag.dragBy(list.scrollBy(step))) tick()
                }
            }
        }
        Box(
            // The held row is placed by the finger, not animated to its slot;
            // the rest slide into theirs.
            (if (held) Modifier.zIndex(1f) else Modifier.animateItem())
                .onSizeChanged { drag.rowPx = it.height.toFloat() }
                // Read while drawing, so the row follows the finger without
                // recomposing the list on every move.
                .graphicsLayer { translationY = if (drag.holding && drag.heldIndex == index) drag.offsetPx else 0f }
                .padding(horizontal = Space.lg)
                .background(if (held) Tokens.surface else Color.Transparent, RoundedCornerShape(12.dp)),
        ) {
            QueueRow(
                item = shown[i], index = i, current = key == playing,
                single = shown.size == 1, mediaBaseUrl = mediaBaseUrl, onJump = onJump, onRemove = onRemove,
                handle = Modifier.pointerInput(key) {
                    // focus-guard: the speaker's queue is the phone's; a television never casts.
                    detectDragGestures(
                        onDragStart = {
                            knock()
                            drag.start(index, queue)
                        },
                        onDragEnd = {
                            knock()
                            drag.end()?.let { (from, to) -> onMove(from, to) }
                        },
                        onDragCancel = { drag.cancel() },
                    ) { change, amount ->
                        change.consume()
                        if (drag.dragBy(amount.y)) tick()
                    }
                },
            )
        }
    }
}

/** How near an edge a held row starts the list scrolling, and how fast at most per frame. */
private val QUEUE_SCROLL_EDGE = 72.dp
private val QUEUE_SCROLL_STEP = 18.dp

@Composable
private fun QueueRow(
    item: CastItem,
    index: Int,
    current: Boolean,
    single: Boolean,
    mediaBaseUrl: String,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    /** The drag gesture, on the handle only: anywhere else on the row the list scrolls. */
    handle: Modifier,
) {
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            HandleIcon, contentDescription = strings.castReorder(item.title), tint = Tokens.text2,
            modifier = Modifier.size(width = 40.dp, height = 48.dp).then(handle).padding(horizontal = 8.dp, vertical = 12.dp),
        )
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
        SmallControl(CloseIcon, strings.castRemove(item.title), { onRemove(index) }, enabled = !single)
    }
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
 * A song playing on a speaker, resting where the miniplayer rests and in the
 * same capsule, with the artwork in its round window. Pressed, it opens the
 * watch screen, which is the remote while a speaker plays.
 */
@Composable
fun CastMiniPlayer(
    session: CastSession,
    mediaBaseUrl: String,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    collapse: Float,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val item = session.current
    MiniBar(
        window = {
            val art = castImage(mediaBaseUrl, item?.thumbnailUrl.orEmpty())
            if (art.isNotEmpty()) {
                AsyncImage(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        },
        title = item?.title.orEmpty(),
        channel = castStatus(session, LocalStrings.current),
        progress = castProgress(session),
        isPlaying = session.state != CastState.Paused,
        onExpand = onExpand,
        onPlayPause = onPlayPause,
        onClose = onClose,
        collapse = collapse,
        bottomInset = bottomInset,
        modifier = modifier,
    )
}

/** How far through the song the speaker is, for the miniplayer's line. */
internal fun castProgress(session: CastSession): Float =
    if (session.durationSeconds <= 0) 0f
    else (session.positionSeconds.toFloat() / session.durationSeconds).coerceIn(0f, 1f)

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
private fun CastStagePreview() = PreviewFrame {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        CastArtwork(previewSession(), "")
        CastStage(previewSession(), {}, {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun CastStageLoadingPreview() = PreviewFrame {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        CastArtwork(previewSession(CastState.Loading), "")
        CastStage(previewSession(CastState.Loading), {}, {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun CastBelowVietnamesePreview() = PreviewFrame(vietnamese = true) {
    LazyColumn {
        castQueueItems(previewSession(), "", QueueDrag(), LazyListState(), {}, { _, _ -> }, {})
    }
}

@Preview
@Composable
private fun CastMiniPlayerPreview() = PreviewFrame(vietnamese = true) {
    CastMiniPlayer(
        previewSession(), mediaBaseUrl = "", onExpand = {}, onPlayPause = {}, onClose = {},
        collapse = 0f, bottomInset = 0.dp, modifier = Modifier.align(Alignment.BottomCenter),
    )
}
