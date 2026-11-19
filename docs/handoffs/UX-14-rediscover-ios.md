# UX-14 — In heavy rotation and Rediscover as on iOS

**Status: COMPLETE** (19 Nov 2026). [Issue #42](https://github.com/marcus-gt/maekster-android/issues/42); supersedes [#41](https://github.com/marcus-gt/maekster-android/issues/41). Branch `ux/01-library-layout`, started at `2a881b9f`. Rediscover was committed in `8f5f9b7b`, In heavy rotation in a later commit.

Owner decisions: on 18 Nov 2026, Rediscover and Artist picks (UX-15, #43) were to behave as on iOS. On 19 Nov 2026, the owner decided In heavy rotation should too.

## Implemented

- `UNSHOWN_FREQUENT_ALBUMS` in `LibraryPaging.kt` follows the iOS `HomeFeedViewModel` rule. It takes the 40 most played albums and drops the 20 most recently played and the first 40 favourite albums. The remaining albums keep their play-count order. No time window applies.
  - `HEAVY_ROTATION_ALBUMS_SQL` gives the first 12. It is used by `LibraryDao.heavyRotationAlbums` (Home row) and `heavyRotationAlbumsQuery` (the "See all" list).
  - `REDISCOVER_ALBUMS_SQL` gives the next 12. It is used by `rediscoverAlbums` and `rediscoverAlbumsQuery`.
  - As on iOS, the two shelves never repeat each other. Each "See all" list shows the same 12 albums as its shelf.
- Removed:
  - `mostPlayedAlbums(sourceId, limit)` (the old top 10);
  - `homeMostPlayedAlbums` (the old unbounded "See all");
  - `REDISCOVER_WINDOW_MS` and `rediscoverCutoff()`, along with the #41 bug where milliseconds were compared with seconds.
- `HomeViewModel` still shows In heavy rotation only when there is play data. Its play-count list info is unchanged.

## Verification

- Tests in `LibraryHomeDaoTest`:
  - `heavyRotationAndRediscoverFollowIosFrequentAlbumsMinusRecentAndFavorites` covers both rows and both paging queries. It also covers the recent and favourite exclusions, the split at 12, the pool of 40 and source isolation.
  - `heavyRotationIgnoresUnplayedAndOrdersByCount`.
- Mutation check: putting back the old top-by-play-count query makes the test fail; it passes again once restored.
- Full checks passed on 19 Nov 2026: `ktlintFormat`, `assembleDebug`, `testDebugUnitTest` (1956 tests, 0 failures), `ktlintCheck`, `lintDebug`.
- Emulator (Pixel 9, real library):
  - In heavy rotation opens with Alive 2007, The Action Is The Juice and Here Because of Hope. None of them appear in Recently played, and Rediscover follows without repeating any (Bossa Nova Soul Samba, Entity Stims, Live God).
  - "See all" shows the same albums in play-count order (69 plays, 57, 51 and so on).
- Not checked on the emulator: that the shelf holds exactly 12 albums. The tests cover it.

## Next

None in #42. The Home recommendation work from the UX-12 audit is done.
