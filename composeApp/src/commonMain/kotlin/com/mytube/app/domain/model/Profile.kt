package com.mytube.app.domain.model

/**
 * A member of the household.
 *
 * There is no password and there never will be: the server charter is explicit
 * that this is *"a profile picker, not a login"* — 2–5 people on one wifi, with
 * media URLs left unprotected on purpose. The separation is convenience, and
 * pretending otherwise would be security theatre over metadata anyone on the
 * LAN can already fetch.
 */
data class Profile(val id: String, val name: String)
