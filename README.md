<p align="center"><img src="docs/icon.png" width="128" alt="gmuse icon"></p>

# gmuse

gmuse is an Android tablature player and editor with a modern, touch-first interface.
It is a fork of [TuxGuitar](https://github.com/helge17/tuxguitar): the score engine, file formats
and synthesizer come from TuxGuitar. gmuse rebuilds the Android app around them.

## What gmuse changes

**Look and feel**
- Material You (Material 3) theme with dynamic colors from the wallpaper, light and dark.
- The score fills the whole screen, edge to edge. A floating menu button (☰) and a settings
  button (⚙) replace the toolbar.
- Menus grow out of the button that opened them as one liquid shape (an AGSL shader on
  Android 13+, a simpler animation on older versions).

**Playback controls (bottom sheet)**
- A tempo card with the BPM, the playback speed and a large play button.
- Tiles for Tracks, Volume (mixer), Loop, Click (metronome), Count-in, Key (transpose), Edit and More.
- Panels slide in smoothly. The sheet can be pulled down to show only the tempo card.
- A tempo menu with a stepper, speed presets, beat unit and where the change applies,
  all in one popup.

**Practice**
- Songsterr-style loop: tap the first bar, then the last bar.
- Changing the volume, the mixer or the metronome does not stop playback.
- The view follows the playing bar. Scroll away and it stays where you are; scroll back and
  it follows again.
- Optional smooth playback cursor that glides between notes.

**Score display**
- Split bars: a bar can take 2, 3 or 4 full lines so the notes get more room. Bars wider
  than the screen are split automatically. The pieces are joined by dashed lines.
- Note size and interface size are separate settings.

gmuse installs next to the official TuxGuitar app (application id `app.gmuse.android`).

## Building

Requirements:
- Android SDK (API 35). Point `sdk.dir` in `android/build-scripts/tuxguitar-android/local.properties` at it.
- JDK 17–21. Gradle 8.9 does not run on newer JDKs; in Android Studio choose a 17–21 JDK under
  Settings → Build Tools → Gradle → Gradle JDK.

Build and install on a connected device:

```sh
cd android/build-scripts/tuxguitar-android
./gradlew :apk:installDebug
```

Or open `android/build-scripts/tuxguitar-android` in Android Studio and run the `apk` configuration.

The desktop TuxGuitar sources (`desktop/`, `common/`) are still in the repository because the
Android app shares the score engine with them. gmuse does not change the desktop app.
For desktop builds see [INSTALL.md](INSTALL.md).

## Project layout

| Path | What it is |
| --- | --- |
| `android/TuxGuitar-android` | The Android app: screens, bottom sheet, menus, score view |
| `android/build-scripts/tuxguitar-android` | Gradle project that builds the APK |
| `common/` | Shared TuxGuitar code: song model, file formats, layout engine, player |
| `desktop/` | Upstream desktop TuxGuitar (unchanged) |

## License

gmuse is released under the GNU Lesser General Public License, like TuxGuitar.

Based on TuxGuitar.
Copyright (C) 2005-2022 Julián Casadesús, 2023-2025 guiv42, helge17.

## Third-party products

Included through TuxGuitar:

* Gervill (Java software synthesizer)
* Magic Sound Font v2.0, contributed by Dennis Deutschmann
* SWT and JavaFX (desktop builds only): https://www.eclipse.org/swt/, https://openjfx.io/
* iText (PDF export): https://itextpdf.com/
