package com.mytube.app.ui.i18n

/**
 * The language this device is set to.
 *
 * `expect/actual` because there is no multiplatform way to ask, and the two
 * platforms answer differently enough that a library would only be hiding one
 * line each. Android has a `LocaleList`, iOS an ordered list of preferred
 * languages.
 *
 * There is no stored preference yet, deliberately: a language switch is a
 * settings screen that does not exist. Following the device is the honest
 * default, and it matches the web app, which reads `navigator.language` for a
 * machine nobody has set up.
 */
expect fun deviceLanguage(): Language
