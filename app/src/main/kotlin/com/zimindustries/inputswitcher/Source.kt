package com.zimindustries.inputswitcher

/**
 * The three inputs exposed to walk-up users.
 *
 * [id] is the CVTE `source_id` (ordinal of `com.cvte.vman.types.EnumInputSourceId`):
 * HDMI1=5, HDMI2=6, HDMI3=7, PC/OPS=8, TYPEC1=14, TYPEC2=15, DP=16, HDMI4=17, ANDROID=24.
 *
 * [label] is the exact user-facing string; [iconRes] is the 24dp glyph used inside the widget and
 * setup screen; [launcherIconRes] is the adaptive launcher icon used for the activity alias and for
 * pinned shortcuts.
 */
enum class Source(
    val id: Int,
    val label: String,
    val iconRes: Int,
    val launcherIconRes: Int,
) {
    CONFERENCE_HUB(5, "Conference Hub", R.drawable.ic_hdmi, R.mipmap.ic_launcher_conference_hub),
    FRONT_USBC(15, "Front USB-C", R.drawable.ic_usb_c, R.mipmap.ic_launcher_front_usbc),
    FRONT_HDMI(7, "Front HDMI", R.drawable.ic_hdmi, R.mipmap.ic_launcher_front_hdmi);

    companion object {
        /** Intent extra carrying a [Source.id]. Same key the CVTE receiver uses, for consistency. */
        const val EXTRA_SOURCE_ID = "source_id"

        fun fromId(id: Int): Source? = entries.firstOrNull { it.id == id }
    }
}
