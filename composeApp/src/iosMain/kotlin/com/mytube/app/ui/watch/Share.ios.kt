package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationPopover
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * The system share sheet.
 *
 * This used to be an empty lambda with a comment saying so, on the reasoning
 * that `UIActivityViewController` needs the presenting view controller and that
 * could not be reached without an Xcode project. There is an Xcode project now,
 * and what the button did in the meantime was nothing — silently, which is worse
 * than a button that is absent: a viewer presses Share, no sheet appears, and
 * nothing anywhere says why.
 *
 * ## Finding the controller to present from
 *
 * `keyWindow` on `UIApplication` has been deprecated since iOS 13 because an app
 * can have several scenes. The supported route is the connected scenes: find the
 * key window among them, then walk down from its root through whatever is
 * already presented — presenting from a controller that is itself covered raises
 * at runtime, and on a screen whose player settings open a bottom sheet that is
 * a real possibility rather than a hypothetical one.
 *
 * ## Why the URL is built here and not passed as a string
 *
 * `UIActivityViewController` treats an `NSURL` and a string differently: a URL
 * offers Safari, Messages with a preview, and Copy as a link; a string offers
 * Copy as text. Handing it the string would produce a sheet that works and
 * shares the wrong kind of thing. A URL that will not parse falls back to the
 * text, which is the only honest thing left to share.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberShare(): (String) -> Unit = share@{ link ->
    val host = topViewController() ?: return@share
    val item: Any = NSURL.URLWithString(link) ?: link

    val sheet = UIActivityViewController(
        activityItems = listOf(item),
        applicationActivities = null,
    )
    // An iPad needs an anchor or presenting the popover raises. There is no iPad
    // build, and an anchor costs two lines against a crash that would only ever
    // be found on a device nobody here tests on.
    //
    // `setSourceView` on the controller rather than an assignment through a
    // safe call: `a?.b = c` is not Kotlin, and the popover controller is null on
    // a phone — where the sheet is presented as a sheet and needs no anchor.
    sheet.setModalPresentationStyle(UIModalPresentationPopover)
    sheet.popoverPresentationController?.setSourceView(host.view)

    host.presentViewController(sheet, animated = true, completion = null)
}

/** The controller actually on screen, or null before there is a window. */
private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
        ?: return null

    var controller = window.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
