# Screen spec

The mockups live on the [TV Home Screen Layouts canvas](https://claude.ai/artifact/AFHQKmbvmFHXpyJ2fZ4MuC).
Colors and type are in `design/tokens.json`. The screen is 1280x720 in the mockups and 1920x1080 on the TV,
so multiply sizes by 1.5.

## Home (dark is the default; light mode matches it)

- **Center:** a 240px hairline analog clock with no second hand. Under it is the day name (50px, wide
  letter-spacing, red/gold edge, a brief flicker every 9 s). Under that is a line with the date, temperature
  and conditions.
- **Chips above the dock** (one line each, no wrapping):
  - **Tonight:** a record from your Discogs collection, the same as Jazz mode's; Pick another there changes it
    for the rest of the day. The collection is read again weekly, after midnight on Sunday.
  - **Continue:** the last app used (favorite or not), plus the title only where that app shares it.
  - **Scores:** your team's live game. It opens More.
- **Dock:** up to 6 favorites (now Spotify, YouTube, Peacock, Prime Video, Plex). Add or remove
  them in All apps (Hold OK); Hold OK on one in the dock, then ‹ › and OK, moves it. Then a divider, then five icons: All apps, Jazz mode,
  Art mode, Light/Dark (sun in dark mode, moon in light mode) and Settings (a toothed gear).
- **Remote:** Home always starts on the first app. Back from another screen returns to the button that opened it.
  On the TCL remote, the gear opens the settings panel; Netflix, Prime Video and YouTube open their apps; Live TV
  (the TV icon) opens TCL's channels; Free TV opens More on its Live section. Tubi, Disney+ and TCLtv+ are TCL's own.
- **Clock center dot:** the crimson of the top-right glow (#A8131D), in both modes.
- **"More" link** under the dock. It opens Picks for you, Scores and Live.
- **Background:** two drifting glows that follow the sky (dawn, day, sunset, night, rain), faint rain streaks
  and scanlines.
- **Panels:** smoked glass at 0.5 opacity with no live blur, cut corners and a gold hairline edge.
- **Selected app:** crimson tile, 1px gold outline 3px out, lifted 3px and scaled to 1.04.

## More

- **Picks for you:** 7 posters of popular titles on your services (TMDB). OK opens the app's search results for
  the title where it has a way in (YouTube, Prime Video, Tubi; Pluto TV's search page; Plex), otherwise the app
  (Peacock). A service that isn't installed opens its Google Play page. Google TV's own search isn't used: it
  belongs to Google's home screen, which is switched off.
- **Scores:** a card per conference, tournament and race (NFC, SEC, Paris Masters, São Paulo GP).
- **Live** (below Scores; Down scrolls to it). Replaces Google TV's Live tab, which goes away with Google's
  home screen.
  - **Channel cards:** the first 4 channels (antenna first): number, logo, name, what's on and a bar to its end.
    OK tunes the channel in the Live TV app.
  - **Full guide card:** opens the full guide (below).
  - **Buttons:** Antenna (the TV's Live TV app), then the installed apps with live guides of their own. (HDMI
    input buttons exist but are off on the owner's TV, NeonWatch.SHOW_INPUTS; the remote's Input button switches.)
    The apps (Pluto TV,
    Tubi, the Roku Channel, Peacock, YouTube TV, Sling, Plex, Haystack News).
- Coming back from a channel or a pick keeps your place.
- **Scrolling:** one smooth glide (about a third of a second) to each section's resting place: Picks at the top,
  Live at the bottom. Android's own jump-to-focus scrolling is off on this screen.

## Live guide (More → Live → Full guide)

- Every channel in the TV's channel list, grouped by source (antenna first, by channel number). Each row shows
  the number, logo, name, what's on with a progress bar and its end time, and what's next. OK tunes it. The
  selected row turns red without growing.
- **Where the channels come from:** antenna channels after a scan in the Live TV app, and the channels that apps
  publish to the TV and that the Live TV app has approved. Apps that keep their guide to themselves have a
  button in More's Live section instead.
- Refreshes every minute while open. Back returns to More, on the Full guide card.
## All apps

- A 6-column grid of every app, sorted A–Z or by most used.
- Hold OK on an app for Add to (or Remove from) favorites, App info or Uninstall.

## Jazz mode (the music-note button)

- **Layout:** a clock chip at the top left, then "After hours", then the turntable in a glass frame. Under
  it is the record title (Fraunces), the artist, and a Pick another button.
- **Dock:** Open Spotify plus 3 jazz playlist cards. The cards rotate through 10+ playlists, 3 at a time,
  every 15 s, or with the back and next arrows after the cards (page dots under both; both wrap around).
  The 15 s turn waits while a card or an arrow is selected.
- **What counts as jazz:** jazz, crooners and vocal pop.
- **Colors:** dimmer than Home (antique brass, oxblood, warm gray). Light mode has its own light Jazz palette.
- **Record:** spins for 20 s after a pick.

## Art Mode

- **When it starts:** after 15 min with no remote input on Home, or from the frame icon. It is also the TV's own
  screensaver (ArtDream, 15 min) in place of Google's photo screensaver, which is switched off. Apps playing
  video keep it away. Any button goes back.
- **Back to your app:** TCL's firmware closes some apps (Peacock, for one) as soon as anything covers them,
  the screensaver included. Art Mode notes the app it covered and opens it again when you wake the TV, unless
  you wake it with Home.
- **Art:** a new painting every 5 minutes with a slow crossfade, and one small caption: Title · Artist · Year.
- **Which paintings:** about 200 landscape-shaped public-domain paintings from the Art Institute of Chicago,
  refreshed weekly, none repeated within the last 40. Black-and-white photographs of paintings are skipped.
  The museum's search pages only its first 1,000 results, so each refresh reads pages 1-10 in either id order.
- **At night:** only darker artworks (about 30), shown in turn before any repeats.
- **Base to build on:** the reference launcher's `ClockDream` (a DreamService).

## Night mode

- **Where:** the launcher's own screens only (Home, Jazz mode, More, Live, All apps, Art Mode). Other apps are never
  dimmed.
- **When:** between set times chosen in Launcher settings (default 9:00 PM to 6:00 AM, in
  half-hour steps; the same time for both turns it off). The sky glows still follow the sun.
- **Effect:** dims 25% with a slight warm tint.
- **Light mode at night:** switches to dark mode automatically.

## Launcher settings

- Night mode's start and end, Choose default home and TV system settings. CleanYourTV's grid, wallpaper, clock
  and motion rows are hidden: Neon Noir covers that grid, so they changed nothing.

## Reused from the CleanYourTV launcher

- **Home guard and key services:** Home stays Home after updates. On a TCL Google TV, `settings_key_keycodes`
  `[83]` maps the gear button.
- **`GameDay`:** the next-game line, a starting point for the Scores chip.
- **`WallpaperRotation`:** the morning, evening and night moods.
- **`ClockDream`:** the screensaver hook for Art Mode.
