# UX-21 — Automatically load similar artists on artist pages

**Status: COMPLETE.** [#51](https://github.com/marcus-gt/maekster-android/issues/51), parent #45.
Branch `ux/45-artist-now-playing`, based on `7745bbc0` (UX-20). The separate M1.2 worktree
was clean and detached.

## Implementation

- The owner requested automatic loading and removal of the artist-page button in #45. The iOS
  artist route loads recommendations on open. The Android artist route now obtains its
  destination-scoped ViewModel before composing the lazy shelf. The album page keeps its manual
  action through the same components.
- The ViewModel reads Room first, keeps cached cards visible while checking freshness online,
  deduplicates source/connectivity emissions, and cancels the old request on a source or offline
  change. The existing cache serializes provider calls and keeps successful results, including
  empty ones, for 24 hours. D9 records the owner request and the cache policy.
- The automatic shelf shows progress while waiting without a cached result. It stays hidden
  offline without a cache or after an empty result. Cached cards remain available offline.
  The current local artist route and external MusicBrainz links are preserved.

## Verification

- Three new ViewModel tests cover online entry, cache-first display during refresh, and offline
  entry plus reconnect. A new Compose test covers loading, button removal and empty hiding.
  All four failed on behavioral assertions with temporary mutations and passed after restoration.
  Existing Room tests cover fresh-cache and concurrent-request deduplication.
- `:app:ktlintFormat`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:ktlintCheck`,
  `:app:lintDebug` and `git diff --check` passed with Studio's JBR.
- Pixel 9 emulator, Bob Dylan: recommendations appeared with no manual button at 100% and 200%
  font. Tapping The Byrds opened its local artist page, and Back restored Bob Dylan's shelf.
  The shelf survived the font configuration change. Restored 100% and preserved the queue.
- Offline behavior and request counts were exercised in automated tests. The emulator check did
  not disable connectivity, rotate the device, or trace live HTTP calls.

## Next action

UX-22 ([#52](https://github.com/marcus-gt/maekster-android/issues/52)) is the next slice.
