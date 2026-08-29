package com.mytube.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the server-address screen is doing.
 *
 * Nothing here is nullable, so the states are said out loud rather than implied
 * by a null: `Untested` is a real answer and a different one from `Failed`.
 */
enum class CheckState { Untested, Checking, Reachable, Failed }

data class ServerSetupState(
    val address: String = "",
    val check: CheckState = CheckState.Untested,
    val saved: Boolean = false,
) {
    /**
     * Nothing to test and nothing to save until something is typed.
     *
     * A button that can be pressed and does nothing is worse than one that is
     * plainly disabled — the whole point of this screen is that somebody is
     * already unsure whether they typed the right thing.
     */
    val canSubmit: Boolean get() = address.isNotBlank()
}

/**
 * Where the library is.
 *
 * The first screen anybody sees, and the only one that can leave the app unable
 * to do anything at all. Modelled on the way Home Assistant asks: type an
 * address, check it, keep it.
 *
 * ## Why the address is typed and not discovered
 *
 * The Mac's address comes from DHCP and the server charter still lists a static
 * LAN IP as an open item. mDNS would find it without being asked and fails
 * quietly on ordinary home routers — leaving somebody in front of a spinner with
 * nothing to type. A field always works.
 *
 * ## Why checking is separate from saving
 *
 * Testing after saving is testing something already accepted. The same rule the
 * web app's speech and proxy screens follow: the button checks *what is in the
 * field*, so somebody learns before committing rather than after.
 */
class ServerSetupViewModel(private val server: ServerRepository) : ViewModel() {

    private val _state = MutableStateFlow(ServerSetupState())
    val state: StateFlow<ServerSetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = server.baseUrl()
            if (existing.isNotBlank()) _state.update { it.copy(address = existing) }
        }
    }

    fun onAddressChanged(value: String) {
        // Any edit invalidates the previous verdict. Leaving a green tick beside
        // a half-edited address is the screen telling a lie about the thing it
        // exists to confirm.
        _state.update { it.copy(address = value, check = CheckState.Untested, saved = false) }
    }

    fun check() {
        val address = _state.value.address
        if (address.isBlank()) return

        _state.update { it.copy(check = CheckState.Checking) }
        viewModelScope.launch {
            val ok = server.reachable(address)
            _state.update {
                it.copy(check = if (ok) CheckState.Reachable else CheckState.Failed)
            }
        }
    }

    /**
     * Saves whatever was typed, tested or not.
     *
     * Deliberately not gated on a successful check. The Mac may simply be asleep
     * at the moment somebody sets their phone up, and refusing to remember a
     * correct address because the server is off would be the app being clever at
     * the user's expense.
     */
    fun save() {
        val address = _state.value.address
        if (address.isBlank()) return

        viewModelScope.launch {
            server.setBaseUrl(address)
            _state.update { it.copy(saved = true) }
        }
    }
}
