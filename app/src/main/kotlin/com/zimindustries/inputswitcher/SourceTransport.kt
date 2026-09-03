package com.zimindustries.inputswitcher

import android.content.Context

/**
 * Outcome of one switch attempt. Persisted by [SwitchStatus] so the setup screen can show the last
 * result, and used to decide whether to toast an error.
 */
data class SwitchResult(
    val ok: Boolean,
    val transport: String,
    val source: Source,
    val message: String,
    val at: Long = System.currentTimeMillis(),
) {
    fun summary(): String =
        (if (ok) "OK" else "FAILED") + " · " + source.label + " via " + transport +
            (if (message.isNotEmpty()) " · $message" else "")
}

/**
 * A way of telling the display to change input. The UI only ever talks to [SourceSwitcher]; the
 * transport behind it can be swapped (Direct broadcast, IP control, Shizuku, ...) without touching
 * any button.
 */
interface SourceTransport {
    /** Short human-readable name shown in the setup screen status line ("Direct", "IP control"). */
    val displayName: String

    /**
     * Switch to [source]. Must be called on the main thread. [onResult] is always invoked exactly
     * once, on the main thread, possibly after a delay or a background network call.
     */
    fun switch(context: Context, source: Source, onResult: (SwitchResult) -> Unit)
}
