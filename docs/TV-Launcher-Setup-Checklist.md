# TV Launcher Setup Checklist

Exported Nov 4, 2026. The live copy, with tick boxes, is at https://claude.ai/artifact/8VjkExKCNPzYxKm1BtNUdS

## Before the TV arrives

Everything here can be done now, before the TCL 75QM7L Pro is in the house.

**Accounts and keys**

- [ ] Spotify developer app at [developer.spotify.com](https://developer.spotify.com/dashboard) (needs Spotify Premium; a personal app is limited to 5 users)
- [ ] Discogs personal access token at [discogs.com/settings/developers](https://www.discogs.com/settings/developers) for the record picker
- [ ] TMDB API key at [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) for Picks for you
- [ ] Store all keys in a password manager, never in the GitHub repo
- Weather (Open-Meteo) and scores need no account

**Your content**

- [ ] Jazz playlist links (10+), crooners and vocal pop included
- [ ] Teams to follow for Scores
- [ ] Home location for sunrise and sunset (drives night mode and the sky glow)
- [ ] Clean up the Discogs collection so the picker has good data

**Art library**

- [ ] Pick public-domain sources for Art Mode
- [ ] Download a starter set, sized for 4K
- [ ] Tag the darker pieces for night mode

**Computer setup**

- [ ] Install Android Studio, ADB (Android platform tools) and Git
- [ ] Clone [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
- [ ] Create your own GitHub repo for the launcher

## Day one with the TV

Get the TV fully updated and backed up before changing anything.

- [ ] Finish Google TV setup and sign in
- [ ] Install every firmware and system update before touching anything else
- [ ] Turn on Developer options (Settings → System → About → tap Build 7 times)
- [ ] Turn on USB debugging and network debugging
- [ ] Give the TV a fixed IP address in your router
- [ ] Connect from the computer with ADB and approve it on the TV
- [ ] Run CleanYourTV's backup and save the package list somewhere safe
- [ ] Run the TCL cleanup
- [ ] Test the one-line restore once, then re-run the cleanup
- [ ] Check what the remote's Home button opens (your launcher or Google's)
- [ ] Check the resolution and frame rate apps actually run at (expect 1080p, 60 fps)
- [ ] Sit at your normal distance and check text size and the rain on the 75"

## Build phases

Build in six phases, and don't start the next one until the current one works on the TV.

1. **Core home**: dark-mode Home, the clock, a dock of recent apps (needs app-usage permission granted over ADB), All apps, Settings
    - [ ] Done when: it boots as the home screen and every dock button works with the remote
2. **Art Mode**: set up as the TV's screensaver, 30-minute idle timer, caption, dark art only at night
    - [ ] Done when: it starts on its own after 30 min and any button returns to Home
3. **Live info**: weather sky glow, sunset-based night mode, the Scores chip and box scores, Picks for you
    - [ ] Done when: the data refreshes on its own and the screen still works offline
4. **Music**: Spotify sign-in, the Discogs record picker, Jazz mode with rotating playlists
    - [ ] Done when: Open Spotify and all 3 playlist cards open the right playlist
5. **Light/dark modes and themes**: the toggle is remembered after a restart, and the other themes become color sets
    - [ ] Done when: switching modes is instant and survives a reboot
6. **Speed pass**: rain as a sliding layer, animations settle after about 1 minute, check for dropped frames
    - [ ] Done when: no stutter on the 75" at your normal viewing distance

## Known risks

None of these block the project, but each needs a plan.

| Risk | What happens | Plan |
| --- | --- | --- |
| Firmware updates | TCL apps or Google's home screen can come back on | Keep the cleanup script ready and re-run it after each update |
| Home button | Some remotes open Google's home no matter what | Check on day one; CleanYourTV's Home guard handles most TVs |
| Continue watching | Apps keep episode progress private | Show the app and the title only where the app shares it |
| Spotify API | Premium is required, there's a 5-user limit, and reacting to the beat may be blocked | Light sync is already removed; keep the music features simple |
| Unofficial scores feed | It can change or stop without notice | Hide the Scores chip quietly if the feed fails |
| GPU load | A full-screen rain redraw at 60 fps | Use a sliding rain layer; let animations settle after 1 min |
| Light mode at night | Bright white on a 75" screen | Switch to dark mode automatically after sunset |

## Design spec

The mockups on the [TV Home Screen Layouts canvas](https://claude.ai/artifact/AFHQKmbvmFHXpyJ2fZ4MuC) are the spec. The default look is Neon Noir in crimson and gold, with subtle smoked glass.

| Token | Dark mode (default) | Light mode |
| --- | --- | --- |
| Background | #0A0606 | #F3ECE2 |
| Text | #F3ECE4 | #2A0F10 |
| Labels | #BBA38C | #7A5A50 |
| Gold accent | #E2B04A | #A8701A |
| Selected tile | #4E1913 | #A8131D |
| Glass panels | 0.5 opacity, no live blur | 0.5 opacity, no live blur |

- Fonts: Arimo (uppercase, wide letter-spacing) and Fraunces for record titles; both are free to use
- Night mode: 9 PM to 6 AM by default (set in Launcher settings), dims only the launcher's own screens 25% with a slight warm tint
- Jazz mode: same look, dimmer; counts crooners and vocal pop as jazz
- Bottom menu: recent apps | All apps · Jazz mode · Art mode · Light/Dark · Settings
- Other themes: 8 dark and 5 light color options on the canvas

## Sharing on CleanYourTV

The full launcher goes in CleanYourTV's showcase, not the main repo: it uses network features, which the reference launcher doesn't allow.

- [ ] Keep the launcher in your own repo, crediting CleanYourTV's launcher as the base
- [ ] Write a README: what it does, which services it connects to, and that it was tested on the TCL 75QM7L Pro
- [ ] Add screenshots or a short clip of Home, More, All apps, Jazz mode and Art Mode in both modes
- [ ] Include the other themes as color sets, with Neon Noir as the default
- [ ] Open a pull request that adds one row to [SHOWCASE.md](https://github.com/Gathin23/cleanyourtv/blob/main/SHOWCASE.md)
- Optional: the Neon Noir theme in `theme/neon-noir/` can also go to CleanYourTV's `themes/` folder as a pull request

## Sources

- [CleanYourTV for Android and Google TV](https://github.com/Gathin23/cleanyourtv)
- [CONTRIBUTING.md](https://github.com/Gathin23/cleanyourtv/blob/main/CONTRIBUTING.md)
- [SHOWCASE.md](https://github.com/Gathin23/cleanyourtv/blob/main/SHOWCASE.md)
