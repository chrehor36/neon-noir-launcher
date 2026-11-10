# Later

Ideas and leftovers, not scheduled yet.

## Features

- **New on your friend's Plex.** A row in More showing what was recently added to the shared Plex server, with
  posters, opening in the Plex app. Needs the server's address and your Plex sign-in token (X-Plex-Token).
  TMDB can't see a private server, so today's "Plex" pick comes from Plex's free catalog only.
- **Spotify playback control.** Play, pause and skip from Jazz mode without leaving Home. Needs a Spotify
  developer app (`SPOTIFY_CLIENT_ID`). For now, Jazz mode's Spotify buttons hand off to the Spotify app.

## On the emulator, next time at the PC

- Sign in to Google Play on the emulator (it's waiting on the sign-in screen), then install Peacock, Prime Video,
  Plex, Tubi, Pluto TV, the Roku Channel and Spotify, so the dock, All apps, Picks and Jazz mode's Spotify
  buttons can be tested with the real apps.

## When the TCL arrives


- (No antenna for now, 2026-11-09: skip until there is one.) Live: with an antenna plugged in, open More → Live →
  Antenna and scan for channels. Then check that the guide lists
  them with what's on, that OK tunes each one, and that the HDMI buttons switch inputs. If the guide stays
  empty, `adb logcat -s NeonLive` says whether the TV refused the channel list.
- Live: install Pluto TV, open it once, and check whether its channels join the guide. If they don't, it keeps
  its guide to itself, and its button in More's Live section is the way in. Do the same for Google TV Freeplay.
- Check that the rain and drifting glows run smoothly. If not, slow the rain first, or turn it off.
- Check that night mode's warm veil dims Home from 9 PM (or the time set in Launcher settings) and leaves
  other apps alone.
- Check that each Picks poster opens its title in Peacock, Prime Video, YouTube, Plex, Tubi, Pluto TV and the
  Roku Channel. The package names for YouTube and the Roku Channel on Google TV are best guesses.
- Check that Jazz mode's Open Spotify, record and playlist buttons open Spotify to the right place.
