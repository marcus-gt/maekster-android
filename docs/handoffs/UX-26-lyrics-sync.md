# UX-26 — Lyrics: find out why lyrics are not synced

**Status: COMPLETE.** [#56](https://github.com/marcus-gt/maekster-android/issues/56), parent #45.
Branch `ux/45-artist-now-playing`, based on `e575e67a`.

## Reproduction (emulator, Pixel 9, owner's Navidrome server, 20 Nov 2026)

- Device settings (`user_prefs.preferences_pb`): `lyrics_source = Auto`, `lyrics_lrclib_allowed = false`.
- "My One and Only Love" (John Coltrane and Johnny Hartman): the pane showed plain lyrics, provider: server.
- Room `lyrics_cache` (copied off the device, read-only): 46 entries, all `provider = server`, all
  `synced: false`, none from LRCLIB. Sampled: My One and Only Love, You Are Too Beautiful, All Along the
  Watchtower (Bob Dylan & Grateful Dead), Autumn Leaves, Silver Bells, Do You Hear What I Hear? (Bob Dylan).
- No parsing defect: no cached line holds an LRC timestamp, so the server sends plain text. Selection, timing
  offset and position updates were not involved (no timed variant existed).

## Causes

1. With Auto, `LyricsRepository` asks LRCLIB for timed lyrics only if `allowLrclib` is on; it was off by
   default. iOS has no such switch (Auto: server, then LRCLIB; disclosure under the setting).
2. Found after turning LRCLIB on: `GET /api/get` with title, artist, album "The Gentle Side of John Coltrane
   (digital)" and duration 292 returned 404. LRCLIB has the song under album "John Coltrane and Johnny
   Hartman" (295 s, synced); without `album_name` (duration kept) it returns 200. iOS sends the same exact
   request, so it misses such songs too (worth porting there).

## Owner decision (asked and answered 20 Nov 2026)

Q: LRCLIB on by default as on iOS, a one-time prompt in the lyrics pane, or keep the opt-in?
**A: On by default.** Keep the switch and the disclosure. Recorded in D10, `PRIVACY.md`, FEATURE_PARITY.

## Implementation

- `LyricsPreferences.LRCLIB_ALLOWED_BY_DEFAULT = true`, used by `UserPreferences.lyrics()` and
  `setLyricsSource` when nothing is stored. An explicit saved choice (on or off) is kept, so existing installs
  that saved "off" stay off until changed in Settings > Playback > Lyrics.
- `LrclibProvider.fetch`: after a 404 for a lookup that included an album and a duration, one more lookup
  without `album_name` (same pacing, cooldown and error handling; no new data is sent). No retry without a
  duration, after a hit, or after other errors.
- Disclosure string starts "Auto checks your server first, prefers lyrics with timings and falls back to
  LRCLIB."; `PRIVACY.md` says LRCLIB is on by default and when it is contacted.

## Tests (each new one failed before its change)

- `LyricsPreferencesTest.lrclibIsAllowedByDefaultButAnExplicitChoiceIsKept` and `changesPersistAcrossReaders`
  (failed: allowLrclib false); the `setLyricsSource(Lrclib)` assertion failed with the old stored-flag check.
- `LrclibProviderTest.anAlbumMissRetriesOnceWithoutTheAlbumButKeepsTheDuration` (failed: empty result) and
  `theAlbumRetryNeedsADurationAndHappensOnlyOnce` (failed: 1 request, not 2). The 404 case in
  `notFoundIsEmpty…` now uses a song without an album. Loader and dialog tests start from an explicit "off".
- Final full run: `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest` (2021 tests,
  0 failures), `:app:ktlintCheck`, `:app:lintDebug`, `git diff --check` pass with Studio's JBR.

## Emulator (22 Nov 2026)

The device's explicit "off" was kept; the switch was turned on in Settings > Lyrics (saved true, matching the
decision). With the new build, "My One and Only Love" shows timed lyrics (dimmed lines waiting for the first
timestamp at 0:05), and `lyrics_cache` now holds `lrclib | My One and Only Love | synced`. The queue was not
replaced; the pane was closed again.

Not checked on the device: lines following live playback (the player was paused and scrobbling is on, so it
was not started; following is covered by the existing `LyricsPane` tests); other songs with LRCLIB.

## Next action

UX-27 ([#57](https://github.com/marcus-gt/maekster-android/issues/57)): lyrics blur, reading mode and inline
line as on iOS.
