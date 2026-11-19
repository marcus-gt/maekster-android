# UX-15 — Artist picks as on iOS

**Status: COMPLETE** (19 Nov 2026). [Issue #43](https://github.com/marcus-gt/maekster-android/issues/43) under #31. Branch `ux/01-library-layout`, started at `8f5f9b7b` (UX-14 committed). Owner decision (18 Nov 2026): Artist picks behave as on iOS (`ArtistShelfPicker`).

## Implemented

- **`MixDao` pools**, over pickable artists only: credited, `albumCount > 0`, a non-blank name, and not "Various Artists" or "[unknown artist]" (case and spacing ignored).
  - `artistPickPlayed`: more than 2 plays, by play count, limit 40.
  - `artistPickDormant(cutoff)`: more than 2 plays, `played` before the cutoff or null, limit 26.
  - `artistPickFresh(day)`: at most 2 plays, in daily hash order, limit 12.
  - `artistPickEarliestPlay`.
  - The old starred, dormant and unplayed queries were removed.
- **`data/mix/ArtistPicker`**, a pure port of iOS:
  - 6 picked from the 18 most played.
  - 4 dormant, picked from 20 after removing the chosen favourites.
  - fresh artists fill the rest, up to 12.
  - fill-up order: favourites, dormant, played, fresh.
  - a final shuffle with a seeded `Random(day * 1_000_003 + sourceId)`.
  - `dormancy()` is 90 days, or half the play history, but at least 14 days (epoch seconds).
- `LibraryRepository.artistPicks(sourceId, day, now)` derives the cutoff from the earliest play and combines the pools. `HomeViewModel` keys it on the local epoch day and re-emits at local midnight (`localHomeDays`).
- Kept as an intentional Android difference: picks come from Room, so they also work without a network. Explicit offline mode still shows the downloads-only Home.

## Verification

- New tests: `LibraryHomeDaoTest.artistPickPoolsRankByPlaysExcludeNonArtistsAndStayInTheirSource` and `ArtistPickerTest` (4 tests). Both pass.
- Mutations, each caught and then restored:
  - Taking 4 favourites instead of 6 failed 2 picker tests.
  - Dropping the "Various Artists" exclusion failed the DAO test.

- Passed, on a clean rerun: `ktlintFormat`, `assembleDebug`, `testDebugUnitTest` (1956 tests, 0 failures), `ktlintCheck`, `lintDebug`. One earlier full run failed with `NoSuchFileException` in the test-results directory. Two IDE-"timed out" test runs had actually started late and overlapped it. The code was not at fault.
- Emulator (Pixel 9, real library): Artist picks showed often-played artists (Joni Mitchell, Boys Noize, Miles Davis and others) instead of the previous starred and never-played mix.

## Unverified

- The shelf size of 12 was not counted on the device; the unit tests cover it.
- The midnight rollover was not observed live.

## Next

No Home recommendation follow-up remains, apart from In heavy rotation parity (#42). That was not requested and needs an owner decision.
