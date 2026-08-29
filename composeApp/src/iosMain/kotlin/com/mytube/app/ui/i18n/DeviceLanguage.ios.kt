package com.mytube.app.ui.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The first of the user's preferred languages, which is what iOS actually shows
 * the app in — `NSLocale.currentLocale` answers the region format instead, and
 * a phone set to English in Vietnam reports `vi` from it.
 */
actual fun deviceLanguage(): Language {
    val tag = NSLocale.preferredLanguages.firstOrNull() as? String ?: return Language.English
    return Language.forTag(tag)
}
