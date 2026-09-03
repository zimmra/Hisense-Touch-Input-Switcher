# Input Switcher for the Hisense 75WE3FE

A tiny Android app that lets walk-up users switch the video input of a Hisense 75WE3FE interactive
display (Android 14 AOSP, CVTE/Seewo "ifpdos" firmware, Launcher3 home screen) with one tap, straight
from the home screen.

| Button         | Physical input | CVTE `source_id` |
|----------------|----------------|------------------|
| Conference Hub | HDMI 1         | 5                |
| Front USB-C    | Type-C 2       | 15               |
| Front HDMI     | HDMI 3         | 7                |

It ships three entry points, all driving the same `SourceSwitcher`:

* **Home-screen widget** "Input Switcher": one rounded bar with the three buttons.
* **Launcher icons**: one per input, so each can be dragged onto the home screen or found under
  "More apps".
* **Setup screen** (the app's main icon): test buttons, "Add widget to home screen", "Add shortcuts to
  home screen", transport selection, and a status line showing the last switch result.

Returning to Android is handled by the display's own Home gesture, so there is no "Home" button.

## Building

Requirements: JDK 17+, Android SDK with platform 34 and build-tools 34 (Android Studio installs
these; or point `ANDROID_HOME` at a command-line-tools install). Gradle itself comes via the wrapper.

```sh
./gradlew assembleRelease
# → app/build/outputs/apk/release/input-switcher-release.apk
```

There is no GitHub Actions workflow on purpose; build locally.

The release keystore (`keystore/input-switcher-release.jks`) and its credentials
(`keystore.properties`) are **committed deliberately**. The app is sideload-only, so the key protects
nothing except upgrade continuity: every build, debug or release, is signed with the same key, and
`adb install -r` upgrades in place instead of failing with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## Sideload / deploy

```sh
# One-time: Developer options → USB debugging (or Wireless debugging) on the display
adb devices                                  # confirm the display is listed
adb install -r -g app/build/outputs/apk/release/input-switcher-release.apk
adb shell am start -n com.zimindustries.inputswitcher/.SetupActivity

# On the display: tap "Add widget to home screen" → confirm the launcher prompt,
# or long-press the home screen → Widgets → Input Switcher → drag to place.
# Individual icons: long-press home screen → drag "Conference Hub" / "Front USB-C" /
# "Front HDMI" from the app drawer, or use "Add shortcuts to home screen" in the app.

# Verify
adb logcat -d -s InputSwitcher
adb logcat -d | grep -i -E "InputSwitcher|ljc|SET_INPUT_SOURCE"
# Update later
adb install -r app/build/outputs/apk/release/input-switcher-release.apk
# Remove
adb uninstall com.zimindustries.inputswitcher
```

`./deploy.sh` runs the install and opens the setup screen (`--build` to build first, `--debug` for
the debug APK, `--logs` to tail logcat, `--uninstall` to remove).

## First thing to verify on the device (the spike)

The switching mechanism was verified from the ADB shell, which runs as the `shell` uid. The very first
check with the installed APK is whether the same works from a third-party app:

1. `./deploy.sh` (or the commands above), then tap **Conference Hub** in the setup screen.
2. The display must switch to HDMI 1. The status line should read `OK · Conference Hub via Direct`.

If the picture does **not** change, the CVTE receiver was most likely registered `RECEIVER_NOT_EXPORTED`
and silently drops broadcasts from non-system apps. Check:

```sh
adb logcat -d | grep -i -E "ljc|not vaild|SET_INPUT_SOURCE"
```

If the receiver never logs anything when you tap, the direct transport is blocked; see the fallbacks
below.

## How switching works (direct transport)

Two steps, both always performed (even when already on an HDMI input):

```
am broadcast -a com.cvte.tvapi.action.SET_INPUT_SOURCE --ei source_id <id>
am start -n com.cvte.tv.setting/.TifPlayerActivity
```

1. The broadcast is received by `com.cvte.tv.api.impl` (`HotkeyService`, persistent, `android.uid.system`),
   whose context-registered `TvApiTestReceiver` reads `source_id` and calls
   `VmanMiddleWare.getTvInputCtrl().setCurInputSource(id)` in the video HAL.
2. ~150 ms later, `com.cvte.tv.setting/.TifPlayerActivity` (exported, `singleInstance`,
   `excludeFromRecents`) is started with an explicit component intent and `FLAG_ACTIVITY_NEW_TASK`.
   It reads the HAL's current source and tunes a `TvView` to the matching TIF passthrough input. When
   the user presses Home it sets the HAL back to Android itself.

Implemented in `DirectTransport.kt`. No permissions are needed; the manifest declares
`<queries><package android:name="com.cvte.tv.setting"/></queries>` for package visibility.

Full `source_id` enum (`com.cvte.vman.types.EnumInputSourceId` ordinals): HDMI1=5, HDMI2=6, HDMI3=7,
PC/OPS=8, TYPEC1=14, TYPEC2=15, DP=16, HDMI4=17, ANDROID=24.

## If the direct transport does not work

`SourceSwitcher` talks to a `SourceTransport` interface, so the mechanism can be swapped without
touching any button. Two transports are built in and selectable in the setup screen:

### IP control (built in)

The display runs `com.dss.ipcontrol`, which speaks the RS-232 protocol over TCP. Discover its port:

```sh
adb shell "netstat -tlnp || ss -tlnp"
```

In the setup screen choose **IP control** and enter the port. `IpControlTransport.kt` then sends the
serial frame to `127.0.0.1:<port>` and expects an `AB AB … CD CD` acknowledgement, retrying once after
500 ms:

| Input    | Frame                                     |
|----------|-------------------------------------------|
| HDMI 1   | `DD FF 00 07 C1 08 00 00 01 24 EB BB CC`  |
| HDMI 3   | `DD FF 00 07 C1 08 00 00 01 1A D5 BB CC`  |
| Type-C 2 | `DD FF 00 07 C1 08 00 00 01 1D D2 BB CC`  |

### Shizuku (not built in; add if both of the above fail)

[Shizuku](https://shizuku.rikka.app/) runs commands with shell privileges, i.e. exactly what worked
from ADB. To add it: depend on `dev.rikka.shizuku:api` and `dev.rikka.shizuku:provider`, add a
`ShizukuTransport : SourceTransport` that runs the two `am` commands through a Shizuku user service,
and add it to `TransportKind` / `SourceSwitcher.transport()`. On the display: install the Shizuku
APK, run `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh` after each boot (or
enable Wireless debugging so it self-starts), and grant Input Switcher permission in the Shizuku app.

## Project layout

```
app/src/main/kotlin/com/zimindustries/inputswitcher/
  Source.kt              the three inputs: id, label, icons
  SourceSwitcher.kt      single entry point: pick transport, switch, record status, toast on error
  SourceTransport.kt     transport interface + SwitchResult
  DirectTransport.kt     CVTE broadcast + TifPlayerActivity (verified mechanism)
  IpControlTransport.kt  RS-232-over-TCP fallback
  Settings.kt            transport choice + IP port (SharedPreferences)
  SwitchStatus.kt        last switch result for the status line
  SwitchActivity.kt      invisible trampoline behind the per-input icons and pinned shortcuts
  SwitchReceiver.kt      target of the widget buttons' PendingIntents
  InputSwitcherWidget.kt AppWidgetProvider (RemoteViews bar, pin-widget helper)
  SetupActivity.kt       main screen: test buttons, pin widget/shortcuts, transport, status
app/src/main/res/
  layout/widget_input_switcher.xml   the widget bar (also used as the picker preview)
  layout/activity_setup.xml
  xml/input_switcher_widget_info.xml widget metadata (~4x1 cells, horizontal resize, home screen)
  mipmap-anydpi-v26/                 adaptive icons: app + one per input
  drawable/                          HDMI / USB-C glyphs, widget card + ripple
  values/, values-night/             strings, M3-ish surface colours for light/dark
keystore/                            committed release keystore (see above)
deploy.sh                            adb install + open setup screen
```

Kotlin, Gradle Kotlin DSL, AGP 8.7, `minSdk 30`, `targetSdk 34`, `compileSdk 34`. No AndroidX,
Compose, or Play Services: plain framework widgets and RemoteViews are all this needs.

## Verifying on the device

1. Install, open "Input Switcher", tap each of the three buttons: the display must switch each time.
2. Tap "Add widget to home screen", confirm, tap each widget button.
3. Tap "Add shortcuts to home screen", confirm each, tap each pinned icon. Also try the per-input
   icons under "More apps".
4. Open Recents after tapping icons: no Input Switcher task card should appear.
5. Reboot the display: the widget must still be on the home screen and work.
6. While the setup screen is open, switch inputs: the "Last source change broadcast" line shows the
   extras of `com.cvte.touchmenu.sourcechange`, useful if you later want to highlight the active
   button in the widget.

## Device reference

* Packages: `com.cvte.tv.api.impl` (HotkeyService), `com.cvte.tv.setting` (TifPlayerActivity),
  `com.dss.ipcontrol` (IP control), `com.ifpdos.androidremoters232` (RS-232),
  `com.dss.ninesquared.menu` (sidebar), `com.android.launcher3` (home).
* TIF inputs: `com.mediatek.external/.HdmiInputService/HW2..HW5` = HDMI ports 1–4.
* `input keyevent 243/244/245` (TV_INPUT_HDMI_x) does **not** switch inputs on this firmware.
* Screen: 3840×2160, status bar 72 px top, nav bar 180 px bottom.
