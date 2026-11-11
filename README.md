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

## Lumen Settings (on the TV)

Changes apply as soon as you go back to the home screen; no rebuild needed.

- **Profile**: your name, greeting (Welcome / Good morning·afternoon·evening / Off), profile photo (picked from Downloads, Pictures, DCIM or a USB drive).
- **Clock**: show day and date, 12- or 24-hour time.
- **Look**: background colour that matches the focused app or stays dark; blurred app artwork on/off (off is a little faster).
- **Apps**: choose hidden apps, reset favourites.
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
