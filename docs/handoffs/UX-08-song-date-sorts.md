# UX-08 — Songs date sorts

**Status: COMPLETE.** [Issue #7](https://github.com/marcus-gt/maekster-android/issues/7),
under [parent #31](https://github.com/marcus-gt/maekster-android/issues/31). Branch
`ux/01-library-layout`, based on `3dce7ba6`. Both worktrees were clean.

## Behavior trace

- Android Songs offers Title, Artist, Album and Added. `songs.played` mirrors Subsonic's
  last-played timestamp and updates locally on a submitted scrobble. It is null when unknown.
- iOS `SongSort.recentlyPlayed` orders by the last-played timestamp, newest first, with name
  ordering for ties and never-played tracks. `releaseDate` orders by the track's year, newest
  first, with unknown years last. Neither uses the date added to the library.
- Android mirrors the album year today. Add the Subsonic track year and use the mirrored album
  year when the track year is absent, including before the first sync after upgrade.

## Implementation

- Added Recently played and Released to Songs list options and the shared query. Newest first
  is the natural direction. Unknown values stay last in both directions. Title (respecting the
  sort-name preference) and song ID break ties.
- Room migration 63→64 adds nullable song year. Subsonic parsing and sync populate it with a
  positive track year. Existing rows can still use the album-year fallback before their next sync.
- Added parser and sync assertions and a two-page downloaded/Favorites/search test that compares
  the listed and reference-queue order and verifies shuffled membership. The trace item in #7
  is checked.

## Verification

- The focused regressions failed when Released used only the album year, Recently played used
  the added date, and the parser and sync writer omitted the year. All mutations were restored.
- Passed format, debug build, ktlint, Android lint and the full unit suite (1,944 tests)
  using the wrapper and Studio JBR. The first full run had one failure in the unchanged
  autoplay queue-tail test. The full retry passed.
- Upgraded the API 37 emulator, selected both sorts and confirmed the newest-first indicator.
  Released composed with a live search and Play and Shuffle stayed visible. Restored Title
  ascending. Queue order, shuffled membership and offline filtering were exercised in tests.
- The queue resolves its saved query against live Room rows. Scrobbles and sync can change
  ordering keys after playback starts. The queue test covers a stable result at playback start.

## Next action

Start UX-09 from the Favorites checklist in [issue #38](https://github.com/marcus-gt/maekster-android/issues/38).
