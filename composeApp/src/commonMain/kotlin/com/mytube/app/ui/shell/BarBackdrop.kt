package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.mytube.app.ui.theme.Tokens

/**
 * What sits behind the floating top bar, tab bar and chip row.
 *
 * All three are drawn *over* the scrolling content — that is what the web app
 * does and what makes the feed read as one continuous page rather than a strip
 * of chrome above a window. Painted solid they were a wall: content slid under
 * an opaque rectangle and stopped existing at its edge.
 *
 * # A real material, after three attempts and one library swap
 *
 * Nothing in Compose Multiplatform 1.11 does a backdrop blur, and two routes
 * that look as though they should were built here and failed, differently:
 *
 * 1. **`UIVisualEffectView` through `UIKitView`** — the correct native answer,
 *    and it renders a flat grey band. Compose draws the scene into its own layer
 *    and gives an interop view a hole punched in it, so the effect view samples
 *    what is behind it *in the UIKit hierarchy*: the window's background. There
 *    is nothing there. (An effect view laid over the *whole* Compose view is a
 *    different arrangement and does work — that is what `ShellBar.swift` uses.)
 *
 * 2. **A hand-rolled `GraphicsLayer` with a `BlurEffect`** — record the content
 *    into a layer, record a second layer that draws the first, blur the second.
 *    It compiles, it is the documented way to reuse a drawing, and it
 *    **segfaults**: `SkRecordCanvas::onDrawTextBlob` inside
 *    `PictureRecorder.finishRecordingAsPicture`, on the first frame. Skia will
 *    not take a layer being recorded while it is also being drawn in the same
 *    frame. Crash report: `Mytube-2026-08-30-235643.ips`.
 *
 * Haze solved the second problem and served until now. It is gone in favour of
 * **Backdrop** (`io.github.kyant0:backdrop`), which manages the same layer
 * recording and adds the one thing Haze never had: a **lens**. A blur says the
 * content continues underneath; a lens bends what is behind the pane's edges the
 * way a thick piece of glass does, and that refraction is the whole difference
 * between frosted and *liquid* glass. It is also what makes the Android bars
 * match what iOS 26 draws for the tab bar.
 *
 * **Do not replace this with a hand-rolled GraphicsLayer** without a Compose
 * release note saying that is supported: it costs a build, a device install and
 * a crash to find out, and this comment exists so that cost is paid once.
 *
 * The lens needs `RuntimeShader` and so is **Android 13 and above**; blur is
 * Android 12 and above. Below that the library draws what it can, which is what
 * this app looked like before — a floor that is a downgrade rather than a hole.
 *
 * # The wash is still here, and is not dead code
 *
 * [LocalHaze] is null wherever nothing has set up a source — a Preview, the
 * setup form, any screen outside the shell — and the gradient is what is drawn
 * there. A bar that refused to draw without a blur would be a blank rectangle
 * over the app. The wash is nearly opaque at the screen's edge, where the clock
 * and the tab labels have to stay legible, and thins toward the inside so a
 * thumbnail visibly passes *under* the bar rather than being clipped by it.
 *
 * # It has no size of its own — and that is load-bearing
 *
 * Pass `Modifier.matchParentSize()` from the Box that holds it. An earlier
 * version applied `fillMaxSize()` inside, which is a different thing: a Box
 * measures its children against the incoming constraints, so each bar grew to
 * full height — the tab row drew across the top of the screen over the clock and
 * the rest of the app went blank behind an opaque sheet.
 */
@Composable
fun BarBackdrop(
    modifier: Modifier = Modifier,
    /**
     * Whether the solid edge is the top one.
     *
     * Only read by the fallback wash. The gradient runs from the screen's edge
     * inward, so getting this wrong puts the thin end under the text and the
     * solid end against nothing.
     */
    fromTop: Boolean = true,
    /**
     * The pane's outline.
     *
     * It has to be a `CornerBasedShape` — the lens effect refracts along the
     * *corners*, and the library refuses anything it cannot read a corner radius
     * from. A square-cornered bar is `RoundedCornerShape(0.dp)`, which is that
     * shape with nothing to refract.
     */
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    /**
     * How hard the pane is being pressed, or null for one nobody presses.
     *
     * The lens deepens with it, which is the half of a press a *sampled* pane
     * can answer — `pressSquish` grows the node and this bends more of what is
     * behind it. Measured off iOS 26's own tab bar: what reads loudest when a
     * finger lands is not that the capsule got bigger, it is that the material
     * lit up. See [GlassPress].
     */
    press: GlassPress? = null,
) {
    GlassBackdrop(modifier, LocalBackdrop.current, fromTop, shape, press = press)
}

/**
 * The same material, told which backdrop to read rather than reading the ambient
 * one.
 *
 * [BarBackdrop] is the shell's chrome and the shell's `LocalBackdrop` is always
 * the right answer for it. A sheet is not: the player's sheet must sample the
 * *watch page* it opens over, which is a different scene from the tab feed the
 * shell registers — so [GlassSheet] holds its own and passes it in here. Same
 * glass, same [TINT_GLASS], one place that knows how to paint it.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    fromTop: Boolean = true,
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    /**
     * How much of the app's own colour goes over the sample.
     *
     * [TINT_GLASS] for the bars and the chips, which are edges content passes
     * under. A sheet and an alert take [TINT_MODAL], which is darker: they are
     * a *surface* somebody reads and answers, and at the bars' tone the feed
     * showing through them competes with their own rows for the eye.
     */
    tint: Float = TINT_GLASS,
    /** See [BarBackdrop]'s own parameter. */
    press: GlassPress? = null,
) {
    if (backdrop != null) {
        Box(
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // The order is fixed by the library and the docs say so:
                    // colour filter, then blur, then lens. Applied the other way
                    // round the lens refracts an unblurred image and the blur
                    // then smears the refraction flat.
                    //
                    // `vibrancy` multiplies saturation by 1.5. It is what stops
                    // a frosted pane over a photograph turning grey — the blur
                    // averages colour away, and this puts back enough of it that
                    // a thumbnail passing under the bar still reads as that
                    // thumbnail.
                    vibrancy()
                    // **Almost no blur, and a large lens.** The opposite of what
                    // this started with, and the opposite of the first two
                    // attempts at fixing it.
                    //
                    // It was blur 16 against a 12/24 lens, inherited from the
                    // Haze version where blur was the only tool there was. Beside
                    // the platform's glass on the tab bar it read as a different
                    // material, and the first guess — that the platform blurred
                    // *harder* — was wrong twice over, at 12 and then at 20.
                    //
                    // **Measured, in one screenshot with both bars in it**: under
                    // the system's tab bar a map showed through with the road
                    // numbers and "Peterborough" perfectly legible. iOS 26's
                    // material barely blurs at all. It is a tint, a refraction at
                    // the rim, and a specular edge — what makes it read as glass
                    // is that you can still *see* through it.
                    //
                    // So: blur enough to take the hard edges off, and let the
                    // lens and the tint do the rest.
                    //
                    // 8 rather than the tutorial's 4, and the reason is what
                    // passes under each pane. The tab bar crosses thumbnails,
                    // where 4dp already reads as glass; the miniplayer crosses
                    // the feed's own captions, and 13sp text under 4dp is still
                    // sharp enough that the pane looks like a clear window with
                    // writing behind it. Reported as the miniplayer not blurring
                    // like the tab bar, when both carried the same number.
                    blur(GLASS_BLUR.toPx())
                    // Height then amount, both in pixels.
                    //
                    // `depthEffect` bends the middle of the pane as well as its
                    // edges, and `chromaticAberration` splits the colour at the
                    // rim the way real glass does — the two things the platform
                    // material has that a blur cannot imitate.
                    //
                    // **Android 13 and above only** — it is a `RuntimeShader`.
                    // Below that the library draws the blur without it, which is
                    // what this app looked like before, so the floor is a
                    // downgrade rather than a hole.
                    // **`refractionHeight` is capped at the shape's smallest
                    // corner radius** — the library's own constraint, because
                    // the refraction is computed from the rounded-rectangle SDF
                    // and there is nothing to bend along a corner tighter than
                    // it. 16 is inside a 56dp capsule's 28, and a chip's 8dp
                    // corner is why `liquidGlass` below carries smaller numbers
                    // rather than these.
                    // Read at draw time, so a press costs a redraw and not a
                    // recomposition of everything inside the bar.
                    val pressed = press?.fraction ?: 0f
                    lens(
                        refractionHeight = 16.dp.toPx() * (1f + PRESS_LENS_GAIN * pressed),
                        refractionAmount = 32.dp.toPx() * (1f + PRESS_LENS_GAIN * pressed),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                // The tint, over the effects rather than inside them. The blur
                // says the content continues underneath; this is what keeps a
                // 10sp tab label legible over whatever is passing.
                onDrawSurface = { drawRect(Tokens.bg.copy(alpha = tint)) },
            ),
        )
        return
    }

    // Where nothing has registered a backdrop — a Preview, the setup form, any
    // screen outside the shell — the wash is what is drawn. A bar that refused
    // to draw without one would be a blank rectangle over the app.
    //
    // 0.94 against 0.72: nearly opaque at the screen's edge, where the clock and
    // the tab labels have to stay legible, thinning toward the inside so a
    // thumbnail visibly passes *under* the bar rather than being clipped by it.
    val edge = Tokens.bg.copy(alpha = 0.94f)
    val inner = Tokens.bg.copy(alpha = 0.72f)
    val stops = if (fromTop) listOf(edge, inner) else listOf(inner, edge)

    Box(modifier.clip(shape).background(Brush.verticalGradient(stops)))
}

/**
 * What the bars sample, or null where nothing publishes one.
 *
 * A composition local rather than a parameter for the reason
 * [LocalMiniPlayerShowing] is one: it is an ambient fact about the shell, three
 * different places read it, and threading it through every screen signature is
 * how the next one to pin something forgets.
 */
val LocalBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The same sample with **no edge at all** — a blur and a tint, nothing else.
 *
 * # A page is not a pane
 *
 * [GlassBackdrop] is built for something that floats: a bar, a capsule, a
 * sheet. The lens and the highlight are what make those read as a *piece* of
 * glass — light bending at the rim is how an eye is told where the material
 * stops. A full-screen ground has no rim to bend at, so the same effects draw
 * a bright line up both sides of the screen and a refracted band across the
 * top and bottom, which reads as a frame somebody put around the page.
 * Reported exactly that way: *"màn watch cho nó blur thôi, ko có viền đc ko"*.
 *
 * So this is `drawPlainBackdrop` — the library's own answer for a sample with
 * no shape furniture on it — carrying [GLASS_BLUR] and the tint and nothing
 * else. `vibrancy` stays: it is not an edge, it is what stops a blurred
 * thumbnail going grey, and the page underneath is the feed.
 *
 * It is also cheaper by a whole `RuntimeShader`, which matters here for a
 * reason the panes do not have: this one is the width and height of the
 * screen and it is redrawn on every frame of the drag.
 *
 * # The tone is the sheet's, and there is no third number any more
 *
 * There was a `TINT_PAGE` at 0.86 for a day. It is gone: what was asked for is
 * *"tăng cái tint lên hay gì đó cho màn watch như cái bottomsheet, tao muốn
 * thấy mờ mờ như bottonsheet"* — the page should read like the sheet, and the
 * sheet's tone is [TINT_MODAL]. A number named after the same look as another
 * number, differing by four hundredths, is two places for one decision.
 *
 * It is worth saying why 0.95 is *more* see-through here than 0.90 sounded:
 * the sheet reads as glass because of what is behind it, not because of its
 * tint. It floats over the watch page, which is nearly black, so its own rows
 * win easily and the shapes underneath still show. This ground floats over the
 * *feed*, whose thumbnails are bright — and at 0.90 the two competed, which is
 * a muddier picture than a darker one.
 */
@Composable
fun PageBackdrop(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    tint: Float = TINT_MODAL,
    /**
     * How solid the ground is, read at **draw** time.
     *
     * A lambda, and it goes to the library's own `layerBlock` rather than into
     * a `Modifier.graphicsLayer` wrapped around this — which is what it was.
     * That arrangement is an offscreen alpha layer with a node inside it that
     * samples *another* layer, and one frame in which the whole watch layer is
     * absent is the symptom it is suspected of. `layerBlock` is the same alpha
     * applied by the node that is already drawing, so there is one layer here
     * instead of two.
     *
     * Read at draw time also means the fade costs a redraw rather than a
     * recomposition, which is the reason every other lambda this library takes
     * is shaped this way.
     */
    alpha: () -> Float = { 1f },
) {
    if (backdrop != null) {
        Box(
            modifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(0.dp) },
                effects = {
                    vibrancy()
                    blur(GLASS_BLUR.toPx())
                },
                layerBlock = { this.alpha = alpha() },
                onDrawSurface = { drawRect(Tokens.bg.copy(alpha = tint)) },
            ),
        )
        return
    }

    // No recording — a Preview, or any screen outside the shell. The page keeps
    // its own colour rather than becoming a hole.
    Box(modifier.graphicsLayer { this.alpha = alpha() }.background(Tokens.bg))
}

/**
 * How much of the app's own colour sits over the blur, everywhere.
 *
 * **One number, and it has to stay one.** There were two — 0.74 for the bars and
 * 0.86 for the miniplayer, on the reasoning that a panel carrying text wants
 * more of the app's colour over it than an edge content merely passes under.
 * That reasoning is fine in isolation and wrong in place: the miniplayer *rests
 * on* the tab bar and shares an edge with it, so the two shades read as two
 * different surfaces that happen to be next to each other, and the seam between
 * them is visible in any screenshot of the bottom of the screen. It was reported
 * exactly that way.
 *
 * The value kept is the higher one, deliberately: unifying downward would have
 * made the miniplayer's two lines of text *less* legible over a passing
 * thumbnail, and 10sp tab labels have room to spare at 0.86.
 *
 * # Then the tab bar stopped being Compose's, and 0.86 became the odd one out
 *
 * `ShellBar.swift` holds `Color.black.opacity(0.55)` over the platform's own
 * glass, arrived at by the same measurement for the same reason. With the two
 * bars sharing an edge at the bottom of the screen, a pane at 0.86 beside one at
 * 0.55 is the seam this constant was created to remove — reported from the phone
 * as the miniplayer being "khác loại" from the tab bar.
 *
 * It is 0.72, which is **not** Swift's 0.55, and the difference is measured
 * rather than sloppy: the platform's material blurs far harder than this one
 * does, so the same tint over a softer blur is not the same pane. At 0.55 here
 * the page's own text read straight through the miniplayer and collided with the
 * title on it — screenshotted, "BC News · 1.1K views" printed across "MÓN NỢ 11
 * NĂM". The two numbers are matched by how they *look* beside each other, which
 * is the only thing that was ever wrong.
 */
const val TINT_GLASS = 0.75f

/**
 * And the tone a sheet or an alert takes.
 *
 * Darker than the bars', deliberately, and it is the one place this app has two
 * numbers rather than one. The bars are read *against* the page moving under
 * them — that is what makes them look like glass edges. A sheet is a surface
 * somebody stops to read: its own rows have to win, and at 0.75 the thumbnails
 * behind them were still bright enough to compete. Reported from the phone,
 * twice: 0.90 was still not it. The reference given was the player's own
 * settings sheet, which is this same material over a page that happens to be
 * almost black — so what was being asked for was that *result*, and the number
 * that produces it over a feed of thumbnails is this one. The rim and the lens
 * still read at the edges, which is what keeps it a pane rather than a panel.
 */
const val TINT_MODAL = 0.95f


/**
 * How far the material blurs, everywhere it is used.
 *
 * One number for the two bars, the miniplayer, the sheet and the chips, for the
 * reason [TINT_GLASS] is one: these panes are read as a set, and a chip that
 * blurred less than the bar above it is the seam in a different parameter.
 *
 * It is deliberately small — see the note in [GlassBackdrop] about the map
 * showing through iOS 26's own tab bar. What makes this read as glass is the
 * lens and the tint; the blur only takes the hard edges off.
 */
val GLASS_BLUR = 8.dp

/**
 * How far in from the screen's edges every floating pane sits, and how round it
 * is.
 *
 * One margin and one radius for the top bar, the tab bar and the miniplayer,
 * because they are read as a set: three panes stacked up one edge of the screen,
 * and a different inset on any of them reads as a mistake rather than a margin.
 * The platform tab bar in `ShellBar.swift` uses the same 16.
 *
 * A **true capsule**, `percent = 50`, which is what Music uses and what the
 * platform tab bar in `ShellBar.swift` already draws. A fixed radius was tried
 * first on the worry that a 28dp curve would eat the first and last thing in the
 * row; it does not, because a capsule's left edge is at x=0 across the whole
 * middle of its height and everything in these rows is vertically centred. What
 * it does eat is a *corner* of something that reaches the pane's own top or
 * bottom edge — a square one. The miniplayer's picture used to be inset further
 * from the left than from the top for that reason, and that was wrong: it is a
 * circle, and a circle concentric with the capsule's own arc keeps an even gap
 * the whole way round at the same inset as the top and bottom.
 */
val GLASS_MARGIN = 16.dp
val GLASS_SHAPE = RoundedCornerShape(percent = 50)

/**
 * How light the selection pill's own wash is.
 *
 * White rather than a second surface token: `surface` and `surfaceHover` are
 * six units apart, which is what the design system uses for a pointer hovering
 * and what the Like button proved is invisible as a state.
 */
private const val PILL_WASH = 0.12f

/**
 * The travelling selection pill, as a lens.
 *
 * # Why the pill is not just a lighter rectangle
 *
 * It was `background(Tokens.text at 0.12f)` — a flat wash that moved. Beside
 * iOS 26's own tab bar, which a screen recording supplied, that is the wrong
 * material: there the pill is a **bubble**. Its rim carries a full spectrum and
 * the smear travels with it, which is the thing named in the report —
 * *"cái capsule nó sẽ phình to ra và có khuếch xạ ánh sáng xung quanh"*.
 *
 * So it samples, with the same `chromaticAberration` the bars carry and the
 * chip's smaller refraction, because the pill is chip-sized rather than
 * bar-sized. The wash stays: it is what says *lit*, and a lens alone over a
 * dark feed says nothing.
 *
 * # Where the reference cannot be followed, and why
 *
 * In the recording the **glyph** distorts as the pill slides over it. It cannot
 * here. This samples the layer `AppShell` records — the page behind the bar —
 * and the tabs are drawn over that, not into it. Refracting them would mean
 * recording the bar's own contents and putting a consumer inside that
 * recording, which is the Skia stack overflow this charter has now paid for
 * twice: `SkBlurImageFilter::onGetOutputLayerBounds`, a filter that contains
 * itself. So the pill bends the feed behind it and the glyph rides on top,
 * undistorted.
 *
 * @param press deepens the lens with the finger, [liquidGlass]'s own gain. The
 *   capsule and the pill share one press object, so the two halves of one
 *   movement cannot drift apart.
 */
@Composable
fun Modifier.selectionLens(
    shape: CornerBasedShape = GLASS_SHAPE,
    press: GlassPress? = null,
): Modifier {
    val backdrop = LocalBackdrop.current
        ?: return this.background(Tokens.text.copy(alpha = PILL_WASH), shape)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(GLASS_BLUR.toPx())
            // Read at draw time, which is what lets the press animate without
            // recomposing a row that is being dragged across.
            val pressed = press?.fraction ?: 0f
            lens(
                refractionHeight = 8.dp.toPx() * (1f + PRESS_LENS_GAIN * pressed),
                refractionAmount = 16.dp.toPx() * (1f + PRESS_LENS_GAIN * pressed),
                depthEffect = true,
                chromaticAberration = true,
            )
        },
        // Two rects, and the order is the whole point.
        //
        // The pill is a lighter patch **of the bar**, not a window cut through
        // it. It samples the same recording the capsule does, which is the raw
        // feed — so without the first rect the thumbnails passing underneath
        // arrive at full brightness and compete with the glyph and the word
        // sitting on top. Reported exactly that way: *"cái pill cho nó đục đục
        // tí đc ko? do ko nhìn rõ icon + text"*.
        //
        // [TINT_GLASS], the tone the capsule under it already wears: knock the
        // sample back to the bar's own level, then put the wash on top, and
        // what is left is the pane it always was — with the refraction added
        // rather than the legibility taken away.
        //
        // **`TINT_MODAL` with `vibrancy` removed was tried and was too far.**
        // That is the darker tone a surface somebody *reads* takes, and on
        // paper this pane qualifies: it carries a glyph and a word. Put on
        // screen beside this one it read as a hole punched in the bar — the
        // glass was gone and only the rim was left. Both were shown side by
        // side and this is the one that was chosen.
        //
        // The honest note is about how it was done rather than which won: the
        // tint and `vibrancy` were changed in the same build, so the first
        // measurement could not say which of them had done what. Two variables,
        // one step.
        onDrawSurface = {
            drawRect(Tokens.bg.copy(alpha = TINT_GLASS))
            drawRect(Tokens.text.copy(alpha = PILL_WASH))
        },
    )
}

/**
 * Record this screen, so the glass over it has something to sample.
 *
 * `AppShell` does this for the four tabs, and for a while that was the whole
 * app. It is not: the search screen, a channel page and the saved shelf are
 * routes of their own with no shell around them — and the **miniplayer floats
 * over all three**. On those screens it was sampling a recording nothing was
 * writing to, so the one pane that crosses every screen was the one pane that
 * stopped being glass whenever somebody opened a channel. Reported exactly that
 * way.
 *
 * It is a no-op where no backdrop has been provided — a Preview, the setup form
 * — rather than an error, for the same reason [GlassBackdrop] draws a wash
 * there.
 *
 * **Only one node may record at a time, and this enforces it.** A recording
 * inside a recording is not a bad look but a segfault — Skia optimises the outer
 * record while the inner one is still open, and the app drops to the springboard
 * with `SkRecordNoopSaveLayerDrawRestores` at the top of the trace. Measured, on
 * the day the playlists page became a tab: the page carried its own
 * `glassSource` because it *was* a route, and `AppShell` already records every
 * tab's content, so the same composable was correct in one place and fatal in
 * the other.
 *
 * So a screen keeps asking for this whatever it is used as, and the second ask
 * is a no-op. [LocalGlassRecording] is what carries the answer, and a screen
 * with no shell around it is still the one that records.
 */
@Composable
fun Modifier.glassSource(): Modifier {
    if (LocalGlassRecording.current) return this
    val backdrop = LocalBackdrop.current ?: return this
    return this.layerBackdrop(backdrop)
}

/**
 * Whether something up the tree is already recording the layer.
 *
 * Provided by [AppShell] around the tab it draws, and by nothing else: a route
 * is drawn on its own and records for itself.
 */
val LocalGlassRecording = androidx.compose.runtime.compositionLocalOf { false }

/**
 * The same material, for a control that sits **on** the page rather than being
 * one of its bars.
 *
 * # Why this exists when [glassControl] already did
 *
 * It did not blur. `glassControl` is paint — white at 0.09 with a sheen and a
 * rim — and it was chosen on the reasoning that anything *on* the page is inside
 * the layer the shell records, so a button would be sampling a recording of
 * itself.
 *
 * That reasoning is right about the page and wrong about the chips. The shell
 * records `content()` alone — the screens — and the chip row is a sibling of the
 * top bar, drawn over that recording and outside it. It could always have
 * sampled. Reported from the phone, accurately: *"chips nó trong suốt ko blur gì
 * cả"*, in a row sitting directly under a bar made of real glass.
 *
 * Everything genuinely inside the recording — the action pills on the watch
 * page, the description box, the sort options — keeps [glassControl], and for
 * the original reason.
 *
 * # Selected is a change of kind, not of shade
 *
 * A selected chip is the app's inverted surface: solid, light, with dark text on
 * it. Not a brighter piece of glass. That is the lesson the Like button cost —
 * `surface` and `surfaceHover` are six units apart, which is invisible as a
 * state — and it is why this takes the same `selected` flag [glassControl] does
 * rather than tuning the tint.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    selected: Boolean = false,
    /**
     * How hard it is being pressed, or null for a pane nobody presses.
     *
     * The lens deepens with it — a sheet pushed on is thicker where the finger
     * is, and bends more of what is behind it. The squash itself is
     * [pressSquish]'s, on the same object, so the two halves of one movement
     * cannot drift apart.
     */
    press: GlassPress? = null,
): Modifier {
    if (selected) return this.clip(shape).background(Tokens.invertBg)

    val backdrop = LocalBackdrop.current
        ?: return this.glassControl(shape, selected = false, press = press)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            // Smaller than the bars'. A chip is 32dp tall and a hundred wide;
            // the bars' numbers are sized for a pane the width of the screen,
            // and a 16dp refraction on a 32dp control bends the whole of it
            // rather than its edge.
            blur(GLASS_BLUR.toPx())
            // Read at draw time, which is the whole reason the press is an
            // object rather than a parameter: this lambda re-runs on every
            // frame of the animation and the control never recomposes.
            val pressed = press?.fraction ?: 0f
            lens(
                // At the chip's own corner radius, which is the library's
                // ceiling for this parameter. See the note on the bars' lens.
                refractionHeight = 8.dp.toPx() * (1f + PRESS_LENS_GAIN * pressed),
                refractionAmount = 16.dp.toPx() * (1f + PRESS_LENS_GAIN * pressed),
                depthEffect = true,
                chromaticAberration = true,
            )
        },
        // The bars' own tone, not one of its own. A chip sits directly under
        // the top bar and above the tab bar, and a third shade in that stack is
        // the seam [TINT_GLASS] exists to remove.
        onDrawSurface = { drawRect(Tokens.bg.copy(alpha = TINT_GLASS)) },
    )
}


