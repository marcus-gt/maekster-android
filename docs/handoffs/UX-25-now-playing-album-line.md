# UX-25 — Now Playing: album line and iOS text hierarchy

**Status: COMPLETE.** [#55](https://github.com/marcus-gt/maekster-android/issues/55), parent #45.
Branch `ux/45-artist-now-playing`, based on `7480c594`.

## Findings

- `NowPlayingScreen` shows a fixed-height row: title (`headlineSmall`, marquee) over the artist
  (`bodyMedium`, `primary` tint, marquee); the whole block opens the artist; `FavoriteHeart` beside it. The cover
  opens the album (`onAlbumClick`, from the song's `albumId`). No album line.
- iOS (first Now Playing screenshot in #45): title large and bold, artist medium and secondary, album smaller
  and tertiary, heart and … beside. Roughly 22 : 20 : 17 pt.

## Implementation

- `NowPlayingScreen`: title `headlineSmall` SemiBold (default `onSurface`); artist `titleLarge` in
  `onSurfaceVariant` (was `bodyMedium` in `primary`); new album line `bodyLarge` in `onSurfaceVariant` at
  75% (`NOW_PLAYING_ALBUM_TAG`), 4 dp below the artist. Each line is one line with `basicMarquee` in a
  fixed-height box, so long text never moves the controls. Title + artist still open the artist (block is now
  66 dp); the album line opens the album (`onAlbumClick`, same as the cover), is disabled without an album id
  and hidden without an album name.
- Large text fix found on the emulator: at 200% the trimmed line height inside the fixed boxes and the
  marquee's clip cut off descenders ("Reggy"). The artist and album lines are now untrimmed and their boxes
  (and the title+artist block) use `heightIn(min = …)`, so the glyphs fit; one-line text keeps every height
  stable, so the controls still never move. The title keeps the trimmed style (as before).

- Owner follow-up (20 Nov 2026): long artist names felt cramped at 22sp, so the artist line is now
  `titleLarge` at 18sp / 26sp line height (between the 24sp title and 16sp album); the title+artist block is
  64 dp. "Reggy van Oers · Joachim Spieth" now fits beside the heart on the Pixel 9. Full run again:
  2016 tests, 0 failures; build, ktlint and lint (no issues) pass.
- Owner follow-up (20 Nov 2026): a split-second "Buffering" at a track change looked like a glitch. Cause: the
  waiting label was an extra line above the controls, so the weighted cover shrank by one line (16 dp) and grew
  back. New `PlaybackStatusLine` (`PlaybackWaitingLabel.kt`) is one reserved slot: the format line
  ("Streaming · FLAC …") keeps its space, hidden and silent for TalkBack, with the wait status drawn over it;
  with no format line it still reserves one `bodySmall` line. `PlaybackWaitingUiTest` gains two tests (with and
  without a format line) that the cover and Next button do not move and the hidden format line is not
  exposed; both failed before the change (cover 328.9 → 312.9 dp bottom). Full run: build, ktlint, lint and
  `git diff --check` pass; 2018 tests with 1 failure, the known `enteringShuffleOrRepeatTrimsTheGeneratedTail`
  flake (2 vs 4; it failed again in one whole-class run and passed 3/3 alone; no playback code changed). Not
  checked on the emulator: the owner was using it, and the blip is too brief to capture.

## Verification

- `NowPlayingTapsTest` now runs at `PixelXL` (as `NowPlayingLyricsTest` does): on Robolectric's default
  320x470 dp screen the cover had at most 36 dp, and the extra album line left it 0 dp, failing the swipe tests.
  New tests: album below the artist and opening the album (and the cover still does), hidden without an album,
  title > artist > album sizes, one line at 200% font with the lyrics pane open and the controls reachable;
  updated the artist target height (58 → 66 dp) and the inert-targets test.
- Without the change: the old artist style failed the size test; removing the album line failed all four
  album tests (the hidden test guards the opposite). Restored; `com.subtracks.ui.playback.*` passes (84).
- Note: nonlinear font scaling (SDK 34+) makes 200% text about 1.5x, so tests assert one line, not 2x heights.

- `PlaybackControllerTest.enteringShuffleOrRepeatTrimsTheGeneratedTail` failed once in a full run (2 vs 4)
  and passed when the class ran alone (190/190); this slice changes no playback code, so it is a timing flake.
- Final full run after the descender fix: `:app:ktlintFormat`, then `:app:assembleDebug`,
  `:app:testDebugUnitTest` (2016 tests, 0 failures), `:app:ktlintCheck`, `:app:lintDebug` (no issues) and
  `git diff --check` passed with Studio's JBR.
- Emulator (Pixel 9), on the queue in use (playlist "Techno Chill", "Noctilucent" by Reggy van Oers ·
  Joachim Spieth): 100% showed the title, artist and album hierarchy with a large cover, the header "NOW
  PLAYING / Techno Chill"; tapping the album line opened the Noctilucent album. 200%: the artist marquees,
  descenders now intact after the fix, the controls stay in place; the lyrics pane open at 200% keeps all
  three lines above the controls. Font restored to 100%; the queue was not replaced.
- Not checked: TalkBack reading of the album line, a song with no album on the device (unit-tested).

## Next action

UX-26 ([#56](https://github.com/marcus-gt/maekster-android/issues/56)): find out why lyrics are not synced
(may need an owner decision on LRCLIB).
