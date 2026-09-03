package com.zimindustries.inputswitcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The mechanism verified from ADB on the 75WE3FE:
 *
 * ```
 * am broadcast -a com.cvte.tvapi.action.SET_INPUT_SOURCE --ei source_id <id>
 * am start -n com.cvte.tv.setting/.TifPlayerActivity
 * ```
 *
 * Step 1 is picked up by `com.cvte.tv.api.impl`'s HotkeyService (a context-registered receiver,
 * no permission on the filter) and sets the source in CVTE's video HAL. Step 2 launches the system
 * activity that reads the HAL's current source and tunes a TvView to the matching TIF passthrough
 * input. Both steps are always performed, even when the display is already on an HDMI source.
 */
object DirectTransport : SourceTransport {

    const val ACTION_SET_INPUT_SOURCE = "com.cvte.tvapi.action.SET_INPUT_SOURCE"
    private const val EXTRA_CVTE_SOURCE_ID = "source_id"

    /** Delay between the HAL broadcast and starting the player, so the HAL has settled. */
    private const val PLAYER_DELAY_MS = 150L

    val TIF_PLAYER: ComponentName =
        ComponentName("com.cvte.tv.setting", "com.cvte.tv.setting.TifPlayerActivity")

    override val displayName: String get() = "Direct"

    override fun switch(context: Context, source: Source, onResult: (SwitchResult) -> Unit) {
        val app = context.applicationContext

        // Step 1: set the source in the HAL. Implicit broadcast, exactly like `am broadcast -a`.
        try {
            app.sendBroadcast(
                Intent(ACTION_SET_INPUT_SOURCE).putExtra(EXTRA_CVTE_SOURCE_ID, source.id)
            )
        } catch (e: Exception) {
            Log.e(SourceSwitcher.TAG, "sendBroadcast failed", e)
            onResult(SwitchResult(false, displayName, source, "broadcast failed: ${e.javaClass.simpleName}"))
            return
        }

        // Step 2: after a short delay, bring up the system's input player.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                app.startActivity(
                    Intent()
                        .setComponent(TIF_PLAYER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onResult(SwitchResult(true, displayName, source, ""))
            } catch (e: ActivityNotFoundException) {
                Log.e(SourceSwitcher.TAG, "TifPlayerActivity not found", e)
                onResult(SwitchResult(false, displayName, source, "TifPlayerActivity not found"))
            } catch (e: SecurityException) {
                Log.e(SourceSwitcher.TAG, "TifPlayerActivity refused", e)
                onResult(SwitchResult(false, displayName, source, "TifPlayerActivity refused: ${e.message}"))
            } catch (e: Exception) {
                Log.e(SourceSwitcher.TAG, "startActivity failed", e)
                onResult(SwitchResult(false, displayName, source, "startActivity failed: ${e.javaClass.simpleName}"))
            }
        }, PLAYER_DELAY_MS)
    }
}
