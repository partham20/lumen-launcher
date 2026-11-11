# Lumen

A minimal Android TV home screen in the spirit of the Apple TV home.

- **Top shelf**: the focused app's name over a blurred backdrop of its banner, tinted with the banner's dominant colour.
- **Favourites row** of your main apps, then an **All apps** grid.
- A clock and nothing else: no ads, no recommendations.
- Tiny (~35 KB), no third-party libraries, built for low-memory TVs.

## Using it

- **OK** opens an app.
- **Hold OK** (or press **Menu**) on a tile to add/remove favourites, move them left/right, hide an app, or open App info. "Show hidden apps" brings hidden ones back.
- **Back** or **Home** jumps to the first favourite.

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
