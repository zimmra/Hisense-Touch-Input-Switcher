package com.zimindustries.inputswitcher

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * The single entry point every button uses. Picks the configured [SourceTransport], runs the
 * switch, records the outcome for the setup screen, and toasts on failure.
 */
object SourceSwitcher {

    /** Logcat tag. `adb logcat -s InputSwitcher` shows everything this app does. */
    const val TAG = "InputSwitcher"

    fun transport(context: Context): SourceTransport =
        when (Settings.transportKind(context)) {
            TransportKind.DIRECT -> DirectTransport
            TransportKind.IP -> IpControlTransport
        }

    /**
     * Switch the display to [source]. Safe to call from any Activity or BroadcastReceiver on the
     * main thread. [onResult] (optional) is invoked once, on the main thread.
     */
    fun switch(context: Context, source: Source, onResult: ((SwitchResult) -> Unit)? = null) {
        val app = context.applicationContext
        val transport = transport(app)
        Log.i(TAG, "switch -> ${source.label} (source_id=${source.id}) via ${transport.displayName}")

        transport.switch(app, source) { result ->
            if (result.ok) Log.i(TAG, result.summary()) else Log.w(TAG, result.summary())
            SwitchStatus.record(app, result)
            if (!result.ok) {
                Toast.makeText(
                    app,
                    app.getString(R.string.toast_switch_failed, source.label, result.message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            onResult?.invoke(result)
        }
    }
}
