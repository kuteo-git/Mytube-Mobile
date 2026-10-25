package com.mytube.app.ui

import com.mytube.app.domain.repository.ServerNotConfigured

/**
 * What a screen shows for a read that failed: the way to the settings when no
 * address has been set, and the failure's own words otherwise.
 *
 * One function because nine screens ask it, and the two answers are not shades
 * of one: a missing address is not a network fault, and a screen that says
 * "check your wifi" to somebody who never typed an address has misled them.
 */
internal fun <S> Throwable.toScreenState(needsServer: S, failed: (String) -> S): S =
    if (this is ServerNotConfigured) needsServer else failed(message ?: "could not reach the library")
