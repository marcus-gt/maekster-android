# UX-02 — Home compact grids and favorite songs carousel

**Status: COMPLETE.** All three items are checked in [issue #32](https://github.com/marcus-gt/maekster-android/issues/32),
under [parent #31](https://github.com/marcus-gt/maekster-android/issues/31).
Branch `ux/01-library-layout`, following the UX-01 commits `10523888` and `8dc4632a`. Not pushed.

## Implementation

- `ui/home/HomeGrid.kt` adds a shared compact tile, modelled on iOS `HomeGridTileChrome`: a 64 dp
  cover beside a two-line title and a one-line subtitle, on a rounded `surfaceContainerHigh` card.
  It also adds `HomeGridRow` and a non-lazy `HomeGrid`.
- `homeGridColumns(width, fontScale)` replaces `pinGridColumns`. It gives two columns on phones,
  more on wide screens, and one column at 200% font.
- Pinned, Your playlists and the album shelves use the tile when their saved layout is Grid.
  Pinned stays lazy, one item per row. The Your playlists subtitle is the song count.
- The saved `HomeLayout.grids` encoding and the editor are unchanged, and so are the carousels.
  Genres keeps its chip grid, because UX-11 replaces that section.
- Favorite songs are a `LazyRow` of columns with up to three `SongRow`s each (tag `favoriteSongs`).
  Columns snap to the start edge.
- With more than one column, each column is 88% of the width so the next one peeks in. A short list
  is one full-width column.
- `HOME_STARRED_LIMIT` went from 5 to 15, which gives five columns. Tap still calls `onPlayStarred`,
  and long-press still opens the song menu.

## Verification

- New `HomeFavoriteSongsTest`: 7 favorites form columns of three at 100% and 200%, and a sideways
  scroll reaches and plays the 7th. 2 favorites stack in a single column.
- `HomeGridTest` and `PinnedGridTest` now check that the title sits beside the cover and that tiles
  sit two per row at 100% and one per row at 200%. Click and long-press are still checked, and
  `columnsFitAvailableWidth` covers the font-scale rule.
- I copied the tests onto HEAD `8dc4632a` in a temporary worktree, since removed. Against that
  pre-change code, all 6 layout tests failed. The short-list test passed, as expected for a
  regression guard.
- Passed `ktlintFormat`, `assembleDebug`, `ktlintCheck` and `lintDebug`, using the wrapper with
  Studio's JBR; Nix is not available here.
- The full `testDebugUnitTest` run passed before the final snap-position change. After that change,
  1927 tests ran and 1 failed: a `PlaybackControllerTest` 30 s playback-state timeout, unrelated to
  this slice. That class passed in isolation, 185/185.
- Pixel 9 emulator on API 37, with a real library:
  - The saved Pinned grid choice survived the upgrade and rendered compactly with artwork.
  - Favorite songs showed 3 rows with a peek. A swipe snapped column 2 to the start edge.
  - At 200% font, Pinned dropped to one column and the favorites stayed readable.
  - Font scale was restored to 1.0.
- Song activation was covered by tests only. I did not tap a song on the emulator, to avoid
  replacing the user's current queue.

## Known issues and follow-ups

- Pinned playlists have no subtitle (`PinnedItem.subtitle` is null for them). iOS shows "Playlist".
  This is a possible small follow-up.
- Section headers still say "More". Switching them to arrows is UX-03.

## Next action

UX-03 ([issue #33](https://github.com/marcus-gt/maekster-android/issues/33)): consistent section arrows on Home and Discover.
