package app.overbay.orbit.util

import android.view.Display
import android.view.Window

object DisplayRefreshRateHelper {
    /**
     * Configure window display refresh rate.
     *
     * Keeping preferredDisplayModeId at 0 (adaptive) allows modern LTPO/VRR displays
     * to conserve battery during static reading while high refresh rate (up to 120Hz)
     * is boosted dynamically during interactions, kinetic gestures, and transitions.
     */
    fun configureHighRefreshRate(window: Window, display: Display?) {
        val params = window.attributes
        params.preferredDisplayModeId = 0
        params.preferredRefreshRate = 0f
        window.attributes = params
    }
}
