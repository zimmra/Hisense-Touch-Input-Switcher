package com.zimindustries.inputswitcher

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Invisible trampoline behind the per-input launcher icons and pinned shortcuts.
 *
 * The source comes from, in order:
 *  1. the `source_id` int extra (pinned shortcuts, `adb shell am start ... --ei source_id 5`);
 *  2. the `<meta-data android:name="source">` of the `<activity-alias>` that launched us.
 *
 * It finishes immediately; the actual switch continues on the application context.
 */
class SwitchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = resolveSource()
        if (source == null) {
            Log.w(SourceSwitcher.TAG, "SwitchActivity launched without a resolvable source: $intent ($componentName)")
            Toast.makeText(this, R.string.toast_unknown_source, Toast.LENGTH_SHORT).show()
        } else {
            SourceSwitcher.switch(this, source)
        }
        finish()
    }

    private fun resolveSource(): Source? {
        val fromExtra = intent?.getIntExtra(Source.EXTRA_SOURCE_ID, -1) ?: -1
        if (fromExtra > 0) return Source.fromId(fromExtra)

        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(
                    componentName,
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
            }
            // android:value="5" is stored as an Int, but tolerate a string too.
            val meta = info.metaData ?: return null
            val id = meta.getInt(META_SOURCE, -1).takeIf { it > 0 }
                ?: meta.getString(META_SOURCE)?.toIntOrNull()
            id?.let(Source::fromId)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        const val META_SOURCE = "source"
    }
}
