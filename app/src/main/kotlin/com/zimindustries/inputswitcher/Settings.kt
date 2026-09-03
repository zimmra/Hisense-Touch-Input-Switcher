package com.zimindustries.inputswitcher

import android.content.Context
import android.content.SharedPreferences

/** Which [SourceTransport] [SourceSwitcher] should use. */
enum class TransportKind { DIRECT, IP }

/** Tiny SharedPreferences wrapper for the transport choice and the IP-control port. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_TRANSPORT = "transport"
    private const val KEY_IP_PORT = "ip_port"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun transportKind(context: Context): TransportKind {
        val name = prefs(context).getString(KEY_TRANSPORT, null) ?: return TransportKind.DIRECT
        return runCatching { TransportKind.valueOf(name) }.getOrDefault(TransportKind.DIRECT)
    }

    fun setTransportKind(context: Context, kind: TransportKind) {
        prefs(context).edit().putString(KEY_TRANSPORT, kind.name).apply()
    }

    /** 0 means "not configured". */
    fun ipPort(context: Context): Int = prefs(context).getInt(KEY_IP_PORT, 0)

    fun setIpPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_IP_PORT, port).apply()
    }
}
