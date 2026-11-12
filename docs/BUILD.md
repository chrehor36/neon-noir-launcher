# Building and installing the launcher

The launcher code is in `launcher/`. It's a copy of the CleanYourTV launcher (MIT) with the Neon Noir Home added
on top. The helper scripts it uses are in `tools/`, also from CleanYourTV.

## One-time setup on Windows

1. Install [Android Studio](https://developer.android.com/studio). It brings the Android SDK, the JDK and the
   build tools.
2. In Android Studio's SDK Manager, check that **Android 14 (API 34)** and **Android SDK Platform-Tools** are
   installed.
3. Install [Git for Windows](https://git-scm.com/download/win). It includes Git Bash, which runs the `.sh` scripts.
4. Install Python 3 from python.org. CleanYourTV's install script uses it to talk to the TV.

## Try it before the TV arrives: the Android TV emulator

This PC is already set up: Android Studio, JDK 17, the SDK and a Google TV (Android 14, 1080p) emulator
called `NeonNoir_TV_1080p`. To build and run after a change, from the `launcher` folder in PowerShell:

```powershell
.\run-emulator.ps1
```

It starts the emulator if needed, builds, installs and opens Home. Arrow keys and Enter on your keyboard work as
the remote while the emulator window is focused; Esc is Back and Home is Home.

Notes:
- **How close it is to the TCL 75QM7L Pro:** same Google TV 14 (Android 14), the same 1920x1080 screen at 320 dpi
  that Google TV draws its menus at, and 2 GB of memory (TCL doesn't publish the TV's, so this is the low end
  Google TV allows). Tested on 2026-11-05 with the remote keys only: Home, All apps, Jazz mode, More, Art Mode,
  Light/Dark, Launcher settings and Night mode, plus a 4K (3840x2160 at 640 dpi) run in case the TV reports its
  panel's full size. The layout is the same at both.
- **Live guide on the emulator:** the emulator has no tuner, so debug builds include made-up test channels.
  Add them (or reset them) with `adb shell am start -n io.github.chrehor36.neonnoir/.DemoChannelsActivity`. They're never
  in a release build.
- **Java:** Gradle 8.9 needs JDK 17. Android Studio comes with Java 25, which is too new for it. If you build
  from Android Studio, set Settings → Build Tools → Gradle → Gradle JDK to the JDK 17 in
  `C:\Program Files\Microsoft\`.
- **Home on the emulator:** its own Google TV home outranks this one, so the script switches it off on the
  emulator only (`adb shell pm enable com.google.android.apps.tv.launcherx` undoes it). On the TCL,
  CleanYourTV's Home guard takes care of this.
- The emulator has only a few TV apps, so the dock looks sparse. That is expected.

What to check: the clock, day name and chips; the dock; More's Live section and Full guide; the sun button (light mode and back); All apps (A–Z, Most
used, Hold OK menu); Jazz mode (Pick another, playlist cards); More (Down from the dock); Art Mode (the frame
button); Back returning to Home from each.

## On the TCL (after the day-one steps in SETUP.md)

From Git Bash, in the `launcher` folder:

```bash
TV_HOST=192.168.1.50 ./build-and-install.sh
```

It builds, installs, makes the launcher the TV's Home, and starts it. Use your TV's IP address. The script and
its options are CleanYourTV's; their main README explains `HOME_KEY_SERVICE=1` and the other switches.

Then one one-time permission:

```bash
# The dock and Most used see every app you use, not just the ones opened from Home
adb shell appops set io.github.chrehor36.neonnoir GET_USAGE_STATS allow
```

Night mode needs no permission: it dims only the launcher's own screens, never other apps. Its times are in
Launcher settings > Night mode (default 9 PM to 6 AM).

Optional: Art Mode as the TV's own screensaver too (it already starts on Home after 15 idle minutes):

```bash
adb shell settings put secure screensaver_components io.github.chrehor36.neonnoir/.ArtDream
```

## Your settings

Copy `secrets.properties.example` (project folder) to `secrets.properties`, fill in what you want, and rebuild.
Every line is optional. A feature without its setting shows a short note saying what to add.

| Setting | Turns on |
| --- | --- |
| `HOME_LATITUDE`, `HOME_LONGITUDE` | Weather on the date line, and sunrise and sunset for the sky glows |
| `DISCOGS_USERNAME` (+ optional `DISCOGS_TOKEN`) | Tonight and Jazz mode pick from your own records (otherwise a built-in crate of classics) |
| `SCORES_TEAMS` | The Scores chip, and your teams' games at the top of their conference's card on More; with several teams, whichever is in season is featured |
| `SCORES_GROUPS`, `SCORES_PLAYERS` | A card per conference on More (e.g. NFC, SEC: the biggest games of the week) and one for the tour's main tournament (Djokovic's match, then the top matches of the day); a followed player's live match also takes the Scores chip |
| `SCORES_RACING` | An F1 card: the race weekend's sessions, otherwise the next race and the last result; a live race takes the Scores chip |
| `TMDB_API_KEY` (+ optional `PICKS_SERVICES`) | Picks for you, from the services you list (otherwise the ones installed) |
| `SPOTIFY_PLAYLISTS` | Your own Jazz mode playlists (otherwise 11 of Spotify's jazz playlists) |

## If a build fails

Copy the first error from the Build window into the chat. Phase 1 first built cleanly on 2026-11-04.

## About the app ID

The app still uses CleanYourTV's ID (`io.github.chrehor36.neonnoir`), because their install, restore and guard scripts
look for it by name. On the TV, it replaces their launcher, which is what we want. It gets its own ID before
it's shared on GitHub.
