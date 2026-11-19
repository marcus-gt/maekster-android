# UX-22 — Optional album list layout on artist pages

**Status: COMPLETE.** [#52](https://github.com/marcus-gt/maekster-android/issues/52), parent #45.
Branch `ux/45-artist-now-playing`, based on `3810f8ca`. The M1.2 worktree is clean and detached.

## Implementation

- `UserPreferences.artistAlbumsAsList()` / `setArtistAlbumsAsList` on DataStore key `artist_albums_as_list`,
  default `false` (grid), separate from the Library's `library_albums_as_list`.
- `ItemActions.albumLayout: AlbumLayoutChoice?` (`ItemContextMenu.kt`). Only the artist page's … button
  passes it, so its menu shows "Show albums as a list" or "Show albums as a grid". Other artist menus,
  such as a Library long-press, do not.
- `ArtistDetailViewModel` now also takes `UserPreferences` (`AppModule` updated) and exposes
  `albumsAsList` / `setAlbumsAsList`. `ArtistDetailScreen(albumsAsList)` renders full-width
  `ArtistAlbumRow`s in the same `LazyVerticalGrid` (full-line span): 64 dp artwork, title (2 lines),
  `[album artist ·] year · N songs` (album artist only on Appears on, as on iOS; 2 lines, ellipsis),
  the existing `ListDownloadIndicator` and a chevron. Shelf headers, Play/Shuffle, collapse, tap and
  long-press are unchanged. `estimateScrollPx` takes `columns` (1 in list mode) for the hero gradient.
- iOS reference: #45 screenshots 2 and 3.

## Verification

- New tests: `ArtistAlbumLayoutTest` (grid default, switching to the list, row content, download progress
  on the downloading row only, tap/long-press/Play/Shuffle/collapse, row layout at 100% and 200% font
  with Roborazzi captures),
  `ItemContextMenuAlbumLayoutTest` (both labels, the chosen value, absent from other artist menus) and
  `UserPreferencesTest.artistPageAlbumLayoutDefaultsToGridAndPersistsIndependentlyOfTheLibrary`.
- Mutations (always grid, album artist dropped, menu item hidden, Library key shared, download status
  not passed to the row) made every new
  test fail except `otherArtistMenusHaveNoLayoutChoice`, which guards the opposite regression (an
  item shown everywhere). After restoring, all pass.
- `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest` (2000 tests, 0 failures),
  `:app:ktlintCheck`, `:app:lintDebug` (no issues) and `git diff --check` passed with Studio's JBR.
- Pixel 9 emulator, Bob Dylan: the … menu offered the list, rows matched iOS at 100%, the choice survived
  a force-stop and relaunch, rows read well at 200% font, the menu then offered the grid, and choosing it
  restored the grid. The font is back at 100%, the queue is untouched and the layout is back on grid.
- Not checked on the emulator: an Appears on row with real data, a list row while downloading (both
  covered by tests), rotation.
- Seen in passing, outside this slice: Search finds nothing when the query ends in a space.

## Next action

UX-23 ([#53](https://github.com/marcus-gt/maekster-android/issues/53)) is next. It needs an owner decision on the
station naming, so ask before implementing it.
