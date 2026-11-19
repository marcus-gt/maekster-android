# UX-10 — Genre and style browsing

**Status: COMPLETE.** Branch `ux/01-library-layout`. The initial implementation is in `e5596c3e`. The follow-up commit is titled `maekster: refine genre rows and album sorting`. All three UX-10 criteria are checked in [issue #9](https://github.com/marcus-gt/maekster-android/issues/9) under [#31](https://github.com/marcus-gt/maekster-android/issues/31).

## Behavior

- Library → Genres groups the active server’s synced song tags through the existing Discogs taxonomy. Room pages the main genres, styles and albums. Groups count distinct albums, and genre search matches main and style names.
- A main genre opens **All** and its available styles. The All row uses the same artwork and count layout as the styles, with the Styles heading separating the sections. Tapping All or a style opens its album collection. Album detail genre chips open their exact-tag collections.
- The All row shows the cover of the most recently added matching album, as in the iOS reference. If the album has no separate cover ID, it uses the album ID. Its count and artwork use the final collection’s local membership and download filter.
- Final genre and style collections use the Albums sort options and directions. Their shared preference is saved separately from Albums and defaults to newest release year first. Play follows the displayed album order and disc/track order. Shuffle uses the same membership and bounded reference queue.
- Android derives an album’s genre membership from its mirrored song tags. Offline, an album appears when any of its tracks has a completed download. The playback queue includes only completed downloads.

## Verification

- The debug build, all 1,951 unit tests, ktlint and Android lint passed with the committed wrapper and Android Studio JBR. Nix was unavailable in Studio. The last full suite passed on rerun after the intermittent `PlaybackControllerTest.enteringShuffleOrRepeatTrimsTheGeneratedTail` failure.
- Room and Compose regressions failed with deliberately incorrect membership, cover selection, album count, style routing or queue sort, and passed after restoring the implementation. The genre Compose fixture supplies its static page through a StateFlow to avoid a timing failure in the full suite.
- On API 37, verified Jazz → Modal → Back, All Jazz navigation, Play and Shuffle, both Title sort directions and the sort selection carrying over to Modal. Restored Year. All Jazz showed the same artwork and 456-album count as the supplied iOS screenshot. The divider and chevron removals passed the automated checks.
- Compose covered the 200% layout. Room covered offline membership, source isolation and queue order. Live offline playback and a physical-device check remain unverified.

## Next action

Start UX-11 from the rotating genre album grid checklist in [issue #39](https://github.com/marcus-gt/maekster-android/issues/39).
