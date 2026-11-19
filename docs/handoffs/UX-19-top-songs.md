# UX-19 — Artist page: Top songs carousel

**Status: COMPLETE.** [#49](https://github.com/marcus-gt/maekster-android/issues/49), parent
[#45](https://github.com/marcus-gt/maekster-android/issues/45). Branch `ux/45-artist-now-playing`,
on top of UX-18 (`85eb5be9`). The M1.2 worktree was clean and detached.

## Implementation

- Subsonic `getTopSongs` uses the artist name and count 25, behind the `TopSongs` interface. The
  relevant iOS screenshot from #45 was downloaded and viewed. The Android carousel follows the
  requested three-row Home Favorites layout, placed after Latest release.
- Room migration 64 → 65 adds the ordered per-source, per-artist cache and fetch records.
  The DAO joins to the local mirror for song metadata and artwork, excludes missing songs, and
  only returns completed downloads offline. Empty successful replies replace the cache.
  Failed or stale-source replies leave the previous result intact.
- The artist route creates the ViewModel on entry, ahead of lazy composition. A request cancelled
  by going offline can retry on reconnect. Cached results stay visible during a refresh.
  A first fetch with no cache keeps the section hidden, so empty and failed requests leave no
  temporary space. The D9 note documents the on-open fetch.
- `SongCarousel` is shared with Home. Top songs shows playback actions, marks playing and downloaded
  rows, and opens the standard song menu with favorite and download actions. A row tap starts the
  ordered list at that index. The controller uses short song-reference queues.

## Verification

- The inherited working tree built with `:app:assembleDebug` using Android Studio’s JBR.
- Added repository, ViewModel, wire and Compose tests. The first seven tests ran against the
  inherited refresh logic and the reconnect test failed as expected (one call after reconnect,
  expected two). Its implementation now records the request after it returns.
- The remaining ten tests failed on behavioral assertions after deliberately breaking the wire
  parameters and error propagation, cache isolation and retention, offline download filters,
  tap index, and empty-section layout. All temporary mutations were restored.
- `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck` and
  `:app:lintDebug` passed with Studio's JBR, including all 11 new tests and the existing migration
  chain schema comparison. `git diff --check` passed.
- Pixel 9 emulator, Bob Dylan on the configured server: Top songs appeared after Latest release
  and before Albums. Confirmed three rows, horizontal scrolling, artwork, Play and Shuffle
  labels, and the long-press song menu with download and favorite actions. Checked the artist
  carousel at 100% and 200% font and restored 100%. Home Favorites was visible on Home at 100%.
- Offline filtering, empty/error handling and playback dispatch were exercised in automated tests.
  Offline on the emulator and actual Play, Shuffle and row taps were skipped to preserve the
  existing playback queue. The paused track remained “Estate”.

## Next action

UX-20 ([#50](https://github.com/marcus-gt/maekster-android/issues/50)) can reuse `SongCarousel`.
Stop this session after the UX-19 commit. Do not push.
