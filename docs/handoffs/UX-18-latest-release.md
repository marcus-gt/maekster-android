# UX-18 — Artist page: Latest release card

**Status: COMPLETE.**
[#48](https://github.com/marcus-gt/maekster-android/issues/48), parent [#45](https://github.com/marcus-gt/maekster-android/issues/45).
Branch `ux/45-artist-now-playing`, on top of UX-17 (`5d118638`).

## iOS reference

`ArtistDetailView.latestRelease` is `artist.album.max(by: year ?? 0)` (Swift keeps the first max), and
`featuredReleaseSection` shows an 88 pt cover, year, title (2 lines) and "N songs" on a white 10% card.
iOS hides the card offline, because its downloads carry no year.

## What changed

- `data/model/LatestRelease.kt`: `latestRelease(albums)`. It picks the newest year among the artist's own albums
  (`ArtistAlbums.albumCredits`, never `appearances`). **Tie-break:** most recently added (`Album.created`),
  then the first in list order (Room's `year DESC, sortName, id`). Albums with no year (or year 0) are never
  picked, so the card is hidden when none has one; that differs from iOS, which would pick an undated album.
- `ui/library/ArtistDetailScreen.kt`: `LatestReleaseCard`, the first grid item after the header (key
  `latestRelease`, tag `LATEST_RELEASE_TAG`). Tap → `onAlbumClick`; long-press → `onAlbumLongClick(MenuTarget.Album)`.
  Offline, the screen already gets downloaded-only albums (`artistAlbums(downloaded = true)`), so the card follows
  that filter; Room keeps years offline, so unlike iOS it still shows.
- String `artist_latest_release` = "Latest release".

## Tests

- `LatestReleaseTest` (4) and `ArtistLatestReleaseTest` (4, tall `w400dp-h1400dp` window so the card is on screen).
- They fail without the change. With the iOS rule (`maxByOrNull { year ?: 0 }`), the tie, no-year and hidden-card
  tests fail. With no card, the shown/above-discography and long-press tests fail. Picking from Appears on too
  fails the appearances test and the selection test. All 8 pass with the change.

- `AppScreenshotTest.artistDetail*` (3) waited for "Kid A", which the new card now pushes below the short
  Robolectric window (the card shows Amnesiac, 2001). They now wait for "Amnesiac"; no other change.

## Verification

- `ktlintFormat`, `assembleDebug`, `ktlintCheck`, `lintDebug`, full `testDebugUnitTest` (266 classes): pass.
- Pixel 9 emulator, Bob Dylan: the card reads 2020 · Rough and Rowdy Ways · 10 songs, as in the iOS screenshot.
  Tap opens the album; long-press opens the album menu. Checked at 200% font (the title wraps to two lines,
  fine); font scale restored to 1.0. Queue untouched.

## Not verified

- Offline on the emulator (no network toggle done); the card uses the same downloaded-only `albumCredits` the
  shelves already use, so it should show the newest downloaded album.

## Next action

UX-19 ([#49](https://github.com/marcus-gt/maekster-android/issues/49)): the Top songs carousel. It fetches
`getTopSongs` on page open, so record that in D9, and extract the Home favourite songs carousel for reuse.
