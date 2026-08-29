package com.mytube.app.ui.i18n

import java.util.Locale

/**
 * The JVM target exists to run tests, not to ship a desktop app — but an
 * `expect` still needs an `actual` for it to compile at all. Reading the JVM
 * locale is the honest answer rather than pinning English: a test that asserts
 * Vietnamese formatting should be able to ask for it the same way the app does.
 */
actual fun deviceLanguage(): Language = Language.forTag(Locale.getDefault().language)
