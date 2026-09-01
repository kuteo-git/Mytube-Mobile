import SwiftUI
import ComposeApp

/// The panes of glass over the video, drawn by iOS so they can be Liquid Glass.
///
/// # Why these are not drawn by Compose like everything else
///
/// `glassSurface` in Kotlin is paint — a dark fill, a sheen, a rim — and it is
/// paint because a backdrop blur over the picture is impossible from inside
/// Compose: the video is an `AVPlayerLayer` in a `UIKitView`, which Compose
/// never draws into the layer `BarBackdrop` samples. It was reported from the
/// phone exactly that way: the controls have glass turned on and do not blur.
///
/// **Measured before this file existed**, the same way `ShellBar.swift` was: a
/// throwaway capsule with `.glassEffect()`, laid over the whole Compose hosting
/// view, over a playing video. Two screenshots 1.2s apart — the content inside
/// the capsule was blurred, and it *changed* between them. A single screenshot
/// would not have told a live sample from a stale snapshot.
///
/// # What this file is not allowed to know
///
/// Where anything goes. Every rectangle here arrives from Kotlin, which laid the
/// controls out exactly as it does on Android and then declined to paint them.
/// That is the whole reason there is no `padding` or size constant in this file:
/// two shells drift when both of them own the numbers, and this one owns none.
@available(iOS 26.0, *)
struct PlayerGlass: View {
    let panes: [NativeGlassPane]

    var body: some View {
        // A GeometryReader rather than a ZStack: the rectangles are in the
        // hosting view's coordinates, which is the whole screen, and `.position`
        // takes a centre in the parent's space.
        GeometryReader { _ in
            ForEach(panes, id: \.id) { pane in
                // No `Color.clear` filling this stack.
                //
                // There was one, to give the stack a size before `.frame` below
                // did — and a `Color` in SwiftUI is a shape that fills its
                // space **and takes touches**. So every tap that landed on a
                // pane was swallowed by it: the button under the finger never
                // fired, and neither did the Compose control still composed
                // beneath. Measured on the simulator — the gear could not be
                // opened at all, and a tap on the pause disc fell through to the
                // picture and merely hid the controls.
                ZStack(alignment: .topLeading) {
                    ForEach(pane.items, id: \.id) { item in
                        Group {
                            if item.interactive {
                                Button {
                                    NativeGlassBridge.shared.pressItem(id: item.id)
                                } label: {
                                    face(item)
                                }
                                .buttonStyle(.plain)
                            } else {
                                // The clock and the fullscreen title. A readout
                                // in a Button would be a control that looks
                                // pressable and does nothing, which is the one
                                // thing §5 of the server charter forbids
                                // outright.
                                face(item)
                            }
                        }
                        .foregroundStyle(tint(item))
                        // Relative to the pane, because that is what the ZStack
                        // is anchored to. Kotlin reports both in screen
                        // coordinates and the subtraction happens here rather
                        // than there: the pane is what moves, and an item that
                        // carried an absolute position would need re-reporting
                        // every time its pane did.
                        .position(
                            x: item.x - pane.x + item.width / 2,
                            y: item.y - pane.y + item.height / 2
                        )
                    }
                }
                .frame(width: pane.width, height: pane.height)
                .environment(\.colorScheme, .dark)
                .glassEffect(.regular.tint(Self.tint), in: shape(pane))
                .position(x: pane.x + pane.width / 2, y: pane.y + pane.height / 2)
                // Kotlin says when to start fading, not when it has finished.
                //
                // Compose fades its own controls and drops the composable at the
                // end of that; a pane that waited for the disposal stayed solid
                // through the whole fade — measured on the phone in fullscreen
                // as the seek bar leaving a beat before the buttons. So the flag
                // arrives at the start and both sides run the same duration:
                // `CONTROLS_FADE_MILLIS` in `PlayerControls.kt`, 0.2 here, and
                // both say so.
                .opacity(pane.visible ? 1 : 0)
                .animation(.easeInOut(duration: 0.2), value: pane.visible)
                .allowsHitTesting(pane.visible)
                .transition(.opacity)
            }
        }
        .ignoresSafeArea(.all)
        // The panes fade rather than vanish.
        //
        // Compose fades its own controls out and then drops the composable; the
        // pane leaves this side only at the end of that, so without a fade of
        // its own the glass stays solid through the whole fade and then pops.
        // Measured on the simulator — it reads as the picture blinking.
        //
        // Keyed on the ids, not the panes: the rectangles change while a video
        // is playing (the clock is a pane's width) and animating on those would
        // slide every control every second.
        .animation(.easeInOut(duration: 0.2), value: panes.map(\.id))
    }

    /// A glyph, a word, or a dot — whichever this item is.
    ///
    /// The size and the box are Kotlin's in every case: `item.width` and
    /// `item.height` are what the composable measured, so a title truncated to
    /// fit a landscape phone is truncated to the same width here.
    @ViewBuilder
    private func face(_ item: NativeGlassItem) -> some View {
        ZStack {
            if item.dot {
                Circle().frame(width: item.width, height: item.height)
            } else if item.symbol.isEmpty {
                Text(item.text)
                    .font(.system(size: item.pointSize, weight: item.bold ? .medium : .regular))
                    .lineLimit(1)
                    .truncationMode(.tail)
            } else {
                Image(systemName: item.symbol)
                    .font(.system(size: item.pointSize))
            }

            // On is an underline, never a filled glyph.
            //
            // Kotlin learned this one the expensive way and the note is in
            // `PlayerControls.kt`: the filled CC icon was a white box with the
            // letters knocked out, and a tint repaints every path, so it became
            // a solid white square. A mark beside the glyph cannot have that
            // fault.
            //
            // A ZStack, not a VStack: the mark is drawn *over* the button. A
            // VStack was the first version and it was reported at once — the
            // mark takes a row of its own, so the glyph is pushed off the centre
            // of its own button whether or not the mark is showing.
            if item.on {
                VStack {
                    Spacer()
                    Rectangle().frame(width: 22, height: 2)
                }
                .padding(.bottom, 8)
            }
        }
        .frame(width: item.width, height: item.height)
        .contentShape(Rectangle())
    }

    /// White at whatever solidity Kotlin asked for, unless it sent a colour.
    ///
    /// The live badge's red arrives as `0xAARRGGBB` rather than being named
    /// here: it is `Tokens.brand`, and a Swift copy of it is a second place it
    /// can be wrong.
    private func tint(_ item: NativeGlassItem) -> Color {
        guard item.tintArgb != 0 else { return .white.opacity(item.opacity) }
        let argb = UInt32(truncatingIfNeeded: item.tintArgb)
        return Color(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255 * item.opacity
        )
    }

    /// A capsule, or a rounded rectangle. `-1` is the capsule, as `GlassPaneShape`
    /// on the Kotlin side documents — a sentinel that is not a legal radius, so
    /// it cannot be mistaken for one.
    private func shape(_ pane: NativeGlassPane) -> AnyShape {
        pane.cornerRadius < 0
            ? AnyShape(Capsule())
            : AnyShape(RoundedRectangle(cornerRadius: pane.cornerRadius))
    }

    /// The same tone `ShellBar` holds over its glass, and for the same measured
    /// reason: `.regular` takes its colour from what passes under it, and over a
    /// bright frame an untinted pane goes nearly white and takes the white glyphs
    /// with it. This is the platform's `TINT_GLASS`.
    private static let tint = Color.black.opacity(0.55)
}

/// What Kotlin pushes, held where SwiftUI can watch it.
///
/// Separate from `ShellModel` because the two answer to different things — that
/// one is the shell's chrome, this one is whatever the player currently has on
/// screen — and because a single `@Observable` holding both would redraw the tab
/// bar every time the controls faded.
@available(iOS 26.0, *)
@Observable
final class PlayerGlassModel {
    var panes: [NativeGlassPane] = []

    func connect() {
        NativeGlassBridge.shared.onPanes = { [weak self] panes in
            self?.panes = panes
        }
    }
}
