import SwiftUI
import ComposeApp

/// The whole app, which is Kotlin.
///
/// Everything above this line is Swift because iOS needs an entry point; nothing
/// below it is. `MainViewControllerKt` is generated from `MainViewController.kt`
/// in `iosMain`, and that is where the object graph is built — the same
/// `AppContainer` the Android side constructs in its Activity.
struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea(.all)
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
