import SwiftUI
import ComposeApp

/// The whole app, which is Kotlin.
///
/// Everything above this line is Swift because iOS needs an entry point; nothing
/// below it is — with one exception since iOS 26, and it is a deliberate one:
/// the panes over the video, which the system draws so they can blur a picture
/// Compose cannot see. See `PlayerGlass.swift` and `GlassPane.kt`.
///
/// `MainViewControllerKt` is generated from `MainViewController.kt` in
/// `iosMain`, and that is where the object graph is built — the same
/// `AppContainer` the Android side constructs in its Activity.
struct ContentView: View {
    var body: some View {
        if #available(iOS 26.0, *) {
            GlassShell()
        } else {
            // Every earlier iOS gets the app as Compose draws it, controls
            // included: `.glassEffect` is iOS 26, so there is no platform layer
            // to hand the player's panes to and `glassSurface` paints them.
            ComposeView(nativeGlass: false).ignoresSafeArea(.all)
        }
    }
}

@available(iOS 26.0, *)
private struct GlassShell: View {
    @State private var glass = PlayerGlassModel()

    var body: some View {
        ZStack(alignment: .bottom) {
            ComposeView(nativeGlass: true).ignoresSafeArea(.all)

            // The panes over the video. Between the Compose view and the tab
            // bar deliberately: they are chrome over the picture, and the bar is
            // chrome over everything.
            PlayerGlass(panes: glass.panes)

            // The tab bar is Compose's again.
            //
            // `ShellBar.swift` drew it with the system's own material for one
            // day, and the day found the flaw: a platform bar is one pane of a
            // set — the top bar, the chip row and the miniplayer are the others
            // — and the other three cannot be platform-drawn, because a live
            // video window and a scrolling row of thumbnails cannot leave the
            // Compose tree. Four panes stacked up the edges of one screen in two
            // materials reads as a mistake, which is how it was reported.
            //
            // So the tab bar came back to where its siblings are, and the
            // material they all share is Backdrop's — the same library, tuned
            // against the platform's own glass while both were on screen
            // together. `ShellBar.swift` and `ShellBridge.kt` are kept, unused,
            // because the measurement in them is the argument for this.
        }
        .ignoresSafeArea(.keyboard)
        .onAppear { glass.connect() }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    let nativeGlass: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(nativeGlass: nativeGlass)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
