# UX-41 — Favorite songs widget

**Status: COMPLETE** (owner approved the phone build on 26 Nov 2026). Launcher star/unstar refresh was not separately reported; see the open criterion. Wave W, `ux/polish-3w-widgets`, starting at `e7af34d3`. UX-40 is complete.

## Acceptance criteria ([issue #66](https://github.com/marcus-gt/maekster-android/issues/66))

- [x] Bounded list in the Library Favorites song order from the local mirror.
- [x] Tap starts the favorites queue at the chosen song. Shuffle uses the same queue as the app.
- [ ] Refresh after star changes and sync. Room star invalidation and a representative sync upsert are covered by the regression. Launcher star/unstar and end-to-end sync checks are pending.
- [x] Empty messages and downloaded-only offline queue. Offline ordering is covered by the regression.
- [x] Android 12 and later only through the existing versioned provider flag.
- [x] Regression failed with a 13-song limit and passed after restoring 12. API 37 emulator placement, row playback, shuffle and layout at 100% and 200% verified.
- [x] Owner phone check: after the row art, playing background, Play/Shuffle/Next header and orange play button, the owner answered “Nice! Commit!”.

The widget uses the same SQL, sort preference and reference-based queue as Library Favorites. It observes a local 12-song preview only while widget instances exist. Row taps resolve the current song ordinal. Shuffle uses all eligible favorites. Cold-start taps read the saved offline preference before querying. Uses the existing RemoteViews collection and Android 12 provider gate. No dependency added.

The API 37 launcher listed the 4×2 widget. It rendered song titles and artist names. Tapping the second row played “a track at 150bpm” at media session index 1. Shuffle started “Hello Future Us” at index 0. At 200% the header and Shuffle remained visible and the list retained a complete first row. Restored the emulator to 100%. Empty copy was checked in resources. No launcher session used an empty library or Android 11.

Formatting, assemble, full unit suite, lint and diff checks passed before the cold-start change. Nix was absent from the IDE shell PATH, so checks used the Studio JBR and wrapper fallback. After the cold-start change, formatting, assemble and lint passed. The full suite completed 2,082 tests with one timeout in existing `enteringShuffleOrRepeatTrimsTheGeneratedTail`. That test and the UX-41 regression passed together in isolation. The final full-suite retry passed all 2,082 tests, with assemble, ktlint, Android lint and diff checks passing.

## Owner check

Asked whether the verified debug APK could be installed on the connected Pixel 9. The owner answered “Yes, install it on the Pixel 9.” Installation succeeded. Asked the owner to add **Your favorite songs** and report whether tapping a row starts at that song, Shuffle works, starring or unstarring updates the widget, and whether the launcher layout has issues. Awaiting the result.

## Owner feedback round 1 (IN PROGRESS)

The owner asked for: (1) album art left of each song, (2) the playing song's artwork as the background, as on the other widgets, (3) Play, Shuffle and Next in the header where Shuffle was, using the shuffle symbol.

Implemented (session interrupted, recovered from the diff on 26 Nov 2026): 40dp cover per row (`widget_favorite_song.xml`, loaded by `WidgetArtwork`); `WidgetArtworkBackground` behind the list while this server's queue is playing, cleared when paused, like the Now Playing and Recently added widgets; header Play/Pause, Shuffle (`ic_widget_shuffle`) and Next. Play toggles the current queue for this server, or starts favorites in order when nothing is loaded; Next only when there is a next item; Shuffle starts all eligible favorites shuffled. Playback mapping is `PlaybackState.favoriteWidgetPlayback` with `FavoriteSongsWidgetTest`, which failed when a paused song kept the background (the recovered code did) and passes after the fix.

Checks after the fix (Studio JBR + wrapper, Nix absent): ktlintFormat, assembleDebug, full testDebugUnitTest, ktlintCheck and lintDebug passed (`BUILD SUCCESSFUL`). The debug APK was installed on the API 37 emulator and the owner's Pixel 9 (install approved earlier). Unverified: launcher rendering of the new header and row art; the widget is not placed on the emulator home. Owner follow-up: the dark play/pause icon read as grey on the dark widget, so the header play button now uses the orange Now Playing play button style (40dp circle, `Widget.Maekster.NowPlaying.PlayButton`); assemble, lintDebug and widget tests passed and the APK was reinstalled on the Pixel 9 and emulator. Next: owner rechecks the three points plus star/unstar refresh on the Pixel 9; then tick #66, update the roadmap row and commit.

Issue #66 has five verified criteria checked. Refresh and owner phone checks are open. Owner check ticked on #66; the refresh criterion stays open until star/unstar refresh is seen on a launcher. Committed on `ux/polish-3w-widgets`, not pushed. Recommended next task: UX-42 Recently played albums widget.
