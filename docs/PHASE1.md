# Phase 1: Core Home

Status: code written, not yet built or tested.

## What's in it

| Part | File | Notes |
| --- | --- | --- |
| Neon Home screen | `NeonHome.java` | Laid over CleanYourTV's Home, so their Home-button guard, remote helpers and Inputs menu keep working |
| Backdrop | `NeonBackgroundView.java` | Background, two glows, rain and scanlines, drawn once into a picture: zero GPU work while idle |
| Hairline clock | `NeonClockView.java` | 60 ticks, no second hand, redraws once a minute |
| Glass panels | `NeonCutDrawable.java` | Cut corners, 50% fill, gold hairline, no blur |
| Recent apps | `NeonRecents.java` | 6 most recent TV apps, from what Home opened plus Android's usage stats when allowed |
| Colors | `NeonPalette.java` | Dark (default) and light, from `design/tokens.json` |
| Icons | `res/drawable/nn_ic_*.xml` | All apps, Jazz, Art, Sun, Moon, Gear (Lucide icons, ISC license) |
| Fonts | `res/font/nn_*.ttf` | Arimo and Fraunces (SIL Open Font License) |
| Hooks | `MainActivity.java` | Six small changes, each marked `NEON` |

## How it behaves

- **Home button:** always opens the Neon Home, with the focus on the first app.
- **Dock:** shows the 6 most recent apps with their TV banners. OK opens the app.
- **Continue chip:** opens the last app you used. It stays hidden until you've opened something.
- **All apps (grid icon):** reveals CleanYourTV's full app grid. Back returns to the Neon Home.
- **Jazz mode:** shows a "comes in phase 4" note.
- **Art mode:** opens the clock screensaver until phase 2 replaces it.
- **Sun / moon:** switches between light and dark instantly, and the choice survives a restart.
- **Gear:** opens Launcher settings. Holding OK on it opens the TV's own Settings.
- **Back on Home:** does nothing, like any TV home screen.

## Not in phase 1 yet

Tonight and Scores chips, weather on the date line, sunset night mode, More (phase 3); Art Mode (phase 2);
Jazz mode, Spotify, Discogs (phase 4); moving rain (phase 6).

## Done when

It boots as the home screen, and every dock button works with the remote.
