# UX-14 — Rediscover as on iOS

**Status: IN PROGRESS** (18 Nov 2026). [Issue #42](https://github.com/marcus-gt/maekster-android/issues/42), Rediscover part; supersedes [#41](https://github.com/marcus-gt/maekster-android/issues/41). Branch `ux/01-library-layout`, started at `2a881b9f`.

Owner decision (18 Nov 2026): Rediscover and Artist picks should behave as on iOS. The Artist picks work is UX-15 (#43). In heavy rotation was not part of the decision and keeps its current Android behaviour (top 10 by play count).

## Implemented

- `REDISCOVER_ALBUMS_SQL` in `LibraryPaging.kt`, shared by `LibraryDao.rediscoverAlbums(sourceId)` (Home row) and `rediscoverAlbumsQuery(sourceId)` (More list), follows the iOS rule. It takes the 40 most played albums, removes the 20 most recently played and the first 40 favourite albums, skips the first 12 (iOS heavy rotation) and returns the next 12. There is no time window. Like iOS "See all", the More list shows the same 12.
- Removed `REDISCOVER_WINDOW_MS` and `rediscoverCutoff()`, together with the milliseconds-versus-seconds bug in #41.

## Verification

- The new test `LibraryHomeDaoTest.rediscoverFollowsIosFrequentAlbumsAfterHeavyRotation` covers the row and paging, the recent and favourite exclusions, the skip of 12, the 40 pool and source isolation. It failed when the exclusions were disabled and passes with them restored.
- Passed: `ktlintFormat`, `assembleDebug`, `testDebugUnitTest` (1952 tests, 0 failures), `ktlintCheck`, `lintDebug`.
- Emulator (Pixel 9, real library): Rediscover now shows frequently played albums that don't appear in In heavy rotation, Recently played or the favourites (Bossa Nova Soul Samba, Entity Stims, Live God, and others).

## Remaining in #42 (not requested)

In heavy rotation still shows the top 10 by play count, without the iOS exclusions. Matching iOS would need its own owner request.

## Next

UX-15 (#43): Artist picks as on iOS.
