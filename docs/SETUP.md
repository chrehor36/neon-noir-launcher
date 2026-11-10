# Day-one setup on the TCL 75QM7L Pro

Do these in order. Nothing here can't be undone, and step 5 is your way back.

1. **Update first.** Finish Google TV setup, sign in, then install every system and firmware update.
2. **Developer options.** Settings → System → About → select Build 7 times. Then in Developer options, turn on
   USB debugging and Network (Wi-Fi) debugging.
3. **Fixed IP.** In your router, reserve an IP address for the TV so `TV_HOST` never changes.
4. **Computer tools.** Install Git and Android platform tools (ADB). Then
   `git clone https://github.com/Gathin23/cleanyourtv ~/cleanyourtv`.
5. **Back up and check.** From this folder, run `TV_HOST=<tv ip> scripts/tv-day-one.sh`. Approve the prompt
   on the TV. It saves the package lists, display info, memory and a screenshot into `backups/`.
   - Check `display.txt` for the UI resolution (expect 1080p) and the refresh rates.
   - Check `memory.txt`. The theme uses `wallpaper_true_color`, which needs about 2 GB or more.
6. **Clean up.** Open `~/cleanyourtv` in Claude Code, paste its `PROMPT.md` with the model `75QM7L Pro`, and
   let it back up, disable the ad packages and install the CleanYourTV launcher.
   - Then test its one-line restore once, and run the cleanup again.
7. **Home button.** Press Home on the remote. If Google's home screen shows instead of the launcher, ask the
   agent to turn on the Home guard (`HOME_KEY_SERVICE=1`).
   - On TCL Google TV with Android 12, if holding a button stops repeating, set `key_filter` to `false`.
8. **Push the theme.** Run `TV_HOST=<tv ip> CLEANYOURTV=~/cleanyourtv scripts/push-theme.sh`.
9. **Look at it from the couch.** Check text size, contrast and the rain at your normal viewing distance.
10. **Screenshot for the PR.** Run `TV_HOST=<tv ip> ~/cleanyourtv/tools/tv.py --screenshot shots/neon-noir.png`
    with the focus on the dock.
    - Save it as `theme/neon-noir/screenshot.jpg` (1280x720, under 300 KB). CleanYourTV's tests require it.

## What was done on 2026-11-09

- Paired over Wireless debugging (Android 14 has no port 5555). The connect port changes whenever Wi-Fi
  reconnects: pair again from Developer options → Wireless debugging, then `adb mdns services` shows the port.
- Backup in `backups/2026-11-09_1009/`. Firmware `UTT2.250416.001/AU27`, 2.3 GB memory, a 4K panel drawn at
  1920x1080 and 320 dpi (the emulator's setup).
- Installed the release build as Home, and disabled Google's home (`com.google.android.apps.tv.launcherx`) and
  its setup recovery screen (`com.google.android.tungsten.setupwraith`), which claims Home once the first is off.
  To undo: `adb shell pm enable com.google.android.apps.tv.launcherx` and the same for setupwraith.
- TCL's Live TV app is `com.tcl.tv` (tunes channel links), set as `live_tv_package` in config.xml.
- The remote's gear sends keycode 83, which only Google's home handled. config.xml maps it
  (`settings_key_keycodes` 83, `settings_key_panel` true) to the launcher's settings panel, and the HomeKeyService
  accessibility helper is on so it also works inside apps. Undo: Settings → Accessibility → Home button → off.
- The remote (BT_RC923A_B2) sends Netflix 4062, Prime Video 4099, YouTube 190, Free TV 297 and Live TV 4001, which
  only Google's home answered. They're in config.xml `app_keys`. To read another button's code:
  `adb shell setprop log.tag.NeonKeys D`, then `adb logcat -s NeonKeys` while pressing it.
- Uninstalled Hulu, YouTube TV and Haystack News (reinstall from Google Play). Switched off Netflix and YouTube
  Music, which came with the TV: `adb shell pm enable com.netflix.ninja` (or `com.google.android.youtube.tvmusic`)
  brings either back. All apps also hides TCL's demo, manual, TCL HOME and Quick panel tiles, TCL's Live TV app and
  Google Play Games (config.xml `hidden_apps`; they stay installed).
- Switched off five ad and demo apps: `com.tcl.exhibit`, `com.tcl.esticker`, `com.tcl.messagebox`,
  `com.tcl.tv.tclhome_passive`, `android.autoinstalls.config.google.gtvpai`. Restart tested: Home, the button
  helper and usage access all came back. Undo any with `adb shell pm enable <package>`.
- Screensaver: `secure screensaver_components` = `io.github.chrehor36.neonnoir/.ArtDream`, `system screen_off_timeout` =
  900000 (15 min), and Google's photo screensaver `com.google.android.apps.tv.dreamx` switched off.
- Turn off display: Never (`secure sleep_timeout` = -1; it was about 20 min, which blacked out Art Mode 5 minutes
  in). Tested: Art Mode was still up at 4 minutes, past the old 2.5-minute test timer. TCL's own power-off
  timer is off (`global tcl_app_power_off_time_delay` = -1). Undo: Settings → System → Power & Energy.
- New app ID, `io.github.chrehor36.neonnoir` (it was CleanYourTV's `com.gathin.tvhome`): installed and set
  up again (Home, button helper, usage access, location, Art Mode screensaver), then the old copy uninstalled.
  Favorites started from the starter list, which is the same five.
- Wireless debugging switches itself off at every restart: turn it on again and pair with the new code.

## Sharing the theme

Copy `theme/neon-noir/` into your fork of CleanYourTV at `themes/neon-noir/`. Then add a row to
`themes/README.md`, and bump the theme count on the front pages (`README.md`, `README.de.md`, `README.nl.md`,
`README.uk.md`, `README.tr.md`).

Suggested row:

```markdown
| `neon-noir/` | Cyberpunk noir in crimson and gold: a near-black sky with two soft glows that follow the time of day (ember and gold in the morning, crimson and gold through the evening, midnight blue at night), faint diagonal rain and scanlines softened behind the clock and dock, a 50% frosted-glass dock with a rim-only focus, icon-only buttons, and the clock screensaver over the picture after 30 idle minutes. Drawn in code by `make_wallpapers.py` (CC0) | <img src="neon-noir/screenshot.jpg" width="320" alt="neon-noir theme"> |
```

In the PR, say which TV, Android version and firmware you tested on, and attach the screenshot.
