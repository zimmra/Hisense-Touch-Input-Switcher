package com.zimindustries.inputswitcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Target of the widget buttons. The widget must not broadcast the CVTE action directly, because a
 * switch is a two-step sequence (HAL broadcast, then start the player); this receiver runs it.
 */
class SwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SWITCH) return
        val source = Source.fromId(intent.getIntExtra(Source.EXTRA_SOURCE_ID, -1))
        if (source == null) {
            Log.w(SourceSwitcher.TAG, "SwitchReceiver: unknown source in $intent")
            return
        }
        // Keep the process alive across the 150 ms delay (and any IP-control round trip).
        val pending = goAsync()
        SourceSwitcher.switch(context, source) { pending.finish() }
    }

    companion object {
        const val ACTION_SWITCH = "com.zimindustries.inputswitcher.action.SWITCH"

        /** Explicit, immutable PendingIntent for one widget button. Request code = source id. */
        fun pendingIntent(context: Context, source: Source): PendingIntent {
            val intent = Intent(context, SwitchReceiver::class.java)
                .setAction(ACTION_SWITCH)
                .putExtra(Source.EXTRA_SOURCE_ID, source.id)
            return PendingIntent.getBroadcast(
                context,
                source.id,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
