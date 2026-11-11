# Lumen

A minimal Android TV home screen in the spirit of the Apple TV home.

- **Top shelf**: the focused app's name over a blurred backdrop of its banner, tinted with the banner's dominant colour.
- **Favourites row** of your main apps, then an **All apps** grid.
- A top bar with your profile photo and a greeting, plus the time, day and date. No ads, no recommendations.
- Tiny (~50 KB), no third-party libraries, built for low-memory TVs.

## Using it

- **OK** opens an app.
- **Hold OK** (or press **Menu**) on a tile to add/remove favourites, move them left/right, hide an app, open App info, or open Lumen settings.
- **Back** or **Home** jumps to the first favourite.
- **Up** to your profile photo, then **OK**, opens **Lumen Settings**.
- **Close apps** (top bar) stops every TV app's background processes except the ones on your keep-running list (Spotify by default), then shows how much memory was freed. Apps stay in the system's recents list; Android doesn't let a launcher clear that.

## Remote buttons

With **Lumen remote buttons** switched on (TV Settings → Accessibility), two remote buttons get new jobs anywhere on the TV:

- **⚙ Settings** opens **Quick Settings**, a panel that slides in from the right: Close apps, volume (Left/Right, OK to mute), and shortcuts to Wi-Fi, Bluetooth, picture, sound, Lumen settings and all settings.
- **📺 Live TV** goes Home and closes every app except the keep-running list.

Either action can be moved to another button in Lumen Settings → Remote. The service only watches button presses; it never reads the screen. Remote keys are logged at error level under the `LumenRemote` tag, since some TVs keep only error logs.

## Lumen Settings (on the TV)

Changes apply as soon as you go back to the home screen; no rebuild needed.

- **Profile**: your name, greeting (Welcome / Good morning·afternoon·evening / Off), profile photo (picked from Downloads, Pictures, DCIM or a USB drive).
- **Clock**: show day and date, 12- or 24-hour time.
- **Look**: background colour that matches the focused app or stays dark; blurred app artwork on/off (off is a little faster).
- **Remote**: turn remote buttons on, and choose which buttons open Quick Settings and close apps.
- **Apps**: show or hide the Close apps button on the home screen, choose which apps keep running when closing, choose hidden apps, reset favourites.
- **System**: open the Android TV settings.

To set the profile photo from a computer instead:

```bash
adb push photo.png /sdcard/Download/profile-photo.png
adb shell pm grant com.partha.lumen android.permission.READ_EXTERNAL_STORAGE
adb shell am start -n com.partha.lumen/.SettingsActivity --es import_avatar /sdcard/Download/profile-photo.png
```

## Building

Needs JDK 17 and the Android SDK (platform 34, build-tools 34). Create `local.properties` with `sdk.dir=/path/to/Android/Sdk`, then:

```bash
gradle assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity com.partha.lumen/.MainActivity
```

On Google TV the stock launcher grabs the Home button. To let Lumen own it:

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Undo with `adb shell pm enable <package>`.

After reinstalling Lumen, run the `set-home-activity` command again: Android can forget the default home app on reinstall.
