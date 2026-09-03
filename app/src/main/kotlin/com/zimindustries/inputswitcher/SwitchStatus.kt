package com.zimindustries.inputswitcher

import android.content.Context
import android.content.SharedPreferences
import java.text.DateFormat
import java.util.Date

/** Remembers the last [SwitchResult] so the setup screen can show it, whichever entry point fired. */
object SwitchStatus {
    private const val PREFS = "status"
    const val KEY_LAST = "last"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(context: Context, result: SwitchResult) {
        val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(result.at))
        prefs(context).edit().putString(KEY_LAST, "$time  ${result.summary()}").apply()
    }

    fun last(context: Context): String? = prefs(context).getString(KEY_LAST, null)
}
