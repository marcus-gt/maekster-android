# UX-20 — Artist page: Liked songs carousel

**Status: COMPLETE.** [#50](https://github.com/marcus-gt/maekster-android/issues/50), parent #45.
Branch `ux/45-artist-now-playing`, based on the UX-19 heart follow-up (`a5d686d0`).

## Implementation

- `LibraryDao.artistLikedSongs` reads at most 25 starred songs, newest star first with stable
  tie-breaks. It matches the track artist, track contributors, primary album artist and additional
  album credits using the local mirror. Name matching is case-insensitive and only applies to songs
  with no track artist ID. It uses a literal SQLite GLOB with Unicode simple case variants.
  Offline mode only includes completed downloads. The query is observed for live changes.
- The artist ViewModel uses the active source, existing offline preference and local artist name.
  Play, Shuffle and row taps queue the displayed song references. The carousel sits below Top
  songs, leaves no grid item when empty, and reuses the header, Favorites heart, Quick playlists
  gesture and standard song menu.
- Compared with iOS `ArtistBestOf.matches` and the artist screenshot attached to #45. Album credits
  come from the corresponding album in the Android mirror.

## Verification

- Four new tests cover ID and literal-name matching, order and limit, offline downloads, live
  star and unstar updates, and shelf placement and playback actions. All four failed on behavioral
  assertions with temporary mutations and passed after restoration.
- `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck`,
  `:app:lintDebug` and `git diff --check` passed with Studio's JBR.
- Pixel 9 emulator, Bob Dylan: “It Ain’t Me, Babe” was visible in Liked songs after Top songs and
  before Albums. Checked the heart and Play/Shuffle controls at 100% and 200% font and restored 100%.
- Offline filtering and live star updates were verified in Room tests. Playback actions were
  verified through Compose callbacks. The emulator check used existing favorites and preserved
  the playback queue.

## Next action

UX-21 ([#51](https://github.com/marcus-gt/maekster-android/issues/51)) can proceed in its own session.
