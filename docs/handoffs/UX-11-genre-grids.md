# UX-11 — Rotating Home genre grids

**Status: COMPLETE** (18 Nov 2026). [Issue #39](https://github.com/marcus-gt/maekster-android/issues/39) under [#31](https://github.com/marcus-gt/maekster-android/issues/31). Branch `ux/01-library-layout`, started at `5ee1ca74`.

## iOS reference (confirmed)

`Maekster/Models/Domain/MainGenre.swift`, `HomeGenreShelves`: albums are grouped by main genre, and the five largest nonempty groups are sorted by size, then name. Each shelf takes a seeded shuffle (FNV-1a of `day|genre`, SplitMix64) of the ID-sorted albums, with the local calendar day as the key. The shelf keeps 20 albums.

## Adopted Android behaviour

- Each album gets one Discogs main genre, chosen by its distinct matching track count. A matching album-level genre breaks ties, then the genre name. This uses the UX-10 mapping, so each heading opens the matching Library genre collection.
- Genre selection (owner decision, 18 Nov 2026; deliberately differs from iOS, which always shows the five largest): genres with more than six albums form the pool, and five are picked in a daily shuffled order. The order uses `dailyGenreShuffle(day, genre)` (FNV-1a over `day|genre` plus a 64-bit mix), computed in Kotlin and passed to the query alongside the tag mapping. If fewer than five genres qualify, the largest smaller genres fill the remaining shelves after the shuffled ones, sorted by size and then name. Each grid shows up to six albums, per the UX-11 brief.
- Rotation: albums are ordered by ID, and the window advances by `min(6, count - 1)` per local epoch day, modulo the group size. Groups larger than six show a new window each day and cycle through every album. Groups of six or fewer show all albums, in a rotated order. This is a deliberate SQL-friendly replacement for the iOS seeded shuffle, and it is stable within a day. Foreground Home re-emits at local midnight (`localHomeDays`, which polls at most every 60 s).
- Offline (`downloaded = true`), only albums with completed downloads take part, and empty groups are omitted. Queries are scoped to the active `sourceId`.
- Tiles reuse the adaptive Home artwork grid. A tile opens album detail and keeps its long-press menu. The heading opens `Routes.genreAlbums(genre, style = null)`. Genres is now last in `DEFAULT_HOME_ORDER` (owner request, 18 Nov 2026), so new installs show it at the bottom of Home. Saved layouts keep their own order, and normalisation only appends shelves that are missing. The saved Genres visibility and position are preserved. The old pill and grid layout switch is gone (`HomeShelf.Genres` no longer `supportsGrid`), and `brickRows` was removed.

## Key files

`data/db/HomeGenreQuery.kt` (new), `LibraryDao.homeGenreAlbums`, `LibraryRepository.homeGenreShelves`, `model/AlbumCollection.kt` (`HomeGenreShelf`, `HomeGenreAlbum`), `HomeViewModel.genreShelves`, `HomeScreen.genreGrids`/`homeAlbumGrid`, `AppRoot.kt`, `HomeLayout.kt`.

## Verification

- Regression: with the day term cancelled (`:day - :day`), `AlbumCollectionDaoTest.homeGenreGridsRotateDistinctAlbumsWithinTheActiveLibrary` failed at the day-change assertion. It passes with rotation restored. The test also covers small-library fill ordering, same-day stability, six distinct albums, a small group, source isolation and the offline filter.
- Genre shuffle regression: with a constant shuffle key, `homeGenreShelvesShuffleDailyAmongGenresWithMoreThanSixAlbums` failed because the genre set never changed across ten days. With the key restored it passes. It checks same-day stability, five shelves drawn only from genres with more than six albums, and that the selected set and the first shelf vary across days.
- `GenreRowsTest` covers the grid at normal and 2× font scale: two columns at normal scale, one column at 2×. Heading and tile clicks are also tested. Roborazzi renders were reviewed locally.
- Passed after the genre shuffle: `ktlintFormat`, `assembleDebug`, `testDebugUnitTest` (1953 tests, 0 failures), `ktlintCheck`, `lintDebug`.
- Emulator after the genre shuffle: Home showed Electronic followed by Folk, World, & Country, which is not one of the five largest genres. This confirms the daily pick is active.
- Emulator (Pixel 9, API 37, debug `io.github.marcus_gt.maekster.nightly`): the Electronic, Hip Hop and Pop grids each showed six artwork tiles. Hip Hop opened the full collection with Play and Shuffle. The Scars tile opened album detail. Back returned to Home.

## Known issues / unverified

- The midnight rollover was not observed live on a device. It is covered only by the `day` parameter in the DAO test.
- The offline grids were not exercised on the emulator. They are covered by the DAO test.
- Not caused by this slice: the floating Settings button overlays content once Home scrolls.

## Next

UX-12, the Home and Discover recommendation audit ([#40](https://github.com/marcus-gt/maekster-android/issues/40)).
